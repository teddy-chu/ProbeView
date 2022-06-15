package com.ultrasoundprobe.probeview;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ultrasoundprobe.probeview.device.DeviceService;
import com.ultrasoundprobe.probeview.format.FormatHelper;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;

import java.text.NumberFormat;

public class NavigationInfoFragment extends Fragment {
    private TextView imuData, gpioData, coilLocation, probeLocation;
    private final NumberFormat imuFormat, locationFormat;

    private final boolean coilLocationVisible;

    private String gpioDataBuffer = null;
    private String coilLocationBuffer = null;
    private String probeLocationBuffer = null;

    public NavigationInfoFragment() {
        super();

        imuFormat = FormatHelper.imu();
        locationFormat = FormatHelper.location();
        
        coilLocationVisible = AppConfig.CoilObject3dViewVisible;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        imuData = null;
        gpioData = null;
        coilLocation = null;
        probeLocation = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_navigation_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imuData = view.findViewById(R.id.textview_imu_data);
        gpioData = view.findViewById(R.id.textview_gpio_data);
        coilLocation = view.findViewById(R.id.textview_coil_location);
        probeLocation = view.findViewById(R.id.textview_probe_location);

        if (!coilLocationVisible) {
            coilLocation.setText(getResources().getString(
                    R.string.textview_coil_location_as_position));
            probeLocation.setText(getResources().getString(
                    R.string.textview_probe_location_as_angle));
        }
    }

    public void updateImuData(DeviceService.ImuData value) {
        String text = "";

        if (imuData == null)
            return;
        else if (value != null)
            text = imuDataToString(value);

        imuData.setText(getResources().getString(
                R.string.textview_imu_data, text));
    }

    public void updateGpioData(DeviceService.GpioData value) {
        String text = "";

        if (gpioData == null)
            return;
        else if (value != null)
            text = gpioDataToString(value);

        if (text.equals(gpioDataBuffer))
            return;

        gpioDataBuffer = text;

        gpioData.setText(getResources().getString(
                R.string.textview_gpio_data, text));
    }

    public void updateCoilLocation(LocationData value, LocationData ratio) {
        String text = "";

        if (coilLocation == null)
            return;
        else if (value != null)
            text = locationToString(value, ratio);

        if (text.equals(coilLocationBuffer))
            return;

        coilLocationBuffer = text;

        if (!coilLocationVisible)
            return;

        coilLocation.setText(getResources().getString(
                R.string.textview_coil_location, text));
    }

    public void updateProbeLocation(LocationData value, LocationData ratio) {
        String text = "";

        if (probeLocation == null)
            return;
        else if (value != null)
            text = locationToString(value, ratio);

        if (text.equals(probeLocationBuffer))
            return;

        probeLocationBuffer = text;

        if (coilLocationVisible) {
            probeLocation.setText(getResources().getString(
                    R.string.textview_probe_location, text));
        } else {
            String posText = "";
            String angleText = "";

            if (value != null) {
                posText = locationToPositionString(value, ratio);
                angleText = locationToAngleString(value);
            }

            coilLocation.setText(getResources().getString(
                    R.string.textview_coil_location_as_position, posText));
            probeLocation.setText(getResources().getString(
                    R.string.textview_probe_location_as_angle, angleText));
        }
    }

    private String imuDataToString(DeviceService.ImuData value) {
        return imuFormat.format(value.mx) + "," +
                imuFormat.format(value.my) + "," +
                imuFormat.format(value.mz) + ":" +
                imuFormat.format(value.gx) + "," +
                imuFormat.format(value.gy) + "," +
                imuFormat.format(value.gz);
    }

    private String gpioDataToString(DeviceService.GpioData value) {
        return FormatHelper.gpio(value.gpio1) + "," +
                FormatHelper.gpio(value.gpio2) + "," +
                FormatHelper.gpio(value.gpio3);
    }

    private String locationToString(LocationData value, LocationData ratio) {
        // Scale position to absolution value in meter and show in millimeter
        return locationFormat.format(value.px / ratio.px * 1000.0) + "," +
                locationFormat.format(value.py / ratio.py * 1000.0) + "," +
                locationFormat.format(value.pz / ratio.pz * 1000.0) + "@" +
                locationFormat.format(value.ax) + "," +
                locationFormat.format(value.ay) + "," +
                locationFormat.format(value.az);
    }

    private String locationToPositionString(LocationData value, LocationData ratio) {
        // Scale position to absolution value in meter and show in millimeter
        return locationFormat.format(value.px / ratio.px * 1000.0) + "," +
                locationFormat.format(value.py / ratio.py * 1000.0) + "," +
                locationFormat.format(value.pz / ratio.pz * 1000.0);
    }

    private String locationToAngleString(LocationData value) {
        return locationFormat.format(value.ax) + "," +
                locationFormat.format(value.ay) + "," +
                locationFormat.format(value.az);
    }
}