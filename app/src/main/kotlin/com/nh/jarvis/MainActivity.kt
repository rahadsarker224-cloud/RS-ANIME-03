package com.nh.jarvis

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val androidBridge by lazy { AndroidBridge() }

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 51426
        private const val JS_PROTECTION = "(function(){" +
            "document.addEventListener('contextmenu',function(e){e.preventDefault();},true);" +
            "document.addEventListener('selectstart',function(e){e.preventDefault();},true);" +
            "document.addEventListener('copy',function(e){e.preventDefault();},true);" +
            "var s=document.createElement('style');" +
            "s.textContent='*{-webkit-user-select:none!important;user-select:none!important;}';" +
            "document.head.appendChild(s);" +
            "console.log=console.warn=console.error=function(){};" +
            "})();"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        fullscreenContainer = findViewById(R.id.fullscreen_container)

        val html = HtmlVault.getHtml(this)
        if (html == null) {
            Toast.makeText(this, "Unable to load app content", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupWebView()
        webView.loadDataWithBaseURL("https://app.local/", html, "text/html", "UTF-8", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.addJavascriptInterface(androidBridge, "AndroidApp")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(JS_PROTECTION, null)
                androidBridge.loadWebData()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return when {
                    url.startsWith("view-source:") -> true
                    url.startsWith("file://") -> true
                    url.startsWith("content://") -> true
                    url.contains("youtube.com") || url.contains("youtu.be") -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) { }
                        true
                    }
                    url.startsWith("https://") || url.startsWith("http://") -> false
                    else -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) { }
                        true
                    }
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                webView.visibility = View.GONE
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }

            override fun onHideCustomView() {
                fullscreenContainer.removeAllViews()
                fullscreenContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: return false
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                Toast.makeText(this@MainActivity, message ?: "", Toast.LENGTH_SHORT).show()
                result?.confirm()
                return true
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val results = if (resultCode == Activity.RESULT_OK && data?.data != null)
                arrayOf(data.data!!) else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
            webView.evaluateJavascript("(function(){try{return typeof _appBack==='function'?_appBack():'true';}catch(e){return 'true';}})();") { result ->
                if (result != "true") {
                    // JS handled the back press, do nothing further
                } else {
                    finish()
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    inner class AndroidBridge {
        @android.webkit.JavascriptInterface
        fun getPlatform(): String = "android"

        @android.webkit.JavascriptInterface
        fun isAndroid(): Boolean = true

        @android.webkit.JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
        }

        @android.webkit.JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) { }
            }
        }

        @android.webkit.JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        /* ===== Native Firebase Realtime Database bridge ===== */
        /* Replaces the old firebase-app-compat.js / firebase-database-compat.js
           Web SDK calls that used to live inside index.html. */

        @android.webkit.JavascriptInterface
        fun loadWebData() {
            val ref = FirebaseDatabase.getInstance().getReference("web")
            ref.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    @Suppress("UNCHECKED_CAST")
                    val map = snapshot.value as? Map<String, Any?> ?: emptyMap()
                    val json = JSONObject(map).toString()
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.onWebDataReceived(${JSONObject.quote(json)});", null
                        )
                    }
                }
                override fun onCancelled(error: DatabaseError) { }
            })
        }

        @android.webkit.JavascriptInterface
        fun loadComments(seriesId: String) {
            val ref = FirebaseDatabase.getInstance()
                .getReference("comments").child(seriesId).orderByChild("timestamp")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val arr = JSONArray()
                    for (child in snapshot.children) {
                        @Suppress("UNCHECKED_CAST")
                        val map = child.value as? Map<String, Any?> ?: continue
                        arr.put(JSONObject(map))
                    }
                    val json = arr.toString()
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.onCommentsReceived(${JSONObject.quote(seriesId)}, ${JSONObject.quote(json)});",
                            null
                        )
                    }
                }
                override fun onCancelled(error: DatabaseError) { }
            })
        }

        @android.webkit.JavascriptInterface
        fun postComment(seriesId: String, name: String, comment: String) {
            val ref = FirebaseDatabase.getInstance().getReference("comments").child(seriesId).push()
            val data = mapOf(
                "userName" to name,
                "comment" to comment,
                "timestamp" to System.currentTimeMillis()
            )
            ref.setValue(data)
                .addOnSuccessListener {
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.onCommentPosted(${JSONObject.quote(seriesId)});", null
                        )
                    }
                }
                .addOnFailureListener {
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.onCommentPostFailed(${JSONObject.quote(seriesId)});", null
                        )
                    }
                }
        }

        @android.webkit.JavascriptInterface
        fun postReport(seriesId: String) {
            val ref = FirebaseDatabase.getInstance().getReference("reports").child(seriesId).push()
            ref.setValue(mapOf("timestamp" to System.currentTimeMillis()))
        }
    }
}
