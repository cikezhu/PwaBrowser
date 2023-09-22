package ddd.pwa.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager.TaskDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


class WebViewActivity : AppCompatActivity() {
    val mTAG: String = "WebViewActivity"
    lateinit var mySharedPreferences: SharedPreferences
    private lateinit var myWebView: WebView
    private lateinit var myLinearLayout: LinearLayout
    private lateinit var myImageLogo: ImageView
    private val statusBarHeight: Int = 40
    var firstUpdated: Boolean = true
    var hostUrl: String = ""
    private var mode: Int? = null
    var name: String = ""
    var nameOK: Boolean = false
    var logo: Bitmap? = null
    var logoOK: Boolean = false
    var bgColor: Int = 0
    var bgColorOK: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 获取传递过来的网址
        val url = intent.getStringExtra("url")
        mode = intent.getIntExtra("mode", LAUNCH_MODE.SHOW_URL_PAGE.intValue)
        name = intent.getStringExtra("name") ?: "沉浸浏览"
        @Suppress("DEPRECATION")
        setTaskDescription(TaskDescription(name))
        if (url == null) {
            finish()
            return
        } else {
            // 绑定url地址
            hostUrl = url
        }
        // 初始化配置
        mySharedPreferences = getSharedPreferences("mySharedPreferences", MODE_PRIVATE)
        bgColor = mySharedPreferences.getInt("${url}bg_color", ContextCompat.getColor(this, R.color.logo_bg))
        // 在设置布局之前设置窗口的背景色
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        // 获取缓存的图标
        logo = getBitmapFromCache()
        super.onCreate(savedInstanceState)
        // 初始化ServiceWorker拦截
        initializeServiceWorker()
        // 载入界面布局
        setContentView(R.layout.activity_web_view)
        // 初始化webView
        initializeWebView()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 浏览器后退
        if (keyCode == KeyEvent.KEYCODE_BACK && myWebView.canGoBack()) {
            myWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun returnMain() {
        if (logoOK && nameOK && bgColorOK) {
            if (mode == LAUNCH_MODE.GET_URL_DETAIL.intValue) {
                Log.e(mTAG, "returnMain: url, $hostUrl")
                Log.e(mTAG, "returnMain: name, $name")
                val resultIntent = Intent()
                resultIntent.putExtra("url", hostUrl)
                resultIntent.putExtra("name", name)
                resultIntent.putExtra("logo", logo)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                @Suppress("DEPRECATION") val taskDescription = TaskDescription(name, logo)
                setTaskDescription(taskDescription)
            }
        }
    }

    fun hideBgLogo() {
        // 隐藏logo界面
        val logo: LinearLayout = findViewById(R.id.bg_logo)
        if (mode  == LAUNCH_MODE.GET_URL_DETAIL.intValue) {
            logo.visibility = View.GONE
        } else {
            // 创建一个透明度动画
            val alphaAnimation = ObjectAnimator.ofFloat(logo, "alpha", 1.0f, 0.0f)
            // 设置动画持续时间
            alphaAnimation.duration = 1000
            // 添加动画监听器
            alphaAnimation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 动画结束时隐藏 LinearLayout
                    logo.visibility = View.GONE
                }
            })
            // 开始动画
            alphaAnimation.start()
        }
    }

    private fun convertStreamToString(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        try {
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return stringBuilder.toString()
    }

    fun convertColorString(colorString: String): String {
        var convertedColorString = colorString.trim('"', '\'').lowercase(Locale.ROOT)
        if (convertedColorString.startsWith("#")) {
            // 如果颜色值以 # 开头，则直接返回该值
            return convertedColorString
        }
        if (convertedColorString.startsWith("rgb")) {
            // 如果颜色值以 rgb 开头，则将其转换为 #RRGGBB 格式的颜色值
            val matchResult = Regex("""\d+""").findAll(convertedColorString)
            val rgbValues = matchResult.map { it.value.toInt() }.toList()
            val hexValues = rgbValues.map { String.format("%02X", it) }
            convertedColorString = "#${hexValues.joinToString(separator = "")}"
        } else if (convertedColorString.startsWith("argb")) {
            // 如果颜色值以 argb 开头，则将其转换为 #AARRGGBB 格式的颜色值
            val matchResult = Regex("""\d+""").findAll(convertedColorString)
            val argbValues = matchResult.map { it.value.toInt() }.toList()
            val hexValues = argbValues.map { String.format("%02X", it) }
            convertedColorString = "#${hexValues[0]}${hexValues.subList(1, 4).joinToString(separator = "")}"
        }
        return convertedColorString
    }

    // 编码字符串为Base64字符串
    private fun encode(data: String): String {
        return Base64.getEncoder().encodeToString(data.toByteArray())
    }

    // 解码Base64字符串为字符串
    @Suppress("unused")
    private fun decode(base64: String): String {
        return String(Base64.getDecoder().decode(base64))
    }

    fun saveBitmapToCache(bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }
        try {
            // 获取缓存目录
            val cacheDir = cacheDir
            // 创建文件对象
            val file = File(cacheDir, encode(hostUrl))
            // 创建文件输出流对象
            val fos = FileOutputStream(file)
            // 将Bitmap对象压缩为PNG格式并写入文件输出流
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            // 关闭文件输出流
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getBitmapFromCache(): Bitmap? {
        try {
            // 获取缓存目录
            val cacheDir = cacheDir
            // 创建文件对象
            val file = File(cacheDir, encode(hostUrl))
            // 创建文件输入流对象
            val fis = FileInputStream(file)
            // 将文件输入流解码为Bitmap对象
            val bitmap = BitmapFactory.decodeStream(fis)
            // 关闭文件输入流
            fis.close()
            // 返回Bitmap对象
            return bitmap
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun replaceCss(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        // 判断是否为CSS文件
        if (url.endsWith(".css")) {
            // 获取原始CSS文件的输入流
            var inputStream: InputStream? = null
            try {
                val connection: HttpURLConnection =
                    URL(request.url.toString()).openConnection() as HttpURLConnection
                inputStream = connection.inputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            // 将输入流转换为字符串，并进行相应的替换操作
            var cssString = convertStreamToString(inputStream!!)
            // 将IOS的安全边距进行替换
            cssString = cssString.replace("env(safe-area-inset-top)", "" + statusBarHeight + "px")

            // 返回新的WebResourceResponse对象，以替换原始的CSS文件
            return WebResourceResponse(
                "text/css",
                "UTF-8",
                ByteArrayInputStream(cssString.toByteArray())
            )
        }
        return null
    }

    private fun initializeServiceWorker() {
        // 配置 Service Worker 拦截
        val swController = ServiceWorkerController.getInstance()
        swController.serviceWorkerWebSettings.allowContentAccess = true
        swController.setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                Log.e(
                    mTAG,
                    "serviceWorker, shouldInterceptRequest: " + request.isForMainFrame + ": " + request.url
                )
                val response = replaceCss(request)
                return if (response !== null) {
                    response
                } else {
                    super.shouldInterceptRequest(request)
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initializeWebView() {
        // 绑定组件 应用背景色和图标
        myWebView = findViewById(R.id.webview_ding)
        myWebView.setBackgroundColor(bgColor)
        myLinearLayout = findViewById(R.id.bg_logo)
        myLinearLayout.setBackgroundColor(bgColor)
        myImageLogo = findViewById(R.id.image_logo)
        myImageLogo.setBackgroundColor(bgColor)
        if (logo != null) {
            myImageLogo.setImageBitmap(logo)
        }

        // 配置 web view
        WebView.setWebContentsDebuggingEnabled(true)
        val settings: WebSettings = myWebView.settings // webView 配置项
        settings.useWideViewPort = true // 是否启用对视口元标记的支持
        settings.javaScriptEnabled = true // 是否启用 JavaScript
        settings.domStorageEnabled = true // 是否启用本地存储（允许使用 localStorage 等）
        settings.allowFileAccess = true // 是否启用文件访问
        // val appCachePath = applicationContext.cacheDir.absolutePath // 缓存地址
        settings.allowContentAccess = true // 是否启用内容 URL 访问
        settings.javaScriptCanOpenWindowsAutomatically = true // 是否允许 JS 弹窗
        settings.mediaPlaybackRequiresUserGesture = false // 是否需要用户手势来播放媒体
        settings.loadWithOverviewMode = true // 是否以概览模式加载页面，即按宽度缩小内容以适应屏幕
        settings.builtInZoomControls = true // 是否应使用其内置的缩放机制
        // Hide the zoom controls for HONEYCOMB+
        settings.displayZoomControls = false  // 是否应显示屏幕缩放控件
        // settings.allowFileAccessFromFileURLs = true // 是否应允许在文件方案 URL 上下文中运行的 JavaScript 访问来自其他文件方案 URL 的内容
        // settings.allowUniversalAccessFromFileURLs = true // 是否应允许在文件方案URL上下文中运行的 JavaScript 访问任何来源的内容
        // myWebView.setDrawingCacheEnabled(true) // 启用或禁用图形缓存
        myWebView.webViewClient = WVViewClient(this, this@WebViewActivity) // 帮助 WebView 处理各种通知、请求事件
        myWebView.webChromeClient = WVChromeClient(this, this@WebViewActivity) // 处理解析，渲染网页

        myWebView.loadUrl(hostUrl)
    }

}

@Suppress("unused")
private class WVChromeClient(private val _context: Context, private val _m: WebViewActivity):
    WebChromeClient() {
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        _m.name = title ?: "PWA"
        Log.d(_m.mTAG, "onReceivedTitle: ${_m.name}")
        _m.nameOK = true
        _m.returnMain()
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        _m.logo = icon
        _m.saveBitmapToCache(icon)
        Log.d(_m.mTAG, "onReceivedIcon: ok")
        _m.logoOK = true
        _m.returnMain()
    }
}

private class WVViewClient(private val _context: Context, private val _m: WebViewActivity):
    WebViewClient() {
    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.proceed()
    }

    // 非hostUrl从外部浏览器打开
    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (Uri.parse(url).host?.let { _m.hostUrl.indexOf(it) }!! > -1) {
            return false
        }
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (view != null) {
                ContextCompat.startActivity(_context, this, null)
            }
        }
        return true
    }

    //页面访问出错
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        // 未加载成功 禁止关闭logo图
        _m.firstUpdated = false
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        Log.e(
            _m.mTAG,
            "WebView, shouldInterceptRequest: " + request.isForMainFrame + ": " + request.url
        )
        val response = _m.replaceCss(request)
        return if (response !== null) {
            response
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    //页面加载完成
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 关闭logo图
        if (_m.firstUpdated) {
            _m.firstUpdated = false
            _m.hideBgLogo()
        }
        // 使用JavaScript获取网站的背景颜色
        val js = "javascript:window.getComputedStyle(document.body).backgroundColor;"
        view?.evaluateJavascript(js) { result ->
            val newResult = _m.convertColorString(result)
            Log.e(_m.mTAG, "onPageFinished: $result  $newResult")
            if (newResult != "") {
                _m.bgColor = Color.parseColor(newResult)
                _m.mySharedPreferences.edit().putInt("${url}bg_color", _m.bgColor).apply()
            }
            Log.e(_m.mTAG, "onPageFinished: ${_m.bgColor}")
            _m.bgColorOK = true
            _m.returnMain()
        }
    }

}