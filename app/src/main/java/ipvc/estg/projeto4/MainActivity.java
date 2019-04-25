package ipvc.estg.projeto4;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{

    private static final String TAG = "MainActivity";
    GoogleMap gMap;
    private GoogleApiClient mGoogleAPIClient;
    LocationRequest mLocationRequest;
    LocationManager locationManager;
    private AddressResultReceiver mResultReceiver;
    Location location;

    Button btnChangeToImageTests;

    LinkedHashMap<Location, Boolean> checkedCoords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkedCoords = new LinkedHashMap<>();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btnChangeToImageTests = (Button) findViewById(R.id.btn_changeto_imagetests);
        btnChangeToImageTests.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, ActivityImageComparison.class);
                MainActivity.this.startActivity(myIntent);
            }
        });

        mResultReceiver = new AddressResultReceiver(null);
        mLocationRequest = new LocationRequest();
        buildGoogleApiClient();
    }

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

        if (isLocationEnabled(MainActivity.this) && location != null) {
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
    protected void onPause(){
        super.onPause();
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleAPIClient, this);
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
