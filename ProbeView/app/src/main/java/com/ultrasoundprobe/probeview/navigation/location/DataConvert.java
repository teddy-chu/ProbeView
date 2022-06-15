package com.ultrasoundprobe.probeview.navigation.location;

import android.content.Context;
import android.util.Log;

import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.navigation.location.algorithm.BaseEngine;
import com.ultrasoundprobe.probeview.navigation.location.algorithm.PythonAlgoBackend;
import com.ultrasoundprobe.probeview.navigation.location.algorithm.RemoteAlgoBackend;

import java.util.Arrays;

public class DataConvert {
    private static final String TAG = "DataConvert";

    public enum EngineType {
        PythonAlgoBackend,
        RemoteAlgoBackend
    }

    private final BaseEngine algoEngine;

    private final LocationData viewInfo;
    private boolean isDebug = false;

    public DataConvert(Context context, EngineType type, LocationData viewInfo) {
        if (type == EngineType.PythonAlgoBackend)
            algoEngine = new PythonAlgoBackend(context);
        else if (type == EngineType.RemoteAlgoBackend)
            algoEngine = new RemoteAlgoBackend();
        else
            // Set default algorithm engine
            algoEngine = new PythonAlgoBackend(context);

        this.viewInfo = viewInfo;
    }

    public void setDebug(boolean enable) {
        isDebug = enable;
    }

    public BaseEngine getEngine() {
        return algoEngine;
    }

    public LocationData getViewRatio() {
        return new LocationData(
                viewInfo.px / algoEngine.getMaxLocation().px,
                viewInfo.py / algoEngine.getMaxLocation().py,
                viewInfo.pz / algoEngine.getMaxLocation().pz,
                0, 0, 0, 0, 0, 0);
    }

    public LocationData getViewData(DeviceService.ImuData[] valuesFromActiveCoils,
                                    DeviceService.ImuData[] valuesFromInactiveCoils) {
        // Suppress background noise from IMU data
        if (valuesFromInactiveCoils != null) {
            double[] magAvg = new double[] { 0, 0, 0 };

            for (DeviceService.ImuData value : valuesFromInactiveCoils) {
                magAvg[0] += value.mx;
                magAvg[1] += value.my;
                magAvg[2] += value.mz;
            }

            // Take an average over the background noise
            magAvg[0] /= valuesFromInactiveCoils.length;
            magAvg[1] /= valuesFromInactiveCoils.length;
            magAvg[2] /= valuesFromInactiveCoils.length;

            if (isDebug) {
                StringBuilder valueBefore = new StringBuilder();
                StringBuilder valueAfter = new StringBuilder();

                for (DeviceService.ImuData value : valuesFromActiveCoils) {
                    valueBefore.append("[").append(value).append("] ");
                    value.applyBiasToMagneticData(magAvg[0], magAvg[1], magAvg[2]);
                    valueAfter.append("[").append(value).append("] ");
                }

                Log.d(TAG, "Source change with bias " + Arrays.toString(magAvg) + ":");
                Log.d(TAG, "\tBefore (origin): " + valueBefore);
                Log.d(TAG, "\tAfter (with bias): " + valueAfter);
            } else {
                for (DeviceService.ImuData value : valuesFromActiveCoils) {
                    value.applyBiasToMagneticData(magAvg[0], magAvg[1], magAvg[2]);
                }
            }
        }

        LocationData value = getLocationData(valuesFromActiveCoils);
        LocationData ratio = getViewRatio();

        if (value == null)
            return null;

        // Scale positions of algorithm result to fit into view ranges
        value.px *= ratio.px;
        value.py *= ratio.py;
        value.pz *= ratio.pz;

        // Convert angles of algorithm result from radian to degree
        value.ax = Math.toDegrees(value.ax);
        value.ay = Math.toDegrees(value.ay);
        value.az = Math.toDegrees(value.az);

        // Transform coordinates of algorithm result to fit into view coordinates
        return transformViewCoordinate(value);
    }

    private LocationData getLocationData(DeviceService.ImuData[] values) {
        long startTime = System.currentTimeMillis();

        LocationData location = algoEngine.getLocationData(values);

        long elapsedTime = System.currentTimeMillis() - startTime;

        if (isDebug) {
            StringBuilder message = new StringBuilder();
            LocationData location_ = null;

            for (DeviceService.ImuData value : values) {
                message.append("[").append(value).append("] ");
            }

            if (location != null) {
                location_ = new LocationData(location.getValues());

                // Transform angle from radian to degree unit
                location_.ax = Math.toDegrees(location.ax);
                location_.ay = Math.toDegrees(location.ay);
                location_.az = Math.toDegrees(location.az);
            }

            Log.d(TAG, "Source: " + message);
            Log.d(TAG, "Time: " + elapsedTime + "ms");
            Log.d(TAG, "Location (angle in Radian): " +
                    (location != null ? Arrays.toString(location.getValues()) : "[]"));
            Log.d(TAG, "Location (angle in Degree): " +
                    (location_ != null ? Arrays.toString(location_.getValues()) : "[]"));

            message = new StringBuilder();

            for (int i = 0; i < values.length; i++) {
                MagneticData magnetic = algoEngine.getMagneticData(location,
                        algoEngine.getCoilId(i));

                message.append(magnetic != null ?
                        Arrays.toString(magnetic.getValues()) : "[]").append(" ");
            }

            Log.d(TAG, "Reverted source: " + message);
        }

        return location;
    }

    private LocationData transformViewCoordinate(LocationData value) {
        // TODO: Transform location coordinates:
        //  1. Algorithm position Z -> view position -Z
        //  2. Algorithm position X -> view position -Y
        //  3. Algorithm position Y -> view position X
        //  4. Algorithm angle Z - > view angle Z (TBC)
        //  5. Algorithm angle X - > view angle X (TBC)
        //  6. Algorithm angle Y - > view angle Y (TBC)
        return new LocationData(
                value.py, -value.px, -value.pz,
                value.ax, value.ay, value.az,
                value.sx, value.sy, value.sz);
    }
}
