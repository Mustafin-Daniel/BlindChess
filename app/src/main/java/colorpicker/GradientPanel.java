package colorpicker;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

abstract class GradientPanel {
    private final static float BORDER_WIDTH_PX = 1;

    protected final RectF rect;
    protected final AHSVColor color;
    protected final float density;
    private final Drawable background;

    private Paint borderPaint = new Paint();
    protected Paint gradientPaint = new Paint();
    protected Paint trackerPaint = new Paint();

    /** Constructor. */
    GradientPanel(RectF rect, AHSVColor color, float density, Drawable background) {
        this.rect = rect;
        this.color = color;
        this.density = density;
        this.background = background;
        borderPaint.setColor(0xff6E6E6E);

        trackerPaint.setColor(0xff1c1c1c);
        trackerPaint.setStyle(Paint.Style.STROKE);
        trackerPaint.setStrokeWidth(2f * density);
        trackerPaint.setAntiAlias(true);
    }

    boolean contains(Point point) {
        return rect != null && rect.contains(point.x, point.y);
    }

    /** Update color from point. */
    abstract void updateColor(Point point);

    void draw(Canvas canvas) {
        if (rect == null)
            return;

        canvas.drawRect(rect.left   - BORDER_WIDTH_PX,
                        rect.top    - BORDER_WIDTH_PX,
                        rect.right  + BORDER_WIDTH_PX,
                        rect.bottom + BORDER_WIDTH_PX,
                        borderPaint);

        if (background != null)
            background.draw(canvas);

        setGradientPaint();
        canvas.drawRect(rect, gradientPaint);

        drawTracker(canvas);
    }

    /** Set gradientPaint properties. */
    abstract protected void setGradientPaint();

    /** Draw "current color" tracker marker. */
    abstract protected void drawTracker(Canvas canvas);

    protected void drawRectangleTracker(Canvas canvas, Point p, boolean horizontal) {
        float size = 2f * density;
        RectF r = new RectF(rect);
        r.inset(-size, -size);
        if (horizontal) {
            r.left   = p.x - size;
            r.right  = p.x + size;
        } else {
            r.top    = p.y - size;
            r.bottom = p.y + size;
        }
        canvas.drawRoundRect(r, 2, 2, trackerPaint);
    }
}
