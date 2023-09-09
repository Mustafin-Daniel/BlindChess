package com.example.caecuschess.activities.util;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.example.caecuschess.R;
import com.example.caecuschess.SVGPictureDrawable;

public class FileBrowseUtil {

    public static String getPickAction(boolean pickDirectory) {
        return pickDirectory ? "org.openintents.action.PICK_DIRECTORY"
                             : "org.openintents.action.PICK_FILE";
    }

    public static boolean hasBrowser(PackageManager pMan, boolean pickDirectory) {
        Intent browser = new Intent(getPickAction(pickDirectory));
        return browser.resolveActivity(pMan) != null;
    }

    public static void setBrowseImage(Resources r, ImageView button, boolean visible) {
        button.setVisibility(visible ? View.VISIBLE : View.GONE);

        try {
            SVG svg = SVG.getFromResource(r, R.raw.open_file);
            button.setBackgroundDrawable(new SVGPictureDrawable(svg));
        } catch (SVGParseException ignore) {
        }

        try {
            SVG touched = SVG.getFromResource(r, R.raw.touch);
            StateListDrawable sld = new StateListDrawable();
            sld.addState(new int[]{android.R.attr.state_pressed}, new SVGPictureDrawable(touched));
            button.setImageDrawable(sld);
        } catch (SVGParseException ignore) {
        }

        int bWidth  = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                                           36, r.getDisplayMetrics()));
        int bHeight = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                                           32, r.getDisplayMetrics()));
        ViewGroup.LayoutParams lp = button.getLayoutParams();
        lp.width = bWidth;
        lp.height = bHeight;
        button.setLayoutParams(lp);
        button.setPadding(0,0,0,0);
        button.setScaleType(ImageView.ScaleType.FIT_XY);
    }
}
