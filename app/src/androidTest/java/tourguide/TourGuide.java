package tourguide;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import floatingactionbutton.FloatingActionButton;

import com.example.caecuschess.databinding.TooltipBinding;

import tourguide.FrameLayoutWithHole;
import tourguide.Overlay;
import tourguide.Pointer;
import tourguide.Sequence;
import tourguide.ToolTip;


public class TourGuide {
    /**
     * This describes the animation techniques
     */
    public enum Technique {
        Click, HorizontalLeft, HorizontalRight, VerticalUpward, VerticalDownward
    }

    /**
     * This describes the allowable motion, for example if you want the users to learn about clicking, but want to stop them from swiping, then use ClickOnly
     */
    public enum MotionType {
        AllowAll, ClickOnly, SwipeOnly
    }

    private Technique mTechnique;
    private View mHighlightedView;
    private Activity mActivity;
    private MotionType mMotionType;
    private FrameLayoutWithHole mFrameLayout;
    private TooltipBinding tooltipBinding;
    ToolTip mToolTip;
    Pointer mPointer;
    Overlay mOverlay;

    private tourguide.Sequence mSequence;

    /*************
     *
     * Public API
     *
     *************/

    /* Static builder */
    public static TourGuide init(Activity activity) {
        return new TourGuide(activity);
    }

    /* Constructor */
    public TourGuide(Activity activity) {
        mActivity = activity;
    }

    /**
     * Setter for the animation to be used
     *
     * @param technique Animation to be used
     * @return return TourGuide instance for chaining purpose
     */
    public TourGuide with(Technique technique) {
        mTechnique = technique;
        return this;
    }

    /**
     * Sets which motion type is motionType
     *
     * @param motionType
     * @return return TourGuide instance for chaining purpose
     */
    public TourGuide motionType(MotionType motionType) {
        mMotionType = motionType;
        return this;
    }

    /**
     * Sets the duration
     *
     * @param view the view in which the tutorial button will be placed on top of
     * @return return TourGuide instance for chaining purpose
     */
    public TourGuide playOn(View view) {
        mHighlightedView = view;
        setupView();
        return this;
    }

    /**
     * Sets the overlay
     *
     * @param overlay this overlay object should contain the attributes of the overlay, such as background color, animation, Style, etc
     * @return return TourGuide instance for chaining purpose
     */
    public TourGuide setOverlay(Overlay overlay) {
        mOverlay = overlay;
        return this;
    }

    /**
     * Set the toolTip
     *
     * @param toolTip this toolTip object should contain the attributes of the ToolTip, such as, the title text, and the description text, background color, etc
     * @return return TourGuide instance for chaining purpose
     */
    public TourGuide setToolTip(ToolTip toolTip) {
        mToolTip = toolTip;
        return this;
    }

    /**
     * Set the Pointer
     *
     * @param pointer this pointer object should contain the attributes of the Pointer, such as the pointer color, pointer gravity, etc, refer to @Link{pointer}
     * @return return TourGuide instance for chaining purpose
     */
    public TourGuide setPointer(Pointer pointer) {
        mPointer = pointer;
        return this;
    }

    /**
     * Clean up the tutorial that is added to the activity
     */
    public void cleanUp() {
        if (mFrameLayout != null)
            mFrameLayout.cleanUp();
        if (tooltipBinding != null) {
            ((ViewGroup) mActivity.getWindow().getDecorView()).removeView(getToolTip());
        }
    }

    public TourGuide playLater(View view) {
        mHighlightedView = view;
        return this;
    }

    /**************************
     * Sequence related method
     **************************/

    public TourGuide playInSequence(tourguide.Sequence sequence) {
        setSequence(sequence);
        next();
        return this;
    }

    public TourGuide setSequence(Sequence sequence) {
        mSequence = sequence;
        mSequence.setParentTourGuide(this);
        for (TourGuide tourGuide : sequence.mTourGuideArray) {
            if (tourGuide.mHighlightedView == null) {
                throw new NullPointerException("Please specify the view using 'playLater' method");
            }
        }
        return this;
    }

    public TourGuide next() {
        if (mFrameLayout != null) {
            cleanUp();
        }

        if (mSequence.mCurrentSequence < mSequence.mTourGuideArray.length) {
            setToolTip(mSequence.getToolTip());
            setPointer(mSequence.getPointer());
            setOverlay(mSequence.getOverlay());

            mHighlightedView = mSequence.getNextTourGuide().mHighlightedView;

            setupView();
            mSequence.mCurrentSequence++;
        }
        return this;
    }

