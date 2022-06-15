package com.ultrasoundprobe.probeview.navigation.model;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class ObjectModel extends BaseModel {
    // Byte size of a float type
    private static final int FLOAT_TYPE_BYTES = 4;
    // Byte size of a short type
    private static final int SHORT_TYPE_BYTES = 2;

    // Number of faces for an object model
    protected int NUM_OF_FACES = 0;
    // Number of index size per face for an object model
    protected int INDEX_SIZE_PER_FACE = 0;

    // Vertices of the shape for an object model
    protected float[] vertices;

    // Index order of vertices of the shape for an object model
    protected short[] indices;

    // Colors of the faces for an object model if touch detection with other object models
    // is disabled
    protected float[][] colors;

    // Buffered colors of the faces for an object model
    private float[][] bufferColors;

    // Buffered colors of the faces for an object model when touched with other object models
    protected float[][] bufferTouchColors;

    // Vertices of the shape for touch detection with other object models
    protected float[][] detectionSurfaceVertices;

    // Values of the faces for an object model by which color of this face will be changed
    // if this object model is touched with other object models
    protected int[] detectionSurfaceFaceIndices;

    // Colors of the faces for an object model if this object model is touched
    // with other object models
    protected float[][] detectionSurfaceFaceActiveColors;

    // Colors of the faces for an object model if this object model is untouched
    // with other object models
    protected float[][] detectionSurfaceFaceInactiveColors;

    // Enable or disable touch detection with other object models
    protected boolean surfaceTouchDetectionEnabled = true;

    private FloatBuffer vertexBuffer;
    private ShortBuffer indexBuffer;
    private int program;

    public int getId() {
        return id;
    }

    public void initModel() {
        final String vertexShaderCode =
                "uniform mat4 uMVPMatrix;" +
                        "attribute vec4 vPosition;" +
                        "void main() {" +
                        "  gl_Position = uMVPMatrix * vPosition;" +
                        "}";

        final String fragmentShaderCode =
                "precision mediump float;" +
                        "uniform vec4 vColor;" +
                        "void main() {" +
                        "  gl_FragColor = vColor;" +
                        "}";

        // Initialize vertex byte buffer for shape coordinates
        vertexBuffer = ByteBuffer.allocateDirect(
                vertices.length * FLOAT_TYPE_BYTES).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        // Initialize index order of vertices byte buffer for shape coordinates
        indexBuffer = ByteBuffer.allocateDirect(
                indices.length * SHORT_TYPE_BYTES).order(
                ByteOrder.nativeOrder()).asShortBuffer();
        indexBuffer.put(indices).position(0);

        // Prepare shaders for OpenGL program
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        // Create empty OpenGL program
        program = GLES20.glCreateProgram();
        // Add the vertex shader to the program
        GLES20.glAttachShader(program, vertexShader);
        // Add the fragment shader to the program
        GLES20.glAttachShader(program, fragmentShader);
        // Create OpenGL program executables
        GLES20.glLinkProgram(program);

        bufferColors = new float[colors.length][];
    }

    @Override
    public void draw(float[] mvpMatrix) {
        int modelViewProjectionHandle;
        int positionHandle;
        int colorHandle;

        GLES20.glUseProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDINATES_PER_VERTEX, GLES20.GL_FLOAT,
                false, COORDINATES_PER_VERTEX * FLOAT_TYPE_BYTES, vertexBuffer);

        modelViewProjectionHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(modelViewProjectionHandle, 1, false, mvpMatrix, 0);

        colorHandle = GLES20.glGetUniformLocation(program, "vColor");

        // Render all the faces
        for (int i = 0; i < NUM_OF_FACES; i++) {
            float[] color = null;

            bufferColors[i] = colors[i];

            // Determine whether current face is touched by another model and use
            // a preset color as touched indication of current face if so
            if (surfaceTouchResults.length == detectionSurfaceFaceIndices.length &&
                    surfaceTouchResults.length == detectionSurfaceFaceActiveColors.length) {
                for (int j = 0; j < surfaceTouchResults.length && color == null; j++) {
                    for (int k = 0; k < surfaceTouchResults[j].length &&
                            detectionSurfaceFaceIndices[j] == i; k++) {
                        if (surfaceTouchResults[j][k].isTouched) {
                            // Load a preset color as touched indication
                            color = detectionSurfaceFaceActiveColors[j];

                            // Store the preset color for touched indication
                            if (bufferTouchColors != null)
                                bufferTouchColors[i] = color;
                            else
                                bufferColors[i] = color;

                            break;
                        } else if (getSurfaceTouchDetection() &&
                                detectionSurfaceFaceInactiveColors != null &&
                                surfaceTouchResults.length == detectionSurfaceFaceInactiveColors.length) {
                            bufferColors[i] = detectionSurfaceFaceInactiveColors[j];
                        }
                    }
                }
            }

            // Set the color for each face
            GLES20.glUniform4fv(colorHandle, 1,
                    getSurfaceTouchDetection() ?
                            (bufferTouchColors == null || bufferTouchColors[i] == null ?
                                    bufferColors[i] :           // Set active color
                                    bufferTouchColors[i]) :     // Set touched color
                            colors[i],                          // Set inactive color
                    0);

            // Update position of the index buffer
            indexBuffer.position(i * INDEX_SIZE_PER_FACE);

            // Draw each face by using the index buffer
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, INDEX_SIZE_PER_FACE,
                    GLES20.GL_UNSIGNED_SHORT, indexBuffer);
        }

        GLES20.glDisableVertexAttribArray(positionHandle);
    }

    @Override
    public void initSurfaceTouchDetection(float[] modelMatrix) {
        super.surfaceNormalVectors = transformSurfaceNormalVectors(modelMatrix);
        super.surfaceVertices = transformSurfaceVertices(modelMatrix);
    }

    @Override
    public void setSurfaceTouchDetection(boolean enable) {
        surfaceTouchDetectionEnabled = enable;
    }

    @Override
    public boolean getSurfaceTouchDetection() {
        return surfaceTouchDetectionEnabled;
    }

    @Override
    public void setSurfaceTouchBufferColor(boolean enable) {
        bufferTouchColors = (enable ? new float[colors.length][] : null);
    }

    private float[][] transformSurfaceNormalVectors(float[] modelMatrix) {
        float[][] normalVectors;

        if (detectionSurfaceVertices.length > 0)
            normalVectors = new float[detectionSurfaceVertices.length][COORDINATES_PER_VERTEX];
        else
            normalVectors = new float[0][];

        for (int i = 0; i < detectionSurfaceVertices.length; i++) {
            float[][] surfaceVertices = new float[3][COORDINATES_PER_VERTEX + 1];
            float[][] surfaceVectors = new float[2][COORDINATES_PER_VERTEX];

            if (detectionSurfaceVertices[i].length < surfaceVertices.length *
                    COORDINATES_PER_VERTEX)
                return new float[0][];

            // Transform model coordinates to world coordinates
            Matrix.multiplyMV(surfaceVertices[0], 0, modelMatrix, 0 , new float[] {
                    detectionSurfaceVertices[i][0],
                    detectionSurfaceVertices[i][1],
                    detectionSurfaceVertices[i][2],
                    1.0f
            }, 0);
            Matrix.multiplyMV(surfaceVertices[1], 0, modelMatrix, 0 , new float[] {
                    detectionSurfaceVertices[i][3],
                    detectionSurfaceVertices[i][4],
                    detectionSurfaceVertices[i][5],
                    1.0f
            }, 0);
            Matrix.multiplyMV(surfaceVertices[2], 0, modelMatrix, 0 , new float[] {
                    detectionSurfaceVertices[i][6],
                    detectionSurfaceVertices[i][7],
                    detectionSurfaceVertices[i][8],
                    1.0f
            }, 0);

            // Get surface vectors from vertices and the vectors are parallel to the surface
            surfaceVectors[0] = subtractMatrix3x1(
                    new float[] {
                            surfaceVertices[0][0],
                            surfaceVertices[0][1],
                            surfaceVertices[0][2] },
                    new float[] {
                            surfaceVertices[1][0],
                            surfaceVertices[1][1],
                            surfaceVertices[1][2] });
            surfaceVectors[1] = subtractMatrix3x1(
                    new float[] {
                            surfaceVertices[2][0],
                            surfaceVertices[2][1],
                            surfaceVertices[2][2] },
                    new float[] {
                            surfaceVertices[1][0],
                            surfaceVertices[1][1],
                            surfaceVertices[1][2] });

            // Get a normal vector to the surface by calculating a cross product of the two
            // surface vectors, the normal vector then represents a vector perpendicular to
            // the surface
            normalVectors[i] = multiplyMatrix3x1(surfaceVectors[1], surfaceVectors[0]);
        }

        return normalVectors;
    }

    private float[][] transformSurfaceVertices(float[] modelMatrix) {
        float[][] vertices;

        if (detectionSurfaceVertices.length > 0)
            vertices = new float[detectionSurfaceVertices.length]
                    [detectionSurfaceVertices[0].length];
        else
            vertices = new float[0][];

        for (int i = 0; i < detectionSurfaceVertices.length; i++) {
            for (int j = 0; j <= (detectionSurfaceVertices[i].length - COORDINATES_PER_VERTEX);
                 j += COORDINATES_PER_VERTEX) {
                float[] surfaceVertices = new float[COORDINATES_PER_VERTEX + 1];

                // Transform model coordinates to world coordinates
                Matrix.multiplyMV(surfaceVertices, 0, modelMatrix, 0 , new float[] {
                        detectionSurfaceVertices[i][j],
                        detectionSurfaceVertices[i][j + 1],
                        detectionSurfaceVertices[i][j + 2],
                        1.0f
                }, 0);

                // Get vertices of the surface
                vertices[i][j] = surfaceVertices[0];
                vertices[i][j + 1] = surfaceVertices[1];
                vertices[i][j + 2] = surfaceVertices[2];
            }
        }

        return vertices;
    }
}
