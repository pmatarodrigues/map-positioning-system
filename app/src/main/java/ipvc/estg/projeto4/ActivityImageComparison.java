package ipvc.estg.projeto4;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import ipvc.estg.projeto4.Classes.BuildingPicture;


public class ActivityImageComparison extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";
    private static final int REQUEST_PERMISSION = 100;
    private int w, h;
    private CameraBridgeViewBase mOpenCvCameraView;

    ArrayList<BuildingPicture> buildingPicturesList;
    BuildingPicture chosenPicture;

    Boolean cameraActive;

    TextView tvName;
    TextView txvNumberOfMatches;
    ImageView imgBitmaptests;

    Scalar RED = new Scalar(255, 0, 0);
    Scalar GREEN = new Scalar(0, 255, 0);

    FeatureDetector detector;
    DescriptorExtractor descriptor;
    DescriptorMatcher matcher;
    Mat descriptors2,descriptors1;

    Mat img1;

    MatOfKeyPoint keypoints1,keypoints2;

    Boolean firstFrame;

    static {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV");
        else
            Log.d("SUCCESS", "OpenCV loaded");
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_image_comparison);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
        }

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        tvName = (TextView) findViewById(R.id.text1);
        txvNumberOfMatches = (TextView) findViewById(R.id.txv_numberofmatches);

        firstFrame = true;
        chosenPicture = null;
    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    try {
                        initializeOpenCVDependencies();
                    } catch (IOException e) {
                        Log.d("TAG", "MAIN INITIAL ERROR");
                        e.printStackTrace();
                    }
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private void initializeOpenCVDependencies() throws IOException {
        mOpenCvCameraView.enableView();
        cameraActive = true;

        AssetManager assetManager = getAssets();

        buildingPicturesList = new ArrayList<>();

        /*
            LOOP THROUGH ALL THE PICTURES
            APPLY COLOR CORRECTION AND FILTERS TO EACH PICTURE
            GET KEYPOINTS AND DECRYPTORS OF EVERY PICTURE
            ADD PICTURE TO LIST
        */
        for(int i = 1; i <= 4; i++){

            // buildingPicture IS THE PICTURE THAT IS BEING USED
            // FROM THE CLASS BuildingPicture

            InputStream istr = assetManager.open(i + ".jpeg");
            Bitmap bitmap = BitmapFactory.decodeStream(istr);

            BuildingPicture buildingPicture = new BuildingPicture(bitmap);

            buildingPicture.setDetector(FeatureDetector.create(FeatureDetector.ORB));
            buildingPicture.setDescriptor(DescriptorExtractor.create(DescriptorExtractor.ORB));
            buildingPicture.setMatcher(DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING));

            buildingPicture.setImage(new Mat());

            ColorMatrix colorMatrix = new ColorMatrix(new float[]
                    {
                            0, 0, 0, 1, 0,
                            1, 0, 0, 0, 1,
                            0, 0, 0, 0, 1,
                            0, 0, 1, 0, 0
                    });

            Bitmap ret = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            Canvas canvas = new Canvas(ret);

            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            canvas.drawBitmap(buildingPicture.getBitmap(), 0, 0, paint);

            Utils.bitmapToMat(ret, buildingPicture.getImage());

            // CHANGE TO BLACK AND WHITE
            Imgproc.cvtColor(buildingPicture.getImage(), buildingPicture.getImage(), Imgproc.COLOR_RGB2GRAY);
            buildingPicture.getImage().convertTo(buildingPicture.getImage(), 0); //converting the image to match with the type of the cameras image

            buildingPicture.setDescriptors(new Mat());
            // GET KEYPOINTS FROM PICTURE
            buildingPicture.setKeypoint(new MatOfKeyPoint());
            buildingPicture.getDetector().detect(buildingPicture.getImage(), buildingPicture.getKeypoint());
            buildingPicture.getDescriptor().compute(buildingPicture.getImage(), buildingPicture.getKeypoint(), buildingPicture.getDescriptors());

            buildingPicturesList.add(buildingPicture);
        }
    }


    public ActivityImageComparison() {

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        w = width;
        h = height;
    }

    public void onCameraViewStopped() {
    }

    public Mat recognize(Mat aInputFrame) {

        /*
            LOOP AGAIN THROUGH ALL PICTURES
            GET KEYPOINTS FROM CURRENT CAMERA FRAME
            CHECK WHICH PICTURE HAS THE MOST POINTS IN COMMON
            USE THAT AS chosenPicture
         */
        for(int j = 0; j < buildingPicturesList.size(); j++) {

            BuildingPicture currentPicture = buildingPicturesList.get(j);

            try {
                Imgproc.cvtColor(aInputFrame, aInputFrame, Imgproc.COLOR_RGB2GRAY);
            } catch (Exception e){
                Log.i("TAG", "ERRO " + e.getMessage());
            }
            descriptors2 = new Mat();
            keypoints2 = new MatOfKeyPoint();
            currentPicture.getDetector().detect(aInputFrame, keypoints2);
            currentPicture.getDescriptor().compute(aInputFrame, keypoints2, descriptors2);

            // Matching
            MatOfDMatch matches = new MatOfDMatch();
            if (currentPicture.getImage().type() == aInputFrame.type()) {
                try {
                    currentPicture.getMatcher().match(currentPicture.getDescriptors(), descriptors2, matches);
                } catch (Exception e) {
                    Log.d("TAG", "[NO DETECTABLE FRAMES]\n");
                }
            } else {
                return aInputFrame;
            }
            List<DMatch> matchesList = matches.toList();

            Double max_dist = 0.0;
            Double min_dist = 20.0;

            for (int i = 0; i < matchesList.size(); i++) {
                Double dist = (double) matchesList.get(i).distance;
                if (dist < min_dist)
                    min_dist = dist;
                if (dist > max_dist)
                    max_dist = dist;
            }

            // THIS IS THE NUMBER OF POINTS IN COMMON DETECTED
            final LinkedList<DMatch> good_matches = new LinkedList<DMatch>();

            for (int i = 0; i < matchesList.size(); i++) {
                if (matchesList.get(i).distance <= (1.5 * min_dist))
                    good_matches.addLast(matchesList.get(i));
            }

            currentPicture.setGood_matches(good_matches);

            if (good_matches.size() > 6) {
                // DISABLE CAMERA IF IT HAS ENOUGH MATCHES
                // Log.d("TAG", "CAMERA STOPPED");

                // CHANGE TEXT TO SHOW NUMBER OF MATCHES
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // UPDATE TEXT TO SHOW POINTS IN COMMON
                        txvNumberOfMatches.setText(good_matches.size() + " pts");
                    }
                });

                // DISABLE CAMERA
                cameraActive = false;
            }
        }

        // ON THE FIRST FRAME
        // THE FIRST PICTURE ON THE LIST IS THE DEFAULT
        // THIS IS RUN ONLY ONE TIME
        if(firstFrame) {
             chosenPicture = buildingPicturesList.get(0);
        }

        // THIS VERIFIES THE NUMBER OF GOOD MATCHES ON EACH PICTURE
        for(int i = 0; i < buildingPicturesList.size(); i++){
            if(chosenPicture.getGood_matches().size() < buildingPicturesList.get(i).getGood_matches().size()){
                chosenPicture = buildingPicturesList.get(i);
            }
        }

        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(chosenPicture.getGood_matches());
        Mat outputImg = new Mat();
        MatOfByte drawnMatches = new MatOfByte();
        if (aInputFrame.empty() || aInputFrame.cols() < 1 || aInputFrame.rows() < 1) {
            return aInputFrame;
        }

        //Imgproc.Canny(outputImg, outputImg, 70, 100);

        Features2d.drawMatches(chosenPicture.getImage(), chosenPicture.getKeypoint(), aInputFrame, keypoints2, goodMatches, outputImg, RED, GREEN, drawnMatches, Features2d.NOT_DRAW_SINGLE_POINTS);
        Imgproc.resize(outputImg, outputImg, aInputFrame.size());

        firstFrame = false;

        return outputImg;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        return recognize(inputFrame.rgba());

    }
}