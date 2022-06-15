package com.ultrasoundprobe.probeview.navigation.location.algorithm;

import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.navigation.location.MagneticData;

public abstract class BaseEngine {
    private static final double MAX_POSITION_X = 0.5;
    private static final double MAX_POSITION_Y = 0.5;
    private static final double MAX_POSITION_Z = 0.5;

    public abstract LocationData getLocationData(DeviceService.ImuData[] values);
    public abstract MagneticData getMagneticData(LocationData value, int coilId);

    public abstract int getCoilId(int index);

    public LocationData getMaxLocation() {
        return new LocationData(
                MAX_POSITION_X, MAX_POSITION_Y, MAX_POSITION_Z,
                0, 0, 0,
                0, 0, 0);
    };
}
