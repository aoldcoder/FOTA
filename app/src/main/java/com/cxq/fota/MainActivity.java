package com.cxq.fota;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.cxq.fota.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();
    // Used to load the 'fota' library on application startup.
    static {
        System.loadLibrary("fota");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;
        tv.setText(stringFromJNI());

        String path = Environment.getExternalStorageState();
        Log.d(TAG,path);


        Button btn_update = binding.btnUpdate;
        btn_update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(MainActivity.this, FotaService.class);
                startForegroundService(serviceIntent);
            }
        });




    }

    /**
     * A native method that is implemented by the 'fota' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}