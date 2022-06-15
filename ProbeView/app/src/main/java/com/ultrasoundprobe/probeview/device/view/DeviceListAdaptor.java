package com.ultrasoundprobe.probeview.device.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ultrasoundprobe.probeview.R;

import java.util.ArrayList;

public class DeviceListAdaptor extends BaseAdapter {
    private final ArrayList<DeviceEntry> deviceEntries;
    private final LayoutInflater layoutInflater;
    private final Context context;

    public DeviceListAdaptor(Context context, ArrayList<DeviceEntry> deviceEntries) {
        layoutInflater = LayoutInflater.from(context);
        this.context = context;
        this.deviceEntries = deviceEntries;
    }

    @Override
    public int getCount() {
        return deviceEntries.size();
    }

    @Override
    public Object getItem(int position) {
        return deviceEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.listview_ble_device_entry,
                    parent, false);
        }

        ImageView type = convertView.findViewById(R.id.imageview_device_type);
        TextView name = convertView.findViewById(R.id.textview_device_name);
        TextView address = convertView.findViewById(R.id.textview_device_address);
        Button connect = convertView.findViewById(R.id.button_device_connect);
        DeviceEntry entry = (DeviceEntry)getItem(position);

        // Show image of device type
        type.setImageResource(entry.getType());
        type.setColorFilter(ContextCompat.getColor(context,
                R.color.cardview_dark_background),
                android.graphics.PorterDuff.Mode.SRC_IN);
        // type.setBackgroundResource(android.R.color.black);
        type.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // Show device name
        name.setText(entry.getInfo().getName());

        // Show device address
        address.setText(entry.getInfo().getAddress());

        // Let this event be handled in parent's onItemClick()
        connect.setOnClickListener(view -> ((ListView)parent)
                .performItemClick(view, position, getItemId(position)));

        return convertView;
    }
}
