package com.dji.sdk.sample.demo.mobileremotecontroller;

import android.app.Service;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.accessory.AccessoryAggregationView;
import com.dji.sdk.sample.demo.accessory.AudioFileListManagerView;
import com.dji.sdk.sample.internal.OnScreenJoystickListener;
import com.dji.sdk.sample.internal.audiohandler.MediaRecorderHandler;
import com.dji.sdk.sample.internal.audiohandler.MediaRecorderOptions;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.Helper;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.OnScreenJoystick;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.utils.VideoFeedView;
import com.dji.sdk.sample.internal.utils.ViewHelper;
import com.dji.sdk.sample.internal.view.PresentableView;


import dji.common.airlink.PhysicalSource;
import dji.common.camera.CameraVideoStreamSource;
import dji.common.camera.LaserMeasureInformation;
import dji.common.error.DJIError;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks.CompletionCallback;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.Simulator;
import dji.sdk.mobilerc.MobileRemoteController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;


/**
 * Class for mobile remote controller.
 */
public class MobileRemoteControllerView extends RelativeLayout
    implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, PresentableView {

    private ToggleButton btnSimulator;
    private Button btnTakeOff;
    private Button autoLand;
    private Button forceLand;
    private Button btn_Recoding;
    private Button btn_stop_Recoding;
    private VideoFeedView primaryVideoFeed;



    private TextView textView;

    private OnScreenJoystick screenJoystickRight;
    private OnScreenJoystick screenJoystickLeft;
    private MobileRemoteController mobileRemoteController;
    private FlightControllerKey isSimulatorActived;

    private TextView curPhysicalSource;
    private TextView curVideoStreamSource;
    private TextView laserInfoTv;
    private VideoFeeder.PhysicalSourceListener sourceListener;


    private CameraVideoStreamSource.Callback videoStreamSourceCallback;
    private LaserMeasureInformation.Callback laserCallback;

    public MobileRemoteControllerView(Context context) {
        super(context);
        init(context);
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setUpListeners();
        setVideoListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        tearDownListeners();
        super.onDetachedFromWindow();
    }

    private void init(Context context) {
        setClickable(true);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_mobile_rc, this, true);
        initAllKeys();
        initUI();
    }

    private void initAllKeys() {
        isSimulatorActived = FlightControllerKey.create(FlightControllerKey.IS_SIMULATOR_ACTIVE);
    }

    private void initUI() {
        btnTakeOff = (Button) findViewById(R.id.btn_take_off);
        autoLand = (Button) findViewById(R.id.btn_auto_land);
        autoLand.setOnClickListener(this);
        forceLand = (Button) findViewById(R.id.btn_force_land);
        forceLand.setOnClickListener(this);
        btnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);

        textView = (TextView) findViewById(R.id.textview_simulator);

        screenJoystickRight = (OnScreenJoystick) findViewById(R.id.directionJoystickRight);
        screenJoystickLeft = (OnScreenJoystick) findViewById(R.id.directionJoystickLeft);

        btnTakeOff.setOnClickListener(this);
        btnSimulator.setOnCheckedChangeListener(MobileRemoteControllerView.this);

        Boolean isSimulatorOn = (Boolean) KeyManager.getInstance().getValue(isSimulatorActived);
        if (isSimulatorOn != null && isSimulatorOn) {
            btnSimulator.setChecked(true);
            textView.setText("Simulator is On.");
        }
        primaryVideoFeed = findViewById(R.id.primary_video_feed);



    }
    private void setVideoListener() {
        if (Helper.isM300Product() && Helper.isH20Series()) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product.getAirLink() != null && product.getAirLink().getOcuSyncLink() != null) {
                product.getAirLink().getOcuSyncLink().assignSourceToPrimaryChannel(PhysicalSource.LEFT_CAM, PhysicalSource.FPV_CAM, djiError -> {
                    String result = "";
                    if (djiError == null) {
                        result = "AssignSource Success";
                    } else {
                        result = "AssignSource Failed, " + djiError.getDescription();
                    }
                    ViewHelper.showToast(this.getContext(), result);
                });
            }
        }

        sourceListener = (videoFeed, physicalSource) -> {
            if (videoFeed == VideoFeeder.getInstance().getPrimaryVideoFeed()) {
                String message = "Change Source To " + physicalSource;
                ViewHelper.showToast(this.getContext(), message);





            }
        };

        primaryVideoFeed.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);
        ToastUtils.setResultToText(curPhysicalSource, VideoFeeder.getInstance().getPrimaryVideoFeed().getVideoSource().name());
        VideoFeeder.getInstance().addPhysicalSourceListener(sourceListener);

        videoStreamSourceCallback = (videoStreamSource) -> {
            String message = "Cur Source: " + videoStreamSource.name();
            ToastUtils.setResultToText(curVideoStreamSource, message);
        };

        laserCallback = (laserInformation) -> {
            String laserInfo = "Laser information: \n" + laserInformation;
            ToastUtils.setResultToText(laserInfoTv, laserInfo);
        };
        Camera curCamera = DJISampleApplication.getProductInstance().getCamera();
        if (curCamera != null) {
            curCamera.setCameraVideoStreamSourceCallback(videoStreamSourceCallback);
            if (curCamera.getLens(0) != null) {
                curCamera.getLens(0).setLaserMeasureInformationCallback(laserCallback);
            }
        }
    }

    private void setUpListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(final SimulatorState djiSimulatorStateData) {
                    ToastUtils.setResultToText(textView,
                                               "Yaw : "
                                                   + djiSimulatorStateData.getYaw()
                                                   + ","
                                                   + "X : "
                                                   + djiSimulatorStateData.getPositionX()
                                                   + "\n"
                                                   + "Y : "
                                                   + djiSimulatorStateData.getPositionY()
                                                   + ","
                                                   + "Z : "
                                                   + djiSimulatorStateData.getPositionZ());
                }
            });
        } else {
            ToastUtils.setResultToToast("Disconnected!");
        }
        try {
            mobileRemoteController =
                ((Aircraft) DJISampleApplication.getAircraftInstance()).getMobileRemoteController();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        if (mobileRemoteController != null) {
            textView.setText(textView.getText() + "\n" + "Mobile Connected");
        } else {
            textView.setText(textView.getText() + "\n" + "Mobile Disconnected");
        }
        screenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }

                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }

                if (mobileRemoteController != null) {
                    mobileRemoteController.setLeftStickHorizontal(pX);
                    mobileRemoteController.setLeftStickVertical(pY);
                }
            }
        });

        screenJoystickRight.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }

                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                if (mobileRemoteController != null) {
                    mobileRemoteController.setRightStickHorizontal(pX);
                    mobileRemoteController.setRightStickVertical(pY);
                }
            }
        });
    }

    private void tearDownListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(null);
        }
        screenJoystickLeft.setJoystickListener(null);
        screenJoystickRight.setJoystickListener(null);
    }

    @Override
    public void onClick(View v) {
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        if (flightController == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.btn_take_off:

                flightController.startTakeoff(new CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;
            case R.id.btn_force_land:
                flightController.confirmLanding(new CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;
            case R.id.btn_auto_land:
                flightController.startLanding(new CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;



            default:
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (compoundButton == btnSimulator) {
            onClickSimulator(b);
        }
    }

    private void onClickSimulator(boolean isChecked) {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator == null) {
            return;
        }
        if (isChecked) {

            textView.setVisibility(VISIBLE);
            simulator.start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                            new CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {

                                }
                            });
        } else {

            textView.setVisibility(INVISIBLE);
            simulator.stop(new CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_mobile_remote_controller;
    }
}
