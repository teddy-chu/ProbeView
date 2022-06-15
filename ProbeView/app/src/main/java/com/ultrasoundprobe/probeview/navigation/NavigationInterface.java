package com.ultrasoundprobe.probeview.navigation;

import com.ultrasoundprobe.probeview.navigation.model.ObjectModel;

public interface NavigationInterface {
    void on3dViewCreated();
    void on3dViewDrawn();
    void on3dModelSurfaceTouched(ObjectModel source, ObjectModel target);
    void on3dModelSurfaceUntouched(ObjectModel source, ObjectModel target);
    void on3dModelSurfaceTouchChecked(ObjectModel source, ObjectModel target);
}
