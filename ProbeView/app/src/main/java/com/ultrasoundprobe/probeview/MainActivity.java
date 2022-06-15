package com.ultrasoundprobe.probeview;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.ultrasoundprobe.probeview.device.DeviceInfo;
import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.device.coil.CoilSwitch;
import com.ultrasoundprobe.probeview.device.power.PowerControl;
import com.ultrasoundprobe.probeview.dialog.DialogHelper;
import com.ultrasoundprobe.probeview.host.HostInfo;
import com.ultrasoundprobe.probeview.host.HostService;
import com.ultrasoundprobe.probeview.image.ScanImageView;
import com.ultrasoundprobe.probeview.navigation.NavigationInterface;
import com.ultrasoundprobe.probeview.navigation.NavigationView;
import com.ultrasoundprobe.probeview.navigation.drawing.SurfaceInterface;
import com.ultrasoundprobe.probeview.navigation.location.DataConvert;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.navigation.location.algorithm.BaseEngine;
import com.ultrasoundprobe.probeview.navigation.location.algorithm.RemoteAlgoBackend;
import com.ultrasoundprobe.probeview.navigation.model.ObjectModel;
import com.ultrasoundprobe.probeview.navigation.scanning.DetectionSurface;
import com.ultrasoundprobe.probeview.preference.DefaultPreference;
import com.ultrasoundprobe.probeview.preference.StorePreferenceHelper;

