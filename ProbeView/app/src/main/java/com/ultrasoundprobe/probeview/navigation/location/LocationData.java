package com.ultrasoundprobe.probeview.navigation.location;

public class LocationData {
    public static final int LENGTH = 9;

    // Position X, Y, Z
    public double px, py, pz;
    // Angle X, Y, Z
    public double ax, ay, az;
    // Scale X, Y, Z
    public double sx, sy, sz;

    public LocationData() {
        this(new double[LENGTH]);
    }

    public LocationData(double px, double py, double pz, double ax, double ay, double az,
                        double sx, double sy, double sz) {
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
    }

    public LocationData(double[] values) {
        setValues(values);
    }

    public double[] getValues() {
        return new double[] { px, py, pz, ax, ay, az, sx, sy, sz };
    }

    public boolean setValues(double[] values) {
        if (!validate(values))
            return false;

        px = values[0];
        py = values[1];
        pz = values[2];
        ax = values[3];
        ay = values[4];
        az = values[5];
        sx = values[6];
        sy = values[7];
        sz = values[8];

        return true;
    }

    public static boolean validate(double[] values) {
        return values != null && values.length == LENGTH;
    }

    public static float[] toFloatArray(double[] values) {
        float[] temp = new float[values.length];

        for (int i = 0; i < temp.length; i++)
            temp[i] = (float)values[i];

        return temp;
    }

    public static double[] toDoubleArray(float[] values) {
        double[] temp = new double[values.length];

        for (int i = 0; i < temp.length; i++)
            temp[i] = values[i];

        return temp;
    }
}