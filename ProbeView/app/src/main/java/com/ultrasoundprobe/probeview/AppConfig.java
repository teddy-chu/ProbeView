package com.ultrasoundprobe.probeview;

public class AppConfig {
    // Enable debug output or not for software module
    public static final boolean CoilSwitchDebug = true;
    public static final boolean NavigationAlgorithmDebug = true;

    // Run navigation algorithm test or not, disable this in normal operation
    public static final boolean NavigationAlgorithmTest = false;

    // Specify ready time of coil switch after some switches were turned on
    // to sample IMU data
    public static final int CoilControlSwitchOnReadyTime = 500;
    // Specify ready time of coil switch after all switches were turned off
    // to sample IMU data
    public static final int CoilControlSwitchOffReadyTime = 500;
    // Specify a timeout for coil state changed to its desire state, retry
    // will enforce after this timeout has occurred
    public static final int CoilControlSwitchTimeout = 200;

    // Hide coil object in 3D view or not
    public static final boolean CoilObject3dViewVisible = false;

    // IP address for use of remote navigation algorithm backend
    public static final String RemoteAlgoBackendAddress = "192.168.0.110";
}
