package com.ultrasoundprobe.probeview.navigation.model;

import android.opengl.GLES20;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ultrasoundprobe.probeview.navigation.location.LocationData;

import java.text.DecimalFormat;
import java.util.Arrays;

public abstract class BaseModel {
    private static final String TAG = "BaseModel";

    // Number of coordinates per vertex
    public static final int COORDINATES_PER_VERTEX = 3;

    // Position and angle properties
    public float px = 0.0f, py = 0.0f, pz = 0.0f;
    public float ax = 0.0f, ay = 0.0f, az = 0.0f;

    // Scale properties
    public float sx = 1.0f, sy = 1.0f, sz = 1.0f;

    // Identification number of a model
    protected int id = -1;

    // Vertices of shape for touch detection
    protected float[][] surfaceNormalVectors = new float[0][];
    protected float[][] surfaceVertices = new float[0][];

    public static class SurfaceTouchResult {
        // Touch score for their surface of two models
        public float score;
        // Surface angle between two models
        public float angle;
        // A flag indicating whether their surface of two models is touched to each other
        public boolean isTouched;

        @NonNull
        @Override
        public String toString() {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            return "Score: " + decimalFormat.format(score) +
                    ", Angle: " + decimalFormat.format(angle) +
                    ", Touched: " + isTouched;
        }
    }

    protected SurfaceTouchResult[][] surfaceTouchResults = new SurfaceTouchResult[0][];

    @NonNull
    @Override
    public String toString() {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < surfaceTouchResults.length; i++) {
            buffer.append("[")
                    .append(i)
                    .append("]")
                    .append(Arrays.toString(surfaceTouchResults[i]))
                    .append("\n");
        }

