package com.ultrasoundprobe.probeview.navigation.location;

public class MagneticData {
    public static final int LENGTH = 3;

    public double mx, my, mz;

    public MagneticData() {
        this(new double[LENGTH]);
    }

    public MagneticData(double mx, double my, double mz) {
        this.mx = mx;
        this.my = my;
        this.mz = mz;
    }

    public MagneticData(double[] values) {
        setValues(values);
    }

    public double[] getValues() {
        return new double[] { mx, my, mz };
    }

    public boolean setValues(double[] values) {
        if (!validate(values))
            return false;

        mx = values[0];
        my = values[1];
        mz = values[2];

        return true;
    }

    public static boolean validate(double[] values) {
        return values != null && values.length == LENGTH;
    }
}
