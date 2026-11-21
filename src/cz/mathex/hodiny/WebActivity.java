package cz.mathex.hodiny;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

/** Ovládání termotiskárny – stránka /clock z thermoprint-serveru ve vestavěném prohlížeči. */
public class WebActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.bar(this, "Tiskárna", "⟳", new View.OnClickListener() {
            public void onClick(View v) {
                web.reload();
            }
        }));

        CookieManager.getInstance().setAcceptCookie(true);
        web = new WebView(this);
        web.setBackgroundColor(Ui.BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    v.loadDataWithBaseURL(null,
                            "<body style='background:#0b0e0d;color:#edf2ef;font:20px sans-serif;"
                                    + "display:flex;align-items:center;justify-content:center;height:90vh;text-align:center'>"
                                    + "<div>Tiskárna (server) nedostupná<br><small style='color:#7e8a85'>"
                                    + Prefs.printerUrl(WebActivity.this)
                                    + "<br>zkus ⟳ vpravo nahoře</small></div></body>",
                            "text/html", "utf-8", null);
                }
            }
        });
        root.addView(web, Ui.lp(-1, 0, 1f));
        setContentView(root);
        web.loadUrl(Prefs.printerUrl(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
