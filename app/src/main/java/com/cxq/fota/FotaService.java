package com.cxq.fota;

import android.app.IntentService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UpdateEngine;
import android.os.RecoverySystem;
import android.os.UpdateEngineCallback;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;



public class FotaService extends Service {

    private final static String TAG = FotaService.class.getSimpleName();

    private static final String KEY_RECOVERY_SYSTEM_UPDATE = "RecoverySystemUpdate";
    private static final String UPDATE_FILE_PATH = "/update.zip";
    private static final String INTERNAL_STORAGE_PHY_PATH = "data/media/0";
    private static final String INTERNAL_STORAGE_MOUNT_PATH = "storage/emulated/0";
    private static final String OTA_PACKAGE_PATH = "data/ota_package";
    private static final String REBOOT_REASON = "reboot-ab-update";
    private static final String DATABASE_KEY_INSTALL_IN_PROGRESS = "install_in_progress";
    private static final String ERROR_NEED_REBOOT = "An update already applied, waiting for reboot";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIF_ID = 1001;
    private Context mContext = null;
    private DownloadNotifier notifier = null;

    private int MINIMUM_LEVEL_POWER =35;
    private NotificationManager notificationManager;
    private Notification notification;
    private NotificationChannel notificationChannel;
    private final static String NOTIFICATION_CHANNEL_ID = "CHANNEL_ID";
    private final static String NOTIFICATION_CHANNEL_NAME = "CHANNEL_NAME";
    private final static int FOREGROUND_ID=1;


    private PowerManager mPowerManager;
    private ProgressDialog mProgressDialog;
    private boolean mInstallationInProgress = false;
    private PowerManager.WakeLock mWakeLock = null;
    private StorageManager mStorageManager;

    private final UpdateEngine mUpdateEngine = new UpdateEngine();
    private final OtaUpdateEngineCallback mOtaUpdateEngineCallback = new OtaUpdateEngineCallback();
    private final boolean mIsVirtualAbSupported = SystemProperties.getBoolean("ro.build.ab_update", false);

    private final Handler handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case 1:
                    if(notifier != null)
                {
                    notifier.showProgress("下载中...",(int)msg.obj,100,false);
                }
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    File ota_zip = new File(Environment.getExternalStorageDirectory(),"update.zip");

    File ota_zip_2 = new File("/data/ota_package","updata.zip");
//    public FotaService() { super("FotaService"); }




    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.d(TAG,"===cxq===onCreate===");
        mContext = this;
        notifier = new DownloadNotifier(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mStorageManager = getSystemService(StorageManager.class);
        //Add for bug1638233 System upgrade is suspended when the screen is stopped
        if (mWakeLock == null) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.setReferenceCounted(false);
        }

//        // 创建通知渠道（Android 8.0+ 必需）
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    "channel_id", "My Foreground Service",
//                    NotificationManager.IMPORTANCE_LOW
//            );
//            getSystemService(NotificationManager.class).createNotificationChannel(channel);
//        }

//        // 构建通知（Android 12+ 需指定 PendingIntent.FLAG_IMMUTABLE）
//        Intent notificationIntent = new Intent(this, MainActivity.class);
//        PendingIntent pendingIntent = PendingIntent.getActivity(
//                this, 0, notificationIntent,
//                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
//        );
//
//        Notification notification = new NotificationCompat.Builder(this, "channel_id")
//                .setContentTitle("服务运行中")
//                .setContentText("正在执行后台任务...")
//                .setSmallIcon(R.drawable.ic_launcher_foreground)
//                .setContentIntent(pendingIntent)
//                .build();
//
//        // 启动前台服务（ID 不可为 0）
//        startForeground(1, notification);



        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("准备下载…", 0, 0, true));

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    "fota_channel", "cxq系统更新", NotificationManager.IMPORTANCE_LOW);
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            manager.createNotificationChannel(channel);
//        }
//        Notification notification = new NotificationCompat.Builder(this, "fota_channel")
//                .setContentTitle("cxq系统更新")
//                .setContentText("cxq正在检查更新…")
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setSmallIcon(R.drawable.ic_launcher_background)
//                .build();
//        startForeground(101, notification);

    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }


    private Notification buildNotification(String text, int progress, int max, boolean indeterminate) {

//        PendingIntent pendingIntent =
//                PendingIntent.getActivity(this, 0, notificationIntent,
//                        PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("cxq系统更新")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        if (indeterminate || max > 0) {
            builder.setProgress(max, progress, indeterminate);
        }
        return builder.build();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public int onStartCommand(Intent intent,  int flags, int startId) {
        Log.d(TAG,"===cxq===onStartCommand===");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        intent = new Intent(getApplicationContext(), MainActivity.class);  //点击通知栏后想要被打开的页面MainActivity.class
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);  //点击通知栏触发跳转
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("标题：测试文案")
                    .setContentText("内容：你好,点击打开app主页")
                    .setContentIntent(pendingIntent)
                    .build();
        }
        notification.flags |= Notification.FLAG_NO_CLEAR;
        startForeground(FOREGROUND_ID, notification);



        notifier = new DownloadNotifier(mContext);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String ota_file = hasNewVersion();
                if ("".equals(ota_file) ? false : true) {
                    File otaFile = downloadOtaPackage(ota_file);
                    if (otaFile != null && verifyOtaPackage(otaFile)) {
//                copyToOtaPackage(otaFile);
//                copyToExternalPublicDir(otaFile);
//                triggerSystemOtaUpdate(otaFile);
                  triggerSystemOtaUpdate(otaFile);
                    }
                }
            }
        }).start();



        return  START_STICKY;
    }

