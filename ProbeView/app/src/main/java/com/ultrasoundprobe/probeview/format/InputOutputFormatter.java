package com.ultrasoundprobe.probeview.format;

import com.ultrasoundprobe.probeview.device.DeviceService;

import java.util.ArrayList;
import java.util.List;

public class InputOutputFormatter {
    static public List<String> extractSensorData(String message) {
        return extractTokenData(message, new String[] {
                "sen_data:", ",", ",",      // Magnetometer X, Y, Z
                ",", ",", ",",              // Accelerometer X, Y, Z
                ",", ""                     // Power down state
        });
    }

    static public String insertGpioControlData(boolean gpio1, boolean gpio2, boolean gpio3) {
        return "gpio_control:" + (gpio1 ? "1" : "0") + "," +
                (gpio2 ? "1" : "0") + "," + (gpio3 ? "1" : "0");
    }

    static public List<String> extractGpioControlData(String message) {
        return extractTokenData(message, new String[] { "gpio_control:", ",", ",", "" });
    }

    static public String insertAlgoData(DeviceService.ImuData[] values) {
        return "algo_data:" + values[0].mx + "," + values[0].my + "," + values[0].mz + "," +
                values[1].mx + "," + values[1].my + "," + values[1].mz + "," +
                values[2].mx + "," + values[2].my + "," + values[2].mz;
    }

    static public List<String> extractAlgoData(String message) {
        return extractTokenData(message, new String[] { "algo_result:", ",", ",", ",",
                ",", ",", "" });
    }

    static public String insertPowerControlData(boolean powerDown) {
        return "power_down:" + (powerDown ? "1" : "0");
    }

    static private List<String> extractTokenData(String message, String[] tokens) {
        List<String> args = new ArrayList<>();
        int startIndex = 0, endIndex;

        // Use an empty string at the end to denote end of token
        // search in a list
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            if (token.isEmpty()) {
                args.add(message.substring(startIndex));
                break;
            } else {
                endIndex = message.indexOf(token, startIndex);
            }

            if (endIndex < 0)
                return null;
            else if (i > 0)
                args.add(message.substring(startIndex, endIndex));

            startIndex = endIndex + token.length();
        }

        if ((args.size() + 1) != tokens.length)
            return null;

        return args;
    }
}