package tourguide;

import android.animation.AnimatorSet;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.FillType;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;

import androidx.core.view.MotionEventCompat;

import java.util.ArrayList;

import tourguide.Overlay;
import tourguide.TourGuide;

/**
 * TODO: document your custom view class.
 */
public class FrameLayoutWithHole extends FrameLayout {
    private TextPaint mTextPaint;
    private tourguide.TourGuide.MotionType mMotionType;

    private Path mPath;
    private Paint mPaint;
    private View mViewHole; // This is the targeted view to be highlighted, where the hole should be placed
    private int mRadius;
    private int [] mPos;
    private float mDensity;
    private tourguide.Overlay mOverlay;

    private ArrayList<AnimatorSet> mAnimatorSetArrayList;

    public void setViewHole(View viewHole) {
        this.mViewHole = viewHole;
        enforceMotionType();
    }
    public void addAnimatorSet(AnimatorSet animatorSet){
        if (mAnimatorSetArrayList==null){
            mAnimatorSetArrayList = new ArrayList<>();
        }
        mAnimatorSetArrayList.add(animatorSet);
    }
    private void enforceMotionType(){
        Log.d("tourguide", "enforceMotionType 1");
        if (mViewHole!=null) {Log.d("tourguide","enforceMotionType 2");
            if (mMotionType!=null && mMotionType == tourguide.TourGuide.MotionType.ClickOnly) {
                Log.d("tourguide","enforceMotionType 3");
                Log.d("tourguide","only Clicking");
                mViewHole.setOnTouchListener((view, motionEvent) -> {
                    mViewHole.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                });
            } else if (mMotionType!=null && mMotionType == tourguide.TourGuide.MotionType.SwipeOnly) {
                Log.d("tourguide","enforceMotionType 4");
                Log.d("tourguide","only Swiping");
                mViewHole.setClickable(false);
            }
        }
    }

    public FrameLayoutWithHole(Activity context, View view) {
        this(context, view, tourguide.TourGuide.MotionType.AllowAll);
    }
    public FrameLayoutWithHole(Activity context, View view, tourguide.TourGuide.MotionType motionType) {
        this(context, view, motionType, new tourguide.Overlay());
    }

