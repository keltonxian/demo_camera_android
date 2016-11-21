package com.kelton.demo.camerademo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by kelton on 16/1/25.
 */
@SuppressWarnings("deprecation")
public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "SURFACE_VIEW";

    private static final int FACE_BACK_ROTATION = 90;
    private static final int FACE_FRONT_ROTATION = 90;
	private static final int CAMERA_BACK  = Camera.CameraInfo.CAMERA_FACING_BACK;
    private static final int CAMERA_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;

    public static final int SAVED_SUCCESS = 2;

    private SurfaceHolder _surfaceHolder = null;
    private Camera _camera = null;
    private int _cameraFacing = CAMERA_BACK;//CAMERA_FRONT; // 0 is back
    private Handler _msgHandler = null;
    private File _saveFile = null;

    public CameraSurfaceView(Context context, String savePath, Handler handler) {
        super(context);

        this._msgHandler = handler;
        this._saveFile = new File(savePath);

        // initVars();
		setupCameraAndHolder();
    }

    public CameraSurfaceView(Context context) {
        super(context);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        setPreview(_camera, surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged format["+format+"], width["+width+"], height["+height+"]");
        if (_surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            _camera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }
//        configPreviewSize(_camera, width, height);
        setPreview(_camera, _surfaceHolder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCamera();
        _surfaceHolder = null;
    }

    private void initVars() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                _cameraFacing = i;
                break;
            }
        }
    }

    private void initCamera() {
        if (!this.checkCameraHardware(this.getContext())) {
            Log.e(TAG, "Error initCamera has no camera");
            return;
        }
        if (null != _camera) {
            Log.d(TAG, "initCamera camera already exists");
            return;
        }
        _camera = getCamera(_cameraFacing);
    }

    public void releaseCamera() {
        if (null == _camera) {
            return;
        }
        _camera.setPreviewCallback(null);
        _camera.stopPreview();
        _camera.release();
        _camera = null;
    }

    private void resetCamera() {
        initCamera();
        setPreview(_camera, _surfaceHolder);
    }

    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private void configPreviewSize(Camera camera, int width, int height) {
        // no need to change preview size. It's good enough to change layout size out side.
        Log.d(TAG, "configPreviewSize: ["+width+"]["+height+"]");
        Camera.Parameters params = camera.getParameters();
        Camera.Size size;
        int previewSizeMark = 0;
        List<Camera.Size> lPreviewSize = params.getSupportedPreviewSizes();
        for (int i = 0; i < lPreviewSize.size(); i++) {
            size = lPreviewSize.get(i);
//            Log.d(TAG, "SupportedPreviewSizes:"+size.width+", "+size.height);
            previewSizeMark = i; // most small one close to 1024
            if (size.width <= width && size.height <= height) {
                break;
            }
        }
        size = lPreviewSize.get(previewSizeMark);
        Log.d(TAG, "Camera Preview size:"+size.width+", "+size.height);
        params.setPreviewSize(size.width, size.height);

        _camera.setParameters(params);
    }

    private void configCamera(Camera camera) {
        Camera.Parameters params = camera.getParameters();

        Camera.Size size;
        int pictureSizeMark = 0;
        List<Camera.Size> lPictureSize = params.getSupportedPictureSizes();
        for (int i = 0; i < lPictureSize.size(); i++) {
            size = lPictureSize.get(i);
//            Log.d(TAG, "Camera Picture size:"+size.width+", "+size.height);
            pictureSizeMark = i; // most small one close to 1024
            if (size.width < 800) {
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
//        params.setRotation(rotation);

        // already set FOCUS_MODE_CONTINUOUS_PICTURE in getCamera
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
            camera.cancelAutoFocus();
        } else if (hasAuto) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        camera.setParameters(params);
    }

    private Camera getCamera(int face) {
        Log.d(TAG, "getCamera face:" + face);
        Camera camera;
        try {
            camera = Camera.open(face);
            int rotation = FACE_BACK_ROTATION;
            if (1 == face) {
                rotation = FACE_FRONT_ROTATION;
            }
            camera.setDisplayOrientation(rotation);

            configCamera(camera);

        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            camera = null;
            Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getMessage());
        }
        return camera;
    }

    private void setPreview(Camera camera, SurfaceHolder holder){
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private void initHolder() {
        _surfaceHolder = this.getHolder();
        _surfaceHolder.addCallback(this);
    }

    public void changeCamera() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();

        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (CAMERA_BACK == _cameraFacing) {
                // 现在是后置，变更为前置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    releaseCamera();
                    _cameraFacing = i;
                    resetCamera();
                    break;
                }
            } else {
                // 现在是前置， 变更为后置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    releaseCamera();
                    _cameraFacing = i;
                    resetCamera();
                    break;
                }
            }

        }
    }

	public boolean isFrontCamera() {
        return CAMERA_FRONT == _cameraFacing;
	}

	public void setupCameraAndHolder() {
        initCamera();
        initHolder();
	}

    private class ImageSaver implements Runnable {

        /**
         * The image Bitmap
         */
        private Bitmap _bitmap;
        /**
         * The file we save the image into.
         */
        private final File _saveFile;

        private boolean _isFrontCamera;

        private ImageSaver(Bitmap bitmap, File saveFile, boolean isFrontCamera) {
            _bitmap = bitmap;
            _saveFile = saveFile;
            _isFrontCamera = isFrontCamera;
        }

        private Bitmap rotate(Bitmap bitmap, int degree) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            Matrix mtx = new Matrix();
            mtx.setRotate(degree);

            return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
        }

        @Override
        public void run() {
            FileOutputStream out;
            try {
                ExifInterface exif = new ExifInterface(_saveFile.toString());

//                Log.d("EXIF value", exif.getAttribute(ExifInterface.TAG_ORIENTATION));
                if(exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("6")){
                    _bitmap = rotate(_bitmap, 90);
                } else if(exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("8")){
                    _bitmap = rotate(_bitmap, 270);
                } else if(exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("3")){
                    _bitmap = rotate(_bitmap, 180);
                } else if(exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("0")){
                    _bitmap = rotate(_bitmap, 90);
                }
                if (_isFrontCamera) {
                    _bitmap = rotate(_bitmap, 180);

                }

                out = new FileOutputStream(_saveFile);
                if (_bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    out.flush();
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            _msgHandler.obtainMessage(SAVED_SUCCESS,"hi").sendToTarget();
        }
    }

    public void takePhoto() {
        _camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Log.d(TAG, "onPictureTaken");
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                _msgHandler.post(new ImageSaver(bitmap, _saveFile, isFrontCamera()));
                releaseCamera();
                resetCamera();
            }
        });
    }

    public void changeSize() {
        releaseCamera();
        resetCamera();
    }

    public void close() {
        _msgHandler = null;
        releaseCamera();
    }
}
