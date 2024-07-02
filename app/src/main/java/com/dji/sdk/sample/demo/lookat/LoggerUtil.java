package com.dji.sdk.sample.demo.lookat;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class LoggerUtil {
    private static String LOG_FILE_NAME;
    private Context activity;
    private File logFile;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    public LoggerUtil(String FileName, Context context){
        LOG_FILE_NAME = FileName;
        activity = (AppCompatActivity) context;
        setupLogFile();
    }

    private void setupLogFile() {
        File dir = new File(activity.getExternalFilesDir(null), "DJI/com.dji.sdk.sample/LOG/SDKLog/Logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        logFile = new File(dir, LOG_FILE_NAME);
        try {
            if (!logFile.exists()) {
                FileOutputStream fos = new FileOutputStream(logFile);
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                BufferedWriter writer = new BufferedWriter(osw);
                writer.write("Time,Speed,Altitude,Yaw,Pitch,Roll,FlyMode,TargetPoint,Battery\n");
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(float speed, float altitude, float yaw, float pitch, float roll, String flyMode, String targetPoint, String battery) {
        try {
            FileOutputStream fos = new FileOutputStream(logFile, true);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter writer = new BufferedWriter(osw);
            String currentTime =String.format(String.valueOf(DATE_FORMAT.format(System.currentTimeMillis())));
            String logMessage = String.format(Locale.getDefault(), "%s,%f,%f,%f,%f,%f,%s,%s,%s\n",
                    currentTime, speed, altitude, yaw, pitch, roll, flyMode, targetPoint, battery);
            writer.write(logMessage);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
