package com.ultrasoundprobe.probeview.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.appcompat.widget.AppCompatImageView;

public class ScanImageView extends AppCompatImageView implements
        View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {
    private static final String TAG = "ScanImageView";

    private static final float MOTION_SCALE_MAX = 6.0f;
    private static final float MOTION_SCALE_MIN = 0.5f;
    private static final float MOTION_SCALE_DEFAULT = 3.0f;

    // Use manual scale for image
    private static final ScaleType IMAGE_SCALE_TYPE = ScaleType.MATRIX;
    // Auto scale image to fit its size and place the image at center
    // private static final ScaleType IMAGE_SCALE_TYPE = ScaleType.FIT_CENTER;

    private ScaleGestureDetector scaleGestureDetector;

    private float motionScale = MOTION_SCALE_DEFAULT;
    private int imageWidth, imageHeight;

    public ScanImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public ScanImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);

        if (getScaleType() == ScaleType.MATRIX) {
            transformImage(true, motionScale);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        scaleGestureDetector.onTouchEvent(motionEvent);
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        // TODO: Support image movement by touch motion
        if (getScaleType() == ScaleType.MATRIX) {
            motionScale *= scaleGestureDetector.getScaleFactor();
            transformImage(false, motionScale);
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

    }

    private void transformImage(boolean autoScale, float scaleFactor) {
        Drawable d = getDrawable();

        if (d == null)
            return;

        Matrix matrix = new Matrix();
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int sourceWidth = d.getIntrinsicWidth();
        int sourceHeight = d.getIntrinsicHeight();
        float offsetX = (viewWidth - sourceWidth) / 2f;
        float offsetY = (viewHeight - sourceHeight) / 2f;
        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;

        if (autoScale && (sourceWidth != imageWidth || sourceHeight != imageHeight)) {
            // Get a full view ratio
            scaleFactor = Math.min((float)viewWidth / (float)sourceWidth,
                    (float)viewHeight / (float)sourceHeight);

            // Reserve a few border margins
            scaleFactor -= 0.2f;

            imageWidth = sourceWidth;
            imageHeight = sourceHeight;
        }

        scaleFactor = Math.max(MOTION_SCALE_MIN, Math.min(scaleFactor, MOTION_SCALE_MAX));

        if (scaleFactor != motionScale)
            motionScale = scaleFactor;

        matrix.setScale(scaleFactor, scaleFactor, centerX, centerY);
        matrix.preTranslate(offsetX, offsetY);

        setImageMatrix(matrix);

        // Log.d(TAG, "Scale: " + scaleFactor);
    }

    private void initView(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, this);

        imageWidth = 0;
        imageHeight = 0;

        setOnTouchListener(this);
        setScaleType(IMAGE_SCALE_TYPE);
    }
}
