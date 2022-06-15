package com.ultrasoundprobe.probeview.navigation.location.algorithm;

import android.util.Log;

import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.format.InputOutputFormatter;
import com.ultrasoundprobe.probeview.host.HostService;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.navigation.location.MagneticData;

import java.util.List;

public class RemoteAlgoBackend extends BaseEngine {
    private static final String TAG = "RemoteAlgoBackend";

    private static final int CONNECT_TIMEOUT = 6000;
    private static final int READ_TIMEOUT = 6000;
    private static final int SOCKET_READ_TIMEOUT = 3000;

    private HostService hostService;
    private String hostAddress = "192.168.0.110";

    public void setHostService(HostService hostService) {
        this.hostService = hostService;
    }

    public void setHostAddress(String address) {
        hostAddress = address;
    }

    @Override
    public LocationData getLocationData(DeviceService.ImuData[] values) {
        if (hostService == null || !hostService.isHostConnected())
            return null;

        if (!hostService.isHostSocketOpened() &&
                !hostService.openHostSocket(hostAddress, CONNECT_TIMEOUT)) {
            Log.e(TAG, "Failed to establish connection to algorithm backend");

            return null;
        }

        if (!hostService.writeHostSocket(InputOutputFormatter.insertAlgoData(values))) {
            Log.e(TAG, "Failed to request location from algorithm backend");

            if (!hostService.closeHostSocket())
                Log.e(TAG, "Failed to close connection to algorithm backend");

            return null;
        }

        LocationData location = null;
        String message = null;
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;

        do {
            int timeout = (int)Math.min(READ_TIMEOUT - elapsedTime, SOCKET_READ_TIMEOUT);

            if (timeout <= 0) {
                Log.e(TAG, "Timed out with " + elapsedTime + "ms while waiting " +
                        "received data from algorithm backend");
                break;
            } else if (!hostService.isHostSocketOpened()) {
                Log.e(TAG, "Connection broken while waiting received data " +
                        "from algorithm backend");
                break;
            } else if (elapsedTime != 0) {
                Log.e(TAG, "No received data from algorithm backend, try again");
            }

            message = hostService.readHostSocket(timeout);
            elapsedTime = System.currentTimeMillis() - startTime;
        } while (message == null);

        if (message != null) {
            List<String> args = InputOutputFormatter.extractAlgoData(message);

            if (args == null) {
                Log.e(TAG, "Failed to extract received data: " + message);
                return null;
            } else {
                Log.d(TAG, "Received data: " + message);
            }

            try {
                location = new LocationData(
                        Double.parseDouble(args.get(0)),
                        Double.parseDouble(args.get(1)),
                        Double.parseDouble(args.get(2)),
                        Double.parseDouble(args.get(3)),
                        Double.parseDouble(args.get(4)),
                        Double.parseDouble(args.get(5)),
                        0, 0, 0);
            } catch (Exception e) {
                Log.e(TAG, "Invalid argument in received data: " + message);
            }
        }

        return location;
    }

    @Override
    public MagneticData getMagneticData(LocationData value, int coilId) {
        // TODO: Implement this function
        return null;
    }

    @Override
    public int getCoilId(int index) {
        return index;
    }
}
