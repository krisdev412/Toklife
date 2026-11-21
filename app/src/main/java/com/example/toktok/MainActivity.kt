package com.example.toktok

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.ByteArrayInputStream

private const val HOME = "https://www.tiktok.com/foryou"
private const val EXPLORE = "https://www.tiktok.com/explore"
private const val PROFILE = "https://www.tiktok.com/profile"

/** Телеметрия и реклама, запросы к этим хостам блокируем (список из APK). */
private val BLOCKED_HOSTS = listOf(
    "mon.tiktokv.com",
    "mon-va.tiktokv.com",
    "mon-sg.tiktokv.com",
    "mon.isnssdk.com",
    "log.tiktokv.com",
    "log-va.tiktokv.com",
    "rtlog.tiktokv.com",
    "mcs.tiktok.com",
    "ads.tiktok.com",
    "analytics.tiktok.com",
    "business-api.tiktok.com",
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "google-analytics.com",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokTokTheme { App() }
        }
    }
}

@Composable
fun TokTokTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

@SuppressLint("SetJavaScriptEnabled")
fun createWebView(context: Context): WebView = WebView(context).apply {
    setBackgroundColor(AColor.BLACK)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.userAgentString = settings.userAgentString.replace("; wv", "")

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(this, true)

    webChromeClient = WebChromeClient()
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val host = request.url.host ?: return null
            if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        // не даём странице открывать внешние приложения (snssdk://, intent:// и т.п.)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        override fun onPageFinished(view: WebView, url: String?) {
            CookieManager.getInstance().flush()
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val web = remember { createWebView(context) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) web.onPause()
            if (event == Lifecycle.Event.ON_RESUME) web.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            web.destroy()
        }
    }

    BackHandler {
        if (web.canGoBack()) web.goBack() else (context as? Activity)?.finish()
    }

    LaunchedEffect(tab) {
        when (tab) {
            0 -> web.loadUrl(HOME)
            1 -> web.loadUrl(EXPLORE)
            else -> web.loadUrl(PROFILE)
        }
    }

    val items = listOf(
        "Лента" to Icons.Filled.Home,
        "Поиск" to Icons.Filled.Search,
        "Профиль" to Icons.Filled.Person,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().statusBarsPadding()) {
            if (tab == 1) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) {
                            web.loadUrl("https://www.tiktok.com/search?q=" + Uri.encode(query))
                        }
                    }),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web
                },
            )
        }
    }
}
