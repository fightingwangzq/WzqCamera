package com.wzq.camerademo.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Collections;

public class CameraManager {
    private final static String TAG = "CameraManager";

    private TextureView mTextureView;
    private Surface mSurface;

    private android.hardware.camera2.CameraManager mCameraManager;
    private CameraDevice mCameraDevice;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCameraCaptureSession;

    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;

    private Size mPreviewSize;
    private Size mCaptureSize;
    private int mCameraDevices;

    private CameraCharacteristics mFrontCameraCharacteristics;
    private String mFrontCameraId;
    private int mFrontCameraOrientation;

    private CameraCharacteristics mBackCameraCharacteristics;
    private String mBackCameraId;
    private int mBackCameraOrientation;

    private Context mContext;
    private boolean mCameraIsFront;

    public CameraManager(Context context) {
        mContext = context;
    }

    public void configure(int width, int height, boolean isFront, TextureView textureView) {
        mCameraIsFront = isFront;
        mTextureView = textureView;
        cameraPreProcess(width, height);
    }

    private void cameraPreProcess(int width, int height) {
        mPreviewSize = new Size(width, height);
        setupCamera();
    }

    private void setupCamera() {
        mCameraManager = (android.hardware.camera2.CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            assert mCameraManager != null;
            final String[] idList = mCameraManager.getCameraIdList();
            mCameraDevices = idList.length;
            Log.d(TAG, "camera device num is " + mCameraDevices);
            for (String id : idList) {
                final CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                final int orientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (orientation == CameraCharacteristics.LENS_FACING_FRONT) {
                    mFrontCameraId = id;
                    mFrontCameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    mFrontCameraCharacteristics = characteristics;
                } else {
                    mBackCameraId = id;
                    mBackCameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    mBackCameraCharacteristics = characteristics;
                }
            }
        } catch (NullPointerException | CameraAccessException e) {
            Log.e(TAG, "setup camera exception " + e.toString());
        }
    }

    private void startCameraThread() {
        mCameraHandlerThread = new HandlerThread("CameraHandlerThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
    }

    private void stopCameraThread() {
        if (mCameraHandlerThread != null) {
            mCameraHandlerThread.quitSafely();
            try {
                mCameraHandlerThread.join();
                mCameraHandlerThread = null;
                mCameraHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startCamera() {
        startCameraThread();
        String cameraId;
        if (mCameraIsFront) {
            cameraId = mFrontCameraId;
        } else {
            cameraId = mBackCameraId;
        }
        openCamera(cameraId);
    }

    private void openCamera(String cameraId) {
        Log.d(TAG, "open camera");
        try {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "no camera permission");
                return;
            }
            mCameraManager.openCamera(cameraId, mCameraDeviceStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void switchCamera() {
        stopCamera();
        mCameraIsFront = !mCameraIsFront;
        startCamera();
    }

    public void stopCamera() {
        closeCamera();
        stopCameraThread();
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            mCameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "onError " + error);
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };

    private void startPreview() {
        Log.d(TAG, "start preview");
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        mSurface = previewSurface;
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface), mCaptureSessionStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback mCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigured");
            mPreviewRequest = mPreviewRequestBuilder.build();
            mCameraCaptureSession = session;
            try {
                mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.d(TAG, "onCaptureCompleted");
        }
    };

    public void capturePicture() {
        if (mCameraDevice == null || mCameraCaptureSession == null) return;
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(mSurface);

            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(builder.build(), mCaptureCallback, mCameraHandler);
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), null, mCameraHandler);
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