        return "Id: " + id +
                ", Position: " +
                decimalFormat.format(px) + " " +
                decimalFormat.format(py) + " " +
                decimalFormat.format(pz) +
                ", Angle: " +
                decimalFormat.format(ax) + " " +
                decimalFormat.format(ay) + " " +
                decimalFormat.format(az) +
                ", Scale: " +
                decimalFormat.format(sx) + " " +
                decimalFormat.format(sy) + " " +
                decimalFormat.format(sz) +
                "\nTouch Results:\n" + buffer;
    }

    public abstract void draw(float[] mvpMatrix);
    public abstract void initSurfaceTouchDetection(float[] modelMatrix);
    public abstract void setSurfaceTouchDetection(boolean enable);
    public abstract boolean getSurfaceTouchDetection();
    public abstract void setSurfaceTouchBufferColor(boolean enable);

    public SurfaceTouchResult[][] getSurfaceTouchResults() {
        return surfaceTouchResults;
    }

    public float[] getLocation() {
        return LocationData.toFloatArray(
                new LocationData(px, py, pz, ax, ay, az, sx, sy, sz).getValues());
    }

    public boolean setLocation(float[] values) {
        double[] temp = LocationData.toDoubleArray(values);

        if (!LocationData.validate(temp))
            return false;

        LocationData location = new LocationData(temp);

        px = (float)location.px;
        py = (float)location.py;
        pz = (float)location.pz;
        ax = (float)location.ax;
        ay = (float)location.ay;
        az = (float)location.az;
        sx = (float)location.sx;
        sy = (float)location.sy;
        sz = (float)location.sz;

        return true;
    }

    public boolean runSurfaceTouchDetection(BaseModel source) {
        BaseModel target = this;

        if (source.surfaceNormalVectors == null || source.surfaceVertices == null ||
                source.surfaceNormalVectors.length != source.surfaceVertices.length ||
                source.surfaceNormalVectors.length == 0) {
            Log.e(TAG, "Invalid source model parameters for surface normal " +
                    "vectors and vertices");
            return false;
        } else if (target.surfaceNormalVectors == null || target.surfaceVertices == null ||
                target.surfaceNormalVectors.length != target.surfaceVertices.length ||
                target.surfaceNormalVectors.length == 0) {
            Log.e(TAG, "Invalid target model parameters for surface normal " +
                    "vectors and vertices");
            return false;
        }

        source.surfaceTouchResults = new SurfaceTouchResult[source.surfaceNormalVectors.length]
                [target.surfaceNormalVectors.length];
        target.surfaceTouchResults = new SurfaceTouchResult[target.surfaceNormalVectors.length]
                [source.surfaceNormalVectors.length];

        for (int i = 0; i < source.surfaceTouchResults.length; i++)
            for (int j = 0; j < source.surfaceTouchResults[i].length; j++)
                source.surfaceTouchResults[i][j] = new SurfaceTouchResult();
        for (int i = 0; i < target.surfaceTouchResults.length; i++)
            for (int j = 0; j < target.surfaceTouchResults[i].length; j++)
                target.surfaceTouchResults[i][j] = new SurfaceTouchResult();

        for (int i = 0; i < target.surfaceNormalVectors.length; i++) {
            float[] targetSurfaceNormalVectors = target.surfaceNormalVectors[i];
            float[] targetSurfaceVertices = target.surfaceVertices[i];

            for (int j = 0; j < source.surfaceNormalVectors.length; j++) {
                float[] sourceSurfaceNormalVectors = source.surfaceNormalVectors[j];
                float[] sourceSurfaceVertices = source.surfaceVertices[j];
                // Calculate an angle between surface of a target and source object model
                float angle = (float)Math.asin(calculateMatrixMagnitude3x1(
                        multiplyMatrix3x1(targetSurfaceNormalVectors, sourceSurfaceNormalVectors)) /
                        (calculateMatrixMagnitude3x1(targetSurfaceNormalVectors) *
                                calculateMatrixMagnitude3x1(sourceSurfaceNormalVectors)));
                float score = 0;
                int count = 0;
                boolean isTouched;

                // Express angle in degree
                angle = (float)Math.toDegrees(angle);

                // Calculate a score of how far the surface of a target and source object
                // model is against each other
                for (int k = 0; k <= sourceSurfaceVertices.length - COORDINATES_PER_VERTEX;
                     k += COORDINATES_PER_VERTEX) {
                    for (int l = 0; l <= targetSurfaceVertices.length - COORDINATES_PER_VERTEX;
                         l += COORDINATES_PER_VERTEX) {
                        score += calculateMatrixMagnitude3x1(
                                // Calculate distance between vertices of a target and source
                                // object model
                                subtractMatrix3x1(
                                        new float[] { targetSurfaceVertices[l],
                                                targetSurfaceVertices[l + 1],
                                                targetSurfaceVertices[l + 2] },
                                        new float[] { sourceSurfaceVertices[k],
                                                sourceSurfaceVertices[k + 1],
                                                sourceSurfaceVertices[k + 2] }));
                        count++;
                    }
                }

                // take an average of the sum score for a final score value
                if (count > 0)
                    score /= (float)count;
                else
                    score = -1;

                // Evaluate whether surface of two models is touched
                isTouched = detectSurfaceTouch(source, target, score, angle);

                // The first dimension of array denotes itself, the second denotes another
                // object model
                source.surfaceTouchResults[j][i].score = score;
                source.surfaceTouchResults[j][i].angle = angle;
                source.surfaceTouchResults[j][i].isTouched = isTouched;
                target.surfaceTouchResults[i][j].score = score;
                target.surfaceTouchResults[i][j].angle = angle;
                target.surfaceTouchResults[i][j].isTouched = isTouched;
            }
        }

        return true;
    }

    private static boolean detectSurfaceTouch(BaseModel source, BaseModel target,
                                              float score, float angle) {
        if (score < 0)
            return false;

        // TODO: Auto set threshold values based on model properties
        // Threshold values for a 1-by-1 surface area
        // return score <= 1.8f && angle <= 10.0f;
        // Threshold values for a 1-by-0.5 surface area
        return score <= 1.42f && angle <= 10.0f;
    }

    protected static float[] subtractMatrix3x1(float[] v1, float[] v2) {
        return new float[] { v1[0] - v2[0], v1[1] - v2[1], v1[2] - v2[2] };
    }

    protected static float[] multiplyMatrix3x1(float[] v1, float[] v2) {
        return new float[] {
                v1[1] * v2[2] - v2[1] * v1[2],
                v1[0] * v2[2] - v2[0] * v1[2],
                v1[0] * v2[1] - v2[0] * v1[1] };
    }

    protected static float calculateMatrixMagnitude3x1(float[] v1) {
        return (float)Math.pow(Math.pow(v1[0], 2) + Math.pow(v1[1], 2) + Math.pow(v1[2], 2), 0.5);
    }

    protected static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);

        // Add source code to shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }
}