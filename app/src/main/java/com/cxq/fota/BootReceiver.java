package com.cxq.fota;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;


public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("====cxq===","===cxq===开机广播====");
            Intent serviceIntent = new Intent(context, FotaService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}