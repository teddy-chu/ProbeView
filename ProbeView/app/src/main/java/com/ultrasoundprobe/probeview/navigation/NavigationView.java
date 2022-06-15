package com.ultrasoundprobe.probeview.navigation;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.ultrasoundprobe.probeview.AppConfig;
import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.navigation.drawing.SurfaceInterface;
import com.ultrasoundprobe.probeview.navigation.location.DataConvert;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class NavigationView extends GLSurfaceView implements
        GLSurfaceView.Renderer, ScaleGestureDetector.OnScaleGestureListener {
    private static final String TAG = "NavigationView";

    private static final float MOTION_SCALE_MAX = 6.0f;
    private static final float MOTION_SCALE_MIN = 0.5f;
    private static final float MOTION_SCALE_DEFAULT = 1.5f;

    private final SurfaceInterface surfaceInterface;

    private final ScaleGestureDetector scaleGestureDetector;
    private final DataConvert dataConvert;

    private float motionStartX, motionStartY;
    private float motionDeltaX, motionDeltaY;
    private float motionScale = MOTION_SCALE_DEFAULT;

    private DeviceService.ImuData[] imuBuffersFromActiveCoils = null;
    private DeviceService.ImuData[] imuBuffersFromInactiveCoils = null;
    private long updateStartTime = -1;

    public NavigationView(Context context) {
        this(context, null);
    }

    public NavigationView(Context context, SurfaceInterface surfaceInterface) {
        super(context);
        this.surfaceInterface = surfaceInterface;

        scaleGestureDetector = new ScaleGestureDetector(context, this);

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        dataConvert = new DataConvert(context,
                // Use Python algorithm engine for data conversion
                DataConvert.EngineType.PythonAlgoBackend,
                // Use native algorithm engine for data conversion
                // DataConvert.EngineType.NativeLeastSquareLM,
                // Use remote algorithm engine for data conversion
                // DataConvert.EngineType.RemoteAlgoBackend,
                // Provide view constraint to the class for data conversion
                this.surfaceInterface.getViewMaxLocation());

        // Enable debug output
        dataConvert.setDebug(AppConfig.NavigationAlgorithmDebug);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        if (surfaceInterface != null) {
            surfaceInterface.onSurfaceCreated(this, gl10, eglConfig, motionScale);
        } else {
            // Set background color: red, green, blue, alpha
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            Log.d(TAG, "Set background color");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        if (surfaceInterface != null) {
            surfaceInterface.onSurfaceChanged(this, gl10, width, height);
        } else {
            GLES20.glViewport(0, 0, width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        if (surfaceInterface != null) {
            surfaceInterface.onDrawFrame(this, gl10);
        } else {
            // Redraw background color
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        final float x = motionEvent.getX();
        final float y = motionEvent.getY();

        scaleGestureDetector.onTouchEvent(motionEvent);

        switch(motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                motionStartX = x;
                motionStartY = y;
                motionDeltaX = 0;
                motionDeltaY = 0;
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = x - motionStartX;
                float deltaY = y - motionStartY;

                if (deltaX != motionDeltaX || deltaY != motionDeltaY) {
                    motionDeltaX = deltaX;
                    motionDeltaY = deltaY;

                    if (surfaceInterface != null) {
                        surfaceInterface.onTouchMove(this,
                                motionStartX, motionStartY, x, y, motionDeltaX, motionDeltaY);
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
                performClick();
                break;

            default :
                return super.onTouchEvent(motionEvent);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        motionScale *= scaleGestureDetector.getScaleFactor();
        motionScale = Math.max(MOTION_SCALE_MIN, Math.min(motionScale, MOTION_SCALE_MAX));

        if (surfaceInterface != null) {
            surfaceInterface.onTouchScale(this, motionScale);
        }

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
    }

    public void releaseSurface() {
        // TODO: Wait thread to terminate
        super.onDetachedFromWindow();
    }

    public boolean updateImuData(DeviceService.ImuData[] valuesFromActiveCoils,
                                 DeviceService.ImuData[] valuesFromInactiveCoils) {
        if (surfaceInterface == null || dataConvert == null)
            return false;

        // Measurement of interval time between consecutive call to algorithm
        if (updateStartTime < 0)
            updateStartTime = System.currentTimeMillis();
        Log.d(TAG, "IMU data received with interval time: " +
                (System.currentTimeMillis() - updateStartTime) + "ms");
        updateStartTime = System.currentTimeMillis();

        // Offload algorithm call to another thread so it does not block UI update
        if (isImuBufferEmpty()) {
            imuBuffersFromActiveCoils = valuesFromActiveCoils;
            imuBuffersFromInactiveCoils = valuesFromInactiveCoils;

            // Start running algorithm call
            new Thread() {
                @Override
                public void run() {
                    runAlgorithm();
                }
            }.start();
        }

        return true;
    }

    public boolean isImuBufferEmpty() {
        return imuBuffersFromActiveCoils == null && imuBuffersFromInactiveCoils == null;
    }

    public SurfaceInterface getSurfaceInterface() {
        return surfaceInterface;
    }

    public DataConvert getDataConvert() {
        return dataConvert;
    }

    private void runAlgorithm() {
        if (surfaceInterface != null && dataConvert != null) {
            Log.d(TAG, "Start running algorithm " +
                    dataConvert.getEngine().getClass().getSimpleName());

            long startTime = System.currentTimeMillis();

            LocationData location = dataConvert.getViewData(
                    imuBuffersFromActiveCoils, imuBuffersFromInactiveCoils);

            Log.d(TAG, "Algorithm finished with " +
                    (System.currentTimeMillis() - startTime) + "ms");

            if (location != null) {
                // Update model view by the location results
                surfaceInterface.onLocationDataUpdated(NavigationView.this, location);
            } else {
                Log.e(TAG, "Invalid location from algorithm");
            }
        }

        imuBuffersFromActiveCoils = null;
        imuBuffersFromInactiveCoils = null;
    }
}
