package com.kelton.demo.camerademo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;
import java.util.List;

/**
 * Created by kelton on 16/1/25.
 */
public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int FACE_BACK_ROTATION = 90;
    private static final int FACE_FRONT_ROTATION = 90;
	private static final int CAMERA_BACK  = 0;
	private static final int CAMERA_FRONT = 1;

    private SurfaceHolder surfaceHolder = null;
    private Camera camera = null;
    private int cameraFacing = CAMERA_FRONT; // 0 is back

    public CameraSurfaceView(Context context) {
        super(context);

        initVars();
		setupCameraAndHolder();
    }

    @Override
    public void surfaceCreated(SurfaceHolder _surfaceHolder) {
        setPreview(camera, surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder _surfaceHolder, int i, int i1, int i2) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }
        setPreview(camera, surfaceHolder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder _surfaceHolder) {
        releaseCamera();
        surfaceHolder = null;
    }

    private void initVars() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraFacing = i;
                break;
            }
        }
    }

    private void initCamera() {
        if (!this.checkCameraHardware(this.getContext())) {
            Log.e(TAG, "Error initCamera has no camera");
            return;
        }
        if (null != camera) {
            Log.d(TAG, "initCamera camera already exists");
            return;
        }
        camera = getCamera(cameraFacing);
    }

    public void releaseCamera() {
        if (null == camera) {
            return;
        }
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    private void resetCamera() {
        initCamera();
        setPreview(camera, surfaceHolder);
    }

    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private void configCamera(Camera _camera, int rotation) {
        Camera.Parameters params = _camera.getParameters();

        Camera.Size size;
        int previewSizeMark = 0;
        List<Camera.Size> lPreviewSize = params.getSupportedPreviewSizes();
        for (int i = 0; i < lPreviewSize.size(); i++) {
            size = lPreviewSize.get(i);
//            Log.d(TAG, "Camera Preview size:"+size.width+", "+size.height);
            previewSizeMark = i; // most small one close to 1024
            if (size.width < 640) {
                break;
            }
        }
        size = lPreviewSize.get(previewSizeMark);
        Log.d(TAG, "Camera Preview size:"+size.width+", "+size.height);
        params.setPreviewSize(size.width, size.height);
        int pictureSizeMark = 0;
        List<Camera.Size> lPictureSize = params.getSupportedPictureSizes();
        for (int i = 0; i < lPictureSize.size(); i++) {
            size = lPictureSize.get(i);
//            Log.d(TAG, "Camera Picture size:"+size.width+", "+size.height);
            pictureSizeMark = i; // most small one close to 1024
            if (size.width < 1024) {
                break;
            }
        }
        size = lPictureSize.get(pictureSizeMark);
        params.setPictureSize(size.width, size.height);
        List<Integer> lFormat = params.getSupportedPictureFormats();
//        for (int i = 0; i < lFormat.size(); i++) {
//            Integer format = lFormat.get(i);
////            Log.d(TAG, "Camera picture format:"+format);
//        }
        if (lFormat.size() > 0) {
            params.setPictureFormat(lFormat.get(0));
        }

        // set the output picture's orientation
        params.setRotation(rotation);

        // already set FOCUS_MODE_CONTINUOUS_PICTURE in getCamera
//        params.setFocusMode(Parameters.FOCUS_MODE_AUTO);
        List<String> lfm = params.getSupportedFocusModes();
        String focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
        boolean hasAuto = false;
        for (int i = 0; i < lfm.size(); i++) {
            String fm = lfm.get(i);
            focusMode = fm;
            if (fm.equals(Camera.Parameters.FOCUS_MODE_AUTO)) {
                hasAuto = true;
            }
        }
        if (focusMode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            _camera.cancelAutoFocus();
        } else if (hasAuto) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        _camera.setParameters(params);
    }

    private Camera getCamera(int face) {
        Camera _camera;
        try {
            _camera = Camera.open(face);
            int rotation = FACE_BACK_ROTATION;
            if (1 == face) {
                rotation = FACE_FRONT_ROTATION;
            }
            _camera.setDisplayOrientation(rotation);

            configCamera(_camera, rotation);

        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            _camera = null;
            Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getMessage());
        }
        return _camera;
    }

    private void setPreview(Camera camera, SurfaceHolder holder){
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private void initHolder() {
        surfaceHolder = this.getHolder();
        surfaceHolder.addCallback(this);
    }

    public void changeCamera() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();

        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (CAMERA_BACK == cameraFacing) {
                // 现在是后置，变更为前置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    releaseCamera();
                    cameraFacing = i;
                    resetCamera();
                    break;
                }
            } else {
                // 现在是前置， 变更为后置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    releaseCamera();
                    cameraFacing = i;
                    resetCamera();
                    break;
                }
            }

        }
    }

	public boolean isFrontCamera() {
        return CAMERA_FRONT == cameraFacing;
	}

	public void setupCameraAndHolder() {
        initCamera();
        initHolder();
	}

    public void takePhoto(Camera.PictureCallback jpegCallback) {
        camera.takePicture(null, null, jpegCallback);
    }
}
