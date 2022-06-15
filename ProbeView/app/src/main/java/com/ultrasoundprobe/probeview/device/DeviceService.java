package com.ultrasoundprobe.probeview.device;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ultrasoundprobe.probeview.format.InputOutputFormatter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeviceService extends Service {
    private static final String TAG = "DeviceService";

    // Service UUID for UART interface on device
    private static final String DEVICE_SERVICE_UUID_UART =
            "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    // Characteristic UUID for UART Rx from device
    private static final String DEVICE_CHARACTERISTIC_UUID_UART_RX =
            "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    // Characteristic UUID for UART Tx from device
    private static final String DEVICE_CHARACTERISTIC_UUID_UART_TX =
            "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    // Client Characteristic Configuration Descriptor for notification settings of the UART Tx
    private static final String DEVICE_DESCRIPTOR_UUID_CCCD =
            "00002902-0000-1000-8000-00805f9b34fb";

    // Filter properties for device scanning
    private static final String DEVICE_NAME_FILTER_COIL = "ASTRI_Coil";
    private static final String DEVICE_NAME_FILTER_PROBE = "ASTRI_Probe";
    private static final String DEVICE_SERVICE_UUID_FILTER = DEVICE_SERVICE_UUID_UART;

    // Maximum Transmission Unit for data transfer between host and device
    private static final int DEVICE_REQUEST_MTU = 64;

    private enum ConnectionState {
        CHANNEL_DISCONNECTED,
        CHANNEL_CONNECTED,
        SERVICE_DISCOVERED,
        NOTIFICATION_ENABLED,
        DATA_TRANSFER_ENABLED
    }

    private static class DeviceStruct {
        public BluetoothGatt gatt;
        public ConnectionState connectionState;
        public BluetoothGattCharacteristic characteristicTransfer;
        public boolean waitCharacteristicTransferResponse;

        public DeviceStruct() {
            connectionState = ConnectionState.CHANNEL_DISCONNECTED;
            waitCharacteristicTransferResponse = false;
        }
    }

    private final Map<String, DeviceStruct> deviceMaps = new HashMap<>();

    private ServiceBinder serviceBinder;
    private List<ServiceCallback> serviceCallbacks;
    private final Object serviceCallbackMutex = new Object();

    private Handler handler;
    private Runnable runnable;
    private int timerInterval = 1000;
    private boolean isTimerEnabled = false;

    private BluetoothManager deviceManager;
    private BluetoothAdapter deviceAdapter;
    private BluetoothLeScanner deviceScanner;

    private boolean isScanning = false;

    private final FPS coilDataCountPerSecond = new FPS();
    private final FPS probeDataCountPerSecond = new FPS();

    public interface ServiceCallback {
        void onScanResult(DeviceInfo deviceInfo, int rssi);
        void onDataReceived(DeviceInfo deviceInfo, Object data, Object extra);
        void onDeviceConnected(DeviceInfo deviceInfo);
        void onDeviceDisconnected(DeviceInfo deviceInfo);
        void onTimerExpired(DeviceInfo deviceInfo);
    }

    public static class ImuData {
        public float mx, my, mz, gx, gy, gz;

        public ImuData(float mx, float my, float mz,
                       float gx, float gy, float gz) {
            this.mx = mx;
            this.my = my;
            this.mz = mz;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
        }

        @NonNull
        @Override
        public String toString() {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            return "Magnetic: " +
                    decimalFormat.format(mx) + " " +
                    decimalFormat.format(my) + " " +
                    decimalFormat.format(mz) +
                    ", Accel: " +
                    decimalFormat.format(gx) + " " +
                    decimalFormat.format(gy) + " " +
                    decimalFormat.format(gz);
        }

        public void applyBiasToMagneticData(double bx, double by, double bz) {
            mx -= bx;
            my -= by;
            mz -= bz;
        }
    }

    public static class GpioData {
        public boolean gpio1;
        public boolean gpio2;
        public boolean gpio3;

        public GpioData() {
            gpio1 = gpio2 = gpio3 = false;
        }

        public GpioData(boolean gpio1, boolean gpio2, boolean gpio3) {
            this.gpio1 = gpio1;
            this.gpio2 = gpio2;
            this.gpio3 = gpio3;
        }
    }

    public static class ExtraData {
        public boolean powerDown;

        public ExtraData() {
            powerDown = false;
        }

        public ExtraData(boolean powerDown) {
            this.powerDown = powerDown;
        }
    }

    private static class FPS {
        private long startTime;
        private long counter;
        private float fps;

        public FPS() {
            start();
        }

        public void start() {
            startTime = System.currentTimeMillis();
            counter = 0;
            fps = 0;
        }

        public float getValue() {
            return fps;
        }

        public boolean signalDataReceived() {
            long elapsedTime = System.currentTimeMillis() - startTime;

            counter++;

            if (elapsedTime < 1000)
                return false;

            fps = (float)counter / (float)elapsedTime * 1000.0f;
            startTime = System.currentTimeMillis();
            counter = 0;

            return true;
        }
    }

    public class ServiceBinder extends Binder {
        public DeviceService getService() {
            return DeviceService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        serviceBinder = new ServiceBinder();
        serviceCallbacks = new ArrayList<>();

        // Setup timer
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        serviceCallback.onTimerExpired(null);
                    }
                }

                long elapsedTime = System.currentTimeMillis() - startTime;

                if (isTimerEnabled)
                    handler.postDelayed(this, Math.max(timerInterval - elapsedTime, 0));
            }
        };

        deviceManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);

        if (deviceManager != null && getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            deviceAdapter = deviceManager.getAdapter();

            if (deviceAdapter != null) {
                deviceScanner = deviceAdapter.getBluetoothLeScanner();
            }
        }

        registerReceiver(stateReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(bondStateReceiver, new IntentFilter(
                BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        unregisterReceiver(bondStateReceiver);
        unregisterReceiver(stateReceiver);

        stopDeviceScan();
        disconnectDevices();

        enableTimer(false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return serviceBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    public synchronized boolean registerCallback(ServiceCallback callback) {
        if (serviceCallbacks.contains(callback))
            return true;
        else {
            boolean ret;

            synchronized (serviceCallbackMutex) {
                ret = serviceCallbacks.add(callback);
            }

            if (!ret) {
                Log.e(TAG, "Failed to register callback (" + callback + ")");
                return false;
            }
        }

        // Log.d(TAG, "Callback has been registered (" + callback + ")");

        return true;
    }

    public synchronized boolean unregisterCallback(ServiceCallback callback) {
        if (!serviceCallbacks.contains(callback)) {
            Log.e(TAG, "Callback had not been registered before (" + callback + ")");
            return false;
        }
        else {
            boolean ret;

            synchronized (serviceCallbackMutex) {
                ret = serviceCallbacks.remove(callback);
            }

            if (!ret) {
                Log.e(TAG, "Failed to unregister callback (" + callback + ")");
                return false;
            }
        }

        // Log.d(TAG, "Callback has been unregistered (" + callback + ")");

        return true;
    }

    public void setTimerInterval(int milliseconds) {
        if (!isTimerEnabled) {
            if (milliseconds < 0)
                return;

            timerInterval = milliseconds;
        }
    }

    public void enableTimer(boolean enable) {
        if (enable && !isTimerEnabled) {
            isTimerEnabled = handler.postDelayed(runnable, 0);
        } else if (!enable && isTimerEnabled) {
            handler.removeCallbacks(runnable);
            isTimerEnabled = false;
        }
    }

    public DeviceInfo getDeviceInfo(String address) {
        DeviceStruct deviceStruct = getDeviceStruct(address);

        if (deviceStruct == null || !isDeviceConnected(address))
            return null;

        BluetoothDevice device = deviceStruct.gatt.getDevice();

        if (device == null)
            return null;

        return new DeviceInfo(getDeviceType(device), device);
    }

    public DeviceInfo[] getConnectedDevices() {
        List<DeviceInfo> deviceInfo = new ArrayList<>();

        for (String key : deviceMaps.keySet()) {
            if (!isDeviceConnected(key))
                continue;

            DeviceStruct deviceStruct = getDeviceStruct(key);

            if (deviceStruct == null)
                continue;

            BluetoothDevice device = deviceStruct.gatt.getDevice();

            deviceInfo.add(new DeviceInfo(getDeviceType(device), device));
        }

        return deviceInfo.toArray(new DeviceInfo[0]);
    }

    public boolean isDeviceConnected(String address) {
        DeviceStruct deviceStruct = getDeviceStruct(address);

        if (deviceManager == null || deviceStruct == null)
            return false;

        BluetoothDevice device = deviceStruct.gatt.getDevice();

        if (device == null)
            return false;

        return checkPermission() &&
                deviceManager.getConnectionState(device, BluetoothProfile.GATT) ==
                BluetoothProfile.STATE_CONNECTED && deviceStruct.connectionState ==
                ConnectionState.DATA_TRANSFER_ENABLED;
    }

    public boolean isDeviceAdapterOn() {
        if (deviceAdapter == null)
            return false;

        return deviceAdapter.isEnabled();
    }

    public boolean startDeviceScan(DeviceInfo.DeviceType type) {
        if (!checkPermission() || deviceScanner == null || isScanning)
            return false;

        deviceScanner.startScan(buildScanFilters(type), buildScanSettings(), scanCallback);
        isScanning = true;

        return true;
    }

    public boolean stopDeviceScan() {
        if (!checkPermission() || deviceScanner == null)
            return false;
        else if (!isScanning)
            return true;

        deviceScanner.stopScan(scanCallback);
        isScanning = false;

        return true;
    }

    public boolean connectDevice(String address) {
        if (!checkPermission() || deviceAdapter == null)
            return false;

        DeviceStruct deviceStruct = getDeviceStruct(address);
        BluetoothDevice device = deviceAdapter.getRemoteDevice(address);

        if (deviceStruct != null || device == null)
            return false;

        deviceStruct = new DeviceStruct();
        deviceStruct.gatt = device.connectGatt(getApplicationContext(),
                false, gattCallback);

        return deviceStruct.gatt != null &&
                putDeviceStruct(address, deviceStruct) == deviceStruct;
    }

    public boolean disconnectDevice(String address) {
        if (!checkPermission())
            return false;

        DeviceStruct deviceStruct = getDeviceStruct(address);

        if (deviceStruct == null)
            return false;

        deviceStruct.gatt.disconnect();

        return true;
    }

    private void disconnectDevices() {
        for (DeviceStruct deviceStruct : deviceMaps.values()) {
            disconnectDevice(deviceStruct.gatt.getDevice().getAddress());
        }
    }

    public boolean abortDevice(String address) {
        // TODO: Abort device
        return disconnectDevice(address);
    }

    private void abortDevices() {
        for (DeviceStruct deviceStruct : deviceMaps.values()) {
            // Remove device map out of this loop to avoid exception
            closeConnection(deviceStruct.gatt, false);
        }
        deviceMaps.clear();
    }

    public boolean writeDevice(String address, byte[] data) {
        if (!checkPermission())
            return false;

        DeviceStruct deviceStruct = getDeviceStruct(address);

        if (deviceStruct == null || deviceStruct.characteristicTransfer == null ||
            deviceStruct.waitCharacteristicTransferResponse)
            return false;

        BluetoothGatt gatt = deviceStruct.gatt;
        BluetoothDevice device = gatt.getDevice();
        BluetoothGattCharacteristic characteristic = deviceStruct.characteristicTransfer;

        int properties = characteristic.getProperties();

        // Check properties for write attribute
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE |
                BluetoothGattCharacteristic.PROPERTY_WRITE)) == 0)
            return false;

        if (getConnectionState(device) != ConnectionState.DATA_TRANSFER_ENABLED)
            return false;

        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        if (!characteristic.setValue(data))
            return false;

        deviceStruct.waitCharacteristicTransferResponse = true;

        if (!gatt.writeCharacteristic(deviceStruct.characteristicTransfer)) {
            deviceStruct.waitCharacteristicTransferResponse = false;
            return false;
        }

        return true;
    }

    public boolean checkPermission() {
        return (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED);
    }

    private void closeConnection(BluetoothGatt gatt, boolean removeDeviceMap) {
        if (!checkPermission())
            return;

        DeviceStruct deviceStruct = getDeviceStruct(gatt.getDevice());

        if (deviceStruct == null)
            return;

        BluetoothDevice device = gatt.getDevice();
        setConnectionState(device, null, ConnectionState.CHANNEL_DISCONNECTED);
        if (removeDeviceMap)
            removeDeviceStruct(device);

        synchronized (serviceCallbackMutex) {
            for (ServiceCallback serviceCallback : serviceCallbacks) {
                if (serviceCallback == null)
                    continue;

                serviceCallback.onDeviceDisconnected(new DeviceInfo(
                        getDeviceType(device), device));
            }
        }

        gatt.close();
    }

    private boolean setConnectionState(BluetoothDevice device,
                                       ConnectionState expectedState,
                                       ConnectionState newState) {
        DeviceStruct deviceStruct = getDeviceStruct(device);

        if (deviceStruct == null)
            return false;

        if (expectedState != null && deviceStruct.connectionState != expectedState) {
            Log.e(TAG, "Invalid state " + deviceStruct.connectionState +
                    " (address: " + device.getAddress() +
                    ", expected state: " + expectedState + ")");
            return false;
        } else if (deviceStruct.connectionState != newState) {
            Log.i(TAG, "State change " + deviceStruct.connectionState + " -> " + newState +
                    " (address: " + device.getAddress() + ")");
        } else {
            Log.i(TAG, "State no change " + deviceStruct.connectionState +
                    " (address: " + device.getAddress() + ")");
        }

        deviceStruct.connectionState = newState;

        return true;
    }

    private ConnectionState getConnectionState(BluetoothDevice device) {
        DeviceStruct deviceStruct = getDeviceStruct(device);

        if (deviceStruct == null)
            return null;

        return deviceStruct.connectionState;
    }

    private DeviceInfo.DeviceType getDeviceType(BluetoothDevice device) {
        if (!checkPermission() || device.getName() == null)
            return DeviceInfo.DeviceType.Unknown;

        if (device.getName().equals(DEVICE_NAME_FILTER_COIL))
            return DeviceInfo.DeviceType.Coil;
        else if (device.getName().equals(DEVICE_NAME_FILTER_PROBE))
            return DeviceInfo.DeviceType.Probe;
        else
            return DeviceInfo.DeviceType.Unknown;
    }

    private DeviceStruct putDeviceStruct(String address, DeviceStruct deviceStruct) {
        if (deviceStruct == null || deviceStruct.gatt == null ||
                deviceStruct.gatt.getDevice() == null ||
                !deviceStruct.gatt.getDevice().getAddress().equals(address)) {
            return null;
        }

        deviceMaps.put(address, deviceStruct);

        return deviceStruct;
    }

    private DeviceStruct getDeviceStruct(BluetoothDevice device) {
        return getDeviceStruct(device.getAddress());
    }

    private DeviceStruct getDeviceStruct(String address) {
        DeviceStruct deviceStruct = deviceMaps.get(address);

        if (deviceStruct == null || deviceStruct.gatt == null ||
                deviceStruct.gatt.getDevice() == null) {
            return null;
        } else if (!deviceStruct.gatt.getDevice().getAddress().equals(address)) {
            Log.e(TAG, "Invalid address mapping to device" +
                    " (address: " + deviceStruct.gatt.getDevice().getAddress() +
                    ", key: " + address + ")");
            return null;
        }

        return deviceStruct;
    }

    private void removeDeviceStruct(BluetoothDevice device) {
        deviceMaps.remove(device.getAddress());
    }

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "Bluetooth is turned off");
                        break;

                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;

                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "Bluetooth is turned on");
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        abortDevices();
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final String action = intent.getAction();

            if (!checkPermission() || device == null || action == null)
                return;

            DeviceStruct deviceStruct = getDeviceStruct(device.getAddress());

            if (deviceStruct == null || deviceStruct.gatt == null)
                return;

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final int bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevBondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

                switch (bondState) {
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "Bonding" +
                                " (address: " + device.getAddress() +
                                ", last state: " + prevBondState + ")");
                        break;

                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "Bonded" +
                                " (address: " + device.getAddress() +
                                ", last state: " + prevBondState + ")");

                        if (deviceStruct.connectionState != ConnectionState.CHANNEL_DISCONNECTED) {
                            // Falling into this state most likely is normal during
                            // the whole process so can ignore this message
                            break;
                        }

                        setConnectionState(device, null,
                                ConnectionState.CHANNEL_CONNECTED);

                        if (!deviceStruct.gatt.discoverServices()) {
                            Log.e(TAG, "Failed to start services discovery" +
                                    " (address: " + device.getAddress() + ")");
                            deviceStruct.gatt.disconnect();
                        }
                        break;

                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "No bonding" +
                                " (address: " + device.getAddress() +
                                ", last state: " + prevBondState + ")");
                        break;
                }
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (!checkPermission())
                return;

            BluetoothDevice device = gatt.getDevice();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection state change error" +
                        " (address: " + device.getAddress() +
                        ", status: " + status + ", state: " + newState + ")");
                closeConnection(gatt, true);
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                int bondState = gatt.getDevice().getBondState();

                Log.d(TAG, "Connected" +
                        " (address: " + device.getAddress() + ")");

                if (bondState == BluetoothDevice.BOND_NONE ||
                        bondState == BluetoothDevice.BOND_BONDED) {
                    setConnectionState(device, null,
                            ConnectionState.CHANNEL_CONNECTED);

                    if (!gatt.discoverServices()) {
                        Log.e(TAG, "Failed to start services discovery" +
                                " (address: " + device.getAddress() + ")");
                        gatt.disconnect();
                    }
                } else if (bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "Bonding now, services discovery will be started later" +
                            " (address: " + device.getAddress() + ")");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected" +
                        " (address: " + device.getAddress() + ")");
                closeConnection(gatt, true);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (!checkPermission())
                return;

            BluetoothDevice device = gatt.getDevice();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Failed to discover services" +
                        " (address: " + device.getAddress() +
                        ", status: " + status + ")");
                gatt.disconnect();
                return;
            }

            DeviceStruct deviceStruct = getDeviceStruct(device.getAddress());

            if (deviceStruct == null) {
                Log.e(TAG, "Invalid device struct for discovered services" +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            BluetoothGattService uartService = gatt.getService(
                    UUID.fromString(DEVICE_SERVICE_UUID_UART));

            if (uartService == null) {
                Log.e(TAG, "Failed to get service UART " +
                        UUID.fromString(DEVICE_SERVICE_UUID_UART).toString() +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            BluetoothGattCharacteristic characteristicTx = uartService.getCharacteristic(
                    UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_TX));

            if (characteristicTx == null) {
                Log.e(TAG, "Failed to get characteristic Tx " +
                        UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_TX).toString() +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            BluetoothGattCharacteristic characteristicRx = uartService.getCharacteristic(
                    UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_RX));

            if (characteristicRx == null) {
                Log.e(TAG, "Failed to get characteristic Rx " +
                        UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_RX).toString() +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            deviceStruct.characteristicTransfer = characteristicRx;

            if (!gatt.setCharacteristicNotification(characteristicTx, true)) {
                Log.e(TAG, "Failed to set characteristic Tx notification " +
                        UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_TX).toString() +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            boolean notificationRequested = false;

            // Enable notification to receive data from device
            for (BluetoothGattDescriptor descriptor : characteristicTx.getDescriptors()) {
                if (descriptor == null)
                    continue;

                String uuid = descriptor.getUuid().toString();
                int properties = characteristicTx.getProperties();

                if (!uuid.equalsIgnoreCase(UUID.fromString(DEVICE_DESCRIPTOR_UUID_CCCD).toString()))
                    continue;

                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                } else {
                    Log.e(TAG, "Invalid descriptor " + uuid +
                            " (address: " + device.getAddress() + ")");
                    gatt.disconnect();
                    return;
                }

                if (!setConnectionState(device,
                        ConnectionState.CHANNEL_CONNECTED,
                        ConnectionState.SERVICE_DISCOVERED)) {
                    gatt.disconnect();
                    return;
                }

                if (!gatt.writeDescriptor(descriptor)) {
                    Log.e(TAG, "Failed to write descriptor " + uuid +
                            " (address: " + device.getAddress() + ")");
                    gatt.disconnect();
                    return;
                }

                notificationRequested = true;
                break;
            }

            if (!notificationRequested) {
                Log.e(TAG, "No valid descriptor " +
                        UUID.fromString(DEVICE_DESCRIPTOR_UUID_CCCD).toString() + " found" +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (!checkPermission())
                return;

            BluetoothDevice device = gatt.getDevice();
            String uuid = characteristic.getUuid().toString();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Failed to write characteristic " + uuid +
                        " (address: " + device.getAddress() +
                        ", status: " + status + ")");
                gatt.disconnect();
                return;
            }

            DeviceStruct deviceStruct = getDeviceStruct(device.getAddress());

            if (deviceStruct == null) {
                Log.e(TAG, "Invalid device struct for written characteristic" +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            if (!uuid.equalsIgnoreCase(
                    UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_RX).toString())) {
                Log.e(TAG, "Invalid written characteristic " + uuid +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            if (getConnectionState(device) != ConnectionState.DATA_TRANSFER_ENABLED)
                return;

            deviceStruct.waitCharacteristicTransferResponse = false;
        }


        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            if (!checkPermission())
                return;

            BluetoothDevice device = gatt.getDevice();
            String uuid = descriptor.getUuid().toString();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Failed to write descriptor " + uuid +
                        " (address: " + device.getAddress() +
                        ", status: " + status + ")");
                gatt.disconnect();
                return;
            }

            if (!uuid.equalsIgnoreCase(UUID.fromString(DEVICE_DESCRIPTOR_UUID_CCCD).toString())) {
                Log.e(TAG, "Invalid written descriptor " + uuid +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
                return;
            }

            if (!setConnectionState(device,
                    ConnectionState.SERVICE_DISCOVERED,
                    ConnectionState.NOTIFICATION_ENABLED)) {
                gatt.disconnect();
                return;
            }

            if (!gatt.requestMtu(DEVICE_REQUEST_MTU)) {
                Log.e(TAG, "Failed to request MTU " + DEVICE_REQUEST_MTU +
                        " (address: " + device.getAddress() + ")");
                gatt.disconnect();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            if (!checkPermission())
                return;

            BluetoothDevice device = gatt.getDevice();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Failed to set MTU" +
                        " (address: " + device.getAddress() +
                        ", status: " + status + ")");
                gatt.disconnect();
                return;
            }

            Log.d(TAG, "MTU set to " + mtu +
                    " (address: " + device.getAddress() + ")");

            if (!setConnectionState(device,
                    ConnectionState.NOTIFICATION_ENABLED,
                    ConnectionState.DATA_TRANSFER_ENABLED)) {
                gatt.disconnect();
                return;
            }

            if (getDeviceType(device) == DeviceInfo.DeviceType.Coil) {
                coilDataCountPerSecond.start();
            } else if (getDeviceType(device) == DeviceInfo.DeviceType.Probe) {
                probeDataCountPerSecond.start();
            }

            synchronized (serviceCallbackMutex) {
                // Last step configuration of device completed, the device can transmit
                // data to host now
                for (ServiceCallback serviceCallback : serviceCallbacks) {
                    if (serviceCallback == null)
                        continue;

                    serviceCallback.onDeviceConnected(new DeviceInfo(
                            getDeviceType(device), device));
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            BluetoothDevice device = gatt.getDevice();
            String uuid = characteristic.getUuid().toString();

            if (!uuid.equalsIgnoreCase(
                    UUID.fromString(DEVICE_CHARACTERISTIC_UUID_UART_TX).toString()))
                return;

            if (getConnectionState(device) != ConnectionState.DATA_TRANSFER_ENABLED)
                return;

            // Get received data
            String message = characteristic.getStringValue(0);

            // Log.d(TAG, message);

            if (getDeviceType(device) == DeviceInfo.DeviceType.Coil) {
                if (coilDataCountPerSecond.signalDataReceived()) {
                    Log.d(TAG, "Coil message count per second: " +
                            coilDataCountPerSecond.getValue());
                }

                List<String> args = InputOutputFormatter.extractGpioControlData(message);

                if (args == null) {
                    Log.e(TAG, "Failed to extract received data: " + message);
                    return;
                }

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        serviceCallback.onDataReceived(
                                new DeviceInfo(getDeviceType(device), device),
                                new GpioData(args.get(0).equals("1"),   // GPIO input 0
                                        args.get(1).equals("1"),        // GPIO input 1
                                        args.get(2).equals("1")),       // GPIO input 2
                                null);
                    }
                }
            } else if (getDeviceType(device) == DeviceInfo.DeviceType.Probe) {
                if (probeDataCountPerSecond.signalDataReceived()) {
                    Log.d(TAG, "Probe message count per second: " +
                            probeDataCountPerSecond.getValue());
                }

                List<String> args = InputOutputFormatter.extractSensorData(message);

                if (args == null) {
                    Log.e(TAG, "Failed to extract received data: " + message);
                    return;
                }

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        try {
                            serviceCallback.onDataReceived(
                                    new DeviceInfo(getDeviceType(device), device),
                                    new ImuData(Float.parseFloat(args.get(0)),  // Magnetometer X
                                            Float.parseFloat(args.get(1)),      // Magnetometer Y
                                            Float.parseFloat(args.get(2)),      // Magnetometer Z
                                            Float.parseFloat(args.get(3)),      // Accelerometer X
                                            Float.parseFloat(args.get(4)),      // Accelerometer Y
                                            Float.parseFloat(args.get(5))),     // Accelerometer Z
                                    new ExtraData(args.get(6).equals("1")));    // Power down state
                        } catch (Exception e) {
                            Log.e(TAG, "Invalid argument in received data: " + message);
                        }
                    }
                }
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();

            synchronized (serviceCallbackMutex) {
                for (ServiceCallback serviceCallback : serviceCallbacks) {
                    if (serviceCallback == null)
                        continue;

                    serviceCallback.onScanResult(new DeviceInfo(
                            getDeviceType(device), device), result.getRssi());
                }
            }
        }
    };

    private List<ScanFilter> buildScanFilters(DeviceInfo.DeviceType type) {
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter.Builder builder = new ScanFilter.Builder();

        if (type != null) {
            builder.setServiceUuid(ParcelUuid.fromString(DEVICE_SERVICE_UUID_FILTER));

            if (type == DeviceInfo.DeviceType.Coil)
                builder.setDeviceName(DEVICE_NAME_FILTER_COIL);
            else if (type == DeviceInfo.DeviceType.Probe)
                builder.setDeviceName(DEVICE_NAME_FILTER_PROBE);
        }

        scanFilters.add(builder.build());
        return scanFilters;
    }

    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();

        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }
}
