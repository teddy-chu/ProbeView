package com.ultrasoundprobe.probeview;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ultrasoundprobe.probeview.navigation.NavigationInterface;
import com.ultrasoundprobe.probeview.navigation.NavigationView;
import com.ultrasoundprobe.probeview.navigation.drawing.ModelViewDrawing;
import com.ultrasoundprobe.probeview.navigation.drawing.SurfaceInterface;
import com.ultrasoundprobe.probeview.navigation.location.LocationData;
import com.ultrasoundprobe.probeview.navigation.model.ObjectModel;

public class NavigationViewFragment extends Fragment implements
        NavigationInterface {
    private static final String TAG = "NavigationViewFragment";

    private final SurfaceInterface surfaceInterface;
    private NavigationInterface navigationInterface;
    private NavigationView navigationView;

    public NavigationViewFragment() {
        super();
        surfaceInterface = new ModelViewDrawing(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        navigationView.releaseSurface();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        navigationView = new NavigationView(this.getActivity(), surfaceInterface);
        return navigationView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void on3dViewCreated() {
        // Initialize default location of all models
        if (!surfaceInterface.setCoilLocation(new LocationData(-3, 0, 0,
                0, 0, 0, 1, 1, 1)))
            Log.e(TAG, "Failed to set default coil location");
        if (!surfaceInterface.setProbeLocation(new LocationData(3, 0, 0,
                0, 0, 0, 1, 1, 1)))
            Log.e(TAG, "Failed to set default probe location");

        if (navigationInterface != null) {
            navigationInterface.on3dViewCreated();
        }
    }

    @Override
    public void on3dViewDrawn() {
        if (navigationInterface != null) {
            navigationInterface.on3dViewDrawn();
        }
    }

    @Override
    public void on3dModelSurfaceTouched(ObjectModel source, ObjectModel target) {
        if (navigationInterface != null) {
            navigationInterface.on3dModelSurfaceTouched(source, target);
        }
    }

    @Override
    public void on3dModelSurfaceUntouched(ObjectModel source, ObjectModel target) {
        if (navigationInterface != null) {
            navigationInterface.on3dModelSurfaceUntouched(source, target);
        }
    }

    @Override
    public void on3dModelSurfaceTouchChecked(ObjectModel source, ObjectModel target) {
        if (navigationInterface != null) {
            navigationInterface.on3dModelSurfaceTouchChecked(source, target);
        }
    }

    public void setNavigationInterface(NavigationInterface navigationInterface) {
        this.navigationInterface = navigationInterface;
    }
}