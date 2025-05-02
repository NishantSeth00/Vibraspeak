package com.example.vibraspeak;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

public class ScannerOverlay extends View {

    private Paint outerPaint;
    private Paint borderPaint;
    private Rect frameRect;

    public ScannerOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        outerPaint = new Paint();
        outerPaint.setColor(0x88000000); // Translucent black

        borderPaint = new Paint();
        borderPaint.setColor(0xFFFFFFFF); // White border
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        int frameSize = Math.min(width, height) * 3 / 4;
        int left = (width - frameSize) / 2;
        int top = (height - frameSize) / 2;
        int right = left + frameSize;
        int bottom = top + frameSize;

        frameRect = new Rect(left, top, right, bottom);

        // Darken outside the frame with proper clipping method for the API level
        canvas.save();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutRect(frameRect);
        } else {
            canvas.clipRect(frameRect, Region.Op.DIFFERENCE);
        }
        canvas.drawRect(0, 0, width, height, outerPaint);
        canvas.restore();

        // Draw the white square frame
        canvas.drawRect(frameRect, borderPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int frameSize = Math.min(w, h) * 3 / 4;
        int left = (w - frameSize) / 2;
        int top = (h - frameSize) / 2;
        frameRect = new Rect(left, top, left + frameSize, top + frameSize);
    }


    public Rect getFrameRect() {
        return frameRect;
    }
}
