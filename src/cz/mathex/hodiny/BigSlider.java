package cz.mathex.hodiny;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * Velký posuvník přes celou dlaždici (jako ovládání jasu na iPhonu): táhne se kdekoliv na ploše,
 * vyplněná část je barevná, popisek vlevo a hodnota vpravo jsou přímo uvnitř.
 * gradient != null → celá dlaždice je přechod (teplota bílé) a pozici ukazuje svislá značka.
 */
final class BigSlider extends View {
    interface Listener {
        void onChange(int value, boolean end);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float radius;

    private final int min, max;
    private int value;
    private final String label;
    private String valueText = "";
    private final int fillColor;
    private final int[] gradient;
    private Listener listener;

    BigSlider(Context c, String label, int min, int max, int fillColor, int[] gradient) {
        super(c);
        this.label = label;
        this.min = min;
        this.max = max;
        this.fillColor = fillColor;
        this.gradient = gradient;
        this.value = min;
        radius = Ui.px(c, .04f);
        Ui.loadFonts(c);
        text.setTypeface(Ui.medium());
        text.setTextSize(Ui.base(c) * .042f);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setValue(int v, String shown) {
        value = Math.max(min, Math.min(max, v));
        valueText = shown;
        invalidate();
    }

    void setValueText(String shown) {
        valueText = shown;
        invalidate();
    }

    int getValue() {
        return value;
    }

    private float fraction() {
        return max > min ? (float) (value - min) / (max - min) : 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        if (gradient != null) {
            paint.setShader(new LinearGradient(0, 0, w, 0, gradient, null, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float x = fraction() * w;
        c.save();
        c.clipPath(clip);

        int onFill; // barva textu přes vyplněnou část
        if (gradient != null) {
            c.drawRect(rect, paint);
            onFill = Ui.ON_AMBER;
        } else {
            Paint bg = new Paint();
            bg.setColor(Ui.TILE);
            c.drawRect(rect, bg);
            Paint f = new Paint();
            f.setColor(fillColor);
            c.drawRect(0, 0, x, h, f);
            onFill = Ui.ON_AMBER;
        }

        // popisek a hodnota: normálně světlé, přes vyplněnou část tmavé
        float pad = Ui.px(getContext(), .045f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        float vw = text.measureText(valueText);
        int light = gradient != null ? Ui.ON_AMBER : Ui.INK;

        text.setColor(light);
        c.drawText(label, pad, baseline, text);
        c.drawText(valueText, w - pad - vw, baseline, text);
        if (gradient == null) {
            c.save();
            c.clipRect(0, 0, x, h);
            text.setColor(onFill);
            c.drawText(label, pad, baseline, text);
            c.drawText(valueText, w - pad - vw, baseline, text);
            c.restore();
        }

        if (gradient != null) {
            // značka pozice: bílá svislá „pilulka“ s tmavým okrajem
            float mw = Ui.px(getContext(), .014f);
            float mx = Math.max(mw, Math.min(w - mw, x));
            Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            edge.setColor(0x661A1408);
            c.drawRoundRect(new RectF(mx - mw - 2, h * .14f - 2, mx + mw + 2, h * .86f + 2), mw + 2, mw + 2, edge);
            Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
            m.setColor(0xFFFFFFFF);
            c.drawRoundRect(new RectF(mx - mw, h * .14f, mx + mw, h * .86f), mw, mw, m);
        }
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
            float f = Math.max(0, Math.min(1, e.getX() / Math.max(1, getWidth())));
            int v = min + Math.round(f * (max - min));
            boolean end = a == MotionEvent.ACTION_UP;
            if (v != value || end) {
                value = v;
                invalidate();
                if (listener != null) listener.onChange(v, end);
            }
            return true;
        }
        if (a == MotionEvent.ACTION_CANCEL && listener != null) listener.onChange(value, true);
        return true;
    }
}
