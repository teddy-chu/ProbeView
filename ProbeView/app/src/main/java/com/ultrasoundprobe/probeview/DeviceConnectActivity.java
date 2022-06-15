package com.ultrasoundprobe.probeview;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import com.ultrasoundprobe.probeview.device.DeviceInfo;
import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.device.view.DeviceEntry;
import com.ultrasoundprobe.probeview.device.view.DeviceListAdaptor;
import com.ultrasoundprobe.probeview.dialog.DialogHelper;

import java.util.ArrayList;

public class DeviceConnectActivity extends AppCompatActivity implements
        ServiceConnection, DeviceService.ServiceCallback {
    private static final String TAG = "DeviceConnect";

    private DeviceService deviceService;

    private ArrayList<DeviceEntry> deviceEntries;
    private DeviceListAdaptor deviceListAdaptor;

    private DeviceInfo.DeviceType deviceType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Log.d(TAG, "onCreate()");

        Intent intent = getIntent();
        deviceType = (DeviceInfo.DeviceType)intent
                .getSerializableExtra(DeviceInfo.DeviceType.class.getSimpleName());
        setResult(Activity.RESULT_CANCELED, intent);

        setContentView(R.layout.activity_device_connect);
        setSupportActionBar(findViewById(R.id.toolbar_device_connect));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getResources().getString(
                    R.string.app_name_device_connect, deviceType == DeviceInfo.DeviceType.Coil ?
                            getResources().getString(R.string.menu_item_coil_connect) :
                            getResources().getString(R.string.menu_item_probe_connect)));
        }

        deviceEntries = new ArrayList<>();
        deviceListAdaptor = new DeviceListAdaptor(getApplicationContext(), deviceEntries);

        ListView deviceList = findViewById(R.id.listview_device);
        deviceList.setAdapter(deviceListAdaptor);
        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            DeviceEntry entry = deviceEntries.get(position);

            if (deviceService == null)
                return;

            if (deviceService.connectDevice(entry.getInfo().getAddress())) {
                DialogHelper.showProgressDialog(this,
                        getResources().getString(R.string.dialog_title_connect),
                        entry.getInfo().getDescription(),
                        getResources().getString(R.string.dialog_button_connect),
                        (dialogInterface, i) -> deviceService.disconnectDevice(
                                entry.getInfo().getAddress()));
            } else {
                DialogHelper.showDialog(this,
                        getResources().getString(R.string.dialog_title_connect_error),
                        entry.getInfo().getDescription());
            }
        });
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

        deviceEntries.clear();
        deviceListAdaptor.notifyDataSetChanged();

        setDeviceCallback(true);
        setDeviceScan(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Log.d(TAG, "onStop()");

        setDeviceScan(false);
        setDeviceCallback(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Log.d(TAG, "onResume()");

        registerReceiver(stateReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Log.d(TAG, "onPause()");

        unregisterReceiver(stateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_connect, menu);

        bindService(new Intent(this, DeviceService.class),
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
            setDeviceScan(true);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Log.i(TAG, "onServiceDisconnected(): " + name.getClassName());

        if (name.getClassName().equals(DeviceService.class.getName())) {
            deviceService = null;
        }
    }

    @Override
    public void onScanResult(DeviceInfo deviceInfo, int rssi) {
        boolean existed = false;

        for (DeviceEntry entry : deviceEntries) {
            if (entry.getInfo().getAddress().equalsIgnoreCase(deviceInfo.getAddress())) {
                existed = true;
                break;
            }
        }

        if (!existed) {
            Log.d(TAG, deviceInfo.getDescription() + ", RSSI: " + rssi);

            deviceEntries.add(new DeviceEntry(
                    deviceType == DeviceInfo.DeviceType.Coil ?
                            R.drawable.ic_baseline_grid_on_24 :
                            deviceType == DeviceInfo.DeviceType.Probe ?
                                    R.drawable.ic_baseline_flashlight_on_24 :
                                    R.drawable.ic_baseline_bluetooth_24,
                    deviceInfo));
            deviceListAdaptor.notifyDataSetChanged();
        }
    }

    @Override
    public void onDataReceived(DeviceInfo deviceInfo, Object data, Object extra) {
        if (deviceInfo.getType() != DeviceInfo.DeviceType.Probe)
            return;

        DeviceService.ImuData value = (DeviceService.ImuData)data;

        Log.d(TAG, deviceInfo.getDescription() +
                ", magnetic data: " + value.mx + " " + value.my + " " + value.mz +
                ", accelerator data: " + value.gx + " " + value.gy + " " + value.gz);
    }

    @Override
    public void onDeviceConnected(DeviceInfo deviceInfo) {
        Log.d(TAG, deviceInfo.getDescription() + " connected");

        setResult(Activity.RESULT_OK, getIntent());

        runOnUiThread(() -> {
            DialogHelper.dismissProgressDialog();
            onBackPressed();
        });
    }

    @Override
    public void onDeviceDisconnected(DeviceInfo deviceInfo) {
        Log.d(TAG, deviceInfo.getDescription() + " disconnected");

        runOnUiThread(() -> {
            DialogHelper.dismissProgressDialog();
            DialogHelper.showDialog(this,
                    getResources().getString(R.string.dialog_title_connect_error),
                    deviceInfo.getDescription());
        });
    }

    @Override
    public void onTimerExpired(DeviceInfo deviceInfo) {

    }

    @Override
    public void onBackPressed() {
        finish();
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
                        DialogHelper.dismissProgressDialog();
                        onBackPressed();
                        break;
                }
            }
        }
    };

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

    private void setDeviceScan(boolean enable) {
        if (deviceService == null)
            return;

        if (enable) {
            // Use `null` to disable device filter during scan, otherwise only our device
            // can be seen in scanning result
            if (!deviceService.startDeviceScan(deviceType))
                Log.e(TAG, "Failed to start device scanning");
        } else {
            if (!deviceService.stopDeviceScan())
                Log.e(TAG, "Failed to stop device scanning");
        }
    }
}