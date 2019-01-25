package com.github.htotten.vidsensorapp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;



public class MainActivity extends Activity implements SensorEventListener {

    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mediaRecorder;
    private ImageButton capture, vid;
    private Context myContext;
    private FrameLayout cameraPreview;
    private Chronometer chrono;
    private TextView txt;

    int rate = 100;
    String timeStampFile;
    Timer timer;
    int VideoFrameRate = 30;

    private int cameraType = 1; //set front (1) or back (0) camera
    long timerCheckBefore;
    long timerCheckExtra;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        myContext = this;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        accLinSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
        mPreview = new CameraPreview(myContext, mCamera);
        cameraPreview.addView(mPreview);
        capture = (ImageButton) findViewById(R.id.button_capture);
        capture.setOnClickListener(captureListener);

        chrono = (Chronometer) findViewById(R.id.chronometer);
        txt = (TextView) findViewById(R.id.txt1);
        txt.setTextColor(-16711936);
    }




    public void onResume() {
        super.onResume();
        if (!checkCameraHardware(myContext)) {
            Toast toast = Toast.makeText(myContext, "Phone doesn't have a camera!", Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
        if (mCamera == null) {
            mCamera = Camera.open(cameraType); // set which camera
            mPreview.refreshCamera(mCamera);
        }
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, accLinSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }




    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera(); // when on Pause, release camera in order to be used from other applications
        sensorManager.unregisterListener(this);
    }


    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true; // this device has a camera
        } else {
            return false; // no camera on this device
        }
    }


    boolean recording = false;
    OnClickListener captureListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (recording) { // stop recording and release camera
                mediaRecorder.stop(); // stop the recording
                releaseMediaRecorder(); // release the MediaRecorder object
                Toast.makeText(MainActivity.this, "Video captured!", Toast.LENGTH_LONG).show();
                recording = false;
                chrono.stop();
                chrono.setBase(SystemClock.elapsedRealtime());
                chrono.start();
                chrono.stop();
                txt.setTextColor(-16711936);
                enddata();
            }
            else { // start recording
                timeStampFile = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File wallpaperDirectory = new File(Environment.getExternalStorageDirectory().getPath()+"/VidSensor/");
                wallpaperDirectory.mkdirs();
                File wallpaperDirectory1 = new File(Environment.getExternalStorageDirectory().getPath()+"/VidSensor/"+timeStampFile);
                wallpaperDirectory1.mkdirs();
                if (!prepareMediaRecorder()) {
                    Toast.makeText(MainActivity.this, "Fail in prepareMediaRecorder()!\n - Ended -", Toast.LENGTH_LONG).show();
                    finish();
                }
                // work on UiThread for better performance
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            mediaRecorder.start();
                        } catch (final Exception ex) {
                        }
                    }
                });
                Toast.makeText(MainActivity.this, "Recording...", Toast.LENGTH_LONG).show();
                storeData();
                chrono.setBase(SystemClock.elapsedRealtime());
                chrono.start();
                timerCheckExtra = (new Date()).getTime();
                txt.setTextColor(-65536);
                recording = true;
            }
        }
    };


    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset(); // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            mCamera.lock(); // lock camera for later use
        }
    }


    private boolean prepareMediaRecorder() {
        mediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        mediaRecorder.setProfile(CamcorderProfile.get(cameraType, CamcorderProfile.QUALITY_720P));
        mediaRecorder.setOrientationHint(270); //rotate to save right
        mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().getPath()+"/VidSensor/" + timeStampFile + "/" + timeStampFile  + ".mp4");
        mediaRecorder.setVideoFrameRate(VideoFrameRate);
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            return false;
        }
        return true;
    }


    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }



    /* --------------------- Saving Data ----------------------------*/


    double proxDist = 0;
    PrintWriter writer = null;


    class SayHello extends TimerTask {
        public void run() {
            long timeStamp1 = timerCheckBefore - timerCheckExtra; //only show time passed since app began running (vs 1970)
            double time1 = timeStamp1*.001; //convert milliseconds to seconds
            time1 = (double)Math.round(time1 * 1000d) / 1000d;

            writer.println(time1 + ","
                    + acc_x + "," + acc_y + "," + acc_z + ","
                    + linear_acc_x + "," + linear_acc_y + "," + linear_acc_z + ","
                    + gyro_x + "," + gyro_y + "," + gyro_z + ","
                    + rotv_x + "," + rotv_y + "," + rotv_z + ","
                    + proxDist);
        }
    }


    public void storeData() {
        String filePath = Environment.getExternalStorageDirectory().getPath()+"/VidSensor/" + timeStampFile + "/" + timeStampFile  +  ".csv";

        try {
            writer = new PrintWriter(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        writer.println("time" + ","
                + "Acc X" + "," + "Acc Y" + "," + "Acc Z" + ","
                + "Linear Acc X" + "," + "Linear Acc Y" + "," + "Linear Acc Z" + ","
                + "Gyro X" + "," + "Gyro Y" + "," + "Gyro Z" + ","
                + "Rot X" + "," + "Rot Y" + "," + "Rot Z" + ","
                + "Proximity Dist");

        timer = new Timer();
        timer.schedule(new SayHello(), 0, rate);

    }


    public void enddata() {
        writer.close();
    }


    /* ---------------------- Reading Data ------------------- */

    private SensorManager sensorManager;
    private Sensor accSensor;
    private Sensor accLinSensor;
    private Sensor proximitySensor;
    private Sensor gyroSensor;
    private Sensor rotSensor;



    float rotv_x = 0;
    float rotv_y = 0;
    float rotv_z = 0;
    float acc_x = 0;
    float acc_y = 0;
    float acc_z = 0;
    float linear_acc_x = 0;
    float linear_acc_y = 0;
    float linear_acc_z = 0;
    float gyro_x = 0;
    float gyro_y = 0;
    float gyro_z = 0;


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            timerCheckBefore = (new Date()).getTime();
            acc_x = event.values[0];
            acc_y = event.values[1];
            acc_z = event.values[2];
        }
        else if(event.sensor.getType() == Sensor.TYPE_PROXIMITY){
            proxDist = event.values[0];
        }
        else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            linear_acc_x = event.values[0];
            linear_acc_y = event.values[1];
            linear_acc_z = event.values[2];
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            gyro_x = event.values[0];
            gyro_y = event.values[1];
            gyro_z = event.values[2];
        }
        else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            rotv_x = event.values[0];
            rotv_y = event.values[1];
            rotv_z = event.values[2];
        }
    }




}