//    @Override
//    protected void onHandleIntent(Intent intent) {
//        if (hasNewVersion()) {
//            File otaFile = downloadOtaPackage(getDownloadUrl());
//            if (otaFile != null && verifyOtaPackage(otaFile)) {
////                copyToOtaPackage(otaFile);
////                copyToExternalPublicDir(otaFile);
////                triggerSystemOtaUpdate(otaFile);
//                triggerSystemOtaUpdate(ota_zip_2);
//            }
//        }
//    }

    private String hasNewVersion() {
        // 伪代码：请求服务器，比较本地和服务器版本
        // return true if server version > local version
        String urlStr = getDownloadUrl();

        // 结果值
        StringBuffer rest = new StringBuffer();
        BufferedReader br = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // 设置请求方式为 POST
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection","keep-Alive");
            conn.setRequestProperty("Content-Type","application/json");
            // 输入 输出 都打开
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.connect();

            String jsonInputString = "{\"versionNo\": \"1.0.0\", \"appCode\": \"padsdk\"}";
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush();
            }

//            String jsonInputString = "{\"versionno\": \"1.0.0\", \"appCode\": \"padsdk\"}";
//            try (OutputStream os = conn.getOutputStream()) {
//                byte[] input = jsonInputString.getBytes("utf-8");
//                os.write(input, 0, input.length);
//                os.flush();
//            }

            // 读取数据
            br=new BufferedReader(new InputStreamReader(conn.getInputStream(),"utf-8"));
            String line=null;
            while (null != (line=br.readLine())){
                rest.append(line);
            }
            Log.d(TAG,"===cxq==="+rest.toString());
            notifier.showProgress("查询版本",12,100,false);

            JSONObject json = new JSONObject(rest.toString());

//            String value = json.getString("value");
//            json = new JSONObject(value);
            String ota_url = json.getString("versionUrl");

            return ota_url;

//            File file = ota_zip_2;
//            try (InputStream in = conn.getInputStream();
//                 FileOutputStream out = new FileOutputStream(file)) {
//                byte[] buf = new byte[4096];
//                int len;
//                while ((len = in.read(buf)) > 0)
//                {
//                    out.write(buf, 0, len);
//                }
//                Log.d(TAG,"===cxq==="+String.valueOf(buf));
//            }

