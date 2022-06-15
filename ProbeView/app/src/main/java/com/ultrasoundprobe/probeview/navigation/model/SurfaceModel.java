package com.ultrasoundprobe.probeview.navigation.model;

public class SurfaceModel extends ObjectModel {
    public SurfaceModel() {
        // Number of faces for a surface object model
        NUM_OF_FACES = 1;
        // Number of index size per face for a surface object model
        INDEX_SIZE_PER_FACE = 6;

        // Vertices of the shape for a surface object model
        vertices = new float[] {
                0.0f, -1.0f, -1.0f,
                0.0f, -1.0f,  1.0f,
                0.0f,  1.0f,  1.0f,
                0.0f,  1.0f, -1.0f
        };

        // Index order of vertices of the shape for a surface object model
        indices = new short[] {
                0, 1, 2, 2, 3, 0
        };

        // Colors of the faces for a surface object model
        colors = new float[][] {
                // Grey
                { 0.5f, 0.5f, 0.5f, 1.0f }
        };

        // Vertices of the shape for touch detection with other object models
        detectionSurfaceVertices = new float[][] {
                { 0.0f, -1.0f, -1.0f,
                        0.0f, -1.0f,  1.0f,
                        0.0f,  1.0f,  1.0f,
                        0.0f,  1.0f, -1.0f }
        };

        detectionSurfaceFaceIndices = new int[] { 0 };

        detectionSurfaceFaceActiveColors = new float[][] {
                { 1.0f, 1.0f, 1.0f, 1.0f }
        };

        detectionSurfaceFaceInactiveColors = new float[][] {
                { 0.0f, 1.0f, 0.0f, 1.0f }
        };

        initModel();
    }
}
