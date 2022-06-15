package com.ultrasoundprobe.probeview.host;

public class HostInfo {
    private final String name;
    private final String address;

    public HostInfo(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getDescription() {
        return name + " (" + address + ")";
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}
