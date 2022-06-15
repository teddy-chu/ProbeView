package com.ultrasoundprobe.probeview.preference;

import android.content.Context;

import com.ultrasoundprobe.probeview.navigation.location.LocationData;

public class StorePreferenceHelper extends Keystore {
    private static final String KEY_COIL_LOCATION = "COIL_LOCATION";
    private static final String KEY_PROBE_LOCATION = "PROBE_LOCATION";
    private static final String KEY_SCAN_IMAGE_URL = "SCAN_IMAGE_URL";
    private static final String KEY_POWER_CONTROL_ADDRESS = "POWER_CONTROL_ADDRESS";

    private static Keystore keystore;

    public StorePreferenceHelper(Context context) {
        super(context);
    }

    public static void init(Context context) {
        keystore = getInstance(context);
    }

    public static Keystore getInstance() {
        return keystore;
    }

    public static void setCoilLocation(LocationData value) {
        keystore.setObject(KEY_COIL_LOCATION, value.getValues());
    }

    public static LocationData getCoilLocation() {
        double[] values = (double[])keystore.getObject(KEY_COIL_LOCATION);
        return values == null ? null : new LocationData(values);
    }

    public static void setProbeLocation(LocationData value) {
        keystore.setObject(KEY_PROBE_LOCATION, value.getValues());
    }

    public static LocationData getProbeLocation() {
        double[] values = (double[])keystore.getObject(KEY_PROBE_LOCATION);
        return values == null ? null : new LocationData(values);
    }

    public static void setScanImageServerUrl(String value) {
        keystore.setObject(KEY_SCAN_IMAGE_URL, value);
    }

    public static String getScanImageServerUrl() {
        return (String)keystore.getObject(KEY_SCAN_IMAGE_URL);
    }

    public static void setPowerControlServerAddress(String value) {
        keystore.setObject(KEY_POWER_CONTROL_ADDRESS, value);
    }

    public static String getPowerControlServerAddress() {
        return (String)keystore.getObject(KEY_POWER_CONTROL_ADDRESS);
    }

    public static void clearSettings() {
        keystore.clear();
    }

    public static void removeSettings() {
        keystore.remove();
    }
}
