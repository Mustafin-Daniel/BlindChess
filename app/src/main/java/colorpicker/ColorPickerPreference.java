package colorpicker;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.example.caecuschess.activities.Preferences;

public class ColorPickerPreference
    extends
        Preference
    implements
        Preference.OnPreferenceClickListener,
        ColorPickerDialog.OnColorChangedListener,
        DialogInterface.OnDismissListener,
        Preferences.ConfigChangedListener {

    private View mView;
    private ColorPickerDialog mDialog;
    private int mDefaultValue = Color.BLACK;
    private int mValue = Color.BLACK;
    private float mDensity = 0;

    private static final String androidns = "http://schemas.android.com/apk/res/android";

    public ColorPickerPreference(Context context) {
        super(context);
        init(context, null);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }
    
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        onColorChanged(restoreValue ? getValue() : (Integer) defaultValue);
    }

    private void init(Context context, AttributeSet attrs) {
        mDensity = getContext().getResources().getDisplayMetrics().density;
        setOnPreferenceClickListener(this);
        if (attrs != null) {
            String defaultValue = attrs.getAttributeValue(androidns, "defaultValue");
            if (defaultValue.startsWith("#")) {
                try {
                    mDefaultValue = convertToColorInt(defaultValue);
                } catch (NumberFormatException e) {
                    Log.e("ColorPickerPreference", "Wrong color: " + defaultValue);
                    mDefaultValue = convertToColorInt("#FF000000");
                }
            } else {
                int resourceId = attrs.getAttributeResourceValue(androidns, "defaultValue", 0);
                if (resourceId != 0) {
                    mDefaultValue = context.getResources().getInteger(resourceId);
                }
            }
        }
        mValue = mDefaultValue;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mView = view;
        setPreviewColor();
    }

    private void setPreviewColor() {
        if (mView == null) return;
        ImageView iView = new ImageView(getContext());
        LinearLayout widgetFrameView = mView.findViewById(android.R.id.widget_frame);
        if (widgetFrameView == null) return;
        widgetFrameView.setVisibility(View.VISIBLE);
        widgetFrameView.setPadding(
            widgetFrameView.getPaddingLeft(),
            widgetFrameView.getPaddingTop(),
            (int)(mDensity * 8),
            widgetFrameView.getPaddingBottom()
        );
        // remove already create preview image
        int count = widgetFrameView.getChildCount();
        if (count > 0) {
            widgetFrameView.removeViews(0, count);
        }
        widgetFrameView.addView(iView);
        widgetFrameView.setMinimumWidth(0);
        iView.setBackgroundDrawable(new AlphaPatternDrawable((int)(5 * mDensity)));
        iView.setImageBitmap(getPreviewBitmap());
    }

    private Bitmap getPreviewBitmap() {
        int d = (int) (mDensity * 31); //30dip
        int color = getValue();
        Bitmap bm = Bitmap.createBitmap(d, d, Config.ARGB_8888);
        int w = bm.getWidth();
        int h = bm.getHeight();
        for (int i = 0; i < w; i++) {
            for (int j = i; j < h; j++) {
                int c = (i <= 1 || j <= 1 || i >= w-2 || j >= h-2) ? Color.GRAY : color;
                bm.setPixel(i, j, c);
                if (i != j) {
                    bm.setPixel(j, i, c);
                }
            }
        }

        return bm;
    }

    public int getValue() {
        try {
            if (isPersistent()) {
                String tmpValue = getPersistedString(mDefaultValue + "");
                if (tmpValue == null) {
                    mValue = mDefaultValue;
                } else {
                    try {
                        mValue = Color.parseColor(tmpValue);
                    } catch (IllegalArgumentException e) {
                        mValue = mDefaultValue;
                    } catch (StringIndexOutOfBoundsException e) {
                        mValue = mDefaultValue;
                    }
                }
            }
        } catch (ClassCastException e) {
            mValue = mDefaultValue;
        }

        return mValue;
    }

    @Override
    public void onColorChanged(int color) {
        if (isPersistent()) {
            persistString(convertToARGB(color));
        }
        mValue = color;
        setPreviewColor();
        try {
            OnPreferenceChangeListener listener = getOnPreferenceChangeListener();
            if (listener != null)
                listener.onPreferenceChange(this, color);
        } catch (NullPointerException ignore) {
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        showDialog();
        return false;
    }
    
    private void showDialog() {
        mDialog = new ColorPickerDialog(getContext(), getValue(), getTitle());
        mDialog.setOnColorChangedListener(this);
        mDialog.setOnDismissListener(this);
        addRemoveConfigChangedListener();
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
        addRemoveConfigChangedListener();
    }

    private void addRemoveConfigChangedListener() {
        Context context = getContext();
        if (context instanceof Preferences) {
            Preferences prefs = ((Preferences)context);
            prefs.addRemoveConfigChangedListener(this, mDialog != null);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mDialog != null)
            mDialog.reInitUI();
    }

    private static String convertToARGB(int color) {
        String alpha = Integer.toHexString(Color.alpha(color));
        String red = Integer.toHexString(Color.red(color));
        String green = Integer.toHexString(Color.green(color));
        String blue = Integer.toHexString(Color.blue(color));

        if (alpha.length() == 1) {
            alpha = "0" + alpha;
        }

        if (red.length() == 1) {
            red = "0" + red;
        }

        if (green.length() == 1) {
            green = "0" + green;
        }

        if (blue.length() == 1) {
            blue = "0" + blue;
        }

        return "#" + alpha + red + green + blue;
    }

    private static int convertToColorInt(String argb) throws NumberFormatException {
        if (argb.startsWith("#")) {
            argb = argb.replace("#", "");
        }

        int alpha = 0, red = 0, green = 0, blue = 0;

        if (argb.length() == 8) {
            alpha = Integer.parseInt(argb.substring(0, 2), 16);
            red = Integer.parseInt(argb.substring(2, 4), 16);
            green = Integer.parseInt(argb.substring(4, 6), 16);
            blue = Integer.parseInt(argb.substring(6, 8), 16);
        }
        else if (argb.length() == 6) {
            alpha = 255;
            red = Integer.parseInt(argb.substring(0, 2), 16);
            green = Integer.parseInt(argb.substring(2, 4), 16);
            blue = Integer.parseInt(argb.substring(4, 6), 16);
        }

        return Color.argb(alpha, red, green, blue);
    }
}
