package com.netease.cloud.nos.android.http;

import android.content.Context;

import com.netease.cloud.nos.android.constants.Code;
import com.netease.cloud.nos.android.core.RequestCallback;
import com.netease.cloud.nos.android.utils.LogUtil;
import com.netease.cloud.nos.android.utils.Util;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpGetTask implements Runnable {

    private static final String LOGTAG = LogUtil.makeLogTag(HttpGetTask.class);

    protected volatile Request.Builder getRequest;

    protected final String url;
    protected final Context ctx;
    protected final Map<String, String> map;
    protected final RequestCallback callback;

    public HttpGetTask(String url, Context ctx, Map<String, String> map,
                       RequestCallback callback) {
        this.url = url;
        this.ctx = ctx;
        this.map = map;
        this.callback = callback;
    }

    @Override
    public void run() {
        ResponseBody httpEntity = null;

        try {
            getRequest = new Request.Builder().get().url(url);
            if (map != null) {
                getRequest = Util.setHeader(getRequest, map);
            }
            Response response = Util.getLbsHttpClient(ctx).newCall(getRequest.build()).execute();

            if (response != null && (httpEntity = response.body()) != null) {
                int statusCode = response.code();
                String result = httpEntity.string();
                JSONObject msg = new JSONObject(result);
                if (statusCode == Code.HTTP_SUCCESS) {
                    LogUtil.d(LOGTAG,
                            "http get response is correct, response: " + result);
                } else {
                    LogUtil.d(LOGTAG, "http get response is failed.");
                }
                callback.onResult(new HttpResult(statusCode, msg, null));
            } else {
                callback.onResult(new HttpResult(Code.HTTP_NO_RESPONSE,
                        new JSONObject(), null));
            }
        } catch (Exception e) {
            LogUtil.e(LOGTAG, "http get task exception", e);
            callback.onResult(new HttpResult(Code.HTTP_EXCEPTION,
                    new JSONObject(), e));
        } finally {
            if (httpEntity != null) {
                httpEntity.close();
            }

            getRequest = null;
        }
    }
}
