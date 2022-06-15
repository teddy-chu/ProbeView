package com.ultrasoundprobe.probeview.device;

import android.bluetooth.BluetoothDevice;

public class DeviceInfo {
    public enum DeviceType {
        Coil,
        Probe,
        Unknown
    }

    private final DeviceType type;
    private final String name;
    private final String address;

    public DeviceInfo(DeviceType type, BluetoothDevice device) {
        this(type, device.getName(), device.getAddress());
    }

    public DeviceInfo(DeviceType type, String name, String address) {
        this.type = type;
        this.name = name;
        this.address = address;
    }

    public String getDescription() {
        return name + " (" + address + ")";
    }

    public DeviceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}
