package damjay.floating.projects.utils;

import android.graphics.Bitmap;

public class ImageScaler {
    private static final float SCALE_CHANGE = 0.1f;

    private float defaultMinScale = SCALE_CHANGE;
    private float scale = 1;

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getScale() {
        return scale;
    }

    public void increaseScale() {
        scale += SCALE_CHANGE;
    }

    public void decreaseScale() {
        if (scale - SCALE_CHANGE >= defaultMinScale)
            scale -= SCALE_CHANGE;
    }

    public void setDefaultMinScale(float defaultMinScale) {
        this.defaultMinScale = defaultMinScale;
    }

    public float getDefaultMinScale() {
        return defaultMinScale;
    }

    public Bitmap getScaled(Bitmap bitmap) {
        if (scale < defaultMinScale) scale = defaultMinScale;
        return bitmap.createScaledBitmap(bitmap, (int) ((float) bitmap.getWidth() * scale), (int) ((float) bitmap.getHeight() * scale), true);
    }
}
