package com.ultrasoundprobe.probeview.device.view;

import com.ultrasoundprobe.probeview.device.DeviceInfo;

public class DeviceEntry {
    private final int type;
    private final DeviceInfo deviceInfo;

    public DeviceEntry(int type, DeviceInfo deviceInfo) {
        this.type = type;
        this.deviceInfo = deviceInfo;
    }

    public int getType() {
        return type;
    }

    public DeviceInfo getInfo() {
        return deviceInfo;
    }
}
