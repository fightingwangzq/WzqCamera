package com.wzq.camerademo;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.wzq.camerademo.camera.CameraManager;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "CameraActivity";

    private TextureView mTextureView;
    private TextView mTextView;

    private Button mSwitchCameraButton;
    private Button mCapturePictureButton;
    private CameraManager mCameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        initView();
        mCameraManager = new CameraManager(this);
        mCameraManager.configure(1280, 720, true, mTextureView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTextureView.setSurfaceTextureListener(mTextureListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraManager.stopCamera();
    }

    private void initView() {
        mTextureView = findViewById(R.id.texture_view);
        mTextView = findViewById(R.id.message_text);
        mTextView.setOnClickListener(this);
        mSwitchCameraButton = findViewById(R.id.switch_camera);
        mSwitchCameraButton.setOnClickListener(this);

        mCapturePictureButton = findViewById(R.id.capture_picture);
        mCapturePictureButton.setOnClickListener(this);
    }

    private TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            mCameraManager.startCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.switch_camera:
                mCameraManager.switchCamera();
                break;
            case R.id.capture_picture:
                mCameraManager.capturePicture();
                break;
        }
    }
}
