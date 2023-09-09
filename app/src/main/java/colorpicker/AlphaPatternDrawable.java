package colorpicker;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * This drawable that draws a simple white and gray chessboard pattern.
 * It's pattern you will often see as a background behind a
 * partly transparent image in many applications.
 */
public class AlphaPatternDrawable extends Drawable {

    private int mRectangleSize;

    private Paint mPaint = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintGray = new Paint();

    private int numRectanglesHorizontal;
    private int numRectanglesVertical;

    /**
     * Bitmap in which the pattern will be cached.
     */
    private Bitmap        mBitmap;

    public AlphaPatternDrawable(int rectangleSize) {
        mRectangleSize = rectangleSize;
        mPaintWhite.setColor(0xffffffff);
        mPaintGray.setColor(0xffcbcbcb);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mBitmap != null)
            canvas.drawBitmap(mBitmap, null, getBounds(), mPaint);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    @Override
    public void setAlpha(int alpha) {
        throw new UnsupportedOperationException("Alpha is not supported by this drawable.");
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        throw new UnsupportedOperationException("ColorFilter is not supported by this drawable.");
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        numRectanglesHorizontal = bounds.width() / mRectangleSize;
        numRectanglesVertical = bounds.height() / mRectangleSize;
        generatePatternBitmap();
    }

    /**
     * This will generate a bitmap with the pattern
     * as big as the rectangle we were allow to draw on.
     * We do this to cache the bitmap so we don't need to
     * recreate it each time draw() is called since it
     * takes a few milliseconds.
     */
    private void generatePatternBitmap() {
        if (getBounds().width() <= 0 || getBounds().height() <= 0)
            return;

        mBitmap = Bitmap.createBitmap(getBounds().width(), getBounds().height(), Config.ARGB_8888);
        Canvas canvas = new Canvas(mBitmap);

        Rect r = new Rect();
        for (int i = 0; i <= numRectanglesVertical; i++) {
            for (int j = 0; j <= numRectanglesHorizontal; j++) {
                boolean isWhite = (i + j) % 2 == 0;
                r.top = i * mRectangleSize;
                r.left = j * mRectangleSize;
                r.bottom = r.top + mRectangleSize;
                r.right = r.left + mRectangleSize;

                canvas.drawRect(r, isWhite ? mPaintWhite : mPaintGray);
            }
        }
    }
}
