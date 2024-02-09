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
import android.view.ViewGroup.MarginLayoutParams
import android.webkit.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    private var isFull: Boolean = true
    var fullOK: Boolean = false
    var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // 获取选择的文件URI
            val data = if (result.data != null) arrayOf(result.data!!.data!!) else emptyArray()
            Log.e(mTAG, "获取选择的文件: $data")
            // 调用回调函数，将选择的文件URI传递给WebView
            mFilePathCallback?.onReceiveValue(data)
        } else {
            // 用户取消选择文件，将回调函数置为null
            mFilePathCallback?.onReceiveValue(null)
        }
        // 重置回调函数和参数
        mFilePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 获取传递过来的网址
        val url = intent.getStringExtra("url")
        mode = intent.getIntExtra("mode", LAUNCH_MODE.SHOW_URL_PAGE.intValue)
        name = intent.getStringExtra("name") ?: "沉浸浏览"
        isFull = intent.getBooleanExtra("full", true)
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
        val parsedUrl = URL(url)
        bgColor = mySharedPreferences.getInt("${parsedUrl.host}:${parsedUrl.port}bg_color", ContextCompat.getColor(this, R.color.logo_bg))
        // 设置背景色和状态栏颜色
        setActivityColor()
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
        if (logoOK && nameOK && bgColorOK && fullOK) {
            if (mode == LAUNCH_MODE.GET_URL_DETAIL.intValue) {
                Log.e(mTAG, "returnMain: url, $hostUrl")
                Log.e(mTAG, "returnMain: name, $name")
                val resultIntent = Intent()
                resultIntent.putExtra("url", hostUrl)
                resultIntent.putExtra("name", name)
                resultIntent.putExtra("logo", logo)
                resultIntent.putExtra("full", isFull)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                @Suppress("DEPRECATION") val taskDescription = TaskDescription(name, logo)
                setTaskDescription(taskDescription)
            }
        }
    }

    fun setNotFull() {
        isFull = false
        val param = myWebView.layoutParams as MarginLayoutParams
        param.setMargins(0, (statusBarHeight * resources.displayMetrics.density).toInt(), 0, 0)
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
        if (convertedColorString.startsWith("rgba")) {
            val matchResult = Regex("""\d+\.?\d*""").findAll(convertedColorString)
            val rgbaValues = matchResult.map { it.value.toFloat() }.toList()
            val alpha = (rgbaValues[3] * 255).toInt()
            val hexValues = rgbaValues.subList(0, 3).map { String.format("%02X", it.toInt()) }
            convertedColorString = "#${String.format("%02X", alpha)}${hexValues.joinToString("")}"
        } else if (convertedColorString.startsWith("rgb")) {
            // 如果颜色值以 rgb 开头，则将其转换为 #RRGGBB 格式的颜色值
            val matchResult = Regex("""\d+""").findAll(convertedColorString)
            val rgbValues = matchResult.map { it.value.toInt() }.toList()
            val hexValues = rgbValues.map { String.format("%02X", it) }
            convertedColorString = "#${hexValues.joinToString(separator = "")}"
        } else if (convertedColorString.startsWith("hsl")) {
            val matchResult = Regex("""\d+\.?\d*%?""").findAll(convertedColorString)
            val hslValues = matchResult.map { it.value }.toList()
            val h = hslValues[0].toFloat() / 360
            val s = hslValues[1].removeSuffix("%").toFloat() / 100
            val l = hslValues[2].removeSuffix("%").toFloat() / 100
            val c = (1 - kotlin.math.abs(2 * l - 1)) * s
            val x = c * (1 - kotlin.math.abs((h * 6) % 2 - 1))
            val m = l - c / 2
            var (r, g, b) = when {
                h < 1 / 6f -> listOf(c, x, 0f)
                h < 2 / 6f -> listOf(x, c, 0f)
                h < 3 / 6f -> listOf(0f, c, x)
                h < 4 / 6f -> listOf(0f, x, c)
                h < 5 / 6f -> listOf(x, 0f, c)
                else -> listOf(c, 0f, x)
            }
            r += m
            g += m
            b += m
            if (convertedColorString.startsWith("hsla")) {
                val alpha = hslValues[3].removeSuffix("%").toFloat() / 100
                r *= alpha
                g *= alpha
                b *= alpha
            }
            convertedColorString = "#${String.format("%02X%02X%02X", (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())}"
        } else {
            convertedColorString = ""
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
            val inputStream: InputStream?
            try {
                val connection: HttpURLConnection =
                    URL(request.url.toString()).openConnection() as HttpURLConnection
                inputStream = connection.inputStream
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
            // 将输入流转换为字符串，并进行相应的替换操作
            var cssString = convertStreamToString(inputStream!!)
            // 如果存在IOS的安全边距
            val safe = "env(safe-area-inset-top)"
            if (cssString.indexOf(safe) > -1) {
                // 将IOS的安全边距进行替换
                cssString = cssString.replace(safe, "" + statusBarHeight + "px")
                Log.w(
                    mTAG,
                    "replaceCss, 替换成功: " + request.isForMainFrame + ": " + request.url
                )
            }

            // 返回新的WebResourceResponse对象，以替换原始的CSS文件
            return WebResourceResponse(
                "text/css",
                "UTF-8",
                ByteArrayInputStream(cssString.toByteArray())
            )
        }
        return null
    }

    fun setActivityColor() {
        // 设置窗口的背景色
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        // 设置状态栏的颜色
        val isLight = Color.red(bgColor) * 0.299 + Color.green(bgColor) * 0.587 + Color.blue(bgColor) * 0.114 >= 186
        if (isLight) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
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
        if (!isFull) {
            setNotFull()
        }
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

    @SuppressLint("QueryPermissionsNeeded")
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        // 创建Intent，用于打开文件选择器
        val intent = fileChooserParams?.createIntent()
        // 启动文件选择器
        _m.launcher.launch(intent)
        // 保存回调函数，以便在选择文件后调用
        _m.mFilePathCallback = filePathCallback
        return true
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
        // 1.6.2版本moviePilot 新增新窗口打开日志页面, 在深色模式下, 浏览效果不佳, 因此增加判断从外部浏览器打开
        if (Uri.parse(url).host?.let { _m.hostUrl.indexOf(it) }!! > -1 &&
            Uri.parse(url).path?.matches(Regex(".*/api/v\\d+/system/logging$")) != true) {
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
        var js = "javascript:window.getComputedStyle(document.body).backgroundColor;"
        view?.evaluateJavascript(js) { result ->
            val newResult = _m.convertColorString(result)
            Log.e(_m.mTAG, "onPageFinished: $result  $newResult  $url")
            if (newResult != "") {
                _m.bgColor = Color.parseColor(newResult)
                _m.setActivityColor()
                val parsedUrl = URL(url)
                _m.mySharedPreferences.edit().putInt("${parsedUrl.host}:${parsedUrl.port}bg_color", _m.bgColor).apply()
            }
            Log.e(_m.mTAG, "onPageFinished: ${_m.bgColor}")
            _m.bgColorOK = true
            _m.returnMain()
        }
        js = "javascript:(function() {" +
                "var metas = document.getElementsByTagName('meta');" +
                    "for (var i = 0; i < metas.length; i++) {" +
                        "if (metas[i].getAttribute('name') === 'viewport') {" +
                            "var content = metas[i].getAttribute('content');" +
                            "if (content.indexOf('viewport-fit=cover') > -1) {" +
                                "return true;" +
                            "}" +
                        "}" +
                    "}" +
                "return false;" +
                "})()"
        // 在页面加载完成后执行JavaScript代码检查viewport的meta标签
        view?.evaluateJavascript(js) { result ->
            Log.e(_m.mTAG, "onPageFinished: result")
            _m.fullOK = true
            if (result != "true") {
                _m.setNotFull()
            }
            _m.returnMain()
        }
    }

}