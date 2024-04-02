package com.aminogira.aminosecuritylight;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.IOException;

public class SoundDetectionService extends Service {
    private CameraManager mCameraManager;
    private String mCameraId;
    private boolean isFlashOn = false;
    private Camera mCamera;

    private MediaRecorder mRecorder;
    private static final double SAMPLE_RATE = 44100.0;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize((int) SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    private AudioRecord mAudioRecorder;
    private Thread mThread;
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startListening();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopListening();
        super.onDestroy();
    }


    private void startListening() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile("/dev/null");

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mRecorder.start();

        isRunning = true;

        mThread = new Thread(new Runnable() {
            public void run() {
                while (isRunning) {
                    if (isSoundDetected()) {
                        // Turn on flashlight
                        turnOnFlash();
                        try {
                            Thread.sleep(60000); // Flashlight on for 1 minute
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // Turn off flashlight after 1 minute
                        turnOffFlash();
                    }
                }
            }
        });

        mThread.start();
    }

    private boolean isSoundDetected() {
        short[] buffer = new short[BUFFER_SIZE];
        int bufferReadResult;

        if (mAudioRecorder == null) {

            mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    (int) SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);
        }

        bufferReadResult = mAudioRecorder.read(buffer, 0, BUFFER_SIZE);

        double sumLevel = 0;

        for (int i = 0; i < bufferReadResult; i++) {
            sumLevel += buffer[i] * buffer[i];
        }

        double rms = Math.sqrt(sumLevel / bufferReadResult);

        final double threshold = 1000;

        if (rms > threshold) {
            return true;
        }

        return false;
    }

    private void stopListening() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }

        isRunning = false;
    }

    private void turnOnFlash() {
        if (!isFlashOn) {
            try {
                mCamera = Camera.open();
                Camera.Parameters params = mCamera.getParameters();
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                mCamera.setParameters(params);
                mCamera.startPreview();
                isFlashOn = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void turnOffFlash() {
        if (isFlashOn) {
            try {
                if (mCamera != null) {
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                    isFlashOn = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameraId = mCameraManager.getCameraIdList()[0]; // Assume first camera is the flashlight
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
