package com.dji.sdk.sample.demo.lookat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJIMissionError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.*;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.MissionState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.*;

import static dji.log.GlobalConfig.TAG;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.ToastUtils;

public class MavicMiniMissionOperator {
    MutableLiveData<LocationCoordinate3D> droneLocationMutableLiveData = new MutableLiveData<>();
    private MissionState state = WaypointMissionState.INITIAL_PHASE;
    private WaypointMission mission;
    private List<Waypoint> waypoints;
    private Observer<Float> gimbalObserver = null;
    private boolean isAirborne = false;
    private AppCompatActivity activity;
    private MutableLiveData<Float> gimbalPitchLiveData = new MutableLiveData<>();
    private WaypointMissionOperatorListener operatorListener = null;
    private float currentGimbalPitch = 0f;
    private int waypointTracker = 0;
    private Waypoint currentWaypoint;
    private LiveData<LocationCoordinate3D> droneLocationLiveData = droneLocationMutableLiveData;
    private double distanceToWaypoint = 0.0;
    private double originalLatitudeDiff = -1.0;
    private double originalLongitudeDiff = -1.0;
    private boolean isLanding = false;
    private boolean isLanded = false;
    private boolean travelledLongitude = false;
    private boolean travelledLatitude = false;
    private boolean observeGimbal = false;
    private Timer sendDataTimer = new Timer();
    private SendDataTask sendDataTask;
    private Direction directions = new Direction();
    private String TAG = "Way point";
    private TextAppender textAppenderOper;
    private int CheckLat=0,checkLong=0;
    private boolean isPausedForUpdate=false;


    public MavicMiniMissionOperator(Context context, TextAppender textAppender) {
        textAppenderOper = textAppender;
        initFlightControl();
        initGimbalListener();
        activity = (AppCompatActivity) context;
    }

