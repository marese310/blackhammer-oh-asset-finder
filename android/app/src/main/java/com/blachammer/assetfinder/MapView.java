package com.blachammer.assetfinder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class MapView extends View {
    interface Listener {
        void onMapTapped(float x, float y);
        void onMarkerSelected(MapMarker marker);
    }

    private static final float MAX_ZOOM = 8f;

    private final AppTheme theme;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final float density;

    private Bitmap bitmap;
    private List<MapMarker> markers = new ArrayList<>();
    private long selectedMarkerId = -1;
    private boolean placing;
    private float zoom = 1f;
    private float panX;
    private float panY;
    private Listener listener;
    private String emptyMessage = "Import a map image to get started.";

    MapView(Context context, AppTheme theme) {
        super(context);
        this.theme = theme;
        density = context.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(android.graphics.Color.WHITE);
        ringPaint.setStrokeWidth(2 * density);
        textPaint.setColor(theme.foreground);
        textPaint.setTextSize(13 * density * theme.fontScale());
        shadowPaint.setColor(android.graphics.Color.BLACK);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(3 * density);
        shadowPaint.setTextSize(textPaint.getTextSize());
        setBackgroundColor(theme.surface());

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        zoomAround(zoom * detector.getScaleFactor(),
                                detector.getFocusX(), detector.getFocusY());
                        return true;
                    }
                });
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent first, MotionEvent second,
                                            float dx, float dy) {
                        if (bitmap != null && !scaleDetector.isInProgress() && !placing) {
                            panX -= dx;
                            panY -= dy;
                            clampPan();
                            invalidate();
                        }
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent event) {
                        handleTap(event.getX(), event.getY());
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        if (bitmap != null && !placing) {
                            zoomAround(zoom < 2f ? 3f : 1f, event.getX(), event.getY());
                        }
                        return true;
                    }
                });
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setEmptyMessage(String message) {
        emptyMessage = message;
        invalidate();
    }

    void setBitmap(Bitmap newBitmap) {
        bitmap = newBitmap;
        fit();
    }

    boolean hasMap() {
        return bitmap != null;
    }

    void setMarkers(List<MapMarker> visibleMarkers) {
        markers = new ArrayList<>(visibleMarkers);
        invalidate();
    }

    void setSelectedMarkerId(long id) {
        selectedMarkerId = id;
        invalidate();
    }

    void setPlacing(boolean value) {
        placing = value;
        invalidate();
    }

    void fit() {
        zoom = 1f;
        panX = 0f;
        panY = 0f;
        invalidate();
    }

    void zoomBy(float factor) {
        zoomAround(zoom * factor, getWidth() / 2f, getHeight() / 2f);
    }

    private RectF imageRect() {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) {
            return new RectF();
        }
        float scale = Math.min(
                getWidth() / (float) bitmap.getWidth(),
                getHeight() / (float) bitmap.getHeight()
        );
        float width = bitmap.getWidth() * scale * zoom;
        float height = bitmap.getHeight() * scale * zoom;
        float left = (getWidth() - width) / 2f + panX;
        float top = (getHeight() - height) / 2f + panY;
        return new RectF(left, top, left + width, top + height);
    }

    private void zoomAround(float requestedZoom, float focusX, float focusY) {
        if (bitmap == null) {
            return;
        }
        RectF old = imageRect();
        if (old.width() <= 0 || old.height() <= 0) {
            return;
        }
        float relX = (focusX - old.left) / old.width();
        float relY = (focusY - old.top) / old.height();
        zoom = Math.max(1f, Math.min(MAX_ZOOM, requestedZoom));

        panX = 0f;
        panY = 0f;
        RectF centered = imageRect();
        panX = focusX - relX * centered.width() - centered.left;
        panY = focusY - relY * centered.height() - centered.top;
        clampPan();
        invalidate();
    }

    private void clampPan() {
        if (zoom <= 1f) {
            panX = 0f;
            panY = 0f;
            return;
        }
        RectF rect = imageRect();
        float slackX = Math.max(0f, (rect.width() - getWidth()) / 2f) + getWidth() * 0.25f;
        float slackY = Math.max(0f, (rect.height() - getHeight()) / 2f) + getHeight() * 0.25f;
        panX = Math.max(-slackX, Math.min(slackX, panX));
        panY = Math.max(-slackY, Math.min(slackY, panY));
    }

    private void handleTap(float x, float y) {
        if (bitmap == null) {
            return;
        }
        RectF rect = imageRect();
        if (placing) {
            if (rect.contains(x, y) && listener != null) {
                listener.onMapTapped((x - rect.left) / rect.width(), (y - rect.top) / rect.height());
            }
            return;
        }
        MapMarker closest = null;
        float closestDistance = (26 * density) * (26 * density);
        for (MapMarker marker : markers) {
            float dx = x - (rect.left + marker.x * rect.width());
            float dy = y - (rect.top + marker.y * rect.height());
            float distance = dx * dx + dy * dy;
            if (distance <= closestDistance) {
                closestDistance = distance;
                closest = marker;
            }
        }
        if (closest != null && listener != null) {
            listener.onMarkerSelected(closest);
        }
    }

    void centerOn(MapMarker marker) {
        if (bitmap == null) {
            return;
        }
        RectF rect = imageRect();
        float markerX = rect.left + marker.x * rect.width();
        float markerY = rect.top + marker.y * rect.height();
        panX += getWidth() / 2f - markerX;
        panY += getHeight() / 2f - markerY;
        clampPan();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clampPan();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(emptyMessage, getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }
        RectF rect = imageRect();
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint);

        for (MapMarker marker : markers) {
            boolean selected = marker.id == selectedMarkerId;
            float radius = (selected ? 11 : 8) * density;
            float cx = rect.left + marker.x * rect.width();
            float cy = rect.top + marker.y * rect.height();
            pinPaint.setColor(selected ? theme.accent : android.graphics.Color.argb(220, 210, 45, 45));
            canvas.drawCircle(cx, cy, radius, pinPaint);
            canvas.drawCircle(cx, cy, radius, ringPaint);
            if (selected) {
                float tx = cx + radius + 4 * density;
                float ty = cy + radius;
                canvas.drawText(marker.name, tx, ty, shadowPaint);
                canvas.drawText(marker.name, tx, ty, textPaint);
            }
        }

        if (placing) {
            String hint = "Tap the map to place the marker";
            textPaint.setTextAlign(Paint.Align.CENTER);
            shadowPaint.setTextAlign(Paint.Align.CENTER);
            float x = getWidth() / 2f;
            float y = 24 * density;
            canvas.drawText(hint, x, y, shadowPaint);
            canvas.drawText(hint, x, y, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            shadowPaint.setTextAlign(Paint.Align.LEFT);
        }
    }
}
