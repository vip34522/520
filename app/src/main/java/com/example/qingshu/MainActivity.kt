package com.example.qingshu

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.webkit.WebView.HitTestResult
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (filePathCallback == null) return@registerForActivityResult
        var results: Array<Uri>? = null
        if (result.resultCode == RESULT_OK && result.data != null) {
            val dataString = result.data?.dataString
            if (dataString != null) results = arrayOf(Uri.parse(dataString))
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderManager.initChannel(this)

        WebView.setDataDirectorySuffix("qingshu")
        WebView.setWebContentsDebuggingEnabled(true)

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.databaseEnabled = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            isLongClickable = true
            
            setOnLongClickListener { 
                val result = hitTestResult
                if (result.type == HitTestResult.IMAGE_TYPE || result.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    saveImage(result.extra); true
                } else { false }
            }

            addJavascriptInterface(WebBridge(), "AndroidBridge")
            
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
                override fun onPageFinished(view: WebView, url: String) { 
                    injectHook(view) 
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    AlertDialog.Builder(this@MainActivity).setTitle("提示").setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result?.confirm() }
                        .setNegativeButton("取消") { _, _ -> result?.cancel() }
                        .setOnCancelListener { result?.cancel() }.show()
                    return true
                }

                override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = callback
                    val intent = params?.createIntent()
                    if (intent != null) {
                        try { fileChooserLauncher.launch(intent); return true } 
                        catch (e: Exception) { this@MainActivity.filePathCallback = null; return false }
                    }
                    return false
                }
            }

            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                try {
                    if (url.startsWith("data:")) {
                        val base64Data = url.substringAfter(",")
                        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        saveFileToDownloads(decodedBytes, mimetype, contentDisposition)
                    } else if (url.startsWith("blob:")) {
                        webView.evaluateJavascript("""
                            (function(){
                                fetch('$url').then(res=>res.blob()).then(blob=>{
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                        window.AndroidBridge.downloadBase64(reader.result, '$mimetype', '$contentDisposition');
                                    }
                                    reader.readAsDataURL(blob);
                                });
                            })();
                        """, null)
                    }
                } catch (e: Exception) { Toast.makeText(this@MainActivity, "下载失败", Toast.LENGTH_SHORT).show() }
            }

            loadUrl("https://appassets.androidplatform.net/assets/qingshu.html")
        }
    }

    override fun onResume() {
        super.onResume()
        val data = WidgetDataStore.load(this)
        if (data != null && ::webView.isInitialized) {
            val jsonStr = data.toString().replace("'", "\\'").replace("\n", "")
            webView.evaluateJavascript("window.syncDataFromAndroid('$jsonStr')", null)
        }
    }

    fun saveFileToDownloads(bytes: ByteArray, mimeType: String, fileName: String) {
        try {
            val ext = if (mimeType.contains("pdf")) ".pdf" else if (mimeType.contains("image")) ".png" else ".csv"
            val safeName = "Qingshu_" + System.currentTimeMillis() + ext
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(path, safeName)
            val out: OutputStream = FileOutputStream(file)
            out.write(bytes); out.flush(); out.close()
            runOnUiThread { showDownloadSuccessDialog(file, mimeType) }
        } catch (e: Exception) { 
            runOnUiThread { Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show() } 
        }
    }

    private fun showDownloadSuccessDialog(file: File, mimeType: String) {
        AlertDialog.Builder(this)
            .setTitle("下载成功")
            .setMessage("文件已保存至下载目录：\n${file.name}")
            .setPositiveButton("打开文件") { _, _ ->
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开文件，请确保手机安装了相关阅读器", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("文件目录") { _, _ ->
                try {
                    // 【解决7】直接打开系统的“下载”管理器
                    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                    startActivity(intent)
                } catch (e: Exception) {
                    // 如果手机没有系统下载管理器，则调起系统文件选择器
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    startActivity(Intent.createChooser(intent, "请选择文件管理器"))
                }
            }
            .show()
    }

    private fun saveImage(imageUrl: String?) {
        if (imageUrl == null) return
        try {
            var bitmap: Bitmap? = null
            if (imageUrl.startsWith("data:image")) {
                val base64Data = imageUrl.substringAfter(",")
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
            if (bitmap != null) {
                val filename = "Qingshu_Img_${System.currentTimeMillis()}.png"
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(path, filename)
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush(); out.close()
                MediaStore.Images.Media.insertImage(contentResolver, file.absolutePath, filename, "情书月记导出")
                Toast.makeText(this, "图片已保存到相册！", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun injectHook(view: WebView) {
        val js = """
            (function(){
              if (window.__bridgeHooked) return;
              window.__bridgeHooked = true;
              const KEY = 'qingshu_period_v1';
              const orig = Storage.prototype.setItem;
              Storage.prototype.setItem = function(k, v){
                orig.call(this, k, v);
                try { if (k === KEY && window.AndroidBridge) window.AndroidBridge.syncData(v); } catch(e){}
              };
              try {
                const d = localStorage.getItem(KEY);
                if (d && window.AndroidBridge) window.AndroidBridge.syncData(d);
              } catch(e){}
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    inner class WebBridge {
        @JavascriptInterface
        fun syncData(json: String) { runOnUiThread { WidgetDataStore.save(applicationContext, json) } }

        @JavascriptInterface
        fun setReminder(hour: Int, minute: Int, title: String, content: String) {
            runOnUiThread {
                ReminderManager.setDailyReminder(applicationContext, hour, minute, title, content)
                Toast.makeText(applicationContext, "系统闹钟已设定: $hour:$minute", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun cancelReminder() {
            runOnUiThread { ReminderManager.cancelReminder(applicationContext) }
        }

        @JavascriptInterface
        fun downloadBase64(base64Url: String, mimeType: String, fileName: String) {
            runOnUiThread {
                try {
                    val base64Data = base64Url.substringAfter(",")
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    saveFileToDownloads(decodedBytes, mimeType, fileName)
                } catch (e: Exception) {}
            }
        }
    }
}
