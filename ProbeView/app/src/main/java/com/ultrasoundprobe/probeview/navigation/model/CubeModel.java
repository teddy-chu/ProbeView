package com.ultrasoundprobe.probeview.navigation.model;

public class CubeModel extends ObjectModel {
    public CubeModel() {
        // Number of faces for a cube object model
        NUM_OF_FACES = 6;
        // Number of index size per face for a cube object model
        INDEX_SIZE_PER_FACE = 6;

        // Vertices of the shape for a cube object model
        // TODO: remove redundant vertices and remap indices to this list
        vertices = new float[] {
                // Front face
                -1.0f, -1.0f,  1.0f,    // Left-bottom-front
                1.0f, -1.0f,  1.0f,     // Right-bottom-front
                -1.0f,  1.0f,  1.0f,    // Left-top-front
                -1.0f,  1.0f,  1.0f,    // Left-top-front
                1.0f, -1.0f,  1.0f,     // Right-bottom-front
                1.0f,  1.0f,  1.0f,     // Right-top-front
                // Back face
                1.0f, -1.0f, -1.0f,     // Right-bottom-back
                1.0f,  1.0f, -1.0f,     // Right-top-back
                -1.0f, -1.0f, -1.0f,    // Left-bottom-back
                -1.0f, -1.0f, -1.0f,    // Left-bottom-back
                -1.0f,  1.0f, -1.0f,    // Left-top-back
                1.0f,  1.0f, -1.0f,     // Right-top-back
                // Left face
                -1.0f, -1.0f, -1.0f,    // Left-bottom-back
                -1.0f, -1.0f,  1.0f,    // Left-bottom-front
                -1.0f,  1.0f,  1.0f,    // Left-top-front
                -1.0f,  1.0f,  1.0f,    // Left-top-front
                -1.0f,  1.0f, -1.0f,    // Left-top-back
                -1.0f, -1.0f, -1.0f,    // Left-bottom-back
                // Right face
                1.0f, -1.0f,  1.0f,     // Right-bottom-front
                1.0f, -1.0f, -1.0f,     // Right-bottom-back
                1.0f,  1.0f, -1.0f,     // Right-top-back
                1.0f,  1.0f, -1.0f,     // Right-top-back
                1.0f,  1.0f,  1.0f,     // Right-top-front
                1.0f, -1.0f,  1.0f,     // Right-bottom-front
                // Top face
                -1.0f,  1.0f,  1.0f,    // Left-top-front
                1.0f,  1.0f,  1.0f,     // Right-top-front
                1.0f,  1.0f, -1.0f,     // Right-top-back
                1.0f,  1.0f, -1.0f,     // Right-top-back
                -1.0f,  1.0f, -1.0f,    // Left-top-back
                -1.0f,  1.0f,  1.0f,    // Left-top-front
                // Bottom face
                -1.0f, -1.0f, -1.0f,    // Left-bottom-back
                1.0f, -1.0f, -1.0f,     // Right-bottom-back
                1.0f, -1.0f,  1.0f,     // Right-bottom-front
                1.0f, -1.0f,  1.0f,     // Right-bottom-front
                -1.0f, -1.0f,  1.0f,    // Left-bottom-front
                -1.0f, -1.0f, -1.0f     // Left-bottom-back
        };

        // Index order of vertices of the shape for a cube object model
        indices = new short[] {
                0, 1, 2, 3, 4, 5,
                6, 7, 8, 9, 10, 11,
                12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29,
                30, 31, 32, 33, 34, 35
        };

        // Colors of the faces for a cube object model
        colors = new float[][] {
                // Orange
                { 1.0f, 0.5f, 0.0f, 1.0f },
                // Violet
                { 1.0f, 0.0f, 1.0f, 1.0f },
                // Green
                { 0.0f, 1.0f, 0.0f, 1.0f },
                // Blue
                { 0.0f, 0.0f, 1.0f, 1.0f },
                // Red
                { 1.0f, 0.0f, 0.0f, 1.0f },
                // Yellow
                { 1.0f, 1.0f, 0.0f, 1.0f }
        };

        // Vertices of the shape for touch detection with other object models
        detectionSurfaceVertices = new float[][] {
                { -1.0f, -1.0f, -1.0f,
                        -1.0f, -1.0f,  1.0f,
                        -1.0f,  1.0f,  1.0f,
                        -1.0f,  1.0f, -1.0f }
        };

        detectionSurfaceFaceIndices = new int[] { 2 };

        detectionSurfaceFaceActiveColors = new float[][] {
                { 1.0f, 1.0f, 1.0f, 1.0f }
        };

        initModel();
    }
}