    public FrameLayoutWithHole(Activity context, View view, TourGuide.MotionType motionType, tourguide.Overlay overlay) {
        super(context);
        mViewHole = view;
        init(null, 0);
        enforceMotionType();
        mOverlay = overlay;

        int [] pos = new int[2];
        mViewHole.getLocationOnScreen(pos);
        mPos = pos;

        mDensity = context.getResources().getDisplayMetrics().density;
        int padding = (int)(20 * mDensity);

        if (mViewHole.getHeight() > mViewHole.getWidth()) {
            mRadius = mViewHole.getHeight()/2 + padding;
        } else {
            mRadius = mViewHole.getWidth()/2 + padding;
        }
        mMotionType = motionType;
    }
    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
//        final TypedArray a = getContext().obtainStyledAttributes(
//                attrs, FrameLayoutWithHole, defStyle, 0);
//
//
//        a.recycle();
        setWillNotDraw(false);
        // Set up a default TextPaint object
        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.LEFT);

        mPath = new Path();

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
    }

    private boolean mCleanUpLock = false;
    protected void cleanUp(){
        if (getParent() != null) {
            if (mOverlay!=null && mOverlay.mExitAnimation!=null) {
                performOverlayExitAnimation();
            } else {
                ((ViewGroup) this.getParent()).removeView(this);
            }
        }
    }
    private void performOverlayExitAnimation(){
        if (!mCleanUpLock) {
            final FrameLayout _pointerToFrameLayout = this;
            mCleanUpLock = true;
            Log.d("tourguide","Overlay exit animation listener is overwritten...");
            mOverlay.mExitAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    ((ViewGroup) _pointerToFrameLayout.getParent()).removeView(_pointerToFrameLayout);
                }
            });
            this.startAnimation(mOverlay.mExitAnimation);
        }
    }
    /* comment this whole method to cause a memory leak */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAnimatorSetArrayList != null && mAnimatorSetArrayList.size() > 0){
            for(int i=0;i<mAnimatorSetArrayList.size();i++){
                mAnimatorSetArrayList.get(i).removeAllListeners();
                mAnimatorSetArrayList.get(i).end();
            }
        }
    }

    /** Show an event in the LogCat view, for debugging */
    private void dumpEvent(MotionEvent event) {
        String names[] = { "DOWN" , "UP" , "MOVE" , "CANCEL" , "OUTSIDE" ,
                "POINTER_DOWN" , "POINTER_UP" , "7?" , "8?" , "9?" };
        StringBuilder sb = new StringBuilder();
        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        sb.append("event ACTION_" ).append(names[actionCode]);
        if (actionCode == MotionEvent.ACTION_POINTER_DOWN
                || actionCode == MotionEvent.ACTION_POINTER_UP) {
            sb.append("(pid " ).append(
                    action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
            sb.append(")" );
        }
        sb.append("[" );
        for (int i = 0; i < event.getPointerCount(); i++) {
            sb.append("#" ).append(i);
            sb.append("(pid " ).append(event.getPointerId(i));
            sb.append(")=" ).append((int) event.getX(i));
            sb.append("," ).append((int) event.getY(i));
            if (i + 1 < event.getPointerCount())
                sb.append(";" );
        }
        sb.append("]" );
        Log.d("tourguide", sb.toString());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        //first check if the location button should handle the touch event
        dumpEvent(ev);
        int action = MotionEventCompat.getActionMasked(ev);
        if(mViewHole != null) {
            int[] pos = new int[2];
            mViewHole.getLocationOnScreen(pos);
            Log.d("tourguide", "[dispatchTouchEvent] mViewHole.getHeight(): "+mViewHole.getHeight());
            Log.d("tourguide", "[dispatchTouchEvent] mViewHole.getWidth(): "+mViewHole.getWidth());

            Log.d("tourguide", "[dispatchTouchEvent] Touch X(): "+ev.getRawX());
            Log.d("tourguide", "[dispatchTouchEvent] Touch Y(): "+ev.getRawY());

//            Log.d("tourguide", "[dispatchTouchEvent] X of image: "+pos[0]);
//            Log.d("tourguide", "[dispatchTouchEvent] Y of image: "+pos[1]);

            Log.d("tourguide", "[dispatchTouchEvent] X lower bound: "+ pos[0]);
            Log.d("tourguide", "[dispatchTouchEvent] X higher bound: "+(pos[0] +mViewHole.getWidth()));

            Log.d("tourguide", "[dispatchTouchEvent] Y lower bound: "+ pos[1]);
            Log.d("tourguide", "[dispatchTouchEvent] Y higher bound: "+(pos[1] +mViewHole.getHeight()));

            if(ev.getRawY() >= pos[1] && ev.getRawY() <= (pos[1] + mViewHole.getHeight()) && ev.getRawX() >= pos[0] && ev.getRawX() <= (pos[0] + mViewHole.getWidth())) { //location button event
                Log.d("tourguide","to the BOTTOM!");
                Log.d("tourguide",""+ev.getAction());

//                switch(action) {
//                    case (MotionEvent.ACTION_DOWN) :
//                        Log.d("tourguide","Action was DOWN");
//                        return false;
//                    case (MotionEvent.ACTION_MOVE) :
//                        Log.d("tourguide","Action was MOVE");
//                        return true;
//                    case (MotionEvent.ACTION_UP) :
//                        Log.d("tourguide","Action was UP");
////                        ev.setAction(MotionEvent.ACTION_DOWN|MotionEvent.ACTION_UP);
////                        return super.dispatchTouchEvent(ev);
//                        return false;
//                    case (MotionEvent.ACTION_CANCEL) :
//                        Log.d("tourguide","Action was CANCEL");
//                        return true;
//                    case (MotionEvent.ACTION_OUTSIDE) :
//                        Log.d("tourguide","Movement occurred outside bounds " +
//                                "of current screen element");
//                        return true;
//                    default :
//                        return super.dispatchTouchEvent(ev);
//                }
//                return mViewHole.onTouchEvent(ev);

                return false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mOverlay != null) {
            mPath.rewind();
            mPath.addRect(0, 0, canvas.getWidth(), canvas.getHeight(), Path.Direction.CW);
            if (mOverlay.mStyle == Overlay.Style.Rectangle) {
                int padding = (int) (10 * mDensity);
                mPath.addRect(mPos[0] - padding, mPos[1] - padding,
                              mPos[0] + mViewHole.getWidth() + padding,
                              mPos[1] + mViewHole.getHeight() + padding,
                              Path.Direction.CCW);
            } else {
                mPath.addCircle(mPos[0] + mViewHole.getWidth() / 2,
                                mPos[1] + mViewHole.getHeight() / 2,
                                mRadius, Path.Direction.CCW);
            }
            mPath.setFillType(FillType.WINDING);
            mPaint.setColor(mOverlay.mBackgroundColor);
            canvas.drawPath(mPath, mPaint);
        }
    }
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mOverlay!=null && mOverlay.mEnterAnimation!=null) {
            this.startAnimation(mOverlay.mEnterAnimation);
        }
    }
    /**
     *
     * Convenient method to obtain screen width in pixel
     *
     * @param activity
     * @return screen width in pixel
     */
    public int getScreenWidth(Activity activity){
        return activity.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     *
     * Convenient method to obtain screen height in pixel
     *
     * @param activity
     * @return screen width in pixel
     */
    public int getScreenHeight(Activity activity){
        return activity.getResources().getDisplayMetrics().heightPixels;
    }
}
