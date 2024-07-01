package com.dji.sdk.sample.demo.lookat;


import android.Manifest;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;

import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.SphericalUtil;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;


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
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;

public class MavicMiniWayPoint extends LinearLayout implements View.OnClickListener, PresentableView, OnMapReadyCallback, GoogleMap.OnMapClickListener,TextAppender {

    private static WaypointMission.Builder waypointMissionBuilder = null;
    private final int FINE_PERMISSION_CODE = 1;
    GoogleMap Mymap;
    private Button locate, add, clear, config, upload, start, stop, puse;
    private boolean isAdd = false;
    private double droneLocationLat = 15.0;
    private double droneLocationLng = 15.0;
    private Marker droneMarker = null;
    private ConcurrentHashMap<Integer, Marker> markers = new ConcurrentHashMap<>();
    private float altitude = 30f;
    private float speed = 2f;
    private List<Waypoint> waypointList = new ArrayList<>();
    private MavicMiniMissionOperator mavicMiniMissionOperator = null;
    private Location current_location;
    private FlightController mFlightController;
    private WaypointMissionOperatorListener eventNotificationListener;
    private WaypointMissionFinishedAction finishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode headingMode = WaypointMissionHeadingMode.AUTO;
    private Camera camera;
    private Polyline currentPolyline = null;
    private String TAG = "Way point";
    private BaseProduct product;
    public TextView logTextView;
    private int checkTextView=0;


