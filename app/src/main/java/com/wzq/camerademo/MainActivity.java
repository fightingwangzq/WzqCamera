package com.wzq.camerademo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private String[] mPermissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private Button mCameraButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        mCameraButton = findViewById(R.id.jump_camera);
        mCameraButton.setOnClickListener(this);

        requestRunTimePermission(mPermissions, mListener);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    private PermissionListener mListener = new PermissionListener() {
        @Override
        public void onGranted() {
            Log.d(TAG, "onGranted");
        }

        @Override
        public void onGranted(List<String> grantedPermission) {
            Log.d(TAG, "onGranted d");
        }

        @Override
        public void onDenied(List<String> deniedPermission) {
            Log.d(TAG, "onDenied d");
        }

        @Override
        public void onDenied() {
            Log.d(TAG, "onDenied");
        }
    };

    // refer to https://blog.csdn.net/u013087553/article/details/94444407
    protected void requestRunTimePermission(String[] permissions, PermissionListener listener) {
        this.mListener = listener;

        //用于存放为授权的权限
        List<String> permissionList = new ArrayList<>();
        //遍历传递过来的权限集合
        for (String permission : permissions) {
            //判断是否已经授权
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                //未授权，则加入待授权的权限集合中
                permissionList.add(permission);
            }
        }

        //判断集合
        if (!permissionList.isEmpty()) {  //如果集合不为空，则需要去授权
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[permissionList.size()]), 1);
        } else {  //为空，则已经全部授权
            if (listener != null) {
                listener.onGranted();
            }
        }
    }

    /**
     * 权限申请结果
     *
     * @param requestCode  请求码
     * @param permissions  所有的权限集合
     * @param grantResults 授权结果集合
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) {
                    //被用户拒绝的权限集合
                    List<String> deniedPermissions = new ArrayList<>();
                    //用户通过的权限集合
                    List<String> grantedPermissions = new ArrayList<>();
                    for (int i = 0; i < grantResults.length; i++) {
                        //获取授权结果，这是一个int类型的值
                        int grantResult = grantResults[i];

                        if (grantResult != PackageManager.PERMISSION_GRANTED) { //用户拒绝授权的权限
                            String permission = permissions[i];
                            deniedPermissions.add(permission);
                        } else {  //用户同意的权限
                            String permission = permissions[i];
                            grantedPermissions.add(permission);
                        }
                    }

                    if (deniedPermissions.isEmpty()) {  //用户拒绝权限为空
                        if (mListener != null) {
                            mListener.onGranted();
                        }
                    } else {  //不为空
                        if (mListener != null) {
                            //回调授权成功的接口
                            mListener.onDenied(deniedPermissions);
                            //回调授权失败的接口
                            mListener.onGranted(grantedPermissions);
                            mListener.onDenied();
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private void jumpCameraActivity() {
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.jump_camera:
                jumpCameraActivity();
                break;
        }
    }

    public interface PermissionListener {

        //授权成功
        void onGranted();

        //授权部分
        void onGranted(List<String> grantedPermission);

        //拒绝授权
        void onDenied(List<String> deniedPermission);

        //授权失败
        void onDenied();

    }

}
