package com.netease.cloud.nos.android.utils;

import android.content.Context;

import com.netease.cloud.nos.android.core.WanAccelerator;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;


public class Http {
    private static OkHttpClient httpClient = null;
    private static OkHttpClient lbsHttpClient = null;

    public static OkHttpClient getHttpClient(Context ctx) {
        OkHttpClient extHttpClient = WanAccelerator.getConf().getHttpClient();
        if (extHttpClient != null)
            return extHttpClient;

        if (httpClient == null) {
            httpClient = buildHttpClient(ctx,
                    WanAccelerator.getConf().getConnectionTimeout(),
                    WanAccelerator.getConf().getSoTimeout());
        }
        return httpClient;
    }

    public static OkHttpClient getLbsHttpClient(Context ctx) {
        OkHttpClient extHttpClient = WanAccelerator.getConf().getHttpClient();
        if (extHttpClient != null)
            return extHttpClient;

        if (lbsHttpClient == null) {
            lbsHttpClient = buildHttpClient(ctx,
                    WanAccelerator.getConf().getLbsConnectionTimeout(),
                    WanAccelerator.getConf().getLbsSoTimeout());
        }
        return lbsHttpClient;
    }

    private static OkHttpClient buildHttpClient(Context context, int connTimeout, int soTimeout) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(10);
        dispatcher.setMaxRequestsPerHost(3);

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };
        HostnameVerifier trustAllHostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            builder.hostnameVerifier(trustAllHostnameVerifier);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return builder.connectTimeout(connTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(soTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(soTimeout, TimeUnit.MILLISECONDS)
                .build();
    }
}
