package com.netease.cloud.nos.android.core;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import com.netease.cloud.nos.android.constants.Code;
import com.netease.cloud.nos.android.exception.InvalidParameterException;
import com.netease.cloud.nos.android.monitor.MonitorTask;
import com.netease.cloud.nos.android.service.MonitorService;
import com.netease.cloud.nos.android.utils.LogUtil;
import com.netease.cloud.nos.android.utils.Util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public class WanAccelerator {

    private static final String LOGTAG = LogUtil
            .makeLogTag(WanAccelerator.class);

    private static AcceleratorConf conf;
    private static boolean isInit;
    protected static boolean isOpened;
    private static Timer monitorTimer;

    public static Map<String, String> map = new ConcurrentHashMap<String, String>();

    private static void initScheduler(Context ctx) {

        if (WanAccelerator.getConf().isMonitorThreadEnabled()) {
            LogUtil.d(LOGTAG, "init monitor timer");
            monitorTimer = new Timer();
            MonitorTask task = new MonitorTask(ctx);
            monitorTimer.schedule(task, getConf().getMonitorInterval(),
                    getConf().getMonitorInterval());
        } else {
            LogUtil.d(LOGTAG, "init scheduler");
            Intent intent = new Intent(ctx, MonitorService.class);
            PendingIntent pintent = PendingIntent.getService(ctx, 0, intent, 0);
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            am.setRepeating(AlarmManager.RTC, 0, getConf().getMonitorInterval(), pintent);
        }

    }

    public static UploadTaskExecutor putFileByHttp(Context context, File file,
                                                   Object fileParam, String uploadContext, WanNOSObject obj,
                                                   Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, false, obj);
    }

    public static UploadTaskExecutor putFileByHttp(Context context, FileDescriptor file,
                                                   Object fileParam, String uploadContext, WanNOSObject obj,
                                                   Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, false, obj);
    }

    public static UploadTaskExecutor putFileByHttp(Context context, Uri file,
                                                   Object fileParam, String uploadContext, WanNOSObject obj,
                                                   Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, false, obj);
    }

    public static UploadTaskExecutor putFileByHttp(Context context, InputStream file,
                                                   Object fileParam, String uploadContext, WanNOSObject obj,
                                                   Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, false, obj);
    }


    public static UploadTaskExecutor putFileByHttps(Context context, File file,
                                                    Object fileParam, String uploadContext, WanNOSObject obj,
                                                    Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, true, obj);
    }

    public static UploadTaskExecutor putFileByHttps(Context context, FileDescriptor file,
                                                    Object fileParam, String uploadContext, WanNOSObject obj,
                                                    Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, true, obj);
    }

    public static UploadTaskExecutor putFileByHttps(Context context, Uri file,
                                                    Object fileParam, String uploadContext, WanNOSObject obj,
                                                    Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, true, obj);
    }

    public static UploadTaskExecutor putFileByHttps(Context context, InputStream file,
                                                    Object fileParam, String uploadContext, WanNOSObject obj,
                                                    Callback callback) throws InvalidParameterException {
        return put(context, obj.getUploadToken(), obj.getNosBucketName(),
                obj.getNosObjectName(), file, fileParam, uploadContext,
                callback, true, obj);
    }

    private static UploadTaskExecutor put(Context context, String uploadToken,
                                          String bucketName, String fileName, Object file, Object fileParam,
                                          String uploadContext, Callback callback, boolean isHttps,
                                          WanNOSObject obj) throws InvalidParameterException {
        InputStream inputStream = null;
        try {
            try {
                Util.checkParameters(context, file, fileParam, obj, callback);
                if (file instanceof InputStream) {
                    inputStream = (InputStream) file;
                } else if (file instanceof File) {
                    inputStream = new FileInputStream((File) file);
                } else if (file instanceof Uri) {
                    inputStream = context.getContentResolver().openInputStream((Uri) file);
                } else if (file instanceof FileDescriptor) {
                    inputStream = new FileInputStream((FileDescriptor) file);
                }
            } catch (FileNotFoundException e) {
                throw new InvalidParameterException("file not found");
            }
        } catch (InvalidParameterException e) {
            Util.closeInputStream(inputStream);
            throw e;
        }

        if (!isInit) {
            isInit = true;
            initScheduler(context);
        }
        try {
            UploadTask task = new UploadTask(context, uploadToken, bucketName,
                    fileName, inputStream, fileParam, uploadContext, callback,
                    isHttps, obj);

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                task.execute();
            } else {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            return new UploadTaskExecutor(task);
        } catch (Exception e) {
            Util.closeInputStream(inputStream);
            callback.onFailure(new CallRet(fileParam, uploadContext,
                    Code.UNKNOWN_REASON, "", "", null, e));
            return null;
        }
    }

    public static void setConf(AcceleratorConf conf) {
        WanAccelerator.conf = conf;
    }

    public static AcceleratorConf getConf() {
        if (conf == null) {
            conf = new AcceleratorConf();
        }
        return conf;
    }
}
