package com.dji.sdk.sample.demo.lookat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.google.android.gms.maps.model.LatLng;

import dji.common.camera.SettingsDefinitions;
import dji.common.model.LocationCoordinate2D;

public class WayPointHelper {
    private boolean Check_RecordVideo = false;

    public WayPointHelper() {
        DJISampleApplication.getProductInstance()
                .getCamera()
                .setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, djiError -> ToastUtils.setResultToToast("SetCameraMode to recordVideo"));

    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    public static LatLng calculateNewPoint(double currentLat, double currentLng, double heading,float distance) {
        double earthRadius = 6378.137; // Radius of earth in KM
        double distanceInKm = distance/ 1000.0;

        double newLat = currentLat + (distanceInKm / earthRadius) * Math.toDegrees(Math.cos(Math.toRadians(heading)));
        double newLng = currentLng + (distanceInKm / earthRadius) * Math.toDegrees(Math.sin(Math.toRadians(heading))) / Math.cos(Math.toRadians(currentLat));

        return new LatLng(newLat, newLng);
    }

    public Bitmap createTextBitmap(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(40); // Text size
        paint.setColor(Color.BLACK); // Text color
        paint.setTextAlign(Paint.Align.LEFT);
        float baseline = -paint.ascent(); // ascent() is negative
        int width = (int) (paint.measureText(text) + 0.5f); // round
        int height = (int) (baseline + paint.descent() + 0.5f);
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawText(text, 0, baseline, paint);
        return image;
    }

    public String nullToIntegerDefault(String value) {
        String newValue = value;
        if (!isIntValue(newValue)) newValue = "0";
        return newValue;
    }

    private boolean isIntValue(String value) {
        try {
            String newValue = value.replace(" ", "");
            Integer.parseInt(newValue);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
    public float calculateYaw(LocationCoordinate2D currentLocation, LocationCoordinate2D nextLocation) {

        double currentLatitude = Math.toRadians(currentLocation.getLatitude());
        double currentLongitude = Math.toRadians(currentLocation.getLongitude());
        double nextLatitude = Math.toRadians(nextLocation.getLatitude());
        double nextLongitude = Math.toRadians(nextLocation.getLongitude());

        double dLongitude = nextLongitude - currentLongitude;

        double x = Math.sin(dLongitude) * Math.cos(nextLatitude);
        double y = Math.cos(currentLatitude) * Math.sin(nextLatitude) - Math.sin(currentLatitude) * Math.cos(nextLatitude) * Math.cos(dLongitude);

        double initialBearing = Math.atan2(x, y);

        double bearing = Math.toDegrees(initialBearing);

        bearing = (bearing + 360) % 360;

        return (float) bearing;
    }

    public void RecordVideo() {
        if (!Check_RecordVideo) {
            DJISampleApplication.getProductInstance()
                    .getCamera()
                    .startRecordVideo(djiError -> {
                        //success so, start recording
                        if (null == djiError) {
                            ToastUtils.setResultToToast("Start record");
                            Check_RecordVideo=true;

                        } else {
                            ToastUtils.setResultToToast("Error Start record Video " + djiError.getDescription());
                        }
                    });
        } else {
            DJISampleApplication.getProductInstance()
                    .getCamera()
                    .stopRecordVideo(djiError -> {
                        if (null == djiError) {
                            ToastUtils.setResultToToast("Stop record");
                            Check_RecordVideo=false;

                        }else{
                            ToastUtils.setResultToToast("Error stop record Video " + djiError.getDescription());

                        }

                    });
        }
    }



}
