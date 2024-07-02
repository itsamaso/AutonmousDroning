package com.dji.sdk.sample.demo.lookat;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.dji.sdk.sample.internal.utils.ToastUtils;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.QRCodeDetector;


public class ImageProcess {
    private ImageView imageDetect;
    private Bitmap bitmap;
    private Point[] pts;
    private QRCodeDetector qrCodeDetector;
    private Context context;
    private CascadeClassifier faceDetector;


    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);




    public ImageProcess(ImageView image,Bitmap bit,QRCodeDetector qrDetector,Context contextCameraBase,CascadeClassifier FDetect){
        context=contextCameraBase;
        imageDetect=image;
        bitmap=bit;
        faceDetector=FDetect;
        qrCodeDetector=qrDetector;
        drawProcessedVideo(bitmap);
    }
    public void drawProcessedVideo(Bitmap bitmap) {
        if (bitmap != null) {
            Mat source = new Mat();
            Utils.bitmapToMat(bitmap, source);
            Mat processed = detectFaces(source,faceDetector);
            //Mat processed = ArucDetect(source);

            if (!processed.empty()) {
                Bitmap edgeBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(processed, edgeBitmap);
                imageDetect.setImageBitmap(edgeBitmap);



            }
        }
    }
    public Mat detectFaces(Mat input, CascadeClassifier faceDetector) {
        Mat grayImgMat = new Mat();
        MatOfRect faces = new MatOfRect();
        Mat output;

        Imgproc.cvtColor(input, grayImgMat, Imgproc.COLOR_RGBA2GRAY);
        if (faceDetector != null) {
            faceDetector.detectMultiScale(grayImgMat, faces, 1.1, 2, 2, new Size(60, 60), new Size());
        }
        output = input;
        Rect[] facesArray = faces.toArray();
        for (Rect rect : facesArray) {
            Imgproc.rectangle(output, rect.tl(), rect.br(), FACE_RECT_COLOR, 3);
        }
        return output;
    }


    private Mat qrDetect(Mat rgb) {
       Mat output = new Mat();
        if (rgb != null) {
            Mat gray = new Mat();
            Mat points = new Mat();
            output=rgb;


            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGBA2GRAY);
            if (gray.empty()) {
                ToastUtils.setResultToToast("Gray Empty");
            }
            String qrCodeData;
            try {
                qrCodeData = qrCodeDetector.detectAndDecode(gray, points);


                if (!qrCodeData.isEmpty()) {
                    ToastUtils.setResultToToast("QR Code detected:" + qrCodeData);
                    if (!points.empty()) {

                        double[] tempDouble;
                        pts = new Point[4];
                        for (int i = 0; i < 4; i++) {
                            tempDouble = points.get(0, i);
                            if (tempDouble != null) {
                                pts[i] = new Point(tempDouble[0], tempDouble[1]);
                            }
                        }

                        for (int i = 0; i < 4; i++) {
                            Imgproc.line(output, pts[i], pts[(i + 1) % 4], new Scalar(255, 0, 255), 7);
                        }
                    } else {
                        ToastUtils.setResultToToast("points in qr detect is null");
                    }

                    // imageWidth = output.cols();
                    // imageHeight = output.rows();


                }
            } catch (CvException e) {
                ToastUtils.setResultToToast("OpenCV error in QR code detection: " + e.getMessage());
            } catch (Exception e) {
                ToastUtils.setResultToToast("General error in QR code detection: " + e.getMessage());
            } finally {
                if (!points.empty()) {
                    points.release();
                    gray.release();

                }
            }

        }
        return output;


    }


}
