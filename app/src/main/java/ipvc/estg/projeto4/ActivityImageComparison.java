package ipvc.estg.projeto4;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import ipvc.estg.projeto4.Classes.BuildingPicture;


public class ActivityImageComparison extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, OnMapReadyCallback, LocationListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = "OCVSample::Activity";
    private static final int REQUEST_PERMISSION = 100;
    private int w, h;
    //================================================================================
    // Image recognition variables
    //================================================================================
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


    //================================================================================
    // Maps variables
    //================================================================================
    GoogleMap gMap;
    private GoogleApiClient mGoogleAPIClient;
    LocationRequest mLocationRequest;
    LocationManager locationManager;
    private ActivityImageComparison.AddressResultReceiver mResultReceiver;
    Location location;
    Button btnChangeToImageTests;
    LinkedHashMap<Location, Boolean> checkedCoords;



    //================================================================================
    // FUNCTIONS
    //================================================================================
    static {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV");
        else
            Log.d("SUCCESS", "OpenCV loaded");
    }


    //================================================================================
    // GENERAL functions
    //================================================================================

    @Override
    public void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        //================================================================================
        // Image recognition declarations
        //================================================================================
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




        //================================================================================
        // Map declarations
        //================================================================================

        checkedCoords = new LinkedHashMap<>();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapa);
        mapFragment.getMapAsync(this);


        mResultReceiver = new AddressResultReceiver(null);
        mLocationRequest = new LocationRequest();
        buildGoogleApiClient();
    }

    public void addMarker(final BuildingPicture chosenPicture){
        Handler mHandler = new Handler(getMainLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(chosenPicture.getLatLng());
                markerOptions.title("Local atual");
                gMap.clear();
                gMap.animateCamera(CameraUpdateFactory.newLatLng(chosenPicture.getLatLng()));
                gMap.addMarker(markerOptions);
            }
        });

    }



    //================================================================================
    // Image recognition functions
    //================================================================================

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

            InputStream istr = assetManager.open(i + ".png");
            Bitmap bitmap = BitmapFactory.decodeStream(istr);

            BuildingPicture buildingPicture = new BuildingPicture(bitmap);

            buildingPicture.setFilename(i + ".png");

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

            if(buildingPicture.getFilename().equals("1.png")){
                buildingPicture.setLatLng(new LatLng(41.6970909,-8.8402198));
            }

            if(buildingPicture.getFilename().equals("2.png")){
                buildingPicture.setLatLng(new LatLng(41.6967705,-8.8394581));
            }
            if(buildingPicture.getFilename().equals("3.png")){
                buildingPicture.setLatLng(new LatLng(41.6967785,-8.8404076));
            }
            if(buildingPicture.getFilename().equals("4.png")){
                buildingPicture.setLatLng(new LatLng(41.6973152,-8.8394366));
            }

            Log.d(TAG, "initializeOpenCVDependencies:" + buildingPicture.getLatLng());


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

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleAPIClient, this);
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
                Log.d(TAG, "recognize: " + "MUDOU DE FOTO " + chosenPicture);
                addMarker(chosenPicture);
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







    //================================================================================
    // MAPS functions
    //================================================================================


    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;

        gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();


        // CHECK USER PERMISSIONS FOR ACCESSING LOCATION
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (isLocationEnabled(ActivityImageComparison.this) && location != null) {
            // GETS USER KNOWN LOCATION
            location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
            //location = getLastKnownLocation();

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            focusMapa(latLng);
        }
        startIntentService(location);

    }

    private Boolean isLocationEnabled(Context context) {
        int locationMode = 0;
        String locationProviders;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            try
            {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
        else
        {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    private Location getLastKnownLocation() {
        Location l=null;
        LocationManager mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
                l = mLocationManager.getLastKnownLocation(provider);
            }
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                bestLocation = l;
            }
        }
        return bestLocation;
    }

    public void createLocationRequest(){
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleAPIClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
        createLocationRequest();
    }

    @Override
    protected void onStart(){
        super.onStart();
        mGoogleAPIClient.connect();
    }
    @Override
    public void onConnected(Bundle connectionHint){
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    protected void startLocationUpdates(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 0);
        } else{
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleAPIClient, mLocationRequest, this);
            gMap.setMyLocationEnabled(true);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        if(requestCode == 0){
            if(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                if(requestCode == 0){
                    startLocationUpdates();
                }
            } else{

            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    // CLASS ADDRESSRESULTRECEIVER
    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler){
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData){
            if(resultData.containsKey(Constants.RESULT_DATA_KEY)){
                final String mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);

                // RESOLVE O ERRO DE "TRYING TO SEND MESSAGE TO A HANDLER ON A DEAD THREAD"
                // PORQUE O HANDLER TENTA EXECUTAR O TOAST DENTRO DO HANDLER onHandleIntent QUE FICA DEPOIS DESTRUÍDO
                Handler mHandler = new Handler(getMainLooper());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        //Toast.makeText(MapsActivity.this, mAddressOutput, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if(resultData.containsKey(Constants.LONGITUDE)){

                final LatLng latLng = new LatLng(
                        resultData.getDouble(Constants.LATITUDE),
                        resultData.getDouble(Constants.LONGITUDE)
                );


                focusMapa(latLng);
            }
        }
    }

    public void focusMapa(LatLng latlng){
        final CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latlng)
                .tilt(50)
                .zoom(19)
                .build();
        Handler mHandler = new Handler(getMainLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                gMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        });
    }

    protected void startIntentService(Location location){
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);
        startService(intent);
    }

}