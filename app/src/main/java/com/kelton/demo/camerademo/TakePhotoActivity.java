package com.kelton.demo.camerademo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

/**
 * Created by kelton on 16/10/9.
 */

public class TakePhotoActivity extends Activity {

    private final static String LOG_TAG = "TAKE_PHOTO";
    private final static int REQUEST_CAMERA_RESULT = 1;
    private final static int SIZE_MAKE_MAX = 3;

    private String _photoSavePath = null;
    private ImageView _imageView = null;
    private RelativeLayout _contentLayout = null;
    private CameraSurfaceView _cameraSurfaceView = null;
    private CameraTextureView _cameraTextureView = null;
    private int _cameraViewX = 0;
    private int _cameraViewY = 0;
    private int _cameraViewWidth = 0;
    private int _cameraViewHeight = 0;
    private int _cameraViewMarkWidth = 0;
    private int _cameraViewMarkHeight = 0;
    private int _sizeMarkIndex = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_photo);

        _photoSavePath = this.getFilesDir() + "/take_photo_saved_image.png";

        Point displaySize = new Point();
        Display display = getWindowManager().getDefaultDisplay();
        display.getSize(displaySize);
        Log.d(LOG_TAG, "display size: ["+displaySize.x+"]["+displaySize.y+"]"); // e.g: nexus5x 1080x1794

//        DisplayMetrics metrics = new DisplayMetrics();
//        display.getMetrics(metrics);
//        Log.d(LOG_TAG, "display metrics pixels: ["+metrics.widthPixels+"]["+metrics.heightPixels+"]"); // e.g: nexus5x 1080x1794
//        Log.d(LOG_TAG, "display metrics density["+metrics.density+"], densityDPI["+metrics.densityDpi+"]"); // e.g: nexus5x 1080x1794

        _cameraViewMarkWidth = displaySize.x/2;
        _cameraViewMarkHeight = displaySize.y/2;
