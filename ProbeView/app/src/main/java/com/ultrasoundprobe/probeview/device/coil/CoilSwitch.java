package com.ultrasoundprobe.probeview.device.coil;

import android.util.Log;

import com.ultrasoundprobe.probeview.AppConfig;
import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.format.InputOutputFormatter;

import java.util.Arrays;

public class CoilSwitch {
    private static final String TAG = "CoilSelector";

    private static final boolean[][] COIL_CONTROL_GPIO_MAP = {
            { true, false, false },
            { false, false, false },
            { false, true, false },
            { false, false, false },
            { false, false, true },
            { false, false, false }
    };
    private static final int COIL_CONTROL_SWITCH_ON_READY_TIME =
            AppConfig.CoilControlSwitchOnReadyTime;
    private static final int COIL_CONTROL_SWITCH_OFF_READY_TIME =
            AppConfig.CoilControlSwitchOffReadyTime;
    private static final int COIL_CONTROL_SWITCH_TIMEOUT =
            AppConfig.CoilControlSwitchTimeout;

    private enum CoilControlState {
        CoilRequest,
        CoilSwitchTime,
        Unknown
    }

    private CoilControlState coilControlState;
    private long coilSwitchTime;
    private int coilIndex;

    private boolean isDebug = false;

    private final DeviceService.ImuData[] coilImuData = new DeviceService.ImuData[
            COIL_CONTROL_GPIO_MAP.length];

    public CoilSwitch() {
        resetCoilControl();
    }

    public void setDebug(boolean enable) {
        isDebug = enable;
    }

    public void resetCoilControl() {
        coilControlState = CoilControlState.Unknown;
        coilSwitchTime = 0;
        coilIndex = 0;
    }

    public DeviceService.ImuData[] getImuDataFromCoilSwitch(DeviceService deviceService,
                                                       String connectedCoilAddress,
                                                       DeviceService.GpioData gpioData,
                                                       DeviceService.ImuData imuData) {
        boolean imuDataAvailable = false;

        if (coilControlState == CoilControlState.CoilRequest) {
            boolean[] gpioMap = COIL_CONTROL_GPIO_MAP[coilIndex - 1];
            // All coil switches turned to what we want?
            boolean coilSwitched = (gpioMap[0] == gpioData.gpio1) &&
                    (gpioMap[1] == gpioData.gpio2) &&
                    (gpioMap[2] == gpioData.gpio3);

            if (coilSwitched) {
                if (coilSwitchTime < 0)
                    Log.w(TAG, "[Done] Switch to coil " + Arrays.toString(gpioMap) +
                            ", but previous coil switch failed");
                else if (isDebug)
                    Log.d(TAG, "[Done] Switch to coil " + Arrays.toString(gpioMap));

                coilSwitchTime = System.currentTimeMillis();
                coilControlState = CoilControlState.CoilSwitchTime;
            } else {
                if (coilSwitchTime >= 0 && (System.currentTimeMillis() - coilSwitchTime) >=
                        COIL_CONTROL_SWITCH_TIMEOUT) {
                    Log.e(TAG, "[Timeout] Switch to coil " + Arrays.toString(gpioMap) +
                            ", try again");
                } else if (coilSwitchTime >= 0)
                    // Not timed out yet but still waiting for coil switch
                    return null;

                // Retry last request for coil switch
                coilIndex--;
            }
        }

        if (coilControlState == CoilControlState.CoilSwitchTime) {
            boolean[] gpioMap = COIL_CONTROL_GPIO_MAP[coilIndex - 1];
            // All coil switches turned off?
            boolean coilSwitchedOff = (!gpioMap[0] && !gpioMap[1] && !gpioMap[2]);
            // Get timeout value according to coil switch states
            long timeout = (coilSwitchedOff ?
                    COIL_CONTROL_SWITCH_OFF_READY_TIME :
                    COIL_CONTROL_SWITCH_ON_READY_TIME);

            if ((System.currentTimeMillis() - coilSwitchTime) >= timeout) {
                imuDataAvailable = (coilIndex == COIL_CONTROL_GPIO_MAP.length);

                // Store IMU data after coil switch is stable
                coilImuData[coilIndex - 1] = new DeviceService.ImuData(
                        imuData.mx, imuData.my, imuData.mz,
                        imuData.gx, imuData.gy, imuData.gz);

                if (isDebug)
                    Log.d(TAG, "[Stored] IMU data [" + imuData + "] from coil " +
                            Arrays.toString(gpioMap));
            } else {
                /*
                // Debug for sensor data dump during all-off of switches
                if (isDebug && coilSwitchedOff) {
                    Log.d(TAG, "IMU data [" + imuData + "] from coil " +
                            Arrays.toString(gpioMap));
                }
                 */

                return null;
            }
        }

        coilIndex %= COIL_CONTROL_GPIO_MAP.length;

        boolean[] gpioMap = COIL_CONTROL_GPIO_MAP[coilIndex];
        String message = InputOutputFormatter.insertGpioControlData(
                gpioMap[0],
                gpioMap[1],
                gpioMap[2]);

        coilControlState = CoilControlState.CoilRequest;
        coilIndex++;

        // Send a request for coil switch
        if (deviceService.writeDevice(connectedCoilAddress, message.getBytes())) {
            coilSwitchTime = System.currentTimeMillis();

            if (isDebug)
                Log.d(TAG, "Switching to coil " + Arrays.toString(gpioMap));
        } else {
            // Use a negative value as an error
            coilSwitchTime = -1;

            Log.e(TAG, "Cannot switch to coil " + Arrays.toString(gpioMap));
        }

        return imuDataAvailable ? coilImuData : null;
    }

    public DeviceService.ImuData[] getImuDataFromActiveCoils(DeviceService.ImuData[] values) {
        if (values.length != COIL_CONTROL_GPIO_MAP.length)
            return null;

        return new DeviceService.ImuData[] { values[0], values[2], values[4] };
    }

    public DeviceService.ImuData[] getImuDataFromInactiveCoils(DeviceService.ImuData[] values) {
        if (values.length != COIL_CONTROL_GPIO_MAP.length)
            return null;

        return new DeviceService.ImuData[] { values[1], values[3], values[5] };
    }
}
