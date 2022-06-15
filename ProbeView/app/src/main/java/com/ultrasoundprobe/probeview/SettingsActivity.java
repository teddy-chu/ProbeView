package com.ultrasoundprobe.probeview;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.ultrasoundprobe.probeview.device.DeviceInfo;
import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.format.FormatHelper;
import com.ultrasoundprobe.probeview.format.LocationInputFilter;
import com.ultrasoundprobe.probeview.host.HostInfo;
import com.ultrasoundprobe.probeview.host.HostService;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.preference.StorePreferenceHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements
        ServiceConnection, DeviceService.ServiceCallback,
        HostService.ServiceCallback, View.OnFocusChangeListener {
    private static final String TAG = "Settings";

    private DeviceService deviceService;
    private HostService hostService;

    private InputMethodManager inputMethodManager;
    private LocationInputFilter locationInputFilter;
    private List<EditText> locationInputs;

    private LocationData coilLocation;
    private LocationData probeLocation;
    private String scanImageServerUrl;
    private String powerControlServerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar_settings));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        locationInputFilter = new LocationInputFilter();
        locationInputs = new ArrayList<>();

        InputFilter[] inputFilter = new InputFilter[] { locationInputFilter };

        locationInputs.add(findViewById(R.id.edittext_coil_position_x));
        locationInputs.add(findViewById(R.id.edittext_coil_position_y));
        locationInputs.add(findViewById(R.id.edittext_coil_position_z));
        locationInputs.add(findViewById(R.id.edittext_coil_angle_x));
        locationInputs.add(findViewById(R.id.edittext_coil_angle_y));
        locationInputs.add(findViewById(R.id.edittext_coil_angle_z));
        locationInputs.add(findViewById(R.id.edittext_probe_position_x));
        locationInputs.add(findViewById(R.id.edittext_probe_position_y));
        locationInputs.add(findViewById(R.id.edittext_probe_position_z));
        locationInputs.add(findViewById(R.id.edittext_probe_angle_x));
        locationInputs.add(findViewById(R.id.edittext_probe_angle_y));
        locationInputs.add(findViewById(R.id.edittext_probe_angle_z));

        for (EditText editText: locationInputs) {
            editText.setFilters(inputFilter);
            editText.setOnFocusChangeListener(this);
        }

        loadPreference();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Log.d(TAG, "onDestroy()");

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
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Log.d(TAG, "onPause()");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);

        bindService(new Intent(this, DeviceService.class),
                this, BIND_AUTO_CREATE);
        bindService(new Intent(this, HostService.class),
                this, BIND_AUTO_CREATE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        // Log.i(TAG, "onServiceConnected(): " + name.getClassName());

        if (name.getClassName().equals(DeviceService.class.getName())) {
            deviceService = ((DeviceService.ServiceBinder)binder).getService();
            setDeviceCallback(true);
        } else if (name.getClassName().equals(HostService.class.getName())) {
            hostService = ((HostService.ServiceBinder)binder).getService();
            setHostCallback(true);
        }

        allowPreferenceEditable();
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
    public void onScanResult(DeviceInfo deviceInfo, int rssi) {

    }

    @Override
    public void onDataReceived(DeviceInfo deviceInfo, Object data, Object extra) {

    }

    @Override
    public void onDeviceConnected(DeviceInfo deviceInfo) {

    }

    @Override
    public void onDeviceDisconnected(DeviceInfo deviceInfo) {

    }

    @Override
    public void onTimerExpired(DeviceInfo deviceInfo) {

    }

    @Override
    public void onScanResult(HostInfo hostInfo, int rssi) {

    }

    @Override
    public void onImageReceived(HostInfo hostInfo, Bitmap bitmap) {

    }

    @Override
    public void onHostConnected(HostInfo hostInfo) {

    }

    @Override
    public void onHostDisconnected(HostInfo hostInfo) {

    }

    @Override
    public void onTimerExpired(HostInfo hostInfo) {

    }

    @Override
    public void onBackPressed() {
        int resultCode = savePreference() ?
                Activity.RESULT_OK : Activity.RESULT_CANCELED;

        setResult(resultCode, new Intent());
        finish();
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (hasFocus)
            return;

        for (EditText editText: locationInputs) {
            if (editText.getId() != view.getId())
                continue;

            // Hide software input before set text to avoid warning message
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

            // Apply filter to text input after its focus is lost
            ((EditText)view).setText(locationInputFilter.getValue(
                    ((EditText)view).getText().toString()));
            break;
        }
    }

    private void setDeviceCallback(boolean enable) {
        if (deviceService == null)
            return;

        if (enable) {
            if (!deviceService.registerCallback(this))
                Log.e(TAG, "Failed to register callback for " +
                        DeviceService.class.getName());
        } else {
            if (!deviceService.unregisterCallback(this))
                Log.e(TAG, "Failed to unregister callback for " +
                        DeviceService.class.getName());
        }
    }

    private void setHostCallback(boolean enable) {
        if (hostService == null)
            return;

        if (enable) {
            if (!hostService.registerCallback(this))
                Log.e(TAG, "Failed to register callback for " +
                        HostService.class.getName());
        } else {
            if (!hostService.unregisterCallback(this))
                Log.e(TAG, "Failed to unregister callback for " +
                        HostService.class.getName());
        }
    }

    private void allowPreferenceEditable() {
        if (deviceService != null) {
            DeviceInfo[] deviceInfo = deviceService.getConnectedDevices();

            for (EditText editText: locationInputs) {
                boolean isCoilSelected = editText.toString().equals(
                                findViewById(R.id.edittext_coil_position_x).toString()) ||
                        editText.toString().equals(
                                findViewById(R.id.edittext_coil_position_y).toString()) ||
                        editText.toString().equals(
                                findViewById(R.id.edittext_coil_position_z).toString()) ||
                        editText.toString().equals(
                                findViewById(R.id.edittext_coil_angle_x).toString()) ||
                        editText.toString().equals(
                                findViewById(R.id.edittext_coil_angle_y).toString()) ||
                        editText.toString().equals(
                                findViewById(R.id.edittext_coil_angle_z).toString());
                boolean isEnabled = !isCoilSelected ||
                        (isCoilSelected && AppConfig.CoilObject3dViewVisible);

                editText.setEnabled(deviceInfo.length == 0 && isEnabled);
                editText.setFocusableInTouchMode(deviceInfo.length == 0 && isEnabled);
            }
        }

        if (hostService != null) {
            boolean hostConnected = hostService.isHostConnected();

            findViewById(R.id.edittext_scan_image_server)
                    .setEnabled(!hostConnected);
            findViewById(R.id.edittext_scan_image_server)
                    .setFocusableInTouchMode(!hostConnected);
        }
    }

    private void loadPreference() {
        NumberFormat locationFormat = FormatHelper.location();

        coilLocation = StorePreferenceHelper.getCoilLocation();
        probeLocation = StorePreferenceHelper.getProbeLocation();
        scanImageServerUrl = StorePreferenceHelper.getScanImageServerUrl();
        powerControlServerAddress = StorePreferenceHelper.getPowerControlServerAddress();

        Log.d(TAG, "Read coil location settings: " +
                Arrays.toString(coilLocation.getValues()));
        Log.d(TAG, "Read probe location settings: " +
                Arrays.toString(probeLocation.getValues()));
        Log.d(TAG, "Read scan image server settings: " +
                scanImageServerUrl);
        Log.d(TAG, "Read power control server settings: " +
                powerControlServerAddress);

        ((EditText)findViewById(R.id.edittext_coil_position_x))
                .setText(locationFormat.format(coilLocation.px));
        ((EditText)findViewById(R.id.edittext_coil_position_y))
                .setText(locationFormat.format(coilLocation.py));
        ((EditText)findViewById(R.id.edittext_coil_position_z))
                .setText(locationFormat.format(coilLocation.pz));
        ((EditText)findViewById(R.id.edittext_coil_angle_x))
                .setText(locationFormat.format(coilLocation.ax));
        ((EditText)findViewById(R.id.edittext_coil_angle_y))
                .setText(locationFormat.format(coilLocation.ay));
        ((EditText)findViewById(R.id.edittext_coil_angle_z))
                .setText(locationFormat.format(coilLocation.az));

        ((EditText)findViewById(R.id.edittext_probe_position_x))
                .setText(locationFormat.format(probeLocation.px));
        ((EditText)findViewById(R.id.edittext_probe_position_y))
                .setText(locationFormat.format(probeLocation.py));
        ((EditText)findViewById(R.id.edittext_probe_position_z))
                .setText(locationFormat.format(probeLocation.pz));
        ((EditText)findViewById(R.id.edittext_probe_angle_x))
                .setText(locationFormat.format(probeLocation.ax));
        ((EditText)findViewById(R.id.edittext_probe_angle_y))
                .setText(locationFormat.format(probeLocation.ay));
        ((EditText)findViewById(R.id.edittext_probe_angle_z))
                .setText(locationFormat.format(probeLocation.az));

        ((EditText)findViewById(R.id.edittext_scan_image_server))
                .setText(scanImageServerUrl);
        ((EditText)findViewById(R.id.edittext_power_control_server))
                .setText(powerControlServerAddress);
    }

    private boolean savePreference() {
        if (coilLocation == null || probeLocation == null ||
                scanImageServerUrl == null || powerControlServerAddress == null)
            return false;

        for (EditText editText: locationInputs) {
            float value;

            try {
                value = Float.parseFloat(
                        locationInputFilter.getValue(editText.getText().toString()));
            } catch (NumberFormatException e) {
                return false;
            }

            if (editText.getId() == R.id.edittext_coil_position_x) {
                coilLocation.px = value;
            } else if (editText.getId() == R.id.edittext_coil_position_y) {
                coilLocation.py = value;
            } else if (editText.getId() == R.id.edittext_coil_position_z) {
                coilLocation.pz = value;
            } else if (editText.getId() == R.id.edittext_coil_angle_x) {
                coilLocation.ax = value;
            } else if (editText.getId() == R.id.edittext_coil_angle_y) {
                coilLocation.ay = value;
            } else if (editText.getId() == R.id.edittext_coil_angle_z) {
                coilLocation.az = value;
            } else if (editText.getId() == R.id.edittext_probe_position_x) {
                probeLocation.px = value;
            } else if (editText.getId() == R.id.edittext_probe_position_y) {
                probeLocation.py = value;
            } else if (editText.getId() == R.id.edittext_probe_position_z) {
                probeLocation.pz = value;
            } else if (editText.getId() == R.id.edittext_probe_angle_x) {
                probeLocation.ax = value;
            } else if (editText.getId() == R.id.edittext_probe_angle_y) {
                probeLocation.ay = value;
            } else if (editText.getId() == R.id.edittext_probe_angle_z) {
                probeLocation.az = value;
            } else {
                return false;
            }
        }

        scanImageServerUrl = ((EditText)findViewById(R.id.edittext_scan_image_server))
                .getText().toString();
        powerControlServerAddress = ((EditText)findViewById(R.id.edittext_power_control_server))
                .getText().toString();

        Log.d(TAG, "Write coil location settings: " +
                Arrays.toString(coilLocation.getValues()));
        Log.d(TAG, "Write probe location settings: " +
                Arrays.toString(probeLocation.getValues()));
        Log.d(TAG, "Write scan image server settings: " +
                scanImageServerUrl);
        Log.d(TAG, "Write power control server settings: " +
                powerControlServerAddress);

        StorePreferenceHelper.setCoilLocation(coilLocation);
        StorePreferenceHelper.setProbeLocation(probeLocation);
        StorePreferenceHelper.setScanImageServerUrl(scanImageServerUrl);
        StorePreferenceHelper.setPowerControlServerAddress(powerControlServerAddress);

        return true;
    }
}