    /**
     * @return FrameLayoutWithHole that is used as overlay
     */
    public FrameLayoutWithHole getOverlay() {
        return mFrameLayout;
    }

    /**
     * @return the ToolTip container View
     */
    public View getToolTip() {
        return tooltipBinding.getRoot();
    }

    /******
     *
     * Private methods
     *
     *******/
    //TODO: move into Pointer
    private int getXBasedOnGravity(int width) {
        int[] pos = new int[2];
        mHighlightedView.getLocationOnScreen(pos);
        int x = pos[0];
        if ((mPointer.mGravity & Gravity.RIGHT) == Gravity.RIGHT) {
            return x + mHighlightedView.getWidth() - width;
        } else if ((mPointer.mGravity & Gravity.LEFT) == Gravity.LEFT) {
            return x;
        } else { // this is center
            return x + mHighlightedView.getWidth() / 2 - width / 2;
        }
    }

    //TODO: move into Pointer
    private int getYBasedOnGravity(int height) {
        int[] pos = new int[2];
        mHighlightedView.getLocationInWindow(pos);
        int y = pos[1];
        if ((mPointer.mGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            return y + mHighlightedView.getHeight() - height;
        } else if ((mPointer.mGravity & Gravity.TOP) == Gravity.TOP) {
            return y;
        } else { // this is center
            return y + mHighlightedView.getHeight() / 2 - height / 2;
        }
    }

    private void setupView() {
//        TODO: throw exception if either mActivity, mDuration, mHighlightedView is null
        checking();
        final ViewTreeObserver viewTreeObserver = mHighlightedView.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // make sure this only run once
                mHighlightedView.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                /* Initialize a frame layout with a hole */
                mFrameLayout = new FrameLayoutWithHole(mActivity, mHighlightedView, mMotionType, mOverlay);
                /* handle click disable */
                handleDisableClicking(mFrameLayout);

                /* setup floating action button */
                if (mPointer != null) {
                    FloatingActionButton fab = setupAndAddFABToFrameLayout(mFrameLayout);
                    performAnimationOn(fab);
                }
                setupFrameLayout();
                /* setup tooltip view */
                setupToolTip();
            }
        });
    }

    private void checking() {
        // There is not check for tooltip because tooltip can be null, it means there no tooltip will be shown

    }

    private void handleDisableClicking(FrameLayoutWithHole frameLayoutWithHole) {
        // 1. if user provides an overlay listener, use that as 1st priority
        if (mOverlay != null && mOverlay.mOnClickListener != null) {
            frameLayoutWithHole.setClickable(true);
            frameLayoutWithHole.setOnClickListener(mOverlay.mOnClickListener);
        }
        // 2. if overlay listener is not provided, check if it's disabled
        else if (mOverlay != null && mOverlay.mDisableClick) {
            Log.w("tourguide", "Overlay's default OnClickListener is null, it will proceed to next tourguide when it is clicked");
            frameLayoutWithHole.setViewHole(mHighlightedView);
            frameLayoutWithHole.setSoundEffectsEnabled(false);
            // do nothing, disabled.
            frameLayoutWithHole.setOnClickListener(v -> {
            });
        }
    }

    private void setupToolTip() {
        final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        if (mToolTip != null) {
            /* inflate and get views */
            ViewGroup parent = (ViewGroup) mActivity.getWindow().getDecorView();
            tooltipBinding = TooltipBinding.inflate(mActivity.getLayoutInflater(),
                    null, false);

            /* set tooltip attributes */
            tooltipBinding.toolTipContainer.setBackgroundColor(mToolTip.mBackgroundColor);
            if (mToolTip.mTitle == null) {
                tooltipBinding.title.setVisibility(View.GONE);
            } else {
                tooltipBinding.title.setText(mToolTip.mTitle);
            }
            if (mToolTip.mDescription == null) {
                tooltipBinding.description.setVisibility(View.GONE);
            } else {
                tooltipBinding.description.setText(mToolTip.mDescription);
            }


            getToolTip().startAnimation(mToolTip.mEnterAnimation);

            /* add setShadow if it's turned on */
            if (mToolTip.mShadow) {
                getToolTip().setBackgroundDrawable(mActivity.getResources().getDrawable(com.example.caecuschess.R.drawable.drop_shadow));
            }

            /* position and size calculation */
            int[] pos = new int[2];
            mHighlightedView.getLocationOnScreen(pos);
            int targetViewX = pos[0];
            final int targetViewY = pos[1];

            // get measured size of tooltip
            getToolTip().measure(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            int toolTipMeasuredWidth = getToolTip().getMeasuredWidth();
            int toolTipMeasuredHeight = getToolTip().getMeasuredHeight();

            Point resultPoint = new Point(); // this holds the final position of tooltip
            float density = mActivity.getResources().getDisplayMetrics().density;
            final float adjustment = 10 * density; //adjustment is that little overlapping area of tooltip and targeted button

            // calculate x position, based on gravity, tooltipMeasuredWidth, parent max width, x position of target view, adjustment
            if (toolTipMeasuredWidth > parent.getWidth()) {
                resultPoint.x = getXForTooTip(mToolTip.mGravity, parent.getWidth(), targetViewX, adjustment);
            } else {
                resultPoint.x = getXForTooTip(mToolTip.mGravity, toolTipMeasuredWidth, targetViewX, adjustment);
            }

            resultPoint.y = getYForTooTip(mToolTip.mGravity, toolTipMeasuredHeight, targetViewY, adjustment);

            // add view to parent
//            ((ViewGroup) mActivity.getWindow().getDecorView().findViewById(android.R.id.content)).addView(tooltipBinding, layoutParams);
            parent.addView(getToolTip(), layoutParams);

            // 1. width < screen check
            if (toolTipMeasuredWidth > parent.getWidth()) {
                getToolTip().getLayoutParams().width = parent.getWidth();
                toolTipMeasuredWidth = parent.getWidth();
            }
            // 2. x left boundary check
            if (resultPoint.x < 0) {
                getToolTip().getLayoutParams().width = toolTipMeasuredWidth + resultPoint.x; //since point.x is negative, use plus
                resultPoint.x = 0;
            }
            // 3. x right boundary check
            int tempRightX = resultPoint.x + toolTipMeasuredWidth;
            if (tempRightX > parent.getWidth()) {
                getToolTip().getLayoutParams().width = parent.getWidth() - resultPoint.x; //since point.x is negative, use plus
            }

            // pass toolTip onClickListener into toolTipViewGroup
            if (mToolTip.mOnClickListener != null) {
                getToolTip().setOnClickListener(mToolTip.mOnClickListener);
            }

            // TODO: no boundary check for height yet, this is a unlikely case though
            // height boundary can be fixed by user changing the gravity to the other size, since there are plenty of space vertically compared to horizontally

            // this needs an viewTreeObserver, that's because TextView measurement of it's vertical height is not accurate (didn't take into account of multiple lines yet) before it's rendered
            // re-calculate height again once it's rendered
            final ViewTreeObserver viewTreeObserver = getToolTip().getViewTreeObserver();
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getToolTip().getViewTreeObserver().removeGlobalOnLayoutListener(this);// make sure this only run once

                    int fixedY;
                    int toolTipHeightAfterLayouted = getToolTip().getHeight();
                    fixedY = getYForTooTip(mToolTip.mGravity, toolTipHeightAfterLayouted, targetViewY, adjustment);
                    layoutParams.setMargins((int) getToolTip().getX(), fixedY, 0, 0);
                }
            });

            // set the position using setMargins on the left and top
            layoutParams.setMargins(resultPoint.x, resultPoint.y, 0, 0);
        }

    }

    private int getXForTooTip(int gravity, int toolTipMeasuredWidth, int targetViewX, float adjustment) {
        int x;
        if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
            x = targetViewX - toolTipMeasuredWidth + (int) adjustment;
        } else if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) {
            x = targetViewX + mHighlightedView.getWidth() - (int) adjustment;
        } else {
            x = targetViewX + mHighlightedView.getWidth() / 2 - toolTipMeasuredWidth / 2;
        }
        return x;
    }

    private int getYForTooTip(int gravity, int toolTipMeasuredHeight, int targetViewY, float adjustment) {
        int y;
        if ((gravity & Gravity.TOP) == Gravity.TOP) {

            if (((gravity & Gravity.LEFT) == Gravity.LEFT) || ((gravity & Gravity.RIGHT) == Gravity.RIGHT)) {
                y = targetViewY - toolTipMeasuredHeight + (int) adjustment;
            } else {
                y = targetViewY - toolTipMeasuredHeight - (int) adjustment;
            }
        } else if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            if (((gravity & Gravity.LEFT) == Gravity.LEFT) || ((gravity & Gravity.RIGHT) == Gravity.RIGHT)) {
                y = targetViewY + mHighlightedView.getHeight() - (int) adjustment;
            } else {
                y = targetViewY + mHighlightedView.getHeight() + (int) adjustment;
            }
        } else { // this is center
            if (((gravity & Gravity.LEFT) == Gravity.LEFT) || ((gravity & Gravity.RIGHT) == Gravity.RIGHT)) {
                y = targetViewY + mHighlightedView.getHeight() / 2 - (int) adjustment;
            } else {
                y = targetViewY + mHighlightedView.getHeight() / 2 + (int) adjustment;
            }
        }
        return y;
    }

    private FloatingActionButton setupAndAddFABToFrameLayout(final FrameLayoutWithHole frameLayoutWithHole) {
        // invisFab is invisible, and it's only used for getting the width and height
        final FloatingActionButton invisFab = new FloatingActionButton(mActivity);
        invisFab.setSize(FloatingActionButton.SIZE_MINI);
        invisFab.setVisibility(View.INVISIBLE);
        ((ViewGroup) mActivity.getWindow().getDecorView()).addView(invisFab);

        // fab is the real fab that is going to be added
        final FloatingActionButton fab = new FloatingActionButton(mActivity);
        fab.setBackgroundColor(Color.BLUE);
        fab.setSize(FloatingActionButton.SIZE_MINI);
        fab.setColorNormal(mPointer.mColor);
        fab.setStrokeVisible(false);
        fab.setClickable(false);

        // When invisFab is layouted, it's width and height can be used to calculate the correct position of fab
        final ViewTreeObserver viewTreeObserver = invisFab.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // make sure this only run once
                invisFab.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                frameLayoutWithHole.addView(fab, params);

                // measure size of image to be placed
                params.setMargins(getXBasedOnGravity(invisFab.getWidth()), getYBasedOnGravity(invisFab.getHeight()), 0, 0);
            }
        });


        return fab;
    }

    private void setupFrameLayout() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        ViewGroup contentArea = mActivity.getWindow().getDecorView().findViewById(android.R.id.content);
        int[] pos = new int[2];
        contentArea.getLocationOnScreen(pos);
        // frameLayoutWithHole's coordinates are calculated taking full screen height into account
        // but we're adding it to the content area only, so we need to offset it to the same Y value of contentArea

        layoutParams.setMargins(0, -pos[1], 0, 0);
        contentArea.addView(mFrameLayout, layoutParams);
    }

    private void performAnimationOn(final View view) {

        if (mTechnique != null && mTechnique == Technique.HorizontalLeft) {

            final AnimatorSet animatorSet = new AnimatorSet();
            final AnimatorSet animatorSet2 = new AnimatorSet();
            Animator.AnimatorListener lis1 = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationX(0);
                    animatorSet2.start();
                }
            };
            Animator.AnimatorListener lis2 = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationX(0);
                    animatorSet.start();
                }
            };

            long fadeInDuration = 800;
            long scaleDownDuration = 800;
            long goLeftXDuration = 2000;
            long fadeOutDuration = goLeftXDuration;
            float translationX = getScreenWidth() / 2;

            final ValueAnimator fadeInAnim = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            fadeInAnim.setDuration(fadeInDuration);
            final ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.85f);
            scaleDownX.setDuration(scaleDownDuration);
            final ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.85f);
            scaleDownY.setDuration(scaleDownDuration);
            final ObjectAnimator goLeftX = ObjectAnimator.ofFloat(view, "translationX", -translationX);
            goLeftX.setDuration(goLeftXDuration);
            final ValueAnimator fadeOutAnim = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
            fadeOutAnim.setDuration(fadeOutDuration);

            final ValueAnimator fadeInAnim2 = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            fadeInAnim2.setDuration(fadeInDuration);
            final ObjectAnimator scaleDownX2 = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.85f);
            scaleDownX2.setDuration(scaleDownDuration);
            final ObjectAnimator scaleDownY2 = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.85f);
            scaleDownY2.setDuration(scaleDownDuration);
            final ObjectAnimator goLeftX2 = ObjectAnimator.ofFloat(view, "translationX", -translationX);
            goLeftX2.setDuration(goLeftXDuration);
            final ValueAnimator fadeOutAnim2 = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
            fadeOutAnim2.setDuration(fadeOutDuration);

            animatorSet.play(fadeInAnim);
            animatorSet.play(scaleDownX).with(scaleDownY).after(fadeInAnim);
            animatorSet.play(goLeftX).with(fadeOutAnim).after(scaleDownY);

            animatorSet2.play(fadeInAnim2);
            animatorSet2.play(scaleDownX2).with(scaleDownY2).after(fadeInAnim2);
            animatorSet2.play(goLeftX2).with(fadeOutAnim2).after(scaleDownY2);

            animatorSet.addListener(lis1);
            animatorSet2.addListener(lis2);
            animatorSet.start();

            /* these animatorSets are kept track in FrameLayout, so that they can be cleaned up when FrameLayout is detached from window */
            mFrameLayout.addAnimatorSet(animatorSet);
            mFrameLayout.addAnimatorSet(animatorSet2);
        } else if (mTechnique != null && mTechnique == Technique.HorizontalRight) {

        } else if (mTechnique != null && mTechnique == Technique.VerticalUpward) {

        } else if (mTechnique != null && mTechnique == Technique.VerticalDownward) {

        } else { // do click for default case
            final AnimatorSet animatorSet = new AnimatorSet();
            final AnimatorSet animatorSet2 = new AnimatorSet();
            Animator.AnimatorListener lis1 = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationX(0);
                    animatorSet2.start();
                }
            };
            Animator.AnimatorListener lis2 = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationX(0);
                    animatorSet.start();
                }
            };

            long fadeInDuration = 800;
            long scaleDownDuration = 800;
            long fadeOutDuration = 800;
            long delay = 1000;

            final ValueAnimator delayAnim = ObjectAnimator.ofFloat(view, "translationX", 0);
            delayAnim.setDuration(delay);
            final ValueAnimator fadeInAnim = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            fadeInAnim.setDuration(fadeInDuration);
            final ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.85f);
            scaleDownX.setDuration(scaleDownDuration);
            final ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.85f);
            scaleDownY.setDuration(scaleDownDuration);
            final ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.85f, 1f);
            scaleUpX.setDuration(scaleDownDuration);
            final ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.85f, 1f);
            scaleUpY.setDuration(scaleDownDuration);
            final ValueAnimator fadeOutAnim = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
            fadeOutAnim.setDuration(fadeOutDuration);

            final ValueAnimator delayAnim2 = ObjectAnimator.ofFloat(view, "translationX", 0);
            delayAnim2.setDuration(delay);
            final ValueAnimator fadeInAnim2 = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            fadeInAnim2.setDuration(fadeInDuration);
            final ObjectAnimator scaleDownX2 = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.85f);
            scaleDownX2.setDuration(scaleDownDuration);
            final ObjectAnimator scaleDownY2 = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.85f);
            scaleDownY2.setDuration(scaleDownDuration);
            final ObjectAnimator scaleUpX2 = ObjectAnimator.ofFloat(view, "scaleX", 0.85f, 1f);
            scaleUpX2.setDuration(scaleDownDuration);
            final ObjectAnimator scaleUpY2 = ObjectAnimator.ofFloat(view, "scaleY", 0.85f, 1f);
            scaleUpY2.setDuration(scaleDownDuration);
            final ValueAnimator fadeOutAnim2 = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
            fadeOutAnim2.setDuration(fadeOutDuration);
            view.setAlpha(0);
            animatorSet.setStartDelay(mToolTip != null ? mToolTip.mEnterAnimation.getDuration() : 0);
            animatorSet.play(fadeInAnim);
            animatorSet.play(scaleDownX).with(scaleDownY).after(fadeInAnim);
            animatorSet.play(scaleUpX).with(scaleUpY).with(fadeOutAnim).after(scaleDownY);
            animatorSet.play(delayAnim).after(scaleUpY);

            animatorSet2.play(fadeInAnim2);
            animatorSet2.play(scaleDownX2).with(scaleDownY2).after(fadeInAnim2);
            animatorSet2.play(scaleUpX2).with(scaleUpY2).with(fadeOutAnim2).after(scaleDownY2);
            animatorSet2.play(delayAnim2).after(scaleUpY2);

            animatorSet.addListener(lis1);
            animatorSet2.addListener(lis2);
            animatorSet.start();

            /* these animatorSets are kept track in FrameLayout, so that they can be cleaned up when FrameLayout is detached from window */
            mFrameLayout.addAnimatorSet(animatorSet);
            mFrameLayout.addAnimatorSet(animatorSet2);
        }
    }

    private int getScreenWidth() {
        if (mActivity != null) {
            return mActivity.getResources().getDisplayMetrics().widthPixels;
        } else {
            return 0;
        }
    }

}
