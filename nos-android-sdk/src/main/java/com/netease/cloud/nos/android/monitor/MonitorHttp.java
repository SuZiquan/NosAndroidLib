package com.netease.cloud.nos.android.monitor;

import android.content.Context;

import com.netease.cloud.nos.android.constants.Code;
import com.netease.cloud.nos.android.utils.LogUtil;
import com.netease.cloud.nos.android.utils.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class MonitorHttp {

    private static final String LOGTAG = LogUtil.makeLogTag(MonitorHttp.class);

    public static void post(Context ctx, String url) {
        List<StatisticItem> list = Monitor.get();
        ByteArrayOutputStream bos = Monitor.getPostData(list);
        if (bos == null) {
            LogUtil.d(LOGTAG, "post data is null");
            return;
        }

        Request.Builder postMethod = new Request.Builder()
                .post(RequestBody.create(null, bos.toByteArray()))
                .url(Util.getMonitorUrl(url))
                .addHeader("Content-Encoding", "gzip");

        ResponseBody responseBody = null;
        try {
            Response response = Util.getHttpClient(ctx).newCall(postMethod.build()).execute();
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
                                    + statusCode + ", result: " + responseBody.string());
                }
            }
        } catch (IOException e) {
            LogUtil.e(LOGTAG, "post monitor data failed with io exception", e);
        } finally {
//			Monitor.clean();
            if (list != null)
                list.clear();

            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    LogUtil.e(LOGTAG, "bos close exception", e);
                }
            }
            if (responseBody != null) {
                responseBody.close();
            }
        }

    }
}
