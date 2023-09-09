package colorpicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * This class draws a panel which will be filled with a color which can be set.
 * It can be used to show the currently selected color which you will get from
 * the {@link ColorPickerView}.
 */
public class ColorPickerPanelView extends View {
    /** The width in pixels of the border surrounding the color panel. */
    private final static float BORDER_WIDTH_PX = 1;

    private float mDensity = 1f;

    private int   mColor = 0xff000000;

    private Paint mBorderPaint = new Paint();
    private Paint mColorPaint = new Paint();

    private RectF mDrawingRect;
    private RectF mColorRect;

    private AlphaPatternDrawable mAlphaPattern;


    public ColorPickerPanelView(Context context) {
        this(context, null);
    }

    public ColorPickerPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mDensity = getContext().getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (BORDER_WIDTH_PX > 0) {
            mBorderPaint.setColor(0xff6E6E6E);
            canvas.drawRect(mDrawingRect, mBorderPaint);
        }

        if (mAlphaPattern != null)
            mAlphaPattern.draw(canvas);

        mColorPaint.setColor(mColor);
        canvas.drawRect(mColorRect, mColorPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        width = chooseSize(widthMode, width, Math.round(120f * mDensity));
        height = chooseSize(heightMode, height, 0);
        setMeasuredDimension(width, height);
    }

    int chooseSize(int mode, int size, int preferred) {
        if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY)
            return size;
        return preferred; // MeasureSpec.UNSPECIFIED
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mDrawingRect = new RectF();
        mDrawingRect.left   =     getPaddingLeft();
        mDrawingRect.right  = w - getPaddingRight();
        mDrawingRect.top    =     getPaddingTop();
        mDrawingRect.bottom = h - getPaddingBottom();

        setUpColorRect();
    }

    private void setUpColorRect() {
        final RectF dRect = mDrawingRect;

        float left   = dRect.left   + BORDER_WIDTH_PX;
        float top    = dRect.top    + BORDER_WIDTH_PX;
        float bottom = dRect.bottom - BORDER_WIDTH_PX;
        float right  = dRect.right  - BORDER_WIDTH_PX;

        mColorRect = new RectF(left,top, right, bottom);

        mAlphaPattern = new AlphaPatternDrawable((int)(5 * mDensity));
        mAlphaPattern.setBounds(Math.round(mColorRect.left),
                                Math.round(mColorRect.top),
                                Math.round(mColorRect.right),
                                Math.round(mColorRect.bottom));
    }

    /**
     * Set the color that should be shown by this view.
     */
    public void setColor(int color) {
        mColor = color;
        invalidate();
    }

    /**
     * Get the color currently shown by this view.
     */
    public int getColor() {
        return mColor;
    }
}
