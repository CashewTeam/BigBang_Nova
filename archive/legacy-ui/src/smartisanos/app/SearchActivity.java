package smartisanos.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import com.smartisanos.textboom.R;

public abstract class SearchActivity extends Activity {
    protected SearchActivity mContext;
    protected TextView mTitle;
    protected View mProgess;
    protected View mColse;
    protected WebView mWebView;
    protected ImageView mGoBack;
    protected ImageView mGoForward;
    protected View mBrowser;
    protected String mSearchText;
    protected boolean mFirstPage = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSearchText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        initContentView();
        setupWebView();
        setupViews();
    }

    protected abstract void initContentView();

    protected void setupViews() {
    }

    protected abstract void loadUrl();

    protected abstract String getOriUrl();

    protected void gotoBrowser() {
        if (mWebView != null && !TextUtils.isEmpty(mWebView.getUrl())) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(mWebView.getUrl())));
        }
    }

    private void setupWebView() {
        if (mWebView == null) {
            return;
        }
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.setWebViewClient(new WebViewClient());
    }
}
