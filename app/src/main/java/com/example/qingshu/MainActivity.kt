package com.example.qingshu

import android.annotation.SuppressLint
import android.app.AlertDialog
// ... (保留你原来的 import)
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat // 注意改用 Compat 类

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 1. 初始化官方资源加载器
    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderManager.initChannel(this)

        // 防多进程崩溃 & 开启调试（可选）
        WebView.setDataDirectorySuffix("qingshu")
        WebView.setWebContentsDebuggingEnabled(true)

        // 2. 先从 XML 加载布局，避免直接 new WebView 在部分机型崩溃
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false // 官方 Loader 不需要文件访问权限
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            isLongClickable = true
            
            // ... (保留你的 setOnLongClickListener 代码不变)
            // ... (保留你的 addJavascriptInterface(WebBridge(), "AndroidBridge") 不变)
            
            // 3. 改用 WebViewClientCompat 和官方 Loader 拦截
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    // 交给官方 Loader 处理，自动处理 MIME 和响应头，彻底解决 ERR_INVALID_RESPONSE
                    return assetLoader.shouldInterceptRequest(request.url)
                }
                
                override fun onPageFinished(view: WebView, url: String) { 
                    injectHook(view) 
                }
            }

            // ... (保留你的 webChromeClient 和 setDownloadListener 代码不变)

            // 4. 保持 URL 不变
            loadUrl("https://appassets.androidplatform.net/assets/qingshu.html")
        }
    }
    // ... (保留你其余的所有方法不变)
}
