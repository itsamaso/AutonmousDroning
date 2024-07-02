package com.dji.sdk.sample.demo.lookat;


import android.Manifest;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.error.DJIMissionError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.mission.waypoint.WaypointTurnMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;

public class MavicMiniWayPoint extends LinearLayout implements View.OnClickListener, PresentableView, OnMapReadyCallback, GoogleMap.OnMapClickListener, TextAppender {

    private static WaypointMission.Builder waypointMissionBuilder = null;
    private final int FINE_PERMISSION_CODE = 1;
    public TextView logTextView;
    GoogleMap Mymap;
    int batteryLevel;
    private Button locate, add, clear, config, upload, start, stop, add_waypoint, RecordVideo;
    private Button choosePoint;
    private boolean isAdd = false;
    private double droneLocationLat = 15.0;
    private double droneLocationLng = 15.0, droneHomeLocationlat = 0.0, droneHomeLocationlng = 0.0;
    private Marker droneMarker = null;
    private ConcurrentHashMap<Integer, Marker> markers = new ConcurrentHashMap<>();
    private float altitude = 30f;
    private float speed = 2f;
    private List<Waypoint> waypointList = new ArrayList<>();
    private MavicMiniMissionOperator mavicMiniMissionOperator = null;
    private FlightController mFlightController;
    private WaypointMissionOperatorListener eventNotificationListener;
    private WaypointMissionFinishedAction finishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode headingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
    private Camera camera;
    private Polyline currentPolyline = null;
    private BaseProduct product;
    private int checkTextView = 0;
    private double droneHeading;
    private WayPointHelper wayPointHelper = new WayPointHelper();
    private boolean isChoose_Mode = false;
    private Timer logTimer;
    private LoggerUtil loggerUtil;
    private Battery battery;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();



    public MavicMiniWayPoint(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setClickable(true);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.mavicmini_waypoint_view, this, true);
        loggerUtil = new LoggerUtil("DroneLog" + System.currentTimeMillis() + ".csv", context);


