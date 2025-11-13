package com.cxq.fota;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class DownloadNotifier {
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;
    private final Context context;
    private final NotificationManager notificationManager;

    public DownloadNotifier(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示下载进度");
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void showProgress(String title, int progress, int max, boolean indeterminate) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(indeterminate ? "正在下载…" : progress + "%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(max, progress, indeterminate);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void showText(String title, String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }


    public void complete(String title) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("下载完成")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void error(String title, String reason) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("下载失败：" + reason)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}

