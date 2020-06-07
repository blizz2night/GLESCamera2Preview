package com.github.blizz2inght.gridfilter;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import static android.content.Context.CAMERA_SERVICE;

public class CameraController {
    private static final String TAG = "CameraController";
    public static final int OPEN_CAMERA = 1;
    public static final int CLOSE_CAMERA= 2;
    private final Context mContext;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private CameraManager mCameraManager;
    private String[] mCameraIdList;
    private String mCameraId;
    private CameraCharacteristics mCharacteristics;
    private CameraDevice mCameraDevice;
    private StreamConfigurationMap mConfigurationMap;
    private Size[] mPreviewSizes;
    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mRequestBuilder;
    private Surface mSurface;

    public CameraController(Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread("TAG");
        mHandlerThread.start();
        mHandlerThread.getLooper();
        mHandler = new Handler(mHandlerThread.getLooper(), mCameraHandlerCallback);

        mCameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        Log.i(TAG, "onResume: "+mCameraManager);
        if (mCameraManager != null) {
            try {
                mCameraIdList = mCameraManager.getCameraIdList();
                if (mCameraIdList.length > 0) {
                    mCameraId = mCameraIdList[0];
                    mCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
                    mConfigurationMap = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (mConfigurationMap != null) {
                        mPreviewSizes = mConfigurationMap.getOutputSizes(SurfaceTexture.class);
                    }
                }
            } catch (CameraAccessException e) {
                Log.e(TAG, "openCamera: ", e);
            }
        }
    }

    private Handler.Callback mCameraHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case OPEN_CAMERA:
                    if (msg.obj instanceof Surface) {
                        mSurface = (Surface) msg.obj;
                        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            try {
                                Log.i(TAG, "handleMessage: openCamera+++");
                                mCameraManager.openCamera(mCameraId, mCameraCallback, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "handleMessage: ", e);
                            }
                        }
                    }
                    break;
                case CLOSE_CAMERA:
                    Log.i(TAG, "handleMessage: CLOSE_CAMERA");
                    Log.i(TAG, "handleMessage: close session+++");

                    if (mSession != null) {
                        try {
                            mSession.abortCaptures();
//                            mSession.stopRepeating();
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "handleMessage: abortCaptures error", e);
                        }
                        mSession.close();
                        mSession = null;
                    }
                    Log.i(TAG, "handleMessage: close session---");

                    Log.i(TAG, "handleMessage: close device+++");
                    if (mCameraDevice != null) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                    Log.i(TAG, "handleMessage: close device---");

                    if (mSurface != null) {
                        mSurface.release();
                        mSurface = null;
                    }
                    break;
            }
            return false;
        }
    };

    private CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i(TAG, "onOpened---: "+camera);
            mCameraDevice = camera;
            if (mSurface != null) {
                try {
                    List<Surface> surfaces = new ArrayList<>();
                    surfaces.add(mSurface);
                    mRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mRequestBuilder.addTarget(mSurface);
                    mCameraDevice.createCaptureSession(surfaces, mSessionCallback, null);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "onOpened: ", e);
                }
            }
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.i(TAG, "onClosed: ");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "onDisconnected: ");
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.w(TAG, "onError: "+error);
            camera.close();
            mCameraDevice = null;
        }
    };

    private CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "onConfigured: ");
            mSession = session;
            CaptureRequest captureRequest = mRequestBuilder.build();
            try {
                mSession.setRepeatingRequest(captureRequest, mPreviewStateCallBack, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "onConfigured: ", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "onConfigureFailed: " + session);
        }
    };

    private CameraCaptureSession.CaptureCallback mPreviewStateCallBack = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            final Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
            final Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            final Integer aeMode = result.get(CaptureResult.CONTROL_AE_MODE);
            final Integer AeState = result.get(CaptureResult.CONTROL_AE_STATE);
            Log.i(TAG, "onCaptureCompleted: afMode=" + afMode + ", afState=" + afState
                    + ", aeMode=" + aeMode
                    + ", aeState=" + AeState);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.i(TAG, "onCaptureFailed: "+failure);
        }
    };

    public void close() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendEmptyMessage(CLOSE_CAMERA);
    }

    public void open(Surface surface) {
        mHandler.obtainMessage(OPEN_CAMERA, surface).sendToTarget();
    }


    public void release() {
        mHandler.getLooper().quitSafely();
    }


    public Size filterPreviewSize(int screenWidth, int screenHeight) {
        final Size[] previewSizes = mPreviewSizes;
        Size previewSize = new Size(480,640);
        for (int i = 0; i < previewSizes.length; i++) {
            //注意相机的sensor方向，比较时w和h要互换
            Size size = previewSizes[i];
            final int w = size.getWidth();
            final int h = size.getHeight();
            if (w >= previewSize.getWidth()
                    && h >= previewSize.getHeight()
                    && w <= screenHeight && h <= screenWidth) {
                previewSize = size;
            }
        }
        Log.i(TAG, "init: "+previewSize);
        return previewSize;
    }
}
