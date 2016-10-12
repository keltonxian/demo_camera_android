package com.kelton.demo.camerademo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by kelton on 16/2/25.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = "TEXTURE_VIEW";

    private Context _context = null;
    private HandlerThread _backgroundThread = null;

    private int _ratioWidth = 0;
    private int _ratioHeight = 0;

    private String _savePath = null;
    private File _saveFile = null;

    private String _cameraId = null;
    private CameraDevice _cameraDevice = null;
    private Semaphore _cameraOpenCloseLock = new Semaphore(1);
    private CaptureRequest.Builder _previewBuilder = null;
    private CaptureRequest _previewRequest = null;
    private CameraCaptureSession _captureSession = null;
    private boolean _flashSupported = false;

    private Handler _msgHandler = null;
    public static final int SAVED_SUCCESS = 1;

    private ImageReader _imageReader = null;

    private static final int STATE_PREVIEW          = 0;
    private static final int STATE_WAITING_LOCK     = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private int _state = STATE_PREVIEW;

    private int _cameraFace = CameraCharacteristics.LENS_FACING_BACK;//FRONT;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private Size _previewSize;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 270);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 90);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public CameraTextureView(Context context, String savePath, Handler handler) {
        super(context);

        this._context = context;
        this._msgHandler = handler;

        this._savePath = savePath;
        this._saveFile = new File(this._savePath);

        viewStart();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable width["+width+"], height["+height+"]");
        openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged width["+width+"], height["+height+"]");
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    private void startBackgroundThread() {
        _backgroundThread = new HandlerThread("CameraBackground");
        _backgroundThread.start();
//        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        _backgroundThread.quitSafely();
        try {
            _backgroundThread.join();
            _backgroundThread = null;
//            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setAspectRatio(int width, int height) {
        Log.d(TAG, "setAspectRatio size: "+width+","+height);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        _ratioWidth = width;
        _ratioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
//        Log.d(TAG, "func onMeasure: "+_ratioWidth+","+_ratioHeight+","+width+","+height);
        if (0 == _ratioWidth || 0 == _ratioHeight) {
            Log.d(TAG, "onMeasure 1-->: "+width+","+height);
            setMeasuredDimension(width, height);
        } else {
            int d_width = 0;
            int d_height = 0;
            if (width / height >= _ratioWidth / _ratioHeight) {
                d_width = width;
                d_height = width * _ratioHeight / _ratioWidth;
            } else {
                d_width = height * _ratioWidth / _ratioHeight;
                d_height = height;
            }
//            Log.d(TAG, "onMeasure 2-->: "+d_width+","+d_height);
            setMeasuredDimension(d_width, d_height);
        }
    }

    private void openCamera(int width, int height) {
        Log.d(TAG, "openCamera size: "+width+","+height);
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = (Activity)this._context;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!_cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // android above M makes some changes in granted permissions on run-time application
                if (ContextCompat.checkSelfPermission(this._context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            manager.openCamera(_cameraId, _deviceStateCallback, _msgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        Activity activity = (Activity)this._context;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing != _cameraFace) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
//                Size targetSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                Size targetSize = getNearestSizeByWidth(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), 800);
                _imageReader = ImageReader.newInstance(targetSize.getWidth(), targetSize.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/1);
                _imageReader.setOnImageAvailableListener(_onImageAvailableListener, _msgHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                Integer sensorOrientationInteger = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                int sensorOrientation = 0;
                if (null != sensorOrientationInteger)
                {
                    sensorOrientation = sensorOrientationInteger;
                }
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
//                _previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
//                        maxPreviewHeight);
                _previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight);
                Log.d(TAG, "_previewSize size: "+_previewSize.getWidth()+", "+_previewSize.getHeight());

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    this.setAspectRatio(_previewSize.getWidth(), _previewSize.getHeight());
                } else {
                    this.setAspectRatio(_previewSize.getHeight(), _previewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                _flashSupported = available == null ? false : available;

                _cameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error)) .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private static Size getNearestSizeByWidth(List<Size> list, int targetWidth) {
        int minOffset = 10000;
        int targetIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            Size size = list.get(i);
//            Log.d(TAG, "getNearestSizeByWidth size: "+size.getWidth()+", "+size.getHeight());
            if (Math.abs(size.getWidth() - targetWidth) < minOffset) {
                minOffset = Math.abs(size.getWidth() - targetWidth);
                targetIndex = i;
            }
        }
        return list.get(targetIndex);
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
//        Log.d(TAG, "chooseOptimalSize args: "+textureViewWidth+", "+textureViewHeight+", "+maxWidth+", "+maxHeight);
        for (Size option : choices) {
//            Log.d(TAG, "preview size option: ["+option.getWidth()+"]["+option.getHeight()+"]");
            if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                bigEnough.add(option);
            } else {
                notBigEnough.add(option);
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = (Activity)this._context;
        if (null == _previewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, _previewSize.getHeight(), _previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / _previewSize.getHeight(),
                    (float) viewWidth / _previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        this.setTransform(matrix);
    }

    private final ImageReader.OnImageAvailableListener _onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            _msgHandler.post(new ImageSaver(reader.acquireNextImage(), _saveFile));
        }

    };

    private class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        private ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
//            Log.d(TAG,"ImageSaver  ImageSaver");
        }

        @Override
        public void run() {
//            Log.d(TAG,"ImageSaver  run");
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//            FileOutputStream output = null;
//            try {
//                output = new FileOutputStream(mFile);
//                output.write(bytes);
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                mImage.close();
//                if (null != output) {
//                    try {
//                        output.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
            FileOutputStream out;
            try {
                out = new FileOutputStream(mFile);
                if (bm.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    out.flush();
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
            }
            _msgHandler.obtainMessage(SAVED_SUCCESS,"hi").sendToTarget();

        }

    }

    private CameraDevice.StateCallback _deviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG,"_deviceStateCallback onOpened.");
            _cameraOpenCloseLock.release();
            _cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = this.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(_previewSize.getWidth(), _previewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            _previewBuilder = _cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            _previewBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            _cameraDevice.createCaptureSession(Arrays.asList(surface, _imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//                            Log.d(TAG, "createCaptureSession onConfigured");
                            // The camera is already closed
                            if (null == _cameraDevice) {
                                Log.e(TAG, "createCaptureSession onConfigured null == _cameraDevice");
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            _captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                _previewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(_previewBuilder);

                                // Finally, we start displaying the camera preview.
                                _previewRequest = _previewBuilder.build();
                                _captureSession.setRepeatingRequest(_previewRequest,
                                        _captureCallback, _msgHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "createCaptureSession onConfigured " + e.toString());
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
//                            showToast("Failed");
                            Log.e(TAG, "createCaptureSession onConfigureFailed ");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback _captureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (_state) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        Log.d(TAG, "_captureCallback STATE_WAITING_LOCK 2 afState is null");
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            Log.d(TAG, "_captureCallback STATE_WAITING_LOCK aeState:"+aeState);
                            _state = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            Log.d(TAG, "_captureCallback STATE_WAITING_LOCK 2 aeState:"+aeState);
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        _state = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        _state = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private void captureStillPicture() {
        try {
            final Activity activity = (Activity)this._context;
            if (null == activity || null == _cameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    _cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(_imageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
//                    showToast("Saved: " + mFile);
                    Log.d(TAG, _saveFile.toString());
                    unlockFocus();
                }
            };

            _captureSession.stopRepeating();
            _captureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (_flashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlockFocus() {
        if (null == _captureSession) {
            Log.d(TAG, "_captureSession == null~~~~");
            return;
        }
        try {
            // Reset the auto-focus trigger
            _previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(_previewBuilder);

            _captureSession.capture(_previewBuilder.build(), _captureCallback,
                    _msgHandler);
            // After this, the camera will go back to the normal state of preview.
            _state = STATE_PREVIEW;
            _captureSession.setRepeatingRequest(_previewRequest, _captureCallback,
                    _msgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void lockFocus() {
//        Log.d(TAG, "lockFocus~~~~");
        try {
            // This is how to tell the camera to lock focus.
            _previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            _state = STATE_WAITING_LOCK;
            if (_cameraFace == CameraCharacteristics.LENS_FACING_FRONT) {
                captureStillPicture();
            } else {
                _captureSession.capture(_previewBuilder.build(), _captureCallback,
                        _msgHandler);
            }

//            Log.d(TAG, "lockFocus 2~~~~");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            _previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            _state = STATE_WAITING_PRECAPTURE;
            _captureSession.capture(_previewBuilder.build(), _captureCallback,
                    _msgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera");
        try {
            _cameraOpenCloseLock.acquire();
            if (null != _captureSession) {
                _captureSession.close();
                _captureSession = null;
            }
            if (null != _cameraDevice) {
                _cameraDevice.close();
                _cameraDevice = null;
            }
            if (null != _imageReader) {
                _imageReader.close();
                _imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            _cameraOpenCloseLock.release();
        }
    }

    public void viewStart() {
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (this.isAvailable()) {
            int width = this.getLayoutParams().width;
            int height = this.getLayoutParams().height;
            Log.d(TAG, "viewStart size: "+width+", "+height);
            openCamera(width, height);
        } else {
            this.setSurfaceTextureListener(this);
        }
    }

    public void viewEnd() {
        Log.d(TAG, "viewEnd");
        closeCamera();
        stopBackgroundThread();
    }

    public void takePhoto() {
        lockFocus();
    }

    public void changeCamera() {
        _cameraFace = _cameraFace == CameraCharacteristics.LENS_FACING_FRONT?CameraCharacteristics.LENS_FACING_BACK:CameraCharacteristics.LENS_FACING_FRONT;
        viewEnd();
        viewStart();
    }

    public void changeSize() {
        viewEnd();
        viewStart();
    }

    public void close() {
        _msgHandler = null;
        viewEnd();
    }
}