    public MavicMiniWayPoint(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setClickable(true);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.mavicmini_waypoint_view, this, true);
        initUI();
        camera = DJISampleApplication.getProductInstance().getCamera();
        My_Location();

    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void My_Location() {
        Context context = this.getContext();
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_PERMISSION_CODE);
                return;
            }
            FusedLocationProviderClient  fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity);
            Task<Location> task = fusedLocationProviderClient.getLastLocation();
            task.addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        current_location = location;
                        SupportMapFragment myMap = (SupportMapFragment) activity.getSupportFragmentManager().findFragmentById(R.id.map);
                        myMap.getMapAsync(MavicMiniWayPoint.this);

                    }
                }
            });
        } else {
            // Handle the case where the context is not an instance of FragmentActivity
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
                appendTextAndScroll("Connect to the Drone");
            }
            if (mFlightController != null) {
                appendTextAndScroll("The Flight control connect");
                mFlightController.setStateCallback(new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                        droneLocationLat = flightControllerState.getHomeLocation().getLatitude();
                        droneLocationLng = flightControllerState.getHomeLocation().getLongitude();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                mavicMiniMissionOperator.droneLocationMutableLiveData.postValue(flightControllerState.getAircraftLocation());
                                updateDroneLocation();

                            }
                        });


                    }
                });
            }
            else {
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
        if (Mymap != null) {
            Mymap.clear();
            Mymap = null;
        }
        if (waypointList.size() > 0) {
            waypointList.clear();
        }
        if (!markers.isEmpty()) {
            markers.clear();
        }
        removeListener();




    }

    private void initUI() {
        locate = findViewById(R.id.btn_locate);
        add = findViewById(R.id.btn_add);
        clear = findViewById(R.id.btn_clear);
        config = findViewById(R.id.btn_config);
        upload = findViewById(R.id.btn_upload);
        start = findViewById(R.id.btn_start);
        stop = findViewById(R.id.btn_stop);
        puse = findViewById(R.id.btn_puse);
        logTextView = (TextView) findViewById(R.id.logTextView);
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
        puse.setOnClickListener(this);


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
                    break;
                case R.id.btn_stop:
                    stopWaypointMission();
                    break;
                case R.id.btn_puse:
                    getWaypointMissionOperator().pauseMission();
                    break;
                default:
                    break;
            }
        }


    }


    private String nullToIntegerDefault(String value) {
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

    private void showSettingsDialog() {
        LinearLayout wayPointSettings = (LinearLayout) LayoutInflater.from(this.getContext())
                .inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed) {
                    speed = 1.0f;
                } else if (checkedId == R.id.MidSpeed) {
                    speed = 3.0f;
                } else if (checkedId == R.id.HighSpeed) {
                    speed = 7.0f;

                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.finishNone) {
                    finishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome) {
                    finishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding) {
                    finishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst) {
                    finishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                if (checkedId == R.id.headingNext) {
                    headingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    headingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    headingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    headingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this.getContext())
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Float.parseFloat(nullToIntegerDefault(altitudeString));
                        appendTextAndScroll("altitude: " + altitude);
                        appendTextAndScroll("speed:" + speed);
                        appendTextAndScroll("FinishedAction: " + finishedAction);
                        appendTextAndScroll("HeadingMode: " + headingMode);
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
                //waypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
                //waypoint.setShootPhotoDistanceInterval(28.956f);
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
                if(djiMissionError==null){
                    appendTextAndScroll("Upload mission is success");
                }else{
                    appendTextAndScroll("Error Upload mission"+djiMissionError.getDescription());
                }


            }
        });
    }
    private void StartWaypointMission(){
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback<DJIError>() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError==null){
                    appendTextAndScroll("start mission success");
                }else{
                    appendTextAndScroll("Error to start mission"+djiError.getDescription());
                }

            }
        });
    }

    private void stopWaypointMission() {
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback<DJIMissionError>() {
            @Override
            public void onResult(DJIMissionError djiMissionError) {
               if(djiMissionError==null){
                   appendTextAndScroll("The mission stop");
               }else{
                   appendTextAndScroll("Error stop mission"+djiMissionError.getDescription());
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
            if (Mymap == null|| !checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                return;
            }

            LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
            checkTextView++;
            if(checkTextView==2) {
                appendTextAndScroll("Drone Location:" + pos.toString());
            }
            //Create MarkerOptions object
            final MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(pos);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (droneMarker != null) {
                        droneMarker.remove();
                    }
                    if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
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
        float zoomlevel = (float) 18.0;
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
        }


    }
    private void MoveMarkerInRealTime(){
        Mymap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {
                getWaypointMissionOperator().PauseMission();

            }

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {
                LocationCoordinate2D newCoordinate=new LocationCoordinate2D(marker.getPosition().latitude,marker.getPosition().longitude);
                getWaypointMissionOperator().updateWaypoint(newCoordinate);

            }

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                LocationCoordinate2D newCoordinate = new LocationCoordinate2D(marker.getPosition().latitude, marker.getPosition().longitude);
                getWaypointMissionOperator().updateWaypoint(newCoordinate);
                getWaypointMissionOperator().resumeMission();

            }
        });
    }


    private void updateMapUI() {
        // Clear the existing polyline if it exists
        if (currentPolyline != null) {
            currentPolyline.remove();
        }

        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.color(Color.RED); // Set polyline color
        polylineOptions.width(5); // Set polyline width


        MarkerOptions markerOptions = new MarkerOptions();

        LatLng previousLatLng = null;
        for (Waypoint waypoint : waypointList) {
            LatLng currentLatLng = new LatLng(waypoint.coordinate.getLatitude(), waypoint.coordinate.getLongitude());
            polylineOptions.add(currentLatLng);

            if (previousLatLng != null) {
                // Calculate distance and midpoint
                double distance = SphericalUtil.computeDistanceBetween(previousLatLng, currentLatLng);
                LatLng midPoint = new LatLng((previousLatLng.latitude + currentLatLng.latitude) / 2, (previousLatLng.longitude + currentLatLng.longitude) / 2);

                // Create a bitmap with text and use it as marker icon
                String distanceText = String.format(Locale.US, "%.2f m", distance);
                Bitmap bitmap = createTextBitmap(distanceText);
                Mymap.addMarker(new MarkerOptions().position(midPoint).icon(BitmapDescriptorFactory.fromBitmap(bitmap)));

                // Update previousLatLng for the next iteration
                previousLatLng = currentLatLng;
            } else {
                previousLatLng = currentLatLng; // This is for the first waypoint
            }
        }

        // Draw the polyline on the map
        currentPolyline = Mymap.addPolyline(polylineOptions);
    }

    private Bitmap createTextBitmap(String text) {
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
            updateMapUI(); // Update the map UI to reflect the new waypoint and path


        } else {
            appendTextAndScroll("Can't add Waypoint");
        }
    }

    private void markWaypoint(LatLng point) {
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        // Add numbering to the marker
        int waypointNumber = markers.size() + 1; // Assuming markers map is already declared and keeps track of added markers
        markerOptions.title(String.valueOf(waypointNumber));
        Marker marker = Mymap.addMarker(markerOptions);
        markers.put(waypointNumber, marker);
    }


    private void removeListener() {
        getWaypointMissionOperator().removeListener();
    }

    private MavicMiniMissionOperator getWaypointMissionOperator() {
        if (mavicMiniMissionOperator == null) {
            mavicMiniMissionOperator = new MavicMiniMissionOperator(this.getContext(),this);
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
            // Example of adding listeners
            mavicMiniMissionOperator.addListener(eventNotificationListener);
        }
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


