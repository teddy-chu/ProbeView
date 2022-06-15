package com.ultrasoundprobe.probeview.format;

import java.text.DecimalFormat;

public class FormatHelper {
    public static DecimalFormat imu() {
        return new DecimalFormat("0.#");
    }

    public static String gpio(boolean value) {
        return value ? "1" : "0";
    }

    public static DecimalFormat location() {
        return new DecimalFormat("0.##");
    }
}