//            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getDownloadUrl() {
        // 伪代码：从服务器获取OTA包下载地址
        return "http://192.168.191.151:8080/version";
    }

    private File downloadOtaPackage(String urlStr) {
        // 结果值
        StringBuffer rest=new StringBuffer();
        BufferedReader br=null;
        File oatfile = null;
        long total = 0;
        long count = 0;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            // 设置请求方式为 POST
//            conn.setRequestMethod("POST");
//            conn.setRequestProperty("Connection","keep-Alive");
//            conn.setRequestProperty("Content-Type","application/json");
//            // 输入 输出 都打开
//            conn.setDoOutput(true);
//            conn.setDoInput(true);
            conn.connect();

//            String jsonInputString = "{\"versionno\": \"1.0.0\", \"appCode\": \"padsdk\"}";
//            try (OutputStream os = conn.getOutputStream()) {
//                byte[] input = jsonInputString.getBytes("utf-8");
//                os.write(input, 0, input.length);
//                os.flush();
//            }

//            // 读取数据
//            br=new BufferedReader(new InputStreamReader(conn.getInputStream(),"utf-8"));
//            String line=null;
//            while (null != (line=br.readLine())){
//                rest.append(line);
//            }
//            Log.d(TAG,"===cxq==="+rest.toString());
            // 获取响应码
//            int responseCode = conn.getResponseCode();
//            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                String contentLengthHeader = conn.getHeaderField("Content-Length");
                if (contentLengthHeader != null && !contentLengthHeader.isEmpty()) {
                    try {
                        // 转换为长整型（文件大小可能超过int范围）
                        total =  Long.parseLong(contentLengthHeader);
                    } catch (NumberFormatException e) {
                        System.err.println("无法解析Content-Length: " + contentLengthHeader);
                    }
                } else {
                    System.err.println("服务器未返回Content-Length");
                }
            }

            File file = ota_zip;
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[4096];
            int len;

            while ((len = in.read(buf)) > 0)
            {
                out.write(buf, 0, len);
                count += len;
                Log.d(TAG,"===cxq==="+count);
                handler.obtainMessage(1,(int)((count * 1.0f / total) * 100)).sendToTarget();
            }
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
//        return oatfile;
    }

    private boolean copyToExternalPublicDir(File srcFile) {
        File destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        File destFile = new File(destDir, "update.zip");
        try (InputStream in = new FileInputStream(srcFile);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean copyToOtaPackage(File srcFile) {
        File destDir = new File("/data/ota_package");
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        File destFile = new File(destDir, "update.zip");
        try (InputStream in = new FileInputStream(srcFile);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean verifyOtaPackage(File otaFile) {
        // 伪代码：校验SHA256或MD5
        Log.d("===cxq===","===cxq==="+otaFile.exists() +"===cxq===lenght==="+otaFile.length() + "===cxq=="+otaFile.getAbsolutePath());
        return otaFile.exists() && otaFile.length() > 0;
    }

    private void triggerSystemOtaUpdate(File otaFile) {
        try {
            Log.d("===cxq===","===cxq==="+otaFile.exists() +"===cxq===lenght==="+otaFile.length() + "===cxq=="+otaFile.getAbsolutePath());
            if(mIsVirtualAbSupported)
            {
                try {
                    new UpdateVerifier().execute(otaFile);
                } catch (Exception ex) {
                    Log.e(TAG, ex.getMessage());

                }
            }else {
                // 需要系统签名权限
                RecoverySystem.installPackage(this, otaFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Verify the validity of the ota upgrade package */
    private class UpdateVerifier extends AsyncTask<File, Void, UpdateParser.ParsedUpdate> {
        @Override
        protected UpdateParser.ParsedUpdate doInBackground(File... files) {
            if(files.length <= 0) {
                Log.d("====cxq===","====cxq===file is empty===");
                return null;
            }
            File file = files[0];
            try {
                return UpdateParser.parse(file);
            } catch (IOException e) {
                Log.e(TAG, String.format("For file %s", file), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(UpdateParser.ParsedUpdate result) {
            if (result == null) {
//                Toast.makeText(mContext, R.string.recovery_update_package_verify_failed, Toast.LENGTH_SHORT).show();
                Log.e(TAG, String.format("Failed verification %s", result));
                return;
            }
            if (!result.isValid()) {
//                Toast.makeText(mContext, R.string.recovery_update_package_verify_failed, Toast.LENGTH_SHORT).show();
                Log.e(TAG, String.format("Failed verification %s", result));
                return;
            }
            Log.d(TAG, "package verifier success");
            if (isLowBatteryLevel()) {
//                Toast.makeText(mContext, R.string.recovery_update_level, Toast.LENGTH_LONG).show();
            } else {
                showConfirmInstallDialog(result);
            }
        }
    }


    public boolean isLowBatteryLevel() {
        Intent batteryBroadcast = mContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        final int batteryLevel = Utils.getBatteryLevel(batteryBroadcast);
        return batteryLevel < MINIMUM_LEVEL_POWER;
    }



    /** A dialog pops up to confirm whether to upgrade */
    public void showConfirmInstallDialog(final UpdateParser.ParsedUpdate parsedUpdate) {

        showInstallationInProgress();
        Settings.Global.putInt(mContext.getContentResolver(), DATABASE_KEY_INSTALL_IN_PROGRESS, 1);
        installUpdate(parsedUpdate);

    }


    private void showInstallationInProgress() {
//        mInstallationInProgress = true;
//        showStatus(R.string.recovery_update_package_install_title, R.string.recovery_update_package_download_in_progress);
        mUpdateEngine.bind(mOtaUpdateEngineCallback, new Handler(mContext.getMainLooper()));
    }


    /** Send ota package data to updateEngine for upgrade */
    private void installUpdate(UpdateParser.ParsedUpdate parsedUpdate) {
        Log.d(TAG, "mUrl:" + parsedUpdate.mUrl + ",mOffset:" + parsedUpdate.mOffset
                + ",mSize:" + parsedUpdate.mSize + ",mProps:" + parsedUpdate.mProps);

        try {
            if(mContext.getSystemService(KeyguardManager.class).isDeviceSecure()){
                Log.d(TAG, "prepareForUnattendedUpdate");
                RecoverySystem.prepareForUnattendedUpdate(mContext, TAG, null);
            }
            if (mWakeLock != null && !mWakeLock.isHeld()) mWakeLock.acquire();
            mUpdateEngine.applyPayload(
                    parsedUpdate.mUrl, parsedUpdate.mOffset, parsedUpdate.mSize, parsedUpdate.mProps);
        } catch (Exception ex) {
            mInstallationInProgress = false;
            if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
            Settings.Global.putInt(mContext.getContentResolver(), DATABASE_KEY_INSTALL_IN_PROGRESS, 0);
            //Modify for bug1420894, no need to show toast if update succeed
            String message = ex.getMessage();
            Log.e(TAG, message);
            if (!ERROR_NEED_REBOOT.equals(message)) {
                Toast.makeText(mContext, "升级失败重启", Toast.LENGTH_SHORT).show();
            }
        }
    }


    /** Handle events from the UpdateEngine. */
    public class OtaUpdateEngineCallback extends UpdateEngineCallback {
        @Override
        public void onStatusUpdate(int status, float percent) {
            switch (status) {
                case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT:
                    mInstallationInProgress = false;
                    if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
                    Settings.Global.putInt(mContext.getContentResolver(), DATABASE_KEY_INSTALL_IN_PROGRESS, 0);
//                    dismissProgressDialog();
                    Log.d(TAG, "Before show reboot dialog");
//                    showConfirmRebootDialog();
                    rebootNow();
                    break;
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                    Log.d(TAG, "downloading progress:" + ((int) (percent * 100) + "%"));
//                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
//                        String message = "更新正在下载。。。";
//                        String progessMessage = message + ((int) (percent * 100) + "%");
//                        mProgressDialog.setMessage(progessMessage);
//                    }
                    break;
                // Add install progress display for bug1397771
                case UpdateEngine.UpdateStatusConstants.FINALIZING:
                    Log.d(TAG, "finalizing progress:" + ((int) (percent * 100) + "%"));
//                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
//                        String message = mContext.getResources().getString(R.string.recovery_update_package_install_in_progress);
//                        String progessMessage = message + ((int) (percent * 100) + "%");
//                        mProgressDialog.setMessage(progessMessage);
//                    }
                    break;
                default:
                    // do nothing
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            mInstallationInProgress = false;
            if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
            Settings.Global.putInt(mContext.getContentResolver(), DATABASE_KEY_INSTALL_IN_PROGRESS, 0);
//            dismissProgressDialog();
            Log.w(TAG, String.format("onPayloadApplicationComplete %d", errorCode));
//            int messageId = (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) ? R.string.recovery_update_package_install_success : R.string.recovery_update_package_install_failed;
            Toast.makeText(mContext, "更新成功", Toast.LENGTH_SHORT).show();

        }
    }

    /** Reboot the system. */
    private void rebootNow() {
        Log.i(TAG, "RecoverySystem Rebooting Now.");
        try {
            if(RecoverySystem.isPreparedForUnattendedUpdate(mContext)){
                TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
                telephonyManager.prepareForUnattendedReboot();
                if(0 == RecoverySystem.rebootAndApply(mContext, REBOOT_REASON, true)){
                    Log.d(TAG, "rebootAndApply");
                }else{
                    Log.d(TAG, "rebootAndApply failed");
                }
            }else{
                mPowerManager.reboot(REBOOT_REASON);
            }
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
        // mPowerManager.reboot(REBOOT_REASON);
    }




}
