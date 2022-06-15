package com.ultrasoundprobe.probeview.navigation.drawing;

import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.ultrasoundprobe.probeview.AppConfig;
import com.ultrasoundprobe.probeview.navigation.NavigationInterface;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.navigation.model.BaseModel;
import com.ultrasoundprobe.probeview.navigation.model.ObjectModel;
import com.ultrasoundprobe.probeview.navigation.object3d.CoilObject;
import com.ultrasoundprobe.probeview.navigation.object3d.ProbeObject;
import com.ultrasoundprobe.probeview.navigation.object3d.SurfaceObject;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ModelViewDrawing implements SurfaceInterface {
    private static final String TAG = "ModelViewDrawing";

    private static final float CENTER_X_DEFAULT = 0.0f;
    private static final float CENTER_Y_DEFAULT = 0.0f;
    private static final float CENTER_Z_DEFAULT = 0.0f;
    private static final float EYE_X_DEFAULT = 20.0f;
    private static final float EYE_Y_DEFAULT = 20.0f;
    private static final float EYE_Z_DEFAULT = 20.0f;

    // Trucking view (moving the view horizontally) maximum and minimum positions
    private static final float VIEW_X_MAX = 20.0f;
    private static final float VIEW_X_MIN = -20.0f;
    // Pedestal view (moving the view vertically) maximum and minimum positions
    private static final float VIEW_Y_MAX = 15.0f;
    private static final float VIEW_Y_MIN = -15.0f;

    // Maximum absolute position values of X, Z, Y observable in the view as
    // configured by `Matrix.perspectiveM()`
    private static final float VIEW_MAX_POSITION_X = 15.0f;
    private static final float VIEW_MAX_POSITION_Y = 15.0f;
    private static final float VIEW_MAX_POSITION_Z = 15.0f;

    private final List<ObjectModel> objectModels = new ArrayList<>();

    private final float[] projectionMatrix = new float[16];

    private GLSurfaceView glSurfaceView;

    private int screenWidth, screenHeight;

    private float centerX = CENTER_X_DEFAULT;
    private float centerY = CENTER_Y_DEFAULT;
    private float eyeX = EYE_X_DEFAULT;
    private float eyeY = EYE_Y_DEFAULT;

    private float motionScale;

    private final NavigationInterface navigationInterface;

    private enum ModelType {
        MODEL_COIL,
        MODEL_PROBE,
        MODEL_SURFACE,
    }

    public ModelViewDrawing(NavigationInterface navigationInterface) {
        this.navigationInterface = navigationInterface;
    }

    @Override
    public void onSurfaceCreated(GLSurfaceView glSurfaceView,
                                 GL10 gl10, EGLConfig eglConfig,
                                 float scale) {
        Log.d(TAG, "onSurfaceCreated");

        objectModels.add(new CoilObject(objectModels.size()));
        objectModels.add(new ProbeObject(objectModels.size()));

        this.glSurfaceView = glSurfaceView;
        motionScale = scale;

        gl10.glEnable(gl10.GL_DEPTH_TEST);

        // Set background color: red, green, blue, alpha
        gl10.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        if (navigationInterface != null) {
            navigationInterface.on3dViewCreated();
        }
    }

    @Override
    public void onSurfaceChanged(GLSurfaceView glSurfaceView,
                                 GL10 gl10, int width, int height) {
        float ratio = (float)width / height;

        screenWidth = width;
        screenHeight = height;

        Log.d(TAG, "onSurfaceChanged: " + screenWidth + "x" + screenHeight);

        gl10.glViewport(0, 0, width, height);
        // Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 100);
        Matrix.perspectiveM(projectionMatrix, 0, 45, ratio, 0.1f, 150);
    }

    @Override
    public void onDrawFrame(GLSurfaceView glSurfaceView,
                            GL10 gl10) {
        float[] modelMatrix = new float[16];
        float[] viewMatrix = new float[16];
        float[] mvpMatrix = new float[16];
        ObjectModel probe = null;

        // Log.d(TAG, "onDrawFrame");

        // Draw background color
        gl10.glClear(gl10.GL_COLOR_BUFFER_BIT | gl10.GL_DEPTH_BUFFER_BIT);

        // Draw all object models
        for (ObjectModel model: objectModels) {
            // Hide coil object or not
            if (!AppConfig.CoilObject3dViewVisible &&
                    model.getId() == ModelType.MODEL_COIL.ordinal())
                continue;

            Matrix.setIdentityM(modelMatrix, 0);

            // Model transformation is in this inverse order: scaling, rotation,
            // around xyz-axis, and then translation
            Matrix.translateM(modelMatrix, 0, model.px, model.py, model.pz);
            if (model.az != 0)
                Matrix.rotateM(modelMatrix, 0, model.az, 0, 0, 1.0f);
            if (model.ay != 0)
                Matrix.rotateM(modelMatrix, 0, model.ay, 0, 1.0f, 0);
            if (model.ax != 0)
                Matrix.rotateM(modelMatrix, 0, model.ax, 1.0f, 0, 0);
            Matrix.scaleM(modelMatrix, 0, model.sx, model.sy, model.sz);

            // Set camera view position
            Matrix.setLookAtM(viewMatrix, 0,
                    eyeX / motionScale,
                    eyeY / motionScale,
                    EYE_Z_DEFAULT / motionScale,
                    centerX / motionScale,
                    centerY / motionScale,
                    CENTER_Z_DEFAULT / motionScale,
                    0, 1.0f, 0);

            // Calculate model-view-projection matrix, its matrix multiplication is in
            // this inverse order: model, view, and then projection transformation
            Matrix.multiplyMM(mvpMatrix,0, projectionMatrix,0, viewMatrix,0);
            Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0);

            // Initialize surface touch detection
            model.initSurfaceTouchDetection(modelMatrix);

            // Detect surface touch between source and target object models
            if (model.getClass().getName().equals(ProbeObject.class.getName())) {
                probe = model;
            } else if (model.getClass().getName().equals(SurfaceObject.class.getName()) &&
                    probe != null && model.getSurfaceTouchDetection()) {
                // Get old results before run surface touch detection
                BaseModel.SurfaceTouchResult[][] surfaceTouchResults =
                        model.getSurfaceTouchResults();

                // Run surface touch detection
                if (model.runSurfaceTouchDetection(probe)) {
                    // Get new results after run surface touch detection
                    BaseModel.SurfaceTouchResult[][] newSurfaceTouchResults =
                            model.getSurfaceTouchResults();
                    // This flag checks whether the old results are valid
                    boolean isOldResultsValid = (surfaceTouchResults != null &&
                            surfaceTouchResults.length == newSurfaceTouchResults.length &&
                            surfaceTouchResults.length != 0 &&
                            surfaceTouchResults[0].length == newSurfaceTouchResults[0].length);

                    if (navigationInterface == null)
                        continue;

                    // Generate one-time event from detection results
                    for (int i = 0; i < newSurfaceTouchResults.length; i++) {
                        for (int j = 0; j < newSurfaceTouchResults[i].length; j++) {
                            if (newSurfaceTouchResults[i][j].isTouched &&
                                    (!isOldResultsValid || !surfaceTouchResults[i][j].isTouched)) {
                                navigationInterface.on3dModelSurfaceTouched(probe, model);
                            } else if (!newSurfaceTouchResults[i][j].isTouched &&
                                    (isOldResultsValid && surfaceTouchResults[i][j].isTouched)) {
                                navigationInterface.on3dModelSurfaceUntouched(probe, model);
                            } else {
                                navigationInterface.on3dModelSurfaceTouchChecked(probe, model);
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to run surface touch detection between object models");
                }
            }

            // Draw object model
            model.draw(mvpMatrix);
        }

        if (navigationInterface != null) {
            navigationInterface.on3dViewDrawn();
        }
    }

    @Override
    public void onTouchScale(GLSurfaceView glSurfaceView, float scale) {
        this.motionScale = scale;

        // Redraw frame
        glSurfaceView.requestRender();
    }

    @Override
    public void onTouchMove(GLSurfaceView glSurfaceView, float startX, float startY,
                            float endX, float endY, float deltaX, float deltaY) {
        // Convert to OpenGL coordinates
        deltaY = -deltaY;

        float stepX = deltaX / (float)screenWidth * 2.0f;
        float stepY = deltaY / (float)screenWidth * 2.0f;
        float newCenterX = Math.min(Math.max(centerX - stepX, -VIEW_X_MAX), -VIEW_X_MIN);
        float newCenterY = Math.min(Math.max(centerY - stepY, -VIEW_Y_MAX), -VIEW_Y_MIN);

        stepX = Math.abs(newCenterX - centerX);
        stepY = Math.abs(newCenterY - centerY);

        // Update horizontal and vertical movement of view
        eyeX -= (deltaX >= 0 ? stepX : -stepX);
        eyeY -= (deltaY >= 0 ? stepY : -stepY);
        centerX = newCenterX;
        centerY = newCenterY;

        // Redraw frame
        glSurfaceView.requestRender();
    }

    @Override
    public void onLocationDataUpdated(GLSurfaceView glSurfaceView,
                                      LocationData value) {
        // We do not want to change the existing scaling properties of the model
        // from the update, so get the existing model first then set the remaining
        // properties based on the input data
        LocationData location = getModelLocation(ModelType.MODEL_PROBE.ordinal());

        if (location == null) {
            Log.e(TAG, "Existing location data error");
            return;
        }

        location.px = value.px;
        location.py = value.py;
        location.pz = value.pz;
        location.ax = value.ax;
        location.ay = value.ay;
        location.az = value.az;

        if (!setModelLocation(glSurfaceView, ModelType.MODEL_PROBE.ordinal(), location)) {
            Log.e(TAG, "Failed to set model, location info dropped");
        }
    }

    @Override
    public LocationData getViewMaxLocation() {
        return new LocationData(
                VIEW_MAX_POSITION_X, VIEW_MAX_POSITION_Y, VIEW_MAX_POSITION_Z,
                0, 0, 0,
                0, 0, 0);
    }

    @Override
    public LocationData getCoilLocation() {
        return getModelLocation(ModelType.MODEL_COIL.ordinal());
    }

    @Override
    public boolean setCoilLocation(LocationData value) {
        return setModelLocation(glSurfaceView, ModelType.MODEL_COIL.ordinal(), value);
    }

    @Override
    public void setCoilTouchBufferColor(boolean enable) {
        ObjectModel model = objectModels.get(ModelType.MODEL_COIL.ordinal());

        model.setSurfaceTouchBufferColor(enable);
    }

    @Override
    public LocationData getProbeLocation() {
        return getModelLocation(ModelType.MODEL_PROBE.ordinal());
    }

    @Override
    public boolean setProbeLocation(LocationData value) {
        return setModelLocation(glSurfaceView, ModelType.MODEL_PROBE.ordinal(), value);
    }

    @Override
    public void setProbeTouchBufferColor(boolean enable) {
        ObjectModel model = objectModels.get(ModelType.MODEL_PROBE.ordinal());

        model.setSurfaceTouchBufferColor(enable);
    }

    @Override
    public int addSurfaceDetection(LocationData value) {
        SurfaceObject surfaceObject = new SurfaceObject(objectModels.size());

        objectModels.add(surfaceObject);

        if (setModelLocation(glSurfaceView, surfaceObject.getId(), value)) {
            return surfaceObject.getId();
        } else {
            objectModels.remove(surfaceObject.getId());
            return -1;
        }
    }

    @Override
    public void setSurfaceTouchBufferColor(int id, boolean enable) {
        ObjectModel model = objectModels.get(id);

        model.setSurfaceTouchBufferColor(enable);
    }

    @Override
    public void setSurfaceTouchDetection(int id, boolean enable) {
        ObjectModel model = objectModels.get(id);

        model.setSurfaceTouchDetection(enable);
    }

    private LocationData getModelLocation(int id) {
        ObjectModel model = objectModels.get(id);

        if (model == null)
            return null;

        return new LocationData(LocationData.toDoubleArray(model.getLocation()));
    }

    private boolean setModelLocation(GLSurfaceView glSurfaceView, int id, LocationData value) {
        ObjectModel model = objectModels.get(id);

        if (glSurfaceView == null || model == null)
            return false;
        else if (!model.setLocation(LocationData.toFloatArray(value.getValues())))
            return false;

        // Redraw frame
        glSurfaceView.requestRender();

        return true;
    }
}