public class MainActivity extends AppCompatActivity implements
        ServiceConnection, DeviceService.ServiceCallback,
        HostService.ServiceCallback, NavigationInterface {
    private static final String TAG = "ProbeView";

    private static final int PERMISSION_REQUEST_ENABLE = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;

    private DeviceService deviceService;
    private HostService hostService;

    private Menu menu;
    private NavigationViewFragment navigationViewFragment;
    private NavigationInfoFragment navigationInfoFragment;
    private NavigationView navigationView;
    private SurfaceInterface surfaceInterface;
    private DataConvert dataConvert;

    private ScanImageView scanImageView;
    private String scanImageUrl;

    private CoilSwitch coilSwitch;
    private DeviceService.GpioData coilControl;

    private PowerControl powerControl;
    private String powerControlAddress;

    private DetectionSurface detectionSurface;

    private int testSampleIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar_main));

        scanImageView = findViewById(R.id.imageview_scan_image);

        coilSwitch = new CoilSwitch();
        coilControl = new DeviceService.GpioData();
        powerControl = new PowerControl();

        StorePreferenceHelper.init(getApplicationContext());

        // Handle fragment loads multiple times on orientation change
        if (savedInstanceState == null) {
            navigationViewFragment = new NavigationViewFragment();
            navigationInfoFragment = new NavigationInfoFragment();
        } else {
            navigationViewFragment = (NavigationViewFragment)getSupportFragmentManager()
                    .findFragmentByTag(NavigationViewFragment.class.getName());
            navigationInfoFragment = (NavigationInfoFragment)getSupportFragmentManager()
                    .findFragmentByTag(NavigationInfoFragment.class.getName());
        }

        navigationViewFragment.setNavigationInterface(this);

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.linearlayout_navigation_view,
                        navigationViewFragment, NavigationViewFragment.class.getName())
                .commit();
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.linearlayout_navigation_info,
                        navigationInfoFragment, NavigationInfoFragment.class.getName())
                .commit();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Log.d(TAG, "onDestroy()");

        powerControl.stop();

        unbindService(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Log.d(TAG, "onStart()");

        setDeviceCallback(true);
        setHostCallback(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Log.d(TAG, "onStop()");

        setDeviceCallback(false);
        setHostCallback(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Log.d(TAG, "onResume()");

        registerReceiver(stateReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED));

        updateActionBarUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Log.d(TAG, "onPause()");

        unregisterReceiver(stateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;

        bindService(new Intent(this, DeviceService.class),
                this, BIND_AUTO_CREATE);
        bindService(new Intent(this, HostService.class),
                this, BIND_AUTO_CREATE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_coil_connect) {
            handleDeviceConnectivityUi(DeviceInfo.DeviceType.Coil);
        } else if (item.getItemId() == R.id.menu_item_probe_connect) {
            handleDeviceConnectivityUi(DeviceInfo.DeviceType.Probe);
        } else if (item.getItemId() == R.id.menu_item_host_connect) {
            handleHostConnectivityUi();
        } else if (item.getItemId() == R.id.menu_item_settings) {
            handleSettingsUi();
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                return;

            showPermissionDialog(requestCode);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        // Log.i(TAG, "onServiceConnected(): " + name.getClassName());

        if (name.getClassName().equals(DeviceService.class.getName())) {
            deviceService = ((DeviceService.ServiceBinder)binder).getService();
            setDeviceCallback(true);

            if (checkBluetoothOn())
                checkBluetoothPermission();
        } else if (name.getClassName().equals(HostService.class.getName())) {
            hostService = ((HostService.ServiceBinder)binder).getService();
            setHostCallback(true);
            setRemoteAlgoBackend();
            setPowerControlBackend();
        }

        updateActionBarUi();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Log.i(TAG, "onServiceDisconnected(): " + name.getClassName());

        if (name.getClassName().equals(DeviceService.class.getName())) {
            deviceService = null;
        } else if (name.getClassName().equals(HostService.class.getName())) {
            hostService = null;
        }
    }

    @Override
    public void on3dViewCreated() {
        // Log.d(TAG, "on3dViewCreated()");

        navigationView = (NavigationView)navigationViewFragment.getView();

        if (navigationView == null) {
            Log.e(TAG, "Failed to get " + NavigationView.class.getName());
            return;
        }

        surfaceInterface = navigationView.getSurfaceInterface();
        dataConvert = navigationView.getDataConvert();

        setRemoteAlgoBackend();

        // Load detection surface of a model for navigation demo
        detectionSurface = new DetectionSurface(surfaceInterface);
        detectionSurface.loadSurfaceModel();

        loadPreference();

        runOnUiThread(() -> {
            updateCoilLocationUi();
            updateProbeLocationUi();

            // FIXME: Call this update from `onViewCreated()` by the NavigationInfoFragment class
            updateImuDataUi(null);
            updateGpioDataUi(null);
        });
    }

    @Override
    public void on3dViewDrawn() {
        runOnUiThread(() -> {
            updateCoilLocationUi();
            updateProbeLocationUi();
        });
    }

    @Override
    public void on3dModelSurfaceTouched(ObjectModel source, ObjectModel target) {
        Log.d(TAG, "Source touched:\n" + source);
        Log.d(TAG, "Target touched:\n" + target);

        if (detectionSurface == null)
            return;

        int nextId = detectionSurface.getNavigationModelNextId(target.getId());

        if (nextId >= 0) {
            // Enable touch detection of next model with other models for navigation
            surfaceInterface.setSurfaceTouchDetection(nextId, true);

            Log.d(TAG, "Next detection surface Id " + nextId + " for navigation enabled");
        } else {
            Log.d(TAG, "All detection surfaces for navigation enabled");
        }
    }

    @Override
    public void on3dModelSurfaceUntouched(ObjectModel source, ObjectModel target) {
        Log.d(TAG, "Source untouched:\n" + source);
        Log.d(TAG, "Target untouched:\n" + target);
    }

    @Override
    public void on3dModelSurfaceTouchChecked(ObjectModel source, ObjectModel target) {
    }

    @Override
    public void onScanResult(DeviceInfo deviceInfo, int rssi) {
        Log.d(TAG, deviceInfo.getDescription() + ", RSSI: " + rssi);
    }

    @Override
    public void onDataReceived(DeviceInfo deviceInfo, Object data, Object extra) {
        if (deviceInfo.getType() == DeviceInfo.DeviceType.Coil) {
            // GPIO data from coil device
            DeviceService.GpioData gpioData = (DeviceService.GpioData)data;

            coilControl.gpio1 = gpioData.gpio1;
            coilControl.gpio2 = gpioData.gpio2;
            coilControl.gpio3 = gpioData.gpio3;

            runOnUiThread(() -> updateGpioDataUi(gpioData));
        } else if (deviceInfo.getType() == DeviceInfo.DeviceType.Probe) {
            // IMU data from probe device
            DeviceService.ImuData imuData = (DeviceService.ImuData)data;
            // Extra data from probe device
            DeviceService.ExtraData extraData = (DeviceService.ExtraData)extra;
            // Get IMU data associated with coil switch
            DeviceService.ImuData[] values = coilSwitch.getImuDataFromCoilSwitch(
                    deviceService,
                    getConnectedDeviceAddress(DeviceInfo.DeviceType.Coil),
                    coilControl,
                    imuData);

            runOnUiThread(() -> updateImuDataUi(imuData));

            powerControl.start(extraData);

            // IMU data from coil switch is not complete for null return
            if (values == null)
                return;

            if (navigationView != null && !navigationView.updateImuData(
                    coilSwitch.getImuDataFromActiveCoils(values),
                    coilSwitch.getImuDataFromInactiveCoils(values)))
                Log.e(TAG, "Cannot update IMU data");
        }
    }

    @Override
    public void onDeviceConnected(DeviceInfo deviceInfo) {
        Log.d(TAG, deviceInfo.getDescription() + " connected");
    }

    @Override
    public void onDeviceDisconnected(DeviceInfo deviceInfo) {
        Log.d(TAG, deviceInfo.getDescription() + " disconnected");

        runOnUiThread(() -> {
            DialogHelper.dismissProgressDialog();
            updateActionBarUi();
        });
    }

    @Override
    public void onTimerExpired(DeviceInfo deviceInfo) {
        // TODO: Remove this test code
        runReceivedImuDataTest();
    }

    @Override
    public void onScanResult(HostInfo hostInfo, int rssi) {}

    @Override
    public void onImageReceived(HostInfo hostInfo, Bitmap bitmap) {
        // This callback is invoked from the UI thread so it is safe to do
        // UI tasks directly from here
        updateScanImageUi(bitmap);
    }

    @Override
    public void onHostConnected(HostInfo hostInfo) {
        Log.d(TAG, hostInfo.getDescription() + " connected");

        runOnUiThread(() -> {
            DialogHelper.dismissProgressDialog();
            updateActionBarUi();
        });
    }

    @Override
    public void onHostDisconnected(HostInfo hostInfo) {
        Log.d(TAG, hostInfo.getDescription() + " disconnected");

        runOnUiThread(() -> {
            DialogHelper.dismissProgressDialog();
            updateActionBarUi();
        });
    }

    @Override
    public void onTimerExpired(HostInfo hostInfo) {

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
                        updateActionBarUi();
                        checkBluetoothPermission();
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        updateActionBarUi();
                        break;
                }
            }
        }
    };

    private final ActivityResultLauncher<Intent> bluetoothOnResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                updateActionBarUi();

                if (result.getResultCode() == Activity.RESULT_OK) {
                    checkBluetoothPermission();
                    return;
                }

                showPermissionDialog(PERMISSION_REQUEST_ENABLE);
            });

    private final ActivityResultLauncher<Intent> deviceConnectivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                // Device connected for 'RESULT_OK'
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();

                    if (intent == null)
                        return;

                    DeviceInfo.DeviceType deviceType = (DeviceInfo.DeviceType)intent
                            .getSerializableExtra(DeviceInfo.DeviceType.class.getSimpleName());

                    if (deviceType == DeviceInfo.DeviceType.Coil) {
                        Log.d(TAG, "Reset coil switch");

                        // Reset coil switch
                        coilSwitch.resetCoilControl();
                        // Enable debug output
                        coilSwitch.setDebug(AppConfig.CoilSwitchDebug);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> settingsResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Settings updated so reload new status from stored preference
                    loadPreference();

                    runOnUiThread(() -> {
                        updateCoilLocationUi();
                        updateProbeLocationUi();
                    });
                } else {
                    Log.e(TAG, "Failed to store preference from settings");
                }
            });

    private void setDeviceCallback(boolean enable) {
        if (deviceService == null)
            return;

        if (enable) {
            if (!deviceService.registerCallback(this))
                Log.e(TAG, "Failed to register callback for " +
                        DeviceService.class.getName());

            deviceService.setTimerInterval(10);
        } else {
            if (!deviceService.unregisterCallback(this))
                Log.e(TAG, "Failed to unregister callback for " +
                        DeviceService.class.getName());
        }

        deviceService.enableTimer(enable && AppConfig.NavigationAlgorithmTest);
    }

    private void setHostCallback(boolean enable) {
        if (hostService == null)
            return;

        if (enable) {
            if (!hostService.registerCallback(this))
                Log.e(TAG, "Failed to register callback for " +
                        HostService.class.getName());

            hostService.setTimerInterval(1000);
        } else {
            if (!hostService.unregisterCallback(this))
                Log.e(TAG, "Failed to unregister callback for " +
                        HostService.class.getName());
        }
    }

    private String getConnectedDeviceAddress(DeviceInfo.DeviceType type) {
        if (deviceService == null)
            return null;

        String address = null;

        for (DeviceInfo deviceInfo : deviceService.getConnectedDevices()) {
            if (deviceInfo.getType() != type)
                continue;

            // Pick the first available device of a type from the list
            address = deviceInfo.getAddress();

            break;
        }

        return address;
    }

    private void setRemoteAlgoBackend() {
        if (dataConvert == null || hostService == null)
            return;

        BaseEngine algoEngine = dataConvert.getEngine();

        if (algoEngine.getClass() == RemoteAlgoBackend.class) {
            ((RemoteAlgoBackend)algoEngine).setHostService(hostService);
            ((RemoteAlgoBackend)algoEngine).setHostAddress(AppConfig.RemoteAlgoBackendAddress);
        }
    }

    private void setPowerControlBackend() {
        if (hostService == null || powerControl == null)
            return;

        powerControl.setHostService(hostService);
        powerControl.setHostAddress(powerControlAddress);
    }

    private void handleDeviceConnectivityUi(DeviceInfo.DeviceType type) {
        if (deviceService == null)
            return;

        String address = getConnectedDeviceAddress(type);
        DeviceInfo deviceInfo = deviceService.getDeviceInfo(address);

        if (deviceService.isDeviceConnected(address)) {
            if (!deviceService.disconnectDevice(address)) {
                DialogHelper.showDialog(this,
                        getResources().getString(R.string.dialog_title_disconnect_error),
                        deviceInfo.getDescription());
                updateActionBarUi();
                return;
            }

            DialogHelper.showProgressDialog(this,
                    getResources().getString(R.string.dialog_title_disconnect),
                    deviceInfo.getDescription(),
                    getResources().getString(R.string.dialog_button_disconnect),
                    (dialogInterface, i) -> {
                        if (!deviceService.abortDevice(address)) {
                            DialogHelper.dismissProgressDialog();
                            DialogHelper.showDialog(this,
                                    getResources().getString(
                                            R.string.dialog_title_abort_error),
                                    deviceInfo.getDescription());
                        }
                        updateActionBarUi();
                    });
        } else {
            Intent intent = new Intent(this, DeviceConnectActivity.class);
            intent.putExtra(DeviceInfo.DeviceType.class.getSimpleName(), type);
            deviceConnectivityResultLauncher.launch(intent);
        }
    }

    private void handleHostConnectivityUi() {
        if (hostService == null)
            return;

        HostInfo hostInfo = hostService.getHostInfo();

        if (hostService.isHostConnected()) {
            if (!hostService.disconnectHost()) {
                DialogHelper.showDialog(this,
                        getResources().getString(R.string.dialog_title_disconnect_error),
                        hostInfo.getDescription());
                updateActionBarUi();
                return;
            }

            DialogHelper.showProgressDialog(this,
                    getResources().getString(R.string.dialog_title_disconnect),
                    hostInfo.getDescription(),
                    getResources().getString(R.string.dialog_button_disconnect),
                    (dialogInterface, i) -> {
                        if (!hostService.abortHost()) {
                            DialogHelper.dismissProgressDialog();
                            DialogHelper.showDialog(this,
                                    getResources().getString(
                                            R.string.dialog_title_abort_error),
                                    hostInfo.getDescription());
                        }
                        updateActionBarUi();
                    });
        } else {
            // startActivity(new Intent(this, HostConnectActivity.class));

            if (hostService.connectHost(scanImageUrl)) {
                DialogHelper.showProgressDialog(this,
                        getResources().getString(R.string.dialog_title_connect),
                        scanImageUrl,
                        getResources().getString(R.string.dialog_button_connect),
                        (dialogInterface, i) -> hostService.disconnectHost());
            } else {
                DialogHelper.showDialog(this,
                        getResources().getString(R.string.dialog_title_connect_error),
                        scanImageUrl);
            }
        }
    }

    private void handleSettingsUi() {
        settingsResultLauncher.launch(new Intent(this, SettingsActivity.class));
    }

    private void updateActionBarUi() {
        if (menu == null)
            return;

        if (deviceService != null) {
            boolean isBluetoothOn = deviceService.isDeviceAdapterOn();

            menu.findItem(R.id.menu_item_coil_connect).setEnabled(isBluetoothOn);
            menu.findItem(R.id.menu_item_probe_connect).setEnabled(isBluetoothOn);

            if (deviceService.isDeviceConnected(
                    getConnectedDeviceAddress(DeviceInfo.DeviceType.Coil))) {
                menu.findItem(R.id.menu_item_coil_connect)
                        .setIcon(AppCompatResources.getDrawable(this,
                                R.drawable.ic_baseline_grid_on_24));
            } else {
                menu.findItem(R.id.menu_item_coil_connect)
                        .setIcon(AppCompatResources.getDrawable(this,
                                R.drawable.ic_baseline_grid_off_24));
            }

            if (deviceService.isDeviceConnected(
                    getConnectedDeviceAddress(DeviceInfo.DeviceType.Probe))) {
                menu.findItem(R.id.menu_item_probe_connect)
                        .setIcon(AppCompatResources.getDrawable(this,
                                R.drawable.ic_baseline_flashlight_on_24));
            } else {
                menu.findItem(R.id.menu_item_probe_connect)
                        .setIcon(AppCompatResources.getDrawable(this,
                                R.drawable.ic_baseline_flashlight_off_24));
            }
        }

        if (hostService != null) {
            if (hostService.isHostConnected()) {
                menu.findItem(R.id.menu_item_host_connect)
                        .setIcon(AppCompatResources.getDrawable(this,
                                R.drawable.ic_baseline_desktop_windows_24));
            } else {
                menu.findItem(R.id.menu_item_host_connect)
                        .setIcon(AppCompatResources.getDrawable(this,
                                R.drawable.ic_baseline_desktop_access_disabled_24));
            }
        }
    }

    private void updateScanImageUi(Bitmap bitmap) {
        scanImageView.setImageBitmap(bitmap);
    }

    private void updateImuDataUi(DeviceService.ImuData value) {
        navigationInfoFragment.updateImuData(value);
    }

    private void updateGpioDataUi(DeviceService.GpioData value) {
        navigationInfoFragment.updateGpioData(value);
    }

    private void updateCoilLocationUi() {
        if (surfaceInterface == null)
            return;

        navigationInfoFragment.updateCoilLocation(
                surfaceInterface.getCoilLocation(),
                dataConvert.getViewRatio());
    }

    private void updateProbeLocationUi() {
        if (surfaceInterface == null)
            return;

        navigationInfoFragment.updateProbeLocation(
                surfaceInterface.getProbeLocation(),
                dataConvert.getViewRatio());
    }

    private void loadPreference() {
        if (surfaceInterface == null)
            return;

        // Clear all settings
        // StorePreferenceHelper.clearSettings();

        LocationData coilLocation = StorePreferenceHelper.getCoilLocation();
        LocationData probeLocation = StorePreferenceHelper.getProbeLocation();
        String scanImageServerUrl = StorePreferenceHelper.getScanImageServerUrl();
        String powerControlServerAddress = StorePreferenceHelper.getPowerControlServerAddress();

        if (coilLocation == null) {
            coilLocation = DefaultPreference.getCoilLocation();
            StorePreferenceHelper.setCoilLocation(coilLocation);
        }

        if (!surfaceInterface.setCoilLocation(coilLocation)) {
            Log.e(TAG, "Failed to load coil location from stored preference");
        }

        if (probeLocation == null) {
            probeLocation = DefaultPreference.getProbeLocation();
            StorePreferenceHelper.setProbeLocation(probeLocation);
        }

        if (!surfaceInterface.setProbeLocation(probeLocation)) {
            Log.e(TAG, "Failed to load probe location from stored preference");
        }

        if (scanImageServerUrl == null) {
            scanImageServerUrl = DefaultPreference.getScanImageServerUrl();
            StorePreferenceHelper.setScanImageServerUrl(scanImageServerUrl);
        }

        scanImageUrl = scanImageServerUrl;

        if (powerControlServerAddress == null) {
            powerControlServerAddress = DefaultPreference.getPowerControlServerAddress();
            StorePreferenceHelper.setPowerControlServerAddress(powerControlServerAddress);
        }

        powerControlAddress = powerControlServerAddress;
    }

    private boolean checkBluetoothOn() {
        if (deviceService == null)
            return false;

        if (deviceService.isDeviceAdapterOn())
            return true;

        bluetoothOnResultLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        return false;
    }

    private void checkBluetoothPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED)
            return;

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSION_REQUEST_FINE_LOCATION);
        } else {
            showPermissionDialog(PERMISSION_REQUEST_FINE_LOCATION);
        }
    }

    private void showPermissionDialog(int requestCode) {
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                DialogHelper.showDialog(this,
                        getResources().getString(
                                R.string.dialog_title_bluetooth_error),
                        getResources().getString(
                                R.string.dialog_description_bluetooth_error));
                break;
            }

            case PERMISSION_REQUEST_ENABLE: {
                DialogHelper.showDialog(this,
                        getResources().getString(
                                R.string.dialog_title_bluetooth_on_error),
                        getResources().getString(
                                R.string.dialog_description_bluetooth_on_error));
                break;
            }
        }
    }

    private void runReceivedImuDataTest() {
        // Sample test dataset as received IMU data of 3 groups from sensor
        // with ordered coil activation, the table has this array of magnetic
        // data format [Bx0, By0, Bz0, Bx1, By1, Bz1, Bx2, By2, Bz2]:
        //  1. [Bx0, By0, Bz0] as IMU data of group 1
        //  2. [Bx1, By1, Bz1] as IMU data of group 2
        //  3. [Bx2, By2, Bz2] as IMU data of group 3
        double[][] samples = {
                { -128.5, -339, 1268, 92, 498, 204.5, -224.5, 57, 17 },
                { -122, -333.5, 1257.5, 83.5, 505, 219.5, -237.5, 55, 5 },
                { -69, -171.5, 660, 55.5, 331, 132, -232.5, 58, 18 },
                { -127.5, -333, 1254.5, 49.5, 313.5, 122, -238.5, 51, 0.5 },
                { 0.0, -281.94, 488.33, 0.0, -186.82, -107.86, -74.99, 0.0, 0.0 },
                { -45.9, -21.15, 538.05, 24.3, -215.4, -31.95, -102.0, -7.05, -11.4 },
                { 8.1, 577.05, 48.9, 5.55, -36.5, -234.95, -103.65, 5.7, 2.7 },
                { 0.0, 0.0, 2372.66, 0.0, -1131.11, 0.0, -393.84, 0.0, 0.0 },
                { -23.55, -172.2, 496.35, 13.05, -126.45, -88.2, -103.8, -4.95, -4.95 },
                { -48.45, -118.2, 1197.9, 39.45, 75.45, -137.4, -244.8, -0.45, -17.55 }
        };

        if (navigationView == null)
            return;


        if (!navigationView.isImuBufferEmpty())
            return;

        testSampleIndex %= samples.length;

        double[] values = samples[testSampleIndex];

        DeviceService.ImuData[] imuData = new DeviceService.ImuData[]{
                new DeviceService.ImuData(
                        (float)values[0], (float)values[1], (float)values[2],
                        0, 0, 0),
                new DeviceService.ImuData(
                        (float)values[3], (float)values[4], (float)values[5],
                        0, 0, 0),
                new DeviceService.ImuData(
                        (float)values[6], (float)values[7], (float)values[8],
                        0, 0, 0),
        };

        navigationView.updateImuData(imuData, null);

        testSampleIndex++;

        if (testSampleIndex == samples.length)
            deviceService.enableTimer(false);
    }
}