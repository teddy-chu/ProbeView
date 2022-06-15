package com.ultrasoundprobe.probeview.format;

import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;

public class LocationInputFilter implements InputFilter {
    private static final String TAG = "LocationInputFilter";

    private int decimalPlace;
    private final float min, max;

    public LocationInputFilter() {
        this(-999.9f, 999.9f);
    }

    public LocationInputFilter(float min, float max) {
        this.min = min;
        this.max = max;

        String[] split = String.valueOf(min).split("\\.");

        if (split.length > 1)
            decimalPlace = split[1].length();

        split = String.valueOf(max).split("\\.");

        if (split.length > 1)
            decimalPlace = Math.max(split[1].length(), decimalPlace);
    }

    public String getValue(String value) {
        float num = 0;

        try {
            num = Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Log.e(TAG, e.getMessage());
        }

        return num == 0 ? "0" : num % 1.0 == 0 ?
                String.valueOf((int)num) :      // Remove the decimal part
                String.valueOf(num);            // Reserve both integer and decimal parts
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        String lastInput = dest.toString();
        int lastDotIndex = lastInput.indexOf(".");

        if (source.equals("0") && (lastInput.equals("0") || lastInput.equals("-0"))) {
            // Limit leading zero to no more than one digit
            return "";
        } else if (source.equals(".")) {
            if (lastInput.length() == 0)
                // Format decimal number to one leading zero
                return "0.";
            else if (lastInput.equals("-"))
                // Format minus decimal number to one leading zero
                return "-0.";
        } else if (source.equals("-") && lastInput.length() == 0) {
            // Accept a minus sign as the leading character
            return null;
        } else if (lastDotIndex >= 0 && lastInput.substring(lastDotIndex + 1).length() ==
                decimalPlace) {
            // Limit decimal place to a specified length
            return "";
        }

        try {
            float input = Float.parseFloat(lastInput + source);

            if (input > max || input < min)
                // Limit input number to a specified range
                return "";
        } catch (NumberFormatException e) {
            // Drop input for format exception
            return "";
        }

        return null;
    }
}
