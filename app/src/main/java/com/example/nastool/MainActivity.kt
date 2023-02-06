package com.example.nastool

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity


class MainActivity : AppCompatActivity() {
    private lateinit var myWebView: WebView
    companion object {
        @JvmStatic
        val host_url: String = "http://192.168.137.1:3000"
        @JvmStatic
        var status_bar_height: Int = 40
        @JvmStatic
        var to_css_top: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        this.initWebview()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && this.myWebView.canGoBack()) {
            this.myWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebview() {
        this.myWebView = findViewById(R.id.webview)

        val settings: WebSettings = this.myWebView.settings // webView 配置项
        settings.useWideViewPort = true // 是否启用对视口元标记的支持
        settings.javaScriptEnabled = true // 是否启用 JavaScript
        settings.domStorageEnabled = true // 是否启用本地存储（允许使用 localStorage 等）
        settings.allowFileAccess = true // 是否启用文件访问
        //val appCachePath = applicationContext.cacheDir.absolutePath // 缓存地址
        settings.allowContentAccess = true // 是否启用内容 URL 访问
        settings.javaScriptCanOpenWindowsAutomatically = true // 是否允许 JS 弹窗
        settings.mediaPlaybackRequiresUserGesture = false // 是否需要用户手势来播放媒体
        settings.loadWithOverviewMode = true // 是否以概览模式加载页面，即按宽度缩小内容以适应屏幕
        settings.builtInZoomControls = true // 是否应使用其内置的缩放机制
        // Hide the zoom controls for HONEYCOMB+
        settings.displayZoomControls = false  // 是否应显示屏幕缩放控件
//        settings.allowFileAccessFromFileURLs =
//            true // 是否应允许在文件方案 URL 上下文中运行的 JavaScript 访问来自其他文件方案 URL 的内容
//        settings.allowUniversalAccessFromFileURLs =
//            true // 是否应允许在文件方案URL上下文中运行的 JavaScript 访问任何来源的内容
        this.myWebView.loadUrl(host_url) // 设置访问地址
        //this.myWebView.setDrawingCacheEnabled(true) // 启用或禁用图形缓存
        this.myWebView.webViewClient = WVViewClient() // 帮助 WebView 处理各种通知、请求事件
        this.myWebView.webChromeClient = WVChromeClient(this, this@MainActivity) // 处理解析，渲染网页
    }
}

private class WVViewClient : WebViewClient() {
    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.proceed()
    }
    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (Uri.parse(url).host?.let { MainActivity.host_url.indexOf(it) }!! > -1) {
            // This is my web site, so do not override; let my WebView load the page
            return false
        }
        // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (view != null) {
                startActivity( view.context, this, null)
            }
        }
        return true
    }
    //页面加载完调用
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d("MainActivity", "url：$url");
        if (!MainActivity.to_css_top || ((url != null) && (url.indexOf("?theme=") > -1))) {
            MainActivity.to_css_top = true
            view?.loadUrl("javascript:" +
                    "document.getElementsByTagName('body')[0].style.setProperty('--safe-area-inset-top', '" +
                    MainActivity.status_bar_height +
                    "px');")
        }
    }

}

class WVChromeClient(private val _context: Context, private val _m: MainActivity) :
    WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress);
        Log.d("MainActivity", "newProgress：$newProgress");
    }
}