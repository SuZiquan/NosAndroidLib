package com.netease.cloud.nos.android.http;

import android.content.Context;

import com.netease.cloud.nos.android.constants.Code;
import com.netease.cloud.nos.android.constants.Constants;
import com.netease.cloud.nos.android.utils.LogUtil;
import com.netease.cloud.nos.android.utils.Util;

import org.json.JSONObject;

import java.util.concurrent.Callable;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpPostTask implements Callable<HttpResult> {

	private static final String LOGTAG = LogUtil.makeLogTag(HttpPostTask.class);

	protected volatile Request.Builder postRequest;

	protected final String url;
	protected final String token;
	protected final Context ctx;
	protected final byte[] chunkData;

	public HttpPostTask(String url, String token, Context ctx, byte[] chunkData) {
		this.url = url;
		this.token = token;
		this.ctx = ctx;
		this.chunkData = chunkData;
	}

	@Override
	public HttpResult call() throws Exception {
		LogUtil.d(LOGTAG, "http post task is executing");
		HttpResult rs = null;
		try {
			postRequest = new Request.Builder().post(RequestBody.create(null, chunkData))
					.url(url)
					.addHeader(Constants.HEADER_TOKEN, token);
			Response response = Util.getHttpClient(ctx).newCall(postRequest.build()).execute();

			ResponseBody responseBody = null;
			if (response != null && (responseBody = response.body()) != null) {
				int statusCode = response.code();
				String result = responseBody.string();
				if (statusCode == Code.HTTP_SUCCESS) {
					LogUtil.d(LOGTAG,
							"http post response is correct, response: "
									+ result);
				} else {
					rs = new HttpResult(statusCode, null, null);
					LogUtil.d(LOGTAG,
							"http post response is failed, status code: "
									+ statusCode + ", result: " + result);
				}
				rs = new HttpResult(statusCode, new JSONObject(result), null);
			} else {
				rs = new HttpResult(Code.HTTP_NO_RESPONSE, null, null);
			}
		} catch (Exception e) {
			LogUtil.e(LOGTAG, "http post exception", e);
			rs = new HttpResult(Code.HTTP_EXCEPTION, null, e);
		} finally {
			postRequest = null;
		}
		return rs;
	}

}
