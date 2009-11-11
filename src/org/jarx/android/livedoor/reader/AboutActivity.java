package org.jarx.android.livedoor.reader;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class AboutActivity extends Activity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.about);
        w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon);

        WebView bodyView = (WebView) findViewById(R.id.body);
        bodyView.loadUrl("file:///android_asset/about.html");
        WebSettings settings = bodyView.getSettings();
        settings.setDefaultFontSize(11);

        setTitle("About " + getText(R.string.app_name).toString());
    }
}
