package cz.mathex.hodiny;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Seznam všech aplikací. Ťuknutí = spustit, podržení = informace o aplikaci. */
public class AppsActivity extends Activity {
    static final class App {
        String label, pkg, cls;
        Drawable icon;
    }

    private final List<App> apps = new ArrayList<App>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Aplikace", "⚙  Nastavení", new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.showSettings(AppsActivity.this, null);
            }
        }));

        loadApps();
        GridView grid = new GridView(this);
        grid.setNumColumns(6);
        int pad = Ui.px(this, .03f);
        grid.setPadding(pad, 0, pad, pad);
        grid.setVerticalSpacing(pad);
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setAdapter(new Adapter());
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                App a = apps.get(pos);
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(a.pkg, a.cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
                finish();
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + apps.get(pos).pkg)));
                return true;
            }
        });
        root.addView(grid, Ui.lp(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            if (getPackageName().equals(r.activityInfo.packageName)) continue;
            App a = new App();
            a.label = String.valueOf(r.loadLabel(pm));
            a.pkg = r.activityInfo.packageName;
            a.cls = r.activityInfo.name;
            a.icon = r.loadIcon(pm);
            apps.add(a);
        }
        final Collator col = Collator.getInstance(new Locale("cs", "CZ"));
        Collections.sort(apps, new Comparator<App>() {
            public int compare(App x, App y) {
                return col.compare(x.label, y.label);
            }
        });
    }

    private class Adapter extends BaseAdapter {
        public int getCount() {
            return apps.size();
        }

        public Object getItem(int p) {
            return apps.get(p);
        }

        public long getItemId(int p) {
            return p;
        }

        public View getView(int p, View convert, ViewGroup parent) {
            LinearLayout cell;
            if (convert instanceof LinearLayout) {
                cell = (LinearLayout) convert;
            } else {
                Activity c = AppsActivity.this;
                cell = Ui.box(c, true);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                int pad = Ui.px(c, .015f);
                cell.setPadding(pad, pad, pad, pad);
                cell.setBackground(Ui.press(c));
                int s = Ui.px(c, .14f);
                cell.addView(new ImageView(c), new LinearLayout.LayoutParams(s, s));
                TextView t = Ui.text(c, "", .036f, Ui.INK);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = pad;
                cell.addView(t, lp);
            }
            App a = apps.get(p);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(a.icon);
            ((TextView) cell.getChildAt(1)).setText(a.label);
            return cell;
        }
    }
}
