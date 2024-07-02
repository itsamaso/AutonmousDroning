package com.dji.sdk.sample.demo.lookat;

import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.QRCodeDetector;
import org.opencv.objdetect.ArucoDetector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

/**
 * This class is designed for showing the camera video feed from the camera.
 */
public class BaseCameraViewMavicMini extends FrameLayout implements TextureView.SurfaceTextureListener, PresentableView {

    Mat output = null;
    private String Tag = "Qr detect";
    private VideoFeeder.VideoDataListener videoDataListener = null;
    private DJICodecManager codecManager = null;
    private QRCodeDetector qrCodeDetector;
    private TextureView mVideoSurface;
    private ArucoDetector arucoDetector;
    private Bitmap edgeBitmap,droneBitmap;
    private volatile boolean surfaceAvailable = false;

    private ImageView modifiedVideostreamPreview;
    private Canvas canvas;
    private ImageProcess imageProcess;
    private CascadeClassifier faceDetector;
    private Context con;


    private BaseLoaderCallback mlooaderCallback = new BaseLoaderCallback(this.getContext()) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                try {
                    qrCodeDetector = new QRCodeDetector();
                    arucoDetector = new ArucoDetector();
                    InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                    File cascadeDir = ((getContext().getDir("cascade", Context.MODE_PRIVATE)));
                    File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                    FileOutputStream os = new FileOutputStream(cascadeFile);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    os.close();

                    faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
                    if (faceDetector.empty()) {
                        ToastUtils.setResultToToast("Failed to load cascade classifier fo face detection");
                        faceDetector = null;
                    } else {
                    }
                    cascadeDir.delete();



                } catch (Exception e) {
                    ToastUtils.setResultToToast("error baseloader" + e.toString());
                }
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    public BaseCameraViewMavicMini(Context context) {
        super(context);
        con=context;
        setClickable(true);
        initUI();
    }



    private void initUI() {

        LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Service.LAYOUT_INFLATER_SERVICE);

        layoutInflater.inflate(R.layout.base_camera_mavic_mini_disply, this, true);

        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this.getContext(), mlooaderCallback);
        } else {
            mlooaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        modifiedVideostreamPreview = findViewById(R.id.modified_livestream_preview_ttv);


        mVideoSurface = (TextureView) findViewById(R.id.livestream_preview_ttv);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);

            // This callback is for

            videoDataListener = new VideoFeeder.VideoDataListener() {
                @Override
                public void onReceive(byte[] bytes, int size) {
                    if (null != codecManager) {
                        codecManager.sendDataToDecoder(bytes, size);
                    }
                }
            };
        }

        initSDKCallback();


    }

    private void initSDKCallback() {
        try {
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (codecManager == null) {
            codecManager = new DJICodecManager(getContext(), surface, width, height);

        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;

        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        droneBitmap=mVideoSurface.getBitmap();
        imageProcess=new ImageProcess(modifiedVideostreamPreview,droneBitmap,qrCodeDetector,getContext(),faceDetector);


    }












    @Override
    public int getDescription() {
        return R.string.CameraBaseMavicMini;
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }



}