    private void initFlightControl() {
        FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
        if (flightController != null) {

            flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        textAppenderOper.appendTextAndScroll("set Virtual stick success");
                    } else {
                        textAppenderOper.appendTextAndScroll("Error set virtual stick" + djiError.getDescription());
                    }
                }
            });
            flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            flightController.setYawControlMode(YawControlMode.ANGLE);
            flightController.setVerticalControlMode(VerticalControlMode.POSITION);
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.GROUND);
        } else {
            textAppenderOper.appendTextAndScroll("in mavic mini opertator the fligtControl is null");
        }


    }

    private void initGimbalListener() {
        if (DJISampleApplication.getProductInstance() != null) {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            if (gimbal != null) {
                gimbal.setStateCallback(new GimbalState.Callback() {
                    @Override
                    public void onUpdate(GimbalState gimbalState) {
                        currentGimbalPitch = gimbalState.getAttitudeInDegrees().getPitch();
                        gimbalPitchLiveData.postValue(currentGimbalPitch);
                    }
                });

            } else {
                textAppenderOper.appendTextAndScroll("Error(Null) to get the gimbal in method initGimbalListnener N");
                Log.d(TAG, "Error to get the gimbal in method initGimbalListener");
            }
        } else {
            textAppenderOper.appendTextAndScroll("Error in initGimbalListener The productInstance is NULL!!");
        }


    }

    public DJIError loadMission(WaypointMission mission) {
        if (mission == null) {
            this.state = WaypointMissionState.NOT_READY;
            return DJIMissionError.NULL_MISSION;
        } else {
            this.mission = mission;
            this.waypoints = mission.getWaypointList();
            this.state = WaypointMissionState.READY_TO_UPLOAD;
            return null;
        }
    }

    public void pauseMission() {
        if (this.state == WaypointMissionState.EXECUTING) {
            this.state = WaypointMissionState.EXECUTION_PAUSED;

            sendDataTask.cancel();
            sendDataTimer.cancel();
        }
    }

    public void uploadMission(CommonCallbacks.CompletionCallback<DJIMissionError> callback) {
        if (this.state == WaypointMissionState.READY_TO_UPLOAD) {
            this.state = WaypointMissionState.READY_TO_START;
            if (callback != null) {
                callback.onResult(null);
            }
        } else {
            this.state = WaypointMissionState.NOT_READY;
            if (callback != null) {
                callback.onResult(DJIMissionError.UPLOADING_WAYPOINT);
            }
        }
    }

    public void startMission(CommonCallbacks.CompletionCallback<DJIError> callback) {
        FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
        gimbalObserver = new Observer<Float>() {
            @Override
            public void onChanged(Float gimbalPitch) {
                if (gimbalPitch == -90 && !isAirborne) {
                    isAirborne = true;
                    textAppenderOper.appendTextAndScroll("Starting to TakeOff");
                    if (flightController != null) {
                        flightController.startTakeoff(new CommonCallbacks.CompletionCallback<DJIError>() {
                            @Override
                            public void onResult(DJIError error) {
                                if (error == null) {
                                    if (callback != null) {
                                        callback.onResult(null);
                                    }
                                    state = WaypointMissionState.READY_TO_EXECUTE;
                                    /*Camera camera = DJISampleApplication.getProductInstance().getCamera();
                                    if (camera != null) {
                                        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback<DJIError>() {
                                            @Override
                                            public void onResult(DJIError cameraError) {
                                                if (cameraError == null) {
                                                    ToastUtils.setResultToToast( "Switch Camera Mode Succeeded");
                                                } else {
                                                    ToastUtils.setResultToToast( "Switch Camera Error: " + cameraError.getDescription());
                                                }
                                            }
                                        });
                                    }*/

                                    Handler handler = new Handler(Looper.getMainLooper());
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            executeMission();
                                        }
                                    }, 8000);
                                } else {
                                    if (callback != null) {
                                        callback.onResult(error);
                                    }
                                }
                            }
                        });
                    } else {
                        textAppenderOper.appendTextAndScroll("flight control is null in operator start");
                    }
                }
            }
        };

        if (this.state == WaypointMissionState.READY_TO_START) {
            rotateGimbalDown();
            gimbalPitchLiveData.observe(activity, gimbalObserver);
        } else {
            if (callback != null) {
                callback.onResult(DJIMissionError.FAILED);
            }
        }
    }

    private void rotateGimbalDown() {
        Rotation rotation = new Rotation.Builder().mode(RotationMode.ABSOLUTE_ANGLE).pitch(-90).build();
        try {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            gimbal.rotate(rotation, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        textAppenderOper.appendTextAndScroll("rotate gimbal success");
                        Log.d(TAG, "rotate gimbal success");
                    } else {
                        textAppenderOper.appendTextAndScroll("Error rotate gimbal " + djiError.getDescription());
                        Log.d(TAG, "rotate gimbal error");
                    }
                }
            });
        } catch (Exception e) {
            textAppenderOper.appendTextAndScroll("drone not connected");
            Log.d(TAG, "drone likely not connected");

        }
    }

    private double distanceInMeters(LocationCoordinate2D a, LocationCoordinate2D b) {
        return Math.sqrt(Math.pow(a.getLongitude() - b.getLongitude(), 2.0) + Math.pow(a.getLatitude() - b.getLatitude(), 2.0)) * 111139.0;
    }

    private void executeMission() {
        state = WaypointMissionState.EXECUTION_STARTING;
        operatorListener.onExecutionStart();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            // Background work here
            handler.post(() -> {
                // Run on the main thread
                if (waypointTracker >= waypoints.size()) return;
                currentWaypoint = waypoints.get(waypointTracker); // getting the current waypoint
                droneLocationLiveData.observe(activity, locationObserver);
            });
        });
    }

    private void removeObserver() {
        droneLocationLiveData.removeObserver(locationObserver);
        if (gimbalObserver != null) {
            gimbalPitchLiveData.removeObserver(gimbalObserver);
        }
        observeGimbal = false;
        isAirborne = false;
        waypointTracker = 0;
        isLanded = false;
        isLanding = false;
        travelledLatitude = false;
        travelledLongitude = false;
    }
    private Observer<LocationCoordinate3D> locationObserver = new Observer<LocationCoordinate3D>() {
        @Override
        public void onChanged(LocationCoordinate3D currentLocation) {
            // Observing changes to the drone's location coordinates
            state = WaypointMissionState.EXECUTING;

            distanceToWaypoint = distanceInMeters(
                    new LocationCoordinate2D(currentWaypoint.coordinate.getLatitude(), currentWaypoint.coordinate.getLongitude()),
                    new LocationCoordinate2D(currentLocation.getLatitude(), currentLocation.getLongitude())
            );

            /*if (!isLanded && !isLanding) {
                // If the drone has arrived at the destination, take a photo.
                if (!photoTakenToggle && (distanceToWaypoint < 1.5)) { // If you haven't taken a photo
                    photoTakenToggle = takePhoto();
                    Log.d(TAG, "attempting to take photo: " + photoTakenToggle + ", " + photoIsSuccess);
                } else if (photoTakenToggle && (distanceToWaypoint >= 1.5)) {
                    photoTakenToggle = false;
                    photoIsSuccess = false;
                }
            }*/

            double longitudeDiff = currentWaypoint.coordinate.getLongitude() - currentLocation.getLongitude();
            double latitudeDiff = currentWaypoint.coordinate.getLatitude() - currentLocation.getLatitude();
            if(!isPausedForUpdate){
                pauseMission();
            }

            if (Math.abs(latitudeDiff) > originalLatitudeDiff) {
                originalLatitudeDiff = Math.abs(latitudeDiff);
            }

            if (Math.abs(longitudeDiff) > originalLongitudeDiff) {
                originalLongitudeDiff = Math.abs(longitudeDiff);
            }

            // Terminating the sendDataTimer and creating a new one
            sendDataTimer.cancel();
            sendDataTimer = new Timer();

            if (!travelledLongitude) {
                float speed = Math.max(
                        (float) (mission.getAutoFlightSpeed() * (Math.abs(longitudeDiff) / originalLongitudeDiff)),
                        0.5f
                );

                directions.pitch = longitudeDiff > 0 ? speed : -speed;
            }

            if (!travelledLatitude) {
                float speed = Math.max(
                        (float) (mission.getAutoFlightSpeed() * (Math.abs(latitudeDiff) / originalLatitudeDiff)),
                        0.5f
                );

                directions.roll = latitudeDiff > 0 ? speed : -speed;
            }

            // When the longitude difference becomes insignificant
            if (Math.abs(longitudeDiff) < 0.000002) {
                checkLong++;
                if(checkLong==1) {
                    textAppenderOper.appendTextAndScroll("finished travelling LONGITUDE");
                    Log.d(TAG, "finished travelling LONGITUDE");
                }
                directions.pitch = 0f;
                travelledLongitude = true;
            }

            if (Math.abs(latitudeDiff) < 0.000002) {
                CheckLat++;
                if(CheckLat==1) {
                    textAppenderOper.appendTextAndScroll("finished travelling LATITUDE");
                    Log.d(TAG, "finished travelling LATITUDE");
                }
                directions.roll = 0f;
                travelledLatitude = true;
            }

            // When the latitude difference becomes insignificant and there is no longitude difference
            if (travelledLatitude && travelledLongitude) {
                waypointTracker++;
                if (waypointTracker < waypoints.size()) {
                    currentWaypoint = waypoints.get(waypointTracker);
                    originalLatitudeDiff = -1.0;
                    originalLongitudeDiff = -1.0;
                    travelledLongitude = false;
                    travelledLatitude = false;
                    directions = new Direction(); // Assuming Direction is a class with an appropriate constructor
                } else {
                    state = WaypointMissionState.EXECUTION_STOPPING;
                    if (operatorListener != null) {
                        operatorListener.onExecutionFinish(null);
                    }
                    stopMission(null);
                    isLanding = true;
                    sendDataTimer.cancel();
                    if (isLanding && currentLocation.getAltitude() == 0f && !isLanded) {
                        sendDataTimer.cancel();
                        isLanded = true;
                    }

                    removeObserver();
                }
                sendDataTimer.cancel(); // Cancel all scheduled data tasks
            } else {
                if (state == WaypointMissionState.EXECUTING) {
                    directions.altitude = currentWaypoint.altitude;
                } else if (state == WaypointMissionState.EXECUTION_PAUSED) {
                    directions = new Direction(0f, 0f, 0f, currentWaypoint.altitude);
                }
                move(directions);
            }
            if(isPausedForUpdate){
                resumeMission();
            }
        }
    };

    private void move(Direction dir) {
        sendDataTask = new SendDataTask(dir.pitch, dir.roll, dir.yaw, dir.altitude);
        sendDataTimer.schedule(sendDataTask, 0, 200);
    }

    // Assuming WaypointMissionOperatorListener is an interface you have defined
    public void addListener(WaypointMissionOperatorListener listener) {
        this.operatorListener = listener;
    }

    public void removeListener() {
        this.operatorListener = null;
    }

    public void stopMission(CommonCallbacks.CompletionCallback<DJIMissionError> callback) {

        FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
        if (!isLanding) {
            textAppenderOper.appendTextAndScroll("trying to land");
            Log.d(TAG, "trying to land");
        }
        if (flightController != null) {
            flightController.setGoHomeHeightInMeters(20, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError==null){
                        textAppenderOper.appendTextAndScroll("set Height Go Home success -> Height 20 meters");
                    }else{
                        textAppenderOper.appendTextAndScroll("Error to set Height Go Home"+djiError.getDescription());
                    }
                }
            });
            flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError==null){
                        textAppenderOper.appendTextAndScroll("The Drone return Home");

                    }else{
                        textAppenderOper.appendTextAndScroll("Error to return Home :"+djiError.getDescription());

                    }
                }
            });
        }
    }
    public void PauseMission(){
        if(state==WaypointMissionState.EXECUTING){
            directions=new Direction(0f,0f,0f,currentWaypoint.altitude);
            move(directions);
            state=WaypointMissionState.EXECUTION_PAUSED;
            isPausedForUpdate=true;

        }
    }
    public void updateWaypoint(LocationCoordinate2D newCoordinate) {
        if (currentWaypoint != null) {
            currentWaypoint.coordinate = newCoordinate;
            originalLatitudeDiff = -1.0;
            originalLongitudeDiff = -1.0;
            travelledLongitude = false;
            travelledLatitude = false;

            textAppenderOper.appendTextAndScroll("Updated Waypoint: " + newCoordinate.toString());
        }
    }


    public void resumeMission() {
        if (state == WaypointMissionState.EXECUTION_PAUSED && isPausedForUpdate) {
            state = WaypointMissionState.EXECUTING;
            isPausedForUpdate = false;
            textAppenderOper.appendTextAndScroll("Resuming Mission");

            // Re-observe the location to proceed to the updated waypoint
            droneLocationLiveData.observe(activity, locationObserver);
        }
    }

    class SendDataTask extends TimerTask {
        private float mPitch;
        private float mRoll;
        private float mYaw;
        private float mThrottle;

        public SendDataTask(float pitch, float roll, float yaw, float throttle) {
            this.mPitch = pitch;
            this.mRoll = roll;
            this.mYaw = yaw;
            this.mThrottle = throttle;
        }

        @Override
        public void run() {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(mPitch, mRoll, mYaw, mThrottle),
                        null
                );
            }
            this.cancel();
        }
    }

    class Direction {
        float pitch;
        float roll;
        float yaw;
        float altitude;

        public Direction() {
            this.pitch = 0f;
            this.roll = 0f;
            this.yaw = 0f;
            this.altitude = 0f; // Initialize with a default value or current waypoint altitude if accessible
        }

        public Direction(float pitch, float roll, float yaw, float altitude) {
            this.pitch = pitch;
            this.roll = roll;
            this.yaw = yaw;
            this.altitude = altitude;
        }

        // Getter and setter methods for each field can be added here if needed
    }




}
