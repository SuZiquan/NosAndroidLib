# NOS Android SDK
NOS-Android-SDK只包含了安卓客户端使用场景中的必要功能，相比服务端NOS-Java-SDK而言，客户端SDK不会包含对云存储服务的管理和配置功能。该SDK支持不低于2.3的Android版本(API级别9)。

## 实现功能

- 客户端直传：移动端可以将数据直接上传到NOS，不用经过业务方上传服务器；
- 断点续传：网络异常时，可以断点续传，节省用户流量；
- 全国加速节点：遍布全国加速节点，自动选择离用户最近的加速节点；

## 使用步骤

**1. 引入NOS-Android-SDK**

### 依赖配置

在 `build.gradle` 中增加仓库
```gradle
repositories {
    jcenter()
    maven {
        url  "https://dl.bintray.com/nos-sdk/maven"
    }
}
```

在 `build.gradle` 中的 `dependencies` 中加入以下依赖。NOS Android SDK使用OkHttp进行网络传输。

```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:3.12.6'
    implementation 'com.netease.cloud.nos.android:nos-android-sdk:0.0.1'
}
```

### 权限配置
NOS Android SDK 需要读取文件和获取网络状态权限，需要在 `AndroidManifest.xml` 中加入以下权限配置：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 兼容性相关
Android 9 开始 [默认启用网络传输层安全协议 (TLS)](https://developer.android.com/about/versions/pie/android-9.0-changes-28?hl=zh-cn#tls-enabled)，明文传输默认禁止。NOS在获取直传节点时，使用HTTP明文传输。需要在 `AndroidManifest.xml` 的 `application` 中加入 `android:usesCleartextTraffic="true"` 以允许明文传输，如下所示。

```xml
<application
    android:label="@string/app_name"
    android:usesCleartextTraffic="true">
</application>
```


**2. 增加直传统计服务配置**

*   直传统计服务运行于独立进程

``` xml
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application>
        <!-- 直传统计监控服务：默认情况下120秒发送一次统计信息，没有数据则不发送，运行于独立进程 -->
        <service android:name="com.netease.cloud.nos.android.service.MonitorService" 
             android:process=":MonitorService" >
        </service>

        <!-- 用于接收网络状态改变事件，例如：wifi、2G、3G的切换 -->
        <receiver
            android:name="com.netease.cloud.nos.android.receiver.ConnectionChangeReceiver"
            android:label="NetworkConnection" >
            <intent-filter>
                <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

*   直传统计服务非独立进程

``` xml
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application>
        <!-- 直传统计监控服务：默认情况下120秒发送一次统计信息，没有数据则不发送，非独立进程 -->
        <service android:name="com.netease.cloud.nos.android.service.MonitorService" />

        <!-- 用于接收网络状态改变事件，例如：wifi、2G、3G的切换 -->
        <receiver
            android:name="com.netease.cloud.nos.android.receiver.ConnectionChangeReceiver"
            android:label="NetworkConnection" >
            <intent-filter>
                <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

*Manifest修改说明：*

1. 当客户端网络状态发生改变时，NOS-Android-SDK需要在下次文件上传时重新发起一次边缘节点查询，以便获取到最佳的接入节点，实现最优上传加速效果；
2. 为了实现覆盖全体用户的最佳上传效果，我们采用实时监控，动态调优的方式；客户端通过MonitorService将用户上传数据推送给NOS，以便NOS决策，给出推荐的接入点；MonitorService可以根据需要配置成运行于独立进程或非独立进程，可参考上面的Manifest修改。

**3. 使用SDK加速上传**

*3.1. 上传加速配置*

	/**
	 * 对NOS上传加速Android-SDK进行配置，请在初始化时设置配置，初始化完成后修改配置是无效的
	 */
	AcceleratorConf conf = new AcceleratorConf();
	 
	// SDK会根据网络类型自动调整上传分块大小，如果网络类型无法识别，将采用设置的上传分块大小
	// 默认32K，如果网络环境较差，可以设置更小的分块
	// ChunkSize的取值范围为：[4K, 4M]，不在范围内将抛异常InvalidChunkSizeException
	conf.setChunkSize(1024 * 32);
	 
	// 设置分块上传失败时的重试次数，默认2次
	// 如果设置的值小于或等于0，将抛异常InvalidParameterException
	conf.setChunkRetryCount(2);
	 
	// 设置文件上传socket连接超时，默认为10s
	// 如果设置的值小于或等于0，将抛异常InvalidParameterException
	conf.setConnectionTimeout(10 * 1000);
	 
	// 设置文件上传socket读写超时，默认30s
	// 如果设置的值小于或等于0，将抛异常InvalidParameterException
	conf.setSoTimeout(30 * 1000);
	
	// 设置LBS查询socket连接超时，默认为10s
	// 如果设置的值小于或等于0，将抛异常InvalidParameterException
	conf.setLbsConnectionTimeout(10 * 1000);
	 
	// 设置LBS查询socket读写超时，默认10s
	// 如果设置的值小于或等于0，将抛异常InvalidParameterException
	conf.setLbsSoTimeout(10 * 1000);
			
	// 设置刷新上传边缘节点的时间间隔，默认2小时
	// 合法值为大于或等于60s，设置非法将采用默认值
	// 注：当发生网络切换，Android-SDK会在下次上传文件时做一次接入点刷新
	conf.setRefreshInterval(DateUtils.HOUR_IN_MILLIS * 2;);
	 
	// 设置统计监控程序统计发送间隔，默认120s
	// 合法值为大于或等于60s，设置非法将采用默认值
	conf.setMonitorInterval(120 * 1000);

    // 设置httpClient，默认值为null
    // 非null：使用设置的httpClient进行文件上传和统计信息上传
    // null：使用sdk内部的机制进行文件上传和统计信息上传
    conf.setHttpClient(httpClient);
 
    // 设置是否用线程进行统计信息上传，默认值为false
    // true：创建线程进行统计信息上传
    // false：使用service进行统计信息上传
    conf.setMonitorThread(true);
 
	// 配置赋值给上传加速类
	WanAccelerator.setConf(conf);

*3.2. 构造上传对象*


	/**
	 * 构造上传对象类
	 * 注意：uploadToken必须从业务服务器获取，而不是直接在客户端使用AccessKey/SecretKey生成对应的凭证
	 */
	String uploadToken = "I_AM_UPLOAD_TOKEN_FROM_APP_SERVER";
	WanNOSObject wanNOSObject = new WanNOSObject();
	wanNOSObject.setNosBucketName("YOUR_BUCKET_NAME");
	wanNOSObject.setNosObjectName("YOUR_OBJECT_NAME");
	wanNOSObject.setContentType("image/jpeg")     // 请根据实际情况设置正确的MIME-TYPE
	wanNOSObject.setUploadToken(uploadToken);

*3.3. 执行上传*

1. 支持http和https的上传，分别对应putFileByHttp和putFileByHttps；
2. 如果使用https上传失败，内部会退化为http上传，无需外部干涉，对用户透明；
3. 内部使用AsyncTask实现异步操作；

核心代码示例如下：（具体可参见[sample工程](https://git.hz.netease.com/nos/nos-android-sdk/tree/master/nos-android-sdk/src/com/example/nos_android_sdk)）

	 /**
	  * 上传上下文，用于断点续传，如果是新上传则赋值为null
	  * 如果要支持断点续传，需在onUploadContextCreate回调中
	  * 持久化上传上下文信息
	  */
	String uploadContext = null;   
	
	/**
	 * 如果要支持断点续传或者crash后断点重传，需要持久化<filePath,uploadContext>，以SharedPreferences为例
	 * SharedPreferences mPerferences = getDefaultPreferences(context);
	 * String uploadContext = mPerferences.getString(file.getAbsolutePath(), null);
	 */
	 
	UploadTaskExecutor executor = WanAccelerator.putFileByHttp(
	        this.getBaseContext(),                // 把当前Activity传进来
	        f,                                    // 待上传文件对象，支持Uri、File、FileDescriptor、InputStream几种类型
	        f.getAbsolutePath(),                  // 在onUploadContextCreate和onProcess被回调的参数
	                                              // 需要把待上传对象路径传过去
	        uploadContext,                        // 上传上下文，用于断点续传
	        wanNOSObject,                         // 上传对象类，里面封装了桶名、对象名、上传凭证
	        new Callback() {                      // 回调函数类，回调函数在UI线程
	        
	            /**
	             * 正常情况下只回调一次：oldUploadContext为null
	             * 当一次上传花费太长时间时UploadContext可能失效，
	             * 服务端会返回一个新的UploadContext，此时需要更新UploadContext
	             */
	            @Override
	            public void onUploadContextCreate(Object fileParam,
	                    String oldUploadContext,
	                    String newUploadContext) {
	                System.out.println("onUploadContextCreate.......");
	 
	                /**
	                 * 如果要支持断点续传或者crash后断点重传
	                 * SharedPreferences mPerferences =
	                 *           PreferenceManager.getDefaultSharedPreferences(context);
	                 * SharedPreferences.Editor mEditor = mPerferences.edit();
	                 * mEditor.putString("FILE_TO_BE_UPLOADED_PATH", newUploadContext);
	                 * mEditor.commit();
	                 */
	            }
	 
	            /**
	             * 上传进度回调，每上传完一块就调用一次
	             * fileParam: 上传文件的相关参数
	             * current: 当前上传多少
	             * total: 文件总大小
	             */
	            @Override
	            public void onProcess(Object fileParam,
	                    long current, long total) {
	                System.out.println("onProcess.......current = " + current +
	                        ", total = " + total);
	            }
	 
	            /**
	             * 上传成功回调函数: 如果要实现crash后的重传
	             * 使用者需要将持久化的<filePath, uploadContext>删除
	             */
	            @Override
	            public void onSuccess(CallRet ret) {
	                System.out.println("onSuccess......." + ret.getHttpCode());
	            }
	 
	            /**
	             * 上传失败回调函数: CallRet里面有具体的失败信息
	             * 使用者需要将持久化的<filePath, uploadContext>删除
	             */
	            @Override
	            public void onFailure(CallRet ret) {
	                System.out.println("onFailure.......");
	            }
	 
	            /**
	             * 上传取消回调函数: 用户可以在此处做暂停等业务逻辑
	             */
	            @Override
	            public void onCanceled(CallRet ret) {
	                System.out.println("onCanceled.......");
	            }
	 
	});

*3.4. 取消上传*


	/**
	 * 取消上传很简单，调用cancel函数即可，会进入Cancel回调
	 */
	executor.cancel();

## 安全问题

该SDK未包含凭证生成相关的功能。开发者对安全性的控制应遵循安全机制中建议的做法，即客户端应向业务服务器每隔一段时间请求上传凭证（UploadToken），而不是直接在客户端使用AccessKey/SecretKey生成对应的凭证。在客户端使用SecretKey会导致严重的安全隐患。

开发者可以在生成上传凭证前通过配置上传策略以控制上传的后续动作，比如在上传完成后通过回调机制通知业务服务器。该工作在业务服务器端进行，因此非本SDK的功能范畴。完整的上传策略描述请参考《NOS上传加速开发指南》

## 线程安全和并发

Android一般的情况下会使用一个主线程来控制UI，非主线程无法控制UI，在Android4.0+之后不能在主线程完成网络请求，本SDK是根据以上的使用场景设计，所有网络的操作均使用独立的线程异步运行，WanAccelerator.putFileByHttps是在主线程调用的，在回调函数内可以直接操作UI控件。WanAccelerator.putFileByHttps线程安全，可并发调用。
