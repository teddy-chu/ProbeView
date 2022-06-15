package com.ultrasoundprobe.probeview.preference;

import com.ultrasoundprobe.probeview.navigation.location.LocationData;

public class DefaultPreference {
    public static LocationData getCoilLocation() {
        return new LocationData(-12, 0, 0, 0, 0, 0,
                1f, 1f, 1f);
    }

    public static LocationData getProbeLocation() {
        return new LocationData(3, 0, 0, 0, 0, 0,
                2f, 0.5f, 1f);
    }

    public static String getScanImageServerUrl() {
        // URL of a server for dynamic test image download
        // return "https://picsum.photos/200";
        // URL of a server for scan image download
        return "http://10.6.72.78:8010/live.png";
    }

    public static String getPowerControlServerAddress() {
        // IP address of a server for probe power control
        return "10.6.72.78:3000";
    }
}