        initUI();
        camera = DJISampleApplication.getProductInstance().getCamera();
        My_Location();


    }


    private void My_Location() {
        Context context = this.getContext();
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_PERMISSION_CODE);
                return;
            }
            SupportMapFragment myMap = (SupportMapFragment) activity.getSupportFragmentManager().findFragmentById(R.id.map);
            if (myMap != null) {
                myMap.getMapAsync(MavicMiniWayPoint.this);
            }


        }
    }

    private void initFlightController() {
        product = DJISampleApplication.getProductInstance();
        if (product == null || !product.isConnected()) {
            appendTextAndScroll("Disconnect With drone");
            return;
        } else {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
                battery = product.getBattery();
                setBatteryStateCallback();
                appendTextAndScroll("Connect to the Drone");
            }
            if (mFlightController != null) {
                appendTextAndScroll("The Flight control connect");
                mFlightController.setStateCallback(new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                        droneLocationLat = flightControllerState.getAircraftLocation().getLatitude();
                        droneLocationLng = flightControllerState.getAircraftLocation().getLongitude();
                        droneHomeLocationlat = flightControllerState.getHomeLocation().getLatitude();
                        droneHomeLocationlng = flightControllerState.getHomeLocation().getLongitude();
                        droneHeading = flightControllerState.getAttitude().yaw;


                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                mavicMiniMissionOperator.droneLocationMutableLiveData.postValue(flightControllerState.getAircraftLocation());
                                updateDroneLocation();

                            }
                        });


                    }
                });
            } else {
                appendTextAndScroll("The Flight control is null");
            }
        }

        mavicMiniMissionOperator = getWaypointMissionOperator();
        setUpListener();


    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initFlightController();


    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (droneMarker != null) {
            droneMarker.remove();
        }
        removeListener();
        if (!markers.isEmpty()) {
            markers.clear();
        }
        if (Mymap != null) {
            Mymap.setOnMapClickListener(null);
            Mymap.clear();
        }


    }


    private void initUI() {
        locate = findViewById(R.id.btn_locate);
        add = findViewById(R.id.btn_add);
        clear = findViewById(R.id.btn_clear);
        config = findViewById(R.id.btn_config);
        upload = findViewById(R.id.btn_upload);
        start = findViewById(R.id.btn_start);
        stop = findViewById(R.id.btn_stop);
        add_waypoint = findViewById(R.id.btn_diloag_add_waypoint);
        RecordVideo = findViewById(R.id.btn_RecordVideo);
        logTextView = (TextView) findViewById(R.id.logTextView);
        choosePoint = findViewById(R.id.btn_choosePoint);
        logTextView.setMovementMethod(new ScrollingMovementMethod());

        initOnclickListener();
    }

    private void initOnclickListener() {
        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        add_waypoint.setOnClickListener(this);
        RecordVideo.setOnClickListener(this);
        choosePoint.setOnClickListener(this);


    }

    public float getSpeed() {
        return speed;
    }

    private float getYaw() {
        return (float) droneHeading;
    }

    private void startLogging() {
        logTimer = new Timer();
        logTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Retrieve drone parameter
                float speed = getSpeed();
                float altitude = getWaypointMissionOperator().getAltitude();
                float yaw = getYaw();
                float pitch = getWaypointMissionOperator().getPitch();
                float roll = getWaypointMissionOperator().getRoll();
                String flyMode = "auto";
                String targetPoint = getWaypointMissionOperator().getLocation().coordinate.toString();
                String battery = getBatteryLevel();

                // Log the parameters
                loggerUtil.log(speed, altitude, yaw, pitch, roll, flyMode, targetPoint, battery);
            }
        }, 0, 100); // Schedule task to run every 100 milliseconds (10 times per second)
    }

    @Override
    public void onClick(View v) {
        if (v != null) {
            switch (v.getId()) {
                case R.id.btn_locate:
                    updateDroneLocation();
                    cameraUpdate();
                    break;
                case R.id.btn_add:
                    enableDisableAdd();
                    break;
                case R.id.btn_clear:
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Mymap.clear();
                            markers.clear();

                        }
                    });
                    clearWaypoints();
                    break;
                case R.id.btn_config:
                    showSettingsDialog();
                    break;
                case R.id.btn_upload:
                    uploadWaypointMission();
                    break;

                case R.id.btn_start:

                    StartWaypointMission();
                    startLogging();
                    break;
                case R.id.btn_stop:
                    stopWaypointMission();
                    break;
                case R.id.btn_diloag_add_waypoint:
                    showSettingsDialogAddWaypoint();
                    break;
                case R.id.btn_RecordVideo:
                    wayPointHelper.RecordVideo();
                    break;
                case R.id.btn_choosePoint:
                    getWaypointMissionOperator().setisShowDialog(true);
                    //chooseYourPoint.showSettingsDialogChooseYourPoint();
                    //chooseYourPoint.setDialogShowing(false);
                    break;
                default:
                    break;
            }
        }


    }

    private void updateMarkerLocation(LatLng point, float altitude) {

        markWaypoint(point);


        Waypoint waypoint = new Waypoint(point.latitude, point.longitude, altitude);


        if (waypointMissionBuilder == null) {
            waypointMissionBuilder = new WaypointMission.Builder();
            waypointList.add(waypoint); 


            appendTextAndScroll("add Waypoint:" + point.toString());
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        } else {
            waypointList.add(waypoint);
            ;

            appendTextAndScroll("add Waypoint:" + point.toString());
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        }

    }

    private void showSettingsDialogAddWaypoint() {
        LinearLayout PointSetting = new LinearLayout(this.getContext());
        PointSetting.setOrientation(LinearLayout.VERTICAL);
        PointSetting.setPadding(16, 16, 16, 16);

        List<EditText[]> waypointInputs = new ArrayList<>();

        // Add initial waypoint input fields
        addWaypointInputFields(PointSetting, waypointInputs);

        // Button to add more waypoints
        Button addWaypointButton = new Button(this.getContext());
        addWaypointButton.setText("Add Waypoint");
        addWaypointButton.setOnClickListener(v -> addWaypointInputFields(PointSetting, waypointInputs));
        PointSetting.addView(addWaypointButton);

        new AlertDialog.Builder(this.getContext())
                .setTitle("Waypoint Coordinates")
                .setView(PointSetting)
                .setPositiveButton("Finish", (dialog, id) -> {
                    for (EditText[] inputs : waypointInputs) {
                        String latitudeString = inputs[0].getText().toString();
                        String longitudeString = inputs[1].getText().toString();
                        String altitudeString = inputs[2].getText().toString();

                        if (!latitudeString.isEmpty() && !longitudeString.isEmpty() && !altitudeString.isEmpty()) {
                            try {
                                double latitude = Double.parseDouble(latitudeString);
                                double longitude = Double.parseDouble(longitudeString);
                                float altitude = Float.parseFloat(altitudeString);
                                LatLng point = new LatLng(latitude, longitude);
                                updateMarkerLocation(point, altitude);
                            } catch (NumberFormatException e) {

                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel())
                .create()
                .show();
    }

    private void addWaypointInputFields(LinearLayout parentLayout, List<EditText[]> waypointInputs) {
        LinearLayout waypointLayout = new LinearLayout(this.getContext());
        waypointLayout.setOrientation(LinearLayout.HORIZONTAL);
        waypointLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        waypointLayout.setPadding(0, 8, 0, 8);

        EditText latitudeInput = new EditText(this.getContext());
        latitudeInput.setHint("Latitude");
        latitudeInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        waypointLayout.addView(latitudeInput);

        EditText longitudeInput = new EditText(this.getContext());
        longitudeInput.setHint("Longitude");
        longitudeInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        waypointLayout.addView(longitudeInput);

        EditText altitudeInput = new EditText(this.getContext());
        altitudeInput.setHint("Altitude");
        altitudeInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        waypointLayout.addView(altitudeInput);

        parentLayout.addView(waypointLayout);
        waypointInputs.add(new EditText[]{latitudeInput, longitudeInput, altitudeInput});
    }


    private void showSettingsDialog() {
        LinearLayout wayPointSettings = (LinearLayout) LayoutInflater.from(this.getContext())
                .inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        final TextView wpSpeedValue = wayPointSettings.findViewById(R.id.speedValue);
        final SeekBar wpSpeedSeekBar = wayPointSettings.findViewById(R.id.speedSeekBar);

        RadioGroup actionAfterFinished_RG = wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = wayPointSettings.findViewById(R.id.heading);
        RadioGroup choosePointMode_RG = wayPointSettings.findViewById(R.id.Select_mode);


        wpSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                wpSpeedValue.setText(progress + "m/s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        choosePointMode_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                isChoose_Mode = checkedId == R.id.choose_mode;
            }
        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.finishNone:
                        finishedAction = WaypointMissionFinishedAction.NO_ACTION;
                        break;
                    case R.id.finishGoHome:
                        finishedAction = WaypointMissionFinishedAction.GO_HOME;
                        break;
                    case R.id.finishAutoLanding:
                        finishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                        break;
                    case R.id.finishToFirst:
                        finishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                        break;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.headingNext:
                        headingMode = WaypointMissionHeadingMode.AUTO;
                        break;
                    case R.id.headingInitDirec:
                        headingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                        break;
                    case R.id.headingRC:
                        headingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                        break;
                    case R.id.headingWP:
                        headingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                        break;
                }
            }
        });

        new AlertDialog.Builder(this.getContext())
                .setTitle("Waypoint Settings")
                .setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Float.parseFloat(wayPointHelper.nullToIntegerDefault(altitudeString));
                        speed = wpSpeedSeekBar.getProgress();
                        appendTextAndScroll("Altitude: " + altitude + "m");
                        appendTextAndScroll("Speed: " + speed + "m/s");
                        appendTextAndScroll("Finished Action: " + finishedAction);
                        appendTextAndScroll("Heading Mode: " + headingMode);
                        getWaypointMissionOperator().setWaitForTheNextPoint(isChoose_Mode);
                        configWayPointMission();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }


    private void configWayPointMission() {
        if (waypointMissionBuilder == null) {
            waypointMissionBuilder = new WaypointMission.Builder();
            waypointMissionBuilder.finishedAction(finishedAction);
            waypointMissionBuilder.headingMode(headingMode);
            waypointMissionBuilder.autoFlightSpeed(speed);
            waypointMissionBuilder.maxFlightSpeed(speed);
            waypointMissionBuilder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            waypointMissionBuilder.gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);
            waypointMissionBuilder.setGimbalPitchRotationEnabled(true);

        } else {


            waypointMissionBuilder.finishedAction(finishedAction);
            waypointMissionBuilder.headingMode(headingMode);
            waypointMissionBuilder.autoFlightSpeed(speed);
            waypointMissionBuilder.maxFlightSpeed(speed);
            waypointMissionBuilder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            waypointMissionBuilder.gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);
            waypointMissionBuilder.setGimbalPitchRotationEnabled(true);

        }


        if (waypointMissionBuilder.getWaypointList().size() > 0) {
            for (int i = 0; i < waypointMissionBuilder.getWaypointList().size(); i++) {
                Waypoint waypoint = waypointMissionBuilder.getWaypointList().get(i);
                waypoint.altitude = altitude;
                waypoint.heading = 0;
                waypoint.actionRepeatTimes = 1;
                waypoint.actionTimeoutInSeconds = 30;
                waypoint.turnMode = WaypointTurnMode.CLOCKWISE;
                waypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, -90));
            }
            appendTextAndScroll("Set Waypoint attitude successfully");
        }

        MavicMiniMissionOperator operator = getWaypointMissionOperator();
        if (operator != null) {
            DJIError error = operator.loadMission(waypointMissionBuilder.build());
            if (error == null) {
                appendTextAndScroll("Load Waypoint success");
            } else {
                appendTextAndScroll("Load Waypoint success" + error.getDescription());

            }
        }
    }

    private void uploadWaypointMission() {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback<DJIMissionError>() {
            @Override
            public void onResult(DJIMissionError djiMissionError) {
                if (djiMissionError == null) {
                    appendTextAndScroll("Upload mission is success");
                } else {
                    appendTextAndScroll("Error Upload mission" + djiMissionError.getDescription());
                }


            }
        });
    }

    private void StartWaypointMission() {
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback<DJIError>() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    appendTextAndScroll("start mission success");
                } else {
                    appendTextAndScroll("Error to start mission" + djiError.getDescription());
                }

            }
        });
    }

    private void stopWaypointMission() {
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback<DJIMissionError>() {
            @Override
            public void onResult(DJIMissionError djiMissionError) {
                if (djiMissionError == null) {
                    appendTextAndScroll("The mission stop");
                } else {
                    appendTextAndScroll("Error stop mission" + djiMissionError.getDescription());
                }
            }
        });
    }

    private void clearWaypoints() {
        waypointMissionBuilder.getWaypointList().clear();
        waypointList.clear();
        appendTextAndScroll("The Waypoints clear");
    }

    private void enableDisableAdd() {
        if (!isAdd) {
            isAdd = true;
            appendTextAndScroll("Can add Point on Map");
        } else {
            isAdd = false;
            appendTextAndScroll("can't add Point on Map");
        }
    }

    private void updateDroneLocation() {

        try {
            if (Mymap == null || !wayPointHelper.checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                return;
            }

            LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
            checkTextView++;
            if (checkTextView == 2) {
                appendTextAndScroll("Drone Location:" + pos.toString());
                appendTextAndScroll("drone Heading:" + droneHeading);
            }
            //Create MarkerOptions object
            final MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(pos);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
            markerOptions.rotation((float) droneHeading);
            markerOptions.anchor(0.5f, 0.5f);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (droneMarker != null) {
                        droneMarker.remove();

                    }
                    if (wayPointHelper.checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                        if (waypointList.size() == 0) {
                            Waypoint waypointdrone = new Waypoint(droneLocationLat, droneLocationLng, 0);
                            waypointList.add(waypointdrone);

                        }

                        droneMarker = Mymap.addMarker(markerOptions);


                    }

                }
            });


        } catch (Exception e) {
            appendTextAndScroll("Error updating drone location: " + e.getMessage());

        }

    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 20.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        Mymap.moveCamera(cu);
    }

    @Override
    public int getDescription() {
        return R.string.WayPoint;
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    private void setUpMap() {
        Mymap.setOnMapClickListener(this);// add the listener for click for a map object

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if (Mymap == null) {
            Mymap = googleMap;
            setUpMap();
            MoveMarkerInRealTime();
        }
    }

    private void MoveMarkerInRealTime() {
        Mymap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {
                getWaypointMissionOperator().pauseMission();

            }

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {
                LocationCoordinate2D newCoordinate = new LocationCoordinate2D(marker.getPosition().latitude, marker.getPosition().longitude);
                getWaypointMissionOperator().updateWaypoint(newCoordinate);
            }

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                LocationCoordinate2D newCoordinate = new LocationCoordinate2D(marker.getPosition().latitude, marker.getPosition().longitude);
                currentPolyline.remove();
                getWaypointMissionOperator().updateWaypoint(newCoordinate);
                getWaypointMissionOperator().resumeMission();

            }
        });
    }





    @Override
    public void onMapClick(@NonNull LatLng point) {
        if (isAdd) {

            markWaypoint(point);
            Waypoint waypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder == null) {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(waypoint); // add the waypoint to the list
                appendTextAndScroll("add Waypoint:" + point.toString());
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            } else {
                waypointList.add(waypoint);
                appendTextAndScroll("add Waypoint:" + point.toString());
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }


        } else {
            appendTextAndScroll("Can't add Waypoint");
        }
    }

    private void markWaypoint(LatLng point) {
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        // Add numbering to the marker
        int waypointNumber = markers.size() + 1;
        markerOptions.title(String.valueOf(waypointNumber));
        markerOptions.draggable(true);
        Marker marker = Mymap.addMarker(markerOptions);
        markers.put(waypointNumber, marker);
    }


    private void removeListener() {
        getWaypointMissionOperator().removeListener();
    }

    private MavicMiniMissionOperator getWaypointMissionOperator() {
        if (mavicMiniMissionOperator == null) {
            mavicMiniMissionOperator = new MavicMiniMissionOperator(this.getContext(), this, this.waypointList);
        }
        return mavicMiniMissionOperator;
    }

    private void setUpListener() {
        eventNotificationListener = new WaypointMissionOperatorListener() {
            @Override
            public void onDownloadUpdate(WaypointMissionDownloadEvent waypointMissionDownloadEvent) {


            }


            @Override
            public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {


            }

            @Override
            public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

            }

            @Override
            public void onExecutionStart() {
                appendTextAndScroll("Mission Started");

            }

            @Override
            public void onExecutionFinish(@Nullable final DJIError error) {
                ToastUtils.setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));

            }
        };
        if (mavicMiniMissionOperator != null && eventNotificationListener != null) {
            mavicMiniMissionOperator.addListener(eventNotificationListener);
        }
    }

    private void setBatteryStateCallback() {
        battery.setStateCallback(new BatteryState.Callback() {
            @Override
            public void onUpdate(BatteryState batteryState) {
                batteryLevel = batteryState.getChargeRemainingInPercent();

            }
        });
    }

    private String getBatteryLevel() {
        return batteryLevel + "%";
    }



    private LatLng getHomeLocation() {
        return new LatLng(droneHomeLocationlat, droneHomeLocationlat);

    }


    public void appendTextAndScroll(String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                logTextView.append(text + "\n");

            }
        });


    }


}


