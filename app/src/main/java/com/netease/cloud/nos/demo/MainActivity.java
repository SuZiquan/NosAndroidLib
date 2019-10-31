package com.netease.cloud.nos.demo;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.netease.cloud.nos.android.core.AcceleratorConf;
import com.netease.cloud.nos.android.core.CallRet;
import com.netease.cloud.nos.android.core.Callback;
import com.netease.cloud.nos.android.core.UploadTaskExecutor;
import com.netease.cloud.nos.android.core.WanAccelerator;
import com.netease.cloud.nos.android.core.WanNOSObject;
import com.netease.cloud.nos.android.utils.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;

    private static final int FILE_SELECT_CODE = 0;

    private static final String TAG = "ChooseFile";

    private String accessKey = "your accessKey";
    private String secretKey = "your secretKey";
    private String bucketName = "your bucketName";

    private NosUploadTask nosUploadTask;

    // <uriPath + objectName, uploadContext>
    private final Map<String, String> uploadContextMap = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btn = findViewById(R.id.upload);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectFileToUpload();
            }
        });

        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle("上传中...");

        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "取消上传", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                nosUploadTask.cancel(true);
            }
        });
    }

    private void selectFileToUpload() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select a File to Upload"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    nosUploadTask = new NosUploadTask();
                    nosUploadTask.execute(data.getData());
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class NosUploadTask extends AsyncTask<Uri, Integer, List<CallRet>> {

        @Override
        protected List<CallRet> doInBackground(Uri... uris) {
            List<CallRet> result = new ArrayList<>(uris.length);
            AcceleratorConf conf = new AcceleratorConf();
            try {
                conf.setChunkSize(1024 * 32);
                conf.setChunkRetryCount(2);
            } catch (Exception e) {
                e.printStackTrace();
            }
            WanAccelerator.setConf(conf);

            for (final Uri uri : uris) {
                try {
                    Log.d(TAG, "File Uri: " + uri.toString());
                    final String objectName = getFileName(uri);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            progressDialog.setTitle("正在上传 " + objectName + "");
                        }
                    });
                    publishProgress(0);

                    long expires = System.currentTimeMillis() + 3600 * 24 * 30 * 12 * 10 * 1000L;
                    String uploadToken = Util.getToken(bucketName, objectName, expires, accessKey, secretKey, null, null);
                    WanNOSObject wanNOSObject = new WanNOSObject();
                    wanNOSObject.setNosBucketName(bucketName);
                    wanNOSObject.setNosObjectName(objectName);
                    wanNOSObject.setUploadToken(uploadToken);
                    String uploadContext = null;
                    final String uploadContextKey = uri.getPath() == null ? null : uri.getPath() + objectName;
                    if (uploadContextKey != null) {
                        uploadContext = uploadContextMap.get(uploadContextKey);
                    }
                    UploadTaskExecutor executor = WanAccelerator.putFileByHttps(
                            MainActivity.this.getBaseContext(),                // 把当前Activity传进来
                            uri,                                    // 待上传文件对象 f = new File("FILE_TO_BE_UPLOADED_PATH")
                            new Object[]{bucketName, objectName},  // 在onUploadContextCreate和onProcess被回调的参数
                            uploadContext,                        // 上传上下文，用于断点续传
                            wanNOSObject,                         // 上传对象类，里面封装了桶名、对象名、上传凭证
                            new Callback() {                      // 回调函数类，回调函数在UI线程

                                @Override
                                public void onUploadContextCreate(Object fileParam,
                                                                  String oldUploadContext,
                                                                  String newUploadContext) {
                                    System.out.println("onUploadContextCreate.......");
                                    if (uploadContextKey != null) {
                                        uploadContextMap.put(uploadContextKey, newUploadContext);
                                    }
                                }

                                @Override
                                public void onProcess(Object fileParam,
                                                      long current, long total) {
                                    System.out.println("onProcess.......current = " + current +
                                            ", total = " + total);

                                    publishProgress((int) ((current * 1.0 / total) * 100));
                                }

                                @Override
                                public void onSuccess(CallRet ret) {
                                    System.out.println("onSuccess......." + ret.getHttpCode());
                                }

                                @Override
                                public void onFailure(CallRet ret) {
                                    System.out.println("onFailure.......");
                                }

                                @Override
                                public void onCanceled(CallRet ret) {
                                    System.out.println("onCanceled.......");
                                }

                            });

                    CallRet callRet = executor.get();
                    executor.cancel();
                    if (callRet != null && callRet.isOK()) {
                        uploadContextMap.remove(uploadContextKey);
                    }
                    result.add(callRet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return result;
        }

        protected void onPreExecute() {
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(List<CallRet> callRets) {
            progressDialog.dismiss();
            int succeed = 0;
            int failed = 0;
            for (CallRet callRet : callRets) {
                if (callRet != null && callRet.isOK()) {
                    succeed++;
                } else {
                    failed++;
                }
            }
            Toast.makeText(MainActivity.this, "上传完成，成功 " + succeed + " 个，失败 " + failed + " 个", Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressDialog.setProgress(values[0]);
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result == null) {
                return null;
            }
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
