package com.ultrasoundprobe.probeview.navigation.drawing;

import android.opengl.GLSurfaceView;

import com.ultrasoundprobe.probeview.navigation.location.LocationData;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public interface SurfaceInterface {
    void onSurfaceCreated(GLSurfaceView glSurfaceView,
                          GL10 gl10, EGLConfig eglConfig,
                          float scale);
    void onSurfaceChanged(GLSurfaceView glSurfaceView,
                          GL10 gl10, int width, int height);
    void onDrawFrame(GLSurfaceView glSurfaceView,
                     GL10 gl10);

    void onTouchScale(GLSurfaceView glSurfaceView, float scale);
    void onTouchMove(GLSurfaceView glSurfaceView, float startX, float startY,
                     float endX, float endY, float deltaX, float deltaY);

    void onLocationDataUpdated(GLSurfaceView glSurfaceView, LocationData value);

    LocationData getViewMaxLocation();

    LocationData getCoilLocation();
    boolean setCoilLocation(LocationData value);
    void setCoilTouchBufferColor(boolean enable);

    LocationData getProbeLocation();
    boolean setProbeLocation(LocationData value);
    void setProbeTouchBufferColor(boolean enable);

    int addSurfaceDetection(LocationData value);
    void setSurfaceTouchBufferColor(int id, boolean enable);
    void setSurfaceTouchDetection(int id, boolean enable);
}
