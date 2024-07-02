package com.dji.sdk.sample.demo.lookat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dji.common.error.DJIError;
import dji.common.error.DJIMissionError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.MissionState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;

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
    private int CheckLat = 0, checkLong = 0;
    private boolean WaitForTheNextPoint = false, isShowDialog = false;
    private ChooseYourPoint chooseYourPoint;
    private float droneHeading = 0f;
    private Handler handler = new Handler(Looper.getMainLooper());
    private LoggerUtil loggerUtil;
    private float Pitch=0f,Roll=0f;
    private boolean isPausedForUpdate = false;




    public MavicMiniMissionOperator(Context context, TextAppender textAppender, List<Waypoint> waypointList) {
        textAppenderOper = textAppender;
        initFlightControl();
        initGimbalListener();
        activity = (AppCompatActivity) context;

        chooseYourPoint = new ChooseYourPoint(context, waypointList, new ChooseYourPoint.OnPointSelectedListener() {
            @Override
            public void onPointSelected(int pointNumber) {
                navigateToPoint(pointNumber);
            }
        });
        loggerUtil=new LoggerUtil("Drone.csv",context);

    }


    private void initFlightControl() {
        FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
        if (flightController != null) {

            flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        textAppenderOper.appendTextAndScroll("set Virtual stick success");
                        //loggerUtil.log("set Virtual stick success"+ "\n");

                    } else {
                        textAppenderOper.appendTextAndScroll("Error set virtual stick" + djiError.getDescription());
                        //loggerUtil.log("Error set virtual stick" + djiError.getDescription()+ "\n");


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
                //loggerUtil.log("Error(Null) to get the gimbal in method initGimbalListnener N"+ "\n");
            }
        } else {
            textAppenderOper.appendTextAndScroll("Error in initGimbalListener The productInstance is NULL!!");
            //loggerUtil.log("Error in initGimbalListener The productInstance is NULL!!"+ "\n");

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

    public void setWaitForTheNextPoint(boolean iswait) {
        this.WaitForTheNextPoint = iswait;
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
                if (gimbalPitch == -5 && !isAirborne) {
                    isAirborne = true;
                    textAppenderOper.appendTextAndScroll("Starting to TakeOff");
                    //loggerUtil.log("Starting to TakeOff"+ "\n");

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
                        //loggerUtil.log("flight control is null in operator start"+ "\n");


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
        Rotation rotation = new Rotation.Builder().mode(RotationMode.ABSOLUTE_ANGLE).pitch(-5).build();
        try {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            gimbal.rotate(rotation, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        textAppenderOper.appendTextAndScroll("rotate gimbal success");
                        //loggerUtil.log("rotate gimbal success"+ "\n");

                    } else {
                        textAppenderOper.appendTextAndScroll("Error rotate gimbal " + djiError.getDescription());
                        //loggerUtil.log("Error rotate gimbal " + djiError.getDescription()+ "\n");

                    }
                }
            });
        } catch (Exception e) {
            textAppenderOper.appendTextAndScroll("drone not connected");
            //loggerUtil.log("drone not connected"+ "\n");

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


    private void pauseAtWaypoint() {
        if (WaitForTheNextPoint) {
            directions = new Direction(0f, 0f, 0f, currentWaypoint.altitude); // Stop all movements
            move(directions); // Send stop command to the drone
            if (isShowDialog) {

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        chooseYourPoint.showSettingsDialogChooseYourPoint();
                    }
                }, 1000);
                setisShowDialog(false);
            }

        } else {
            proceedToNextWaypoint();
        }
    }

    private void navigateToPoint(int pointNumber) {
        if (pointNumber >= 0 && pointNumber < waypoints.size()) {
            currentWaypoint = waypoints.get(pointNumber);
            originalLatitudeDiff = -1.0;
            originalLongitudeDiff = -1.0;
            travelledLongitude = false;
            travelledLatitude = false;
            directions = new Direction();
            droneLocationLiveData.observe(activity, locationObserver); // Continue observing the location
        } else {
            Log.e(TAG, "Invalid waypoint number: " + pointNumber);
            textAppenderOper.appendTextAndScroll("Invalid waypoint number: " + pointNumber);
        }
    }

    public void proceedToNextWaypoint() {
        waypointTracker++;

        if (waypointTracker < waypoints.size()) {
            currentWaypoint = waypoints.get(waypointTracker);
            originalLatitudeDiff = -1.0;
            originalLongitudeDiff = -1.0;
            travelledLongitude = false;
            travelledLatitude = false;
            directions = new Direction();

            //droneHeading = rotate.calculateYaw(waypoints.get(waypointTracker - 1).coordinate, waypoints.get(waypointTracker).coordinate);
            droneLocationLiveData.observe(activity, locationObserver); // Continue observing the location


        } else {
            state = WaypointMissionState.EXECUTION_STOPPING;
            if (operatorListener != null) {
                operatorListener.onExecutionFinish(null);
            }
            stopMission(null);
            isLanding = true;
            sendDataTimer.cancel();
            removeObserver();
        }
    }

    public void setisShowDialog(boolean is_Show) {
        this.isShowDialog = is_Show;
    }

    private void move(Direction dir) {
        sendDataTask = new SendDataTask(dir.pitch, dir.roll, dir.yaw, dir.altitude);
        sendDataTimer.schedule(sendDataTask, 0, 300);
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
            //loggerUtil.log("trying to land"+ "\n");

        }
        if (flightController != null) {
            flightController.setGoHomeHeightInMeters(20, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        textAppenderOper.appendTextAndScroll("set Height Go Home success -> Height 20 meters");
                        //loggerUtil.log("set Height Go Home success -> Height 20 meters"+ "\n");

                    } else {
                        textAppenderOper.appendTextAndScroll("Error to set Height Go Home" + djiError.getDescription());
                        //loggerUtil.log("Error to set Height Go Home"+djiError.getDescription()+ "\n");

                    }
                }
            });
            flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        textAppenderOper.appendTextAndScroll("The Drone return Home");
                        //loggerUtil.log("The Drone return Home"+ "\n");


                    } else {
                        textAppenderOper.appendTextAndScroll("Error to return Home :" + djiError.getDescription());
                        //loggerUtil.log("Error to return Home :"+djiError.getDescription()+ "\n");


                    }
                }
            });
        }
    }
    private Observer<LocationCoordinate3D> locationObserver = new Observer<LocationCoordinate3D>() {
        @Override
        public void onChanged(LocationCoordinate3D currentLocation) {
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
            Log.d(TAG, "current location" + currentLocation.toString());
            if (!isPausedForUpdate) {
                pauseMission();
            }



            //textAppenderOper.appendTextAndScroll("current location:"+currentLocation.toString());

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
                Pitch=directions.pitch;
            }


            if (!travelledLatitude) {
                float speed = Math.max(
                        (float) (mission.getAutoFlightSpeed() * (Math.abs(latitudeDiff) / originalLatitudeDiff)),
                        0.5f
                );

                directions.roll = latitudeDiff > 0 ? speed : -speed;
                Roll=directions.roll;
            }


            // When the longitude difference becomes insignificant
            if (Math.abs(longitudeDiff) < 0.000002) {
                checkLong++;
                if (checkLong == 1) {
                    textAppenderOper.appendTextAndScroll("finished travelling LONGITUDE");
                    //loggerUtil.log("finished travelling LONGITUDE"+ "\n");

                }
                directions.pitch = 0f;
                Pitch=directions.pitch;
                travelledLongitude = true;
            }

            if (Math.abs(latitudeDiff) < 0.000002) {
                CheckLat++;
                if (CheckLat == 1) {
                    textAppenderOper.appendTextAndScroll("finished travelling LATITUDE");
                    //loggerUtil.log("finished travelling LATITUDE"+ "\n");
                }
                directions.roll = 0f;
                Roll=directions.roll;
                travelledLatitude = true;
            }


            // When the latitude difference becomes insignificant and there is no longitude difference
            if (travelledLatitude && travelledLongitude) {
                if (WaitForTheNextPoint) {
                    pauseAtWaypoint();
                } else {
                    proceedToNextWaypoint();
                }

            } else {
                if (state == WaypointMissionState.EXECUTING) {
                    directions.altitude = currentWaypoint.altitude;
                    //directions.yaw = droneHeading;
                } else if (state == WaypointMissionState.EXECUTION_PAUSED) {
                    directions = new Direction(0f, 0f, 0f, currentWaypoint.altitude);

                }
                move(directions);
            }
            if (isPausedForUpdate) {
                resumeMission();
            }

        }
    };
    public void pauseMission() {
        if (state == WaypointMissionState.EXECUTING) {
            directions = new Direction(0f, 0f, 0f, currentWaypoint.altitude); // Stop all movements
            move(directions); // Send stop command to the drone
            state = WaypointMissionState.EXECUTION_PAUSED;
            isPausedForUpdate = true;
            textAppenderOper.appendTextAndScroll("Mission Paused");
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




    public float getPitch(){
        return Pitch;
    }
    public float getRoll(){
        return Roll;
    }
    public float getAltitude(){
        return waypoints.get(waypointTracker).altitude;
    }
    public Waypoint getLocation(){
        return waypoints.get(waypointTracker);
    }

    public void setPausedForUpdate(boolean b) {
        this.isPausedForUpdate=b;
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
                //loggerUtil.log("Pitch:"+mPitch+"Roll:"+mRoll+"Yaw:"+mYaw+"Throttle:"+mThrottle+"\n");
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
