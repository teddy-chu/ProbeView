package com.ultrasoundprobe.probeview.device.power;

import android.util.Log;

import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.format.InputOutputFormatter;
import com.ultrasoundprobe.probeview.host.HostService;

import java.util.LinkedList;
import java.util.Queue;

public class PowerControl {
    private static final String TAG = "PowerControl";

    private static final int CONNECT_TIMEOUT = 6000;
    private static final int RETRY_TIME = 3000;

    private HostService hostService;
    private String hostAddress = "192.168.0.110";

    private boolean isRunning = false;

    private boolean isFirstState = true;
    private boolean lastPowerDownState = false;
    private final Queue<Boolean> powerDownStateQueue = new LinkedList<>();

    public void setHostService(HostService hostService) {
        this.hostService = hostService;
    }

    public void setHostAddress(String address) {
        hostAddress = address;
    }

    public void start(DeviceService.ExtraData extra) {
        if (!isFirstState && extra.powerDown == lastPowerDownState)
            return;

        isFirstState = false;
        lastPowerDownState = extra.powerDown;
        powerDownStateQueue.offer(extra.powerDown);

        Log.d(TAG, "Received new power down state: " + extra.powerDown +
                ", remaining: " + powerDownStateQueue.size());

        if (!isRunning) {
            isRunning = true;

            // Start running power control call
            new Thread() {
                @Override
                public void run() {
                    runPowerStateChange();
                }
            }.start();
        }
    }

    public void stop() {
        isRunning = false;
        isFirstState = true;
        lastPowerDownState = false;
    }

    private boolean dequeueToLatestPowerDownState() {
        if (powerDownStateQueue.size() <= 0)
            return false;

        if (powerDownStateQueue.size() > 1) {
            do {
                Boolean state = powerDownStateQueue.poll();

                Log.d(TAG, "Ignored old power down state: " + state +
                        ", remaining: " + powerDownStateQueue.size());
            } while (powerDownStateQueue.size() > 1);
        }

        return true;
    }

    private void runPowerStateChange() {
        Log.d(TAG, "Power control started");

        while (isRunning) {
            do {
                if (!dequeueToLatestPowerDownState())
                    break;

                if (hostService == null || !hostService.isHostConnected()) {
                    Log.e(TAG, "Power control backend not available");
                    break;
                }

                if (!hostService.isHostWebSocketOpened() &&
                        !hostService.openHostWebSocket(hostAddress, CONNECT_TIMEOUT)) {
                    Log.e(TAG, "Failed to establish connection to power control backend");
                    break;
                }

                if (!dequeueToLatestPowerDownState())
                    break;

                Boolean state = powerDownStateQueue.element();

                Log.d(TAG, "Requesting power down state: " + state +
                        ", remaining: " + powerDownStateQueue.size());

                if (!hostService.writeHostWebSocket(
                        InputOutputFormatter.insertPowerControlData(state))) {
                    Log.e(TAG, "Failed to request power down state " + state +
                            " from power control backend");

                    if (!hostService.closeHostWebSocket())
                        Log.e(TAG, "Failed to close connection to power control backend");

                    break;
                }

                powerDownStateQueue.poll();

                Log.d(TAG, "Requested power down state: " + state +
                        ", remaining: " + powerDownStateQueue.size());
            } while (powerDownStateQueue.size() > 0);

            if (powerDownStateQueue.size() <= 0)
                break;

            long startTime = System.currentTimeMillis();
            long remainingTime;

            do {
                remainingTime = RETRY_TIME - (System.currentTimeMillis() - startTime);

                if (remainingTime > 0) {
                    Log.e(TAG, "Power control error, try again after " +
                            (double)remainingTime / 1000 + "s");
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!isRunning)
                    break;
            } while (remainingTime > 0);

            if (isRunning)
                Log.e(TAG, "Power control error, try again now");
            else
                Log.e(TAG, "Power control is stopping now, remaining: " +
                        powerDownStateQueue.size());
        }

        Log.d(TAG, "Power control stopped");

        isRunning = false;
    }
}
