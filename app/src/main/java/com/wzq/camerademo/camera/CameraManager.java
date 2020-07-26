package com.wzq.camerademo.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class CameraManager {
    private final static String TAG = "CameraManager";

    private TextureView mTextureView;
    private Surface mSurface;

    private android.hardware.camera2.CameraManager mCameraManager;
    private CameraDevice mCameraDevice;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCameraCaptureSession;

    private ImageReader mCaptureImageReader;

    private HandlerThread mCameraPreviewHandlerThread;
    private Handler mCameraPreviewHandler;

    private HandlerThread mCameraCaptureHandlerThread;
    private Handler mCameraCaptureHandler;

    private Size mPreviewSize;
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
        mCaptureImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
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
        mCameraPreviewHandlerThread = new HandlerThread("CameraPreviewHandlerThread");
        mCameraPreviewHandlerThread.start();
        mCameraPreviewHandler = new Handler(mCameraPreviewHandlerThread.getLooper());

        mCameraCaptureHandlerThread = new HandlerThread("CameraCaptureHandlerThread");
        mCameraCaptureHandlerThread.start();
        mCameraCaptureHandler = new Handler(mCameraCaptureHandlerThread.getLooper());
    }

    private void stopCameraThread() {
        if (mCameraPreviewHandlerThread != null) {
            mCameraPreviewHandlerThread.quitSafely();
            try {
                mCameraPreviewHandlerThread.join();
                mCameraPreviewHandlerThread = null;
                mCameraPreviewHandler = null;
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
            mCameraManager.openCamera(cameraId, mCameraDeviceStateCallback, mCameraPreviewHandler);
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
            mPreviewRequestBuilder.addTarget(mSurface);
            mCaptureImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraCaptureHandler);
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface, mCaptureImageReader.getSurface()), mCaptureSessionStateCallback, mCameraPreviewHandler);
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
                mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraPreviewHandler);
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
            try {
                mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraPreviewHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    };

    public void capturePicture() {
        if (mCameraDevice == null || mCameraCaptureSession == null) return;
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            builder.addTarget(mCaptureImageReader.getSurface());
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.abortCaptures();
            mCameraCaptureSession.capture(builder.build(), mCaptureCallback, mCameraCaptureHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            final String filePath = mContext.getExternalCacheDir().getAbsolutePath() + File.separator + "file.jpg";
            Log.d(TAG, "capture filepath " + filePath);
            mCameraCaptureHandler.post(new CaptureRunnable(reader.acquireNextImage(), filePath));
        }
    };

    public class CaptureRunnable implements Runnable {
        private Image mImage;
        private File mFile;

        public CaptureRunnable(Image image, File file) {
            this.mImage = image;
            this.mFile = file;
        }

        public CaptureRunnable(Image image, String filePath) {
            this(image, new File(filePath));
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            FileOutputStream outputStream = null;
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try {
                outputStream = new FileOutputStream(mFile);
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    assert outputStream != null;
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
