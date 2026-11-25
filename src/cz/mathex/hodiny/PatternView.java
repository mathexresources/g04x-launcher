package cz.mathex.hodiny;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/** Jemný vzor v pozadí plochy (prosvítá jen mezerami mezi dlaždicemi). Mění se každý den. */
public class PatternView extends View {
    static final String[] NAMES = {"Žádné", "Bauhaus", "Memphis", "Tečky", "Vlny", "Barvy standby"};
    private int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    public PatternView(Context c) {
        super(c);
    }

    void setKind(int k) {
        kind = k;
        invalidate();
    }

    private int a(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight();
        c.drawColor(Ui.BG);
        if (kind == 0 || W == 0) return;
        Calendar now = Calendar.getInstance();
        Random r = new Random(now.get(Calendar.YEAR) * 1000L + now.get(Calendar.DAY_OF_YEAR));
        int[] cols = {Ui.AMBER, Ui.TEAL, 0xFFE8735E, 0xFF9A88F2};
        p.setStyle(Paint.Style.FILL);
        switch (kind) {
            case 1: { // velké geometrické tvary
                for (int i = 0; i < 9; i++) {
                    float s = H * (.25f + .35f * r.nextFloat());
                    float x = W * r.nextFloat(), y = H * r.nextFloat();
                    p.setColor(a(cols[r.nextInt(cols.length)], 0x22));
                    switch (r.nextInt(4)) {
                        case 0: c.drawCircle(x, y, s / 2, p); break;
                        case 1: rf.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2);
                            c.drawArc(rf, r.nextInt(4) * 90, 180, true, p); break;
                        case 2: c.drawRect(x - s / 2, y - s / 4, x + s / 2, y + s / 4, p); break;
                        default:
                            path.reset();
                            path.moveTo(x, y - s / 2);
                            path.lineTo(x + s / 2, y + s / 2);
                            path.lineTo(x - s / 2, y + s / 2);
                            path.close();
                            c.drawPath(path, p);
                    }
                }
                break;
            }
            case 2: { // klikyháky
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(H * .012f);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 26; i++) {
                    float x = W * r.nextFloat(), y = H * r.nextFloat(), s = H * (.05f + .06f * r.nextFloat());
                    p.setColor(a(cols[r.nextInt(cols.length)], 0x40));
                    c.save();
                    c.rotate(r.nextFloat() * 360, x, y);
                    path.reset();
                    for (int k = 0; k <= 16; k++) {
                        float px = x - s + 2 * s * k / 16f, py = y + s * .3f * (float) Math.sin(k / 16f * Math.PI * 3);
                        if (k == 0) path.moveTo(px, py); else path.lineTo(px, py);
                    }
                    if (r.nextBoolean()) c.drawPath(path, p);
                    else c.drawCircle(x, y, s * .5f, p);
                    c.restore();
                }
                break;
            }
            case 3: { // rastr teček
                float step = H * .05f;
                p.setColor(a(Ui.AMBER, 0x26));
                for (float y = step / 2; y < H; y += step)
                    for (float x = step / 2; x < W; x += step) {
                        float k = (x / W + y / H) / 2;
                        c.drawCircle(x, y, step * .3f * k, p);
                    }
                break;
            }
            case 4: { // vlny
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, H * .006f));
                float ph = r.nextFloat() * 6;
                for (int i = 0; i < 12; i++) {
                    p.setColor(a(i % 3 == 0 ? Ui.AMBER : Ui.TEAL, 0x24));
                    path.reset();
                    float y0 = H * (i + .5f) / 12f;
                    for (float x = 0; x <= W; x += 8) {
                        float y = y0 + H * .025f * (float) Math.sin(x / W * Math.PI * 3 + ph + i * .5f);
                        if (x == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    c.drawPath(path, p);
                }
                break;
            }
            default: { // barvy standby – měkké skvrny v barvách palety
                for (int i = 0; i < 5; i++) {
                    float x = W * r.nextFloat(), y = H * r.nextFloat(), rad = H * (.5f + .4f * r.nextFloat());
                    int col = cols[i % cols.length];
                    p.setShader(new android.graphics.RadialGradient(x, y, rad, a(col, 0x30), a(col, 0),
                            android.graphics.Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, rad, p);
                }
                p.setShader(null);
            }
        }
    }
}
