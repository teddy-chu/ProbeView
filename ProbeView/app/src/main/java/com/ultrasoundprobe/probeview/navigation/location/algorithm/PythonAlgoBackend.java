package com.ultrasoundprobe.probeview.navigation.location.algorithm;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.navigation.location.MagneticData;

public class PythonAlgoBackend extends BaseEngine {
    private final PyObject pyModule;

    public PythonAlgoBackend(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }

        pyModule = Python.getInstance().getModule("imu2loc");
    }

    @Override
    public LocationData getLocationData(DeviceService.ImuData[] values) {
        Object[] results = pyModule.callAttr("get_location_data",
                values[0].mx, values[0].my, values[0].mz,
                values[1].mx, values[1].my, values[1].mz,
                values[2].mx, values[2].my, values[2].mz).asList().toArray();
        LocationData location = new LocationData();

        try {
            location.px = Double.parseDouble(results[0].toString());
            location.py = Double.parseDouble(results[1].toString());
            location.pz = Double.parseDouble(results[2].toString());
            location.ax = Double.parseDouble(results[3].toString());
            location.ay = Double.parseDouble(results[4].toString());
            location.az = Double.parseDouble(results[5].toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return location;
    }

    @Override
    public MagneticData getMagneticData(LocationData value, int coilId) {
        if (value == null)
            return null;

        Object[] results = pyModule.callAttr("get_magnetic_data",
                value.px, value.py, value.pz,
                value.ax, value.ay, value.az,
                coilId).asList().toArray();
        MagneticData magnetic = new MagneticData();

        try {
            magnetic.mx = Double.parseDouble(results[0].toString());
            magnetic.my = Double.parseDouble(results[1].toString());
            magnetic.mz = Double.parseDouble(results[2].toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return magnetic;
    }

    @Override
    public int getCoilId(int index) {
        String result = pyModule.callAttr("get_coil_id",
                index).toString();
        return Integer.parseInt(result);
    }
}