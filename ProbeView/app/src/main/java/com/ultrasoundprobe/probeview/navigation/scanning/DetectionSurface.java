package com.ultrasoundprobe.probeview.navigation.scanning;

import android.util.Log;

import com.ultrasoundprobe.probeview.navigation.drawing.SurfaceInterface;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DetectionSurface {
    private static final String TAG = "DetectionSurface";

    // This defines the number of surface models to construct a curved surface
    // with end-to-end of 90 degree
    private final int SURFACE_CURVE_STEP = 6;
    // This defines the number of surface models to construct a front surface
    // with width = SURFACE_WIDTH x SURFACE_WIDTH_STEP
    private final int SURFACE_WIDTH_STEP = 2;
    // This defines the number of surface models to construct a top surface
    // following the construction of a curved surface with
    // height = SURFACE_HEIGHT x SURFACE_HEIGHT_STEP
    private final int SURFACE_HEIGHT_STEP = 2;

    // TODO: Auto read width and height from the surface model
    private final float SURFACE_WIDTH = 2;
    private final float SURFACE_HEIGHT = 1;

    private final float SURFACE_PX = 0;
    private final float SURFACE_PY = 0;
    private final float SURFACE_PZ = 0;
    private final float SURFACE_AZ = 0;

    private Map<Integer, Integer> navigationModelNextId = new LinkedHashMap<>();

    private final SurfaceInterface surfaceInterface;

    public DetectionSurface(SurfaceInterface surfaceInterface) {
        this.surfaceInterface = surfaceInterface;

        navigationModelNextId.put(2, 10);
        navigationModelNextId.put(10, 3);
        navigationModelNextId.put(3, 11);
        navigationModelNextId.put(11, 4);
        navigationModelNextId.put(4, 12);
        navigationModelNextId.put(12, 5);
        navigationModelNextId.put(5, 13);
        navigationModelNextId.put(13, 6);
        navigationModelNextId.put(6, 14);
        navigationModelNextId.put(14, 7);
        navigationModelNextId.put(7, 15);
        navigationModelNextId.put(15, 8);
        navigationModelNextId.put(8, 16);
        navigationModelNextId.put(16, 9);
        navigationModelNextId.put(9, 17);
        navigationModelNextId.put(17, -1);
    }

    public void loadSurfaceModel() {
        int curveStep = SURFACE_CURVE_STEP;
        int widthStep = SURFACE_WIDTH_STEP;
        int heightStep = SURFACE_HEIGHT_STEP;
        float width = SURFACE_WIDTH;
        float height = SURFACE_HEIGHT;
        float px = SURFACE_PX;
        float py = SURFACE_PY;
        float pz = SURFACE_PZ;
        float az = SURFACE_AZ;
        float scale = 1;

        for (int i = 0; i < widthStep; i++) {
            float angle = (90 - az) / (curveStep - 1);

            px += (height / 2.0f * (float)Math.cos(Math.toRadians(90 - az)));
            py -= (height / 2.0f * (float)Math.sin(Math.toRadians(90 - az)));

            for (int j = 0; j < 2; j++) {
                // Construct a curved surface
                for (int k = 0; k < curveStep; k++) {
                    float x = (height * (float)Math.cos(Math.toRadians(90 - az)));
                    float y = (height * (float)Math.sin(Math.toRadians(90 - az)));
                    int id;

                    // Add a surface model for touch detection
                    id = surfaceInterface.addSurfaceDetection(new LocationData(
                            px - x / 2.0f, py + y / 2.0f, pz,
                            0, 0, az, scale, scale / 2.0f, scale));

                    // Preserve touched surface color for indication after touch detected with
                    // other models
                    surfaceInterface.setSurfaceTouchBufferColor(id, true);

                    // Disable touch detection with other models
                    surfaceInterface.setSurfaceTouchDetection(id, false);

                    // Position and angle for next surface
                    px -= x;
                    py += y;
                    az += angle;

                    Log.d(TAG, "Detection surface Id " + id + " added");
                }

                az -= angle;
                angle = 0;
                curveStep = heightStep;
            }

            curveStep = SURFACE_CURVE_STEP;
            px = SURFACE_PX;
            py = SURFACE_PY;
            pz -= width;
            az = SURFACE_AZ;
        }

        int startId = -1;

        for (int key : navigationModelNextId.keySet()) {
            if (startId < 0)
                startId = key;

            Log.d(TAG, "Detection surface Id for navigation : " +
                    key + " -> " + getNavigationModelNextId(key));
        }

        if (startId >= 0) {
            // Enable touch detection of first model with other models for navigation
            surfaceInterface.setSurfaceTouchDetection(startId, true);

            Log.d(TAG, "First detection surface Id " + startId + " for navigation enabled");
        }
    }

    public int getNavigationModelNextId(int id) {
        return navigationModelNextId.get(id);
    }
}