//        _cameraViewMarkWidth = displaySize.x;
//        _cameraViewMarkHeight = displaySize.y;
        _cameraViewWidth = _cameraViewMarkWidth;
        _cameraViewHeight = _cameraViewMarkHeight;
        _cameraViewX = displaySize.x/2;
        _cameraViewY = displaySize.y/2;

        Button btn;

        btn = (Button) findViewById(R.id.btn_return);
        btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                returnToMain();
            }
        });

        btn = (Button) findViewById(R.id.btn_change_camera);
        btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeCamera();
            }
        });

        btn = (Button) findViewById(R.id.btn_change_size);
        btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeSize();
            }
        });

        btn = (Button) findViewById(R.id.btn_capture);
        btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                capture();
            }
        });

        showView();

        addImageView();
    }

    private void returnToMain() {
        if (null != _cameraSurfaceView) {
            _cameraSurfaceView.close();
        }
        if (null != _cameraTextureView) {
            _cameraTextureView.close();
        }
        Intent intent = new Intent();
        intent.setClass(this, MainActivity.class);
        this.startActivity(intent);
    }

    private void changeCamera() {
        if (null != _cameraSurfaceView) {
            _cameraSurfaceView.changeCamera();
            return;
        }
        if (null != _cameraTextureView) {
            _cameraTextureView.changeCamera();
        }
    }

    private void changeSize() {
        _sizeMarkIndex += 1;
        if (_sizeMarkIndex > SIZE_MAKE_MAX) {
            _sizeMarkIndex = 1;
        }
        _cameraViewWidth = _cameraViewMarkWidth / _sizeMarkIndex;
        _cameraViewHeight = _cameraViewMarkHeight / _sizeMarkIndex;
        if (_sizeMarkIndex == SIZE_MAKE_MAX) {
            Point displaySize = new Point();
            Display display = getWindowManager().getDefaultDisplay();
            display.getSize(displaySize);
            _cameraViewWidth = displaySize.x;
            _cameraViewHeight = displaySize.y;
        }
//        Log.d(LOG_TAG, "changeSize: "+_cameraViewWidth+", "+_cameraViewHeight);
        RelativeLayout.LayoutParams csViewParams = new RelativeLayout.LayoutParams(_cameraViewWidth, _cameraViewHeight);
        if (null != _cameraSurfaceView) {
            _cameraSurfaceView.setX(_cameraViewX - _cameraViewWidth / 2);
            _cameraSurfaceView.setY(_cameraViewY - _cameraViewHeight / 2);
            _cameraSurfaceView.setLayoutParams(csViewParams);
            _cameraSurfaceView.changeSize();
            return;
        }
        if (null != _cameraTextureView) {
            _cameraTextureView.setX(_cameraViewX - _cameraViewWidth / 2);
            _cameraTextureView.setY(_cameraViewY - _cameraViewHeight / 2);
            _cameraTextureView.setLayoutParams(csViewParams);
//        Log.d(LOG_TAG, "changeSize textureView size: "+_cameraTextureView.getLayoutParams().width+", "+_cameraTextureView.getLayoutParams().height);
            _cameraTextureView.changeSize();
        }
    }

    private void capture() {
        Log.d(LOG_TAG, "capture");
        if (null != _cameraSurfaceView) {
            Log.d(LOG_TAG, "_cameraSurfaceView");
            _cameraSurfaceView.takePhoto();
            return;
        }
        if (null != _cameraTextureView) {
            _cameraTextureView.takePhoto();
        }
    }

    private void addImageView() {
        RelativeLayout layout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams =
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT
                );
        layout.setLayoutParams(layoutParams);
        _imageView = new ImageView(this);

        layout.addView(_imageView);
        this.addContentView(layout, layoutParams);
        updateImageView();
    }

    private void updateImageView() {
        Point displaySize = new Point();
        Display display = getWindowManager().getDefaultDisplay();
        display.getSize(displaySize);
        Bitmap bitMap = BitmapFactory.decodeFile(_photoSavePath);
        if (null == bitMap) {
            return;
        }
        int width = bitMap.getWidth();
        int height = bitMap.getHeight();
        Log.d(LOG_TAG, " save image bitmap size: ["+width+"]["+height+"]");
        float scale = 0.5f;
        _imageView.setImageBitmap(bitMap);
        _imageView.setScaleX(scale);
        _imageView.setScaleY(scale);
        _imageView.setX(displaySize.x - (width * scale) / 2 * 3);
        _imageView.setY(displaySize.y - (height * scale) / 2 * 3);
    }

    private void showView() {
        _contentLayout = (RelativeLayout) findViewById(R.id.content_layout);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(LOG_TAG, "use Texture view");
            createTextureView(_contentLayout, _photoSavePath, _cameraViewX, _cameraViewY, _cameraViewWidth, _cameraViewHeight);
            Utils.showTip(this, "Use Texture View");
        } else {
            Log.d(LOG_TAG, "use surface view");
            createSurfaceView(_contentLayout, _photoSavePath, _cameraViewX, _cameraViewY, _cameraViewWidth, _cameraViewHeight);
            Utils.showTip(this, "Use Surface View");
        }
    }

    private void createSurfaceView(RelativeLayout rl, String savePath, int cameraViewX, int cameraViewY, int cameraViewWidth, int cameraViewHeight) {
        MsgHandler msgHandler = new MsgHandler(this);

        RelativeLayout.LayoutParams csViewParams = new RelativeLayout.LayoutParams(cameraViewWidth, cameraViewHeight);
        _cameraSurfaceView = new CameraSurfaceView(this, savePath, msgHandler);
        _cameraSurfaceView.setX(cameraViewX - cameraViewWidth / 2);
        _cameraSurfaceView.setY(cameraViewY - cameraViewHeight / 2);
        _cameraSurfaceView.setLayoutParams(csViewParams);
        _cameraSurfaceView.setZOrderMediaOverlay(true);

        rl.addView(_cameraSurfaceView);
    }

    private void createTextureView(final RelativeLayout rl, final String savePath, int cameraViewX, int cameraViewY, int cameraViewWidth, int cameraViewHeight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // android above M makes some changes in granted permissions on run-time application
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                    Utils.showTip(this, "need permission to use the camera services");
                }
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_RESULT);
                return;
            }
        }

        MsgHandler msgHandler = new MsgHandler(this);

        RelativeLayout.LayoutParams csViewParams = new RelativeLayout.LayoutParams(cameraViewWidth, cameraViewHeight);
        _cameraTextureView = new CameraTextureView(this, savePath, msgHandler);
        _cameraTextureView.setX(cameraViewX - cameraViewWidth / 2);
        _cameraTextureView.setY(cameraViewY - cameraViewHeight / 2);
        _cameraTextureView.setLayoutParams(csViewParams);
        _cameraTextureView.bringToFront();
        _cameraTextureView.requestLayout();
        _cameraTextureView.requestFocus();

        rl.addView(_cameraTextureView);
    }

    static class MsgHandler extends Handler {
        WeakReference<TakePhotoActivity> outerClass;

        MsgHandler(TakePhotoActivity activity) {
            outerClass = new WeakReference<>(activity);
        }
        public void handleMessage (Message msg) {//此方法在ui线程运行
            TakePhotoActivity ac = outerClass.get();
            switch(msg.what) {
                case CameraTextureView.SAVED_SUCCESS:
                    ac.saveImageSuccessCallback();
                    break;
                case CameraSurfaceView.SAVED_SUCCESS:
                    ac.saveImageSuccessCallback();
                    break;
            }
        }
    }

    private void saveImageSuccessCallback() {
        Utils.showTip(this, "saveImageSuccessCallback");
        updateImageView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(LOG_TAG, "requestCode: "+requestCode);
        switch (requestCode) {
            case  REQUEST_CAMERA_RESULT:
                Log.d(LOG_TAG, "grantResults[0]: "+grantResults[0]);
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    createTextureView(_contentLayout, _photoSavePath, _cameraViewX, _cameraViewY, _cameraViewWidth, _cameraViewHeight);
                } else {
                    Utils.showTip(this, "cannot use camera service without permission granted");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }
}
