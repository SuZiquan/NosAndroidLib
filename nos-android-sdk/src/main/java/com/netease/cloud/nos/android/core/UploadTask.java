package com.netease.cloud.nos.android.core;

import android.content.Context;
import android.os.AsyncTask;

import com.netease.cloud.nos.android.constants.Code;
import com.netease.cloud.nos.android.constants.Constants;
import com.netease.cloud.nos.android.exception.InvalidOffsetException;
import com.netease.cloud.nos.android.http.HttpResult;
import com.netease.cloud.nos.android.monitor.Monitor;
import com.netease.cloud.nos.android.monitor.StatisticItem;
import com.netease.cloud.nos.android.utils.FileDigest;
import com.netease.cloud.nos.android.utils.LogUtil;
import com.netease.cloud.nos.android.utils.NetworkType;
import com.netease.cloud.nos.android.utils.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class UploadTask extends AsyncTask<Object, Object, CallRet> {

    private static final String LOGTAG = LogUtil.makeLogTag(UploadTask.class);
    private static final String HTTP_REQUEST_TAG = "UploadTask";

    protected volatile Request.Builder post;
    protected volatile Request.Builder get;
    private volatile boolean upCancelled = false;

    private Context context;
    private String token;
    private String bucketName;
    private String fileName;
    private InputStream inputStream;
    private long fileSize;
    private Object fileParam;
    private String uploadContext;
    private Callback callback;
    private boolean isHttps;
    private WanNOSObject meta;
    private String MD5 = null;

    private long offset;
    private StatisticItem item;

    public UploadTask(Context context, String uploadToken, String bucketName,
                      String fileName, InputStream inputStream, Object fileParam, String uploadContext,
                      Callback callback, boolean isHttps, WanNOSObject meta) {
        this.context = context;
        this.token = uploadToken;
        this.bucketName = bucketName;
        this.fileName = fileName;
        this.inputStream = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
        try {
            this.fileSize = inputStream.available();
        } catch (IOException e) {
            this.fileSize = 0;
        }
        this.fileParam = fileParam;
        this.uploadContext = uploadContext;
        this.callback = callback;
        this.isHttps = isHttps;
        this.item = new StatisticItem();
        this.meta = meta;
        this.MD5 = meta.getContentMD5();

        if (this.MD5 == null
                && (this.fileSize <= WanAccelerator.getConf()
                .getMd5FileMaxSize())) {
            this.MD5 = FileDigest.getFileMD5(this.inputStream);
        }

    }

    @Override
    protected CallRet doInBackground(Object... params) {
        try {
            NetworkType netType = NetworkType.getNetWorkType(context);
            item.setNetEnv(netType.getNetworkType());
            item.setClientIP(Util.getIPAddress());
            item.setBucketName(bucketName);

            // query LBS to get uploader IP
            HttpResult result = queryLBS(netType.getNetworkType());
            if (result != null && result.getStatusCode() != Code.HTTP_SUCCESS
                    && Util.getData(context, bucketName + Constants.UPLOAD_SERVER_KEY) == null) {
                return new CallRet(fileParam, uploadContext,
                        result.getStatusCode(), Util.getResultString(result,
                        "requestID"), Util.getResultString(result,
                        "callbackRetMsg"), result.getMsg().toString(), null);
            }

            long start = System.currentTimeMillis();
            HttpResult postResult = doUpload(netType.getChunkSize());
            if (postResult == null) {
                postResult = new HttpResult(Code.SERVER_ERROR, new JSONObject(), null);
            }
            long end = System.currentTimeMillis();
            float speed = (float) (((fileSize - offset) / 1024.0) / ((end - start) / 1000.0));
            LogUtil.w(LOGTAG, "upload result:" + postResult.getStatusCode() + ", speed:" + speed + "KB/S");

            item.setUploaderUseTime(end - start);
            item.setUploaderHttpCode(Util.getHttpCode(postResult));

            if (postResult.getStatusCode() != Code.HTTP_SUCCESS && !upCancelled) {
                Util.setBooleanData(context, bucketName + Constants.LBS_STATUS, false);
            }

            return new CallRet(fileParam, uploadContext,
                    postResult.getStatusCode(), Util.getResultString(
                    postResult, "requestID"), Util.getResultString(
                    postResult, "callbackRetMsg"), postResult.getMsg()
                    .toString(), null);
        } catch (Exception e) {
            LogUtil.e(LOGTAG, "upload exception", e);
            return new CallRet(fileParam, uploadContext, Code.HTTP_EXCEPTION,
                    "", "", null, e);
        }
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        LogUtil.d(LOGTAG, "on process update");
        long current = (Long) values[0];
        long total = (Long) values[1];
        callback.onProcess(fileParam, current, total);
    }

    @Override
    protected void onPostExecute(CallRet ret) {
        LogUtil.d(LOGTAG, "on post executed");
        if (ret == null) {
            failureOperation(new CallRet(fileParam, uploadContext,
                    Code.UNKNOWN_REASON, "", "", "result is null", null));
            return;
        }
        if (ret.getException() != null) {
            failureOperation(ret);
        } else if (ret.getHttpCode() == Code.HTTP_SUCCESS) {
            successOperation(ret);
        } else {
            failureOperation(ret);
        }
    }

    @Override
    protected void onCancelled() {
        LogUtil.d(LOGTAG, "on cancelled");
        item.setUploaderSucc(Code.MONITOR_CANCELED);
        item.setUploaderHttpCode(Code.UPLOADING_CANCEL);
        Monitor.add(context, item);
        callback.onCanceled(createCancelCallRet());
    }

    private HttpResult queryLBS(String netType) {
        String curNetType = Util.getData(context, bucketName + Constants.NET_TYPE);
        if (curNetType == null || !curNetType.equals(netType)) {
            LogUtil.d(LOGTAG, "network connection change for bucket " + bucketName);
            Util.setBooleanData(context, bucketName + Constants.LBS_STATUS, false);
            Util.setData(context, bucketName + Constants.NET_TYPE, netType);
        }

        if (Util.getBooleanData(context, bucketName + Constants.LBS_STATUS)
                && Util.getData(context, bucketName + Constants.UPLOAD_SERVER_KEY) != null
                && (Util.getLongData(context, bucketName + Constants.LBS_TIME)
                + WanAccelerator.getConf().getRefreshInterval() > System.currentTimeMillis())
                && WanAccelerator.isOpened) {
            return null;
        }

        WanAccelerator.isOpened = true;
        LogUtil.d(LOGTAG, "get lbs address");
        long start = System.currentTimeMillis();
        // executeQueryTask 函数里确保返回非null, 且result.getMsg()也是非null
        HttpResult result = IOManager.getLBSAddress(context, bucketName, true);
        long end = System.currentTimeMillis();
        item.setLbsUseTime(end - start);
        if (result.getStatusCode() == Code.HTTP_SUCCESS) {
            JSONObject msg = result.getMsg();
            try {
                item.setLbsIP(msg.getString("lbs"));
            } catch (Exception e) {
                e.printStackTrace();
                LogUtil.e(LOGTAG, "Failed to parse LBS result: " + e.getMessage());
            }
        } else {
            item.setLbsSucc(Code.MONITOR_FAIL);
            item.setLbsHttpCode(Util.getHttpCode(result));
        }

        return result;
    }

    private HttpResult doUpload(int chunkSize) {
        boolean isFallback = false;

        LogUtil.d(LOGTAG, "file parameters: ContentMD5=" + meta.getContentMD5()
                + ", realMD5=" + this.MD5 + ", ContentType=" + meta.getContentType()
                + ", chunkSize=" + chunkSize);

        try {
            if (uploadContext != null && !uploadContext.equals("")) {
                // getBreakOffset 函数确保返回非null
                // 且offsetResult.getMsg()也是非null
                HttpResult offsetResult = getBreakOffset(context, bucketName,
                        fileName, uploadContext, token, isHttps);
                if (offsetResult.getStatusCode() == Code.CACHE_EXPIRED) {
                    uploadContext = null;
                } else if (offsetResult.getStatusCode() == Code.HTTP_SUCCESS) {
                    offset = offsetResult.getMsg().getInt("offset");
                } else {
                    // Don't record upload time
                    return offsetResult;
                }
            }
            if ((offset >= fileSize && fileSize != 0) || offset < 0) {
                // Don't record upload time
                return new HttpResult(Code.INVALID_OFFSET, new JSONObject(),
                        new InvalidOffsetException("offset is invalid in server side, with offset:"
                                + offset + ", file length: " + fileSize));
            }

            HttpResult postResult = putFile(context, inputStream, offset, chunkSize,
                    bucketName, fileName, token, uploadContext, isHttps);

            if (isFallback && postResult.getStatusCode() == Code.HTTP_SUCCESS) {
                // stop pipeline for a while
            }

            // save upload type
            item.setUploadType(isFallback ? Constants.UPLOAD_TYPE_MIXED : Constants.UPLOAD_TYPE_NORMAL);
            return postResult;
        } catch (Exception e) {
            LogUtil.e(LOGTAG, "offset result exception", e);
            return new HttpResult(Code.HTTP_EXCEPTION, new JSONObject(), e);
        }

    }


    private HttpResult executeQueryTask(String url, Context ctx,
                                        Map<String, String> map) {
        ResponseBody responseBody = null;

        try {
            get = new Request.Builder().get()
                    .url(url)
                    .tag(HTTP_REQUEST_TAG);
            if (map != null) {
                get = Util.setHeader(get, map);
            }
            Response response = Util.getHttpClient(ctx).newCall(get.build()).execute();

            if (response != null
                    && (responseBody = response.body()) != null) {
                int statusCode = response.code();
                String result = responseBody.string();
                JSONObject msg = new JSONObject(result);
                if (statusCode == Code.HTTP_SUCCESS) {
                    LogUtil.d(LOGTAG,
                            "http get response is correct, response: " + result);
                } else {
                    LogUtil.d(LOGTAG, "http get response is failed.");
                }
                return new HttpResult(statusCode, msg, null);
            } else {
                return new HttpResult(Code.HTTP_NO_RESPONSE, new JSONObject(), null);
            }
        } catch (Exception e) {
            LogUtil.e(LOGTAG, "http get task exception", e);
            return new HttpResult(Code.HTTP_EXCEPTION, new JSONObject(), e);
        } finally {
            if (responseBody != null) {
                responseBody.close();
            }
            get = null;
        }
    }

    private HttpResult getBreakOffset(Context ctx, String bucketName,
                                      String fileName, String uploadContext, String token, boolean isHttps) {
        String[] uploadServers = Util.getUploadServer(ctx, bucketName, isHttps);
        LogUtil.d(LOGTAG, "upload servers: " + Arrays.toString(uploadServers));
        Map<String, String> map = new HashMap<String, String>();
        map.put(Constants.HEADER_TOKEN, token);
        HttpResult result = null;
        try {
            for (String s : uploadServers) {
                String url = Util.buildQueryUrl(s, bucketName, fileName,
                        uploadContext);
                LogUtil.d(LOGTAG, "break query upload server url: " + url);
                // retryQuery必须返回非null
                result = retryQuery(url, ctx, map);
                if (upCancelled) {
                    return result;
                }
                if (result.getStatusCode() == Code.HTTP_SUCCESS
                        || result.getStatusCode() == Code.CACHE_EXPIRED) {
                    return result;
                }
            }
        } catch (Exception ex) {
            LogUtil.e(LOGTAG, "get break offset exception", ex);
            if (result == null) {
                result = new HttpResult(Code.HTTP_EXCEPTION, new JSONObject(), null);
            }
        }
        return result;
    }

    private HttpResult retryQuery(String url, Context ctx,
                                  Map<String, String> map) throws JSONException {
        int retries = WanAccelerator.getConf().getQueryRetryCount();
        int count = 0;
        HttpResult result = null;
        while (count++ < retries && !upCancelled) {
            LogUtil.d(LOGTAG, "query offset with url: " + url
                    + ", retry times: " + count);
            // executeQueryTask 必须返回非null
            result = executeQueryTask(url, ctx, map);
            if (result.getStatusCode() == Code.HTTP_SUCCESS) {
                JSONObject msg = result.getMsg();
                LogUtil.d(LOGTAG, "get break offset result:" + msg.toString());
                return result;
            } else {
                item.setQueryRetryCount(item.getQueryRetryCount() + 1);
            }
            if (result.getStatusCode() == Code.CACHE_EXPIRED) {
                LogUtil.d(LOGTAG, "upload file is expired in server side.");
                return result;
            }
        }
        return result;
    }

    private HttpResult putFile(Context ctx, InputStream inputStream, long offset, int chunkSize,
                               String bucketName, String fileName, String token,
                               String uploadContext, boolean isHttps) throws IOException {
        final long len = inputStream.available();

        inputStream.skip(offset);
        inputStream.mark(chunkSize);
        item.setFileSize(len);
        LogUtil.d(LOGTAG, "file length is: " + len);
        boolean flag = true;
        int result = Constants.CODE_RETRY;
        HttpResult httpResult = null;
        this.uploadContext = uploadContext;
        try {
            int count = 0;
            while (flag && ((offset < len) || (offset == 0 && len == 0)) && !upCancelled) {
                boolean isLast = false;
                int lg = (int) Math.min(chunkSize, len - offset);
                LogUtil.d(LOGTAG, "upload block size is: " + lg);
                String[] uploadServers = Util.getUploadServer(ctx, bucketName, isHttps);
                byte[] chunkData = new byte[lg];
                if (inputStream.read(chunkData) == 0) {
                    break;
                }
                int fails = 0;
                for (String s : uploadServers) {
                    if (lg + offset >= len) {
                        LogUtil.d(LOGTAG, "upload block is the last block");
                        isLast = true;
                    }
                    item.setUploaderIP(s);
                    String url = Util.buildPostDataUrl(s, bucketName, fileName,
                            this.uploadContext, offset, isLast);
                    httpResult = retryPutFile(url, token, ctx, chunkData);
                    if (upCancelled) {
                        return httpResult;
                    }
                    result = httpResult.getStatusCode();
                    // put block success
                    if (result == Code.HTTP_SUCCESS) {
                        long oldOffset = offset;
                        offset = httpResult.getMsg().getInt("offset");
                        if (oldOffset + lg != offset) {
                            inputStream.reset();
                            inputStream.skip(offset - oldOffset);
                        }
                        inputStream.mark(chunkSize);
                        String newUploadContext = httpResult.getMsg()
                                .getString("context");
                        if (!newUploadContext.equals(this.uploadContext)) {
                            callback.onUploadContextCreate(fileParam,
                                    this.uploadContext, newUploadContext);
                        }
                        this.uploadContext = newUploadContext;
                        this.publishProgress(offset, fileSize);
                        count++;
                        LogUtil.d(LOGTAG, "http post success, offset: "
                                + offset + ", len: " + len + ", this is "
                                + count + " block uploaded");
                        if (offset == 0 && len == 0) {
                            flag = false;
                        }
                        break;
                    }
                    // put block failed with retry
                    switch (result) {
                        case Code.INVALID_TOKEN:
                            flag = false;
                            LogUtil.w(LOGTAG, "token is expired, token: " + token
                                    + ", offset: " + offset);
                            return httpResult;
                        case Code.CALLBACK_ERROR:
                            flag = false;
                            LogUtil.w(LOGTAG, "callback error.");
                            return httpResult;
                        case Code.BAD_REQUEST:
                            LogUtil.w(LOGTAG, "bad request.");
                            return httpResult;
                        case Code.HTTP_EXCEPTION:
                        case Code.HTTP_NO_RESPONSE:
                        case Code.SERVER_ERROR:
                        default:
                            inputStream.reset();
                            item.setUploadRetryCount(++fails);
                            if (fails >= uploadServers.length) {
                                flag = false;
                                LogUtil.w(LOGTAG,
                                        "upload block failed with all tries, offset: "
                                                + offset);
                            }
                            LogUtil.w(LOGTAG, "http post failed: " + fails);
                            break;
                    }
                }
            }
            return httpResult;
        } catch (Exception e) {
            LogUtil.e(LOGTAG, "upload block exception", e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return httpResult;
    }

    private HttpResult retryPutFile(String url, String token, Context ctx,
                                    byte[] chunkData) {
        int retries = WanAccelerator.getConf().getChunkRetryCount();
        LogUtil.d(LOGTAG, "user set the retry times is : " + retries);
        int count = 0;
        int result = Constants.CODE_RETRY;
        HttpResult httpResult = null;
        try {
            while (count++ < retries && !upCancelled) {
                LogUtil.d(LOGTAG, "put block to server side with url: " + url
                        + ", length: " + chunkData.length + ", retryTime: "
                        + count);
                httpResult = post(url, chunkData);
                if (upCancelled) {
                    return httpResult;
                }
                int code = httpResult.getStatusCode();
                switch (code) {
                    case Code.HTTP_SUCCESS:
                        LogUtil.d(LOGTAG, "http post result is back, result:"
                                + httpResult.toString() + ", retryTime: " + count);
                        JSONObject msg = httpResult.getMsg();
                        if (msg != null && msg.has("context") && msg.has("offset")) {
                            result = httpResult.getMsg().getInt("offset");
                            LogUtil.d(LOGTAG,
                                    "http post result success with context: "
                                            + context + ", offset: " + result);
                        }
                        break;
                    case Code.INVALID_TOKEN:
                        // invalid token without try
                        return httpResult;
                    case Code.CALLBACK_ERROR:
                        // callback error
                        return httpResult;
                    case Code.BAD_REQUEST:
                        return httpResult;
                    case Code.HTTP_EXCEPTION:
                        result = Constants.CODE_HTTP_EXCEPTION;
                        break;
                    case Code.HTTP_NO_RESPONSE:
                        result = Constants.CODE_NO_RESPONSE;
                        break;
                    case Code.SERVER_ERROR:
                        // server error without try this server
                        return httpResult;
                }

                if (result > 0) {
                    LogUtil.d(LOGTAG, "retryPutFile with success result: "
                            + result);
                    return httpResult;
                } else {
                    item.setChunkRetryCount(item.getChunkRetryCount() + 1);
                }
            }
        } catch (Exception e) {
            LogUtil.e(LOGTAG, "put file exception", e);
        }
        return httpResult;
    }

    private HttpResult post(String url, byte[] chunkData) {
        LogUtil.d(LOGTAG, "http post task is executing");
        HttpResult rs = null;
        ResponseBody responseBody = null;

        try {
            post = new Request.Builder()
                    .post(RequestBody.create(null, chunkData))
                    .url(url)
                    .tag(HTTP_REQUEST_TAG);
            post.addHeader(Constants.HEADER_TOKEN, token);
            if (MD5 != null && !MD5.equals("")) {
                post.addHeader("Content-MD5", MD5);
            }
            if (meta != null) {
                Util.addHeaders(post, meta);
            }
            Response response = Util.getHttpClient(context).newCall(post.build()).execute();
            LogUtil.d(LOGTAG, "http post task executing finished");

            if (response != null && (responseBody = response.body()) != null) {
                int statusCode = response.code();
                String result = responseBody.string();
                if (statusCode == Code.HTTP_SUCCESS) {
                    LogUtil.d(LOGTAG,
                            "http post response is correct, response: "
                                    + result);
                } else {
                    LogUtil.d(LOGTAG,
                            "http post response is failed, status code: "
                                    + statusCode);
                }
                rs = new HttpResult(statusCode, new JSONObject(result), null);
            } else {
                rs = new HttpResult(Code.HTTP_NO_RESPONSE, null, null);
            }
        } catch (Exception e) {
            LogUtil.d(LOGTAG, "http post exception", e);
            rs = new HttpResult(Code.HTTP_EXCEPTION, new JSONObject(), e);
        } finally {
            if (responseBody != null) {
                responseBody.close();
            }
            post = null;
        }
        return rs;
    }


    public void cancel() {
        LogUtil.d(LOGTAG, "uploading is canceling");
        upCancelled = true;
        abort();
        this.cancel(true);
        abort();
        this.cancel(true);
    }

    public boolean isUpCancelled() {
        return upCancelled;
    }

    private void abort() {
        for (Call call : Util.getHttpClient(context).dispatcher().queuedCalls()) {
            Object tag = call.request().tag();
            if (tag != null && tag.equals(HTTP_REQUEST_TAG))
                call.cancel();
        }
    }

    private CallRet createCancelCallRet() {
        return new CallRet(fileParam, uploadContext, Code.UPLOADING_CANCEL, "",
                "", "uploading is cancelled", null);
    }

    private void failureOperation(CallRet ret) {
        item.setUploaderSucc(Code.MONITOR_FAIL);
        Monitor.add(context, item);
        callback.onFailure(ret);
    }

    private void successOperation(CallRet ret) {
        item.setUploaderSucc(Code.MONITOR_SUCCESS);
        Monitor.add(context, item);
        callback.onSuccess(ret);
    }

}
