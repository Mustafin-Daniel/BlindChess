package com.example.caecuschess;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.PictureDrawable;

import com.caverock.androidsvg.SVG;

/**
 * Like PictureDrawable but scales the picture according to current drawing bounds.
 */
public class SVGPictureDrawable extends PictureDrawable {

    private final int iWidth;
    private final int iHeight;

    private Rect cachedBounds;
    private Bitmap cachedBitmap;

    public SVGPictureDrawable(SVG svg) {
        super(svg.renderToPicture());
        int w = (int)svg.getDocumentWidth();
        int h = (int)svg.getDocumentHeight();
        if (w == -1 || h == -1) {
            RectF box = svg.getDocumentViewBox();
            if (box != null) {
                w = (int)box.width();
                h = (int)box.height();
            }
        }
        iWidth = w;
        iHeight = h;
    }

    @Override
    public int getIntrinsicWidth() {
        return iWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return iHeight;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (!b.equals(cachedBounds)) {
            Bitmap bm = Bitmap.createBitmap(b.right-b.left, b.bottom-b.top, Bitmap.Config.ARGB_8888);
            Canvas bmCanvas = new Canvas(bm);
            bmCanvas.drawPicture(getPicture(), b);
            cachedBitmap = bm;
            cachedBounds = b;
        }
        canvas.drawBitmap(cachedBitmap, null, b, null);
    }
}
