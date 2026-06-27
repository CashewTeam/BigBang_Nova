@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.smartisanos.textboom

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.smartisanos.textboom.data.BigBangSettings
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BoomSearchOverlayActivity : ComponentActivity() {
    private lateinit var settings: BigBangSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BigBangSettings.get(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val searchText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val initialType = intent.getIntExtra(EXTRA_SEARCH_TYPE, settings.webSearchType)
        setContent {
            SearchOverlayScreen(
                settings = settings,
                searchText = searchText,
                initialType = initialType,
                onClose = { finish() },
                onOpenSettings = { startActivity(Intent(this, TextBoomSettingsActivity::class.java)) },
            )
        }
    }

    companion object {
        private const val EXTRA_SEARCH_TYPE = "search_type"

        @JvmStatic
        fun createIntent(context: Context, text: String, type: Int): Intent {
            return Intent(context, BoomSearchOverlayActivity::class.java).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(EXTRA_SEARCH_TYPE, type)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
    }
}

private enum class SearchKind {
    Web,
    Dict,
    Wiki,
}

private data class SearchPalette(
    val panel: Color,
    val topBar: Color,
    val bottomBar: Color,
    val border: Color,
    val divider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val iconTint: Color,
    val accent: Color,
    val accentSoft: Color,
)

private data class SearchProvider(
    val type: Int,
    val title: String,
    @DrawableRes val iconRes: Int,
)

@Composable
private fun SearchOverlayScreen(
    settings: BigBangSettings,
    searchText: String,
    initialType: Int,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val panelMetrics = rememberOverlayPanelMetrics()
    val palette = if (dark) {
        SearchPalette(
            panel = Color(0xFF171A1F),
            topBar = Color(0xFF1E2328),
            bottomBar = Color(0xFF1A1F24),
            border = Color(0xFF2E343C),
            divider = Color(0xFF2A3138),
            primaryText = Color(0xFFF2F5F8),
            secondaryText = Color(0xFF98A3AF),
            iconTint = Color(0xFFE8EDF2),
            accent = Color(0xFF79A8FF),
            accentSoft = Color(0x223E7BFF),
        )
    } else {
        SearchPalette(
            panel = Color(0xFFF3F3F4),
            topBar = Color(0xFFFFFFFF),
            bottomBar = Color(0xFFECECEE),
            border = Color(0xFFD7D7DA),
            divider = Color(0xFFD8D8DB),
            primaryText = Color(0xFF6C6760),
            secondaryText = Color(0xFF9D9790),
            iconTint = Color(0xFF6F6962),
            accent = Color(0xFF5F86F4),
            accentSoft = Color(0x225F86F4),
        )
    }

    val webProviders = remember {
        listOf(
            SearchProvider(BigBangSettings.TYPE_BAIDU, "百度", R.drawable.boom_win_search_baidu),
            SearchProvider(BigBangSettings.TYPE_GOOGLE, "Google", R.drawable.boom_win_search_google),
            SearchProvider(BigBangSettings.TYPE_BING, "Bing", R.drawable.boom_win_search_bing),
            SearchProvider(BigBangSettings.TYPE_SHENMA, "神马", R.drawable.boom_win_search_shenma),
        )
    }
    val dictProviders = remember {
        listOf(
            SearchProvider(BigBangSettings.TYPE_YOUDAO, "有道词典", R.drawable.boom_win_search_youdao),
            SearchProvider(BigBangSettings.TYPE_KINGSOFT, "金山词霸", R.drawable.boom_win_search_kingsoft),
            SearchProvider(BigBangSettings.TYPE_BINGDICT, "必应词典", R.drawable.boom_win_search_bingdict),
            SearchProvider(BigBangSettings.TYPE_HIDICT, "海词词典", R.drawable.boom_win_search_hidict),
        )
    }
    val wikiProviders = remember {
        listOf(
            SearchProvider(BigBangSettings.TYPE_WIKI, "互动百科", R.drawable.boom_win_search_hudongdict),
            SearchProvider(BigBangSettings.TYPE_BAIKE, "百度百科", R.drawable.boom_win_search_baike),
        )
    }

    var webType by rememberSaveable { mutableIntStateOf(settings.webSearchType) }
    var dictType by rememberSaveable { mutableIntStateOf(settings.dictSearchType) }
    var wikiType by rememberSaveable { mutableIntStateOf(settings.wikiSearchType) }
    var activeKind by rememberSaveable { mutableStateOf(kindForType(initialType)) }
    var currentType by rememberSaveable { mutableIntStateOf(initialType) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentUrl by remember(searchText, currentType) { mutableStateOf(buildSearchUrl(currentType, searchText)) }
    var expandedKind by remember { mutableStateOf<SearchKind?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun activeProviderFor(kind: SearchKind): SearchProvider {
        return when (kind) {
            SearchKind.Web -> webProviders.first { it.type == webType }
            SearchKind.Dict -> dictProviders.first { it.type == dictType }
            SearchKind.Wiki -> wikiProviders.first { it.type == wikiType }
        }
    }

    fun updateCurrentTypeFor(kind: SearchKind) {
        activeKind = kind
        currentType = when (kind) {
            SearchKind.Web -> webType
            SearchKind.Dict -> dictType
            SearchKind.Wiki -> wikiType
        }
        currentUrl = buildSearchUrl(currentType, searchText)
    }

    fun applyProvider(provider: SearchProvider, kind: SearchKind) {
        when (kind) {
            SearchKind.Web -> {
                webType = provider.type
                settings.setWebSearchType(provider.type)
            }
            SearchKind.Dict -> {
                dictType = provider.type
                settings.setDictSearchType(provider.type)
            }
            SearchKind.Wiki -> {
                wikiType = provider.type
                settings.setWikiSearchType(provider.type)
            }
        }
        updateCurrentTypeFor(kind)
        expandedKind = null
    }

    fun syncNavigationState() {
        val webView = webViewRef ?: return
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

    BackHandler {
        val webView = webViewRef
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webViewRef = null
        }
    }

    OverlayScene(scrimColor = Color.Transparent, onDismiss = onClose) {
        FloatingPanel(
            width = panelMetrics.width,
            height = panelMetrics.height,
            modifier = overlayPanelPlacement(panelMetrics),
            shape = RoundedCornerShape(panelMetrics.cornerRadius),
            backgroundColor = palette.panel,
            borderColor = palette.border,
            shadowColor = null,
        ) {
            OverlayPanelScaffold(
                topBar = {
                    SearchTopBar(
                        palette = palette,
                        title = activeProviderFor(activeKind).title,
                        searchText = searchText,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        onBack = { webViewRef?.goBack() },
                        onForward = { webViewRef?.goForward() },
                        onRefresh = { webViewRef?.reload() },
                        onOpenSettings = onOpenSettings,
                    )
                    if (progress in 0.01f..0.99f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent,
                            trackColor = palette.accentSoft,
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.Transparent),
                        )
                    }
                },
                bottomBar = {
                    HorizontalDivider(color = palette.divider)
                    SearchBottomBar(
                        palette = palette,
                        activeKind = activeKind,
                        webProvider = activeProviderFor(SearchKind.Web),
                        dictProvider = activeProviderFor(SearchKind.Dict),
                        wikiProvider = activeProviderFor(SearchKind.Wiki),
                        onClose = onClose,
                        onBrowser = { openInBrowser(context, currentUrl) },
                        onKindClick = { kind -> updateCurrentTypeFor(kind) },
                        onKindLongPress = { kind -> expandedKind = kind },
                        expandedKind = expandedKind,
                        onDismissMenu = { expandedKind = null },
                        providersForKind = { kind ->
                            when (kind) {
                                SearchKind.Web -> webProviders
                                SearchKind.Dict -> dictProviders
                                SearchKind.Wiki -> wikiProviders
                            }
                        },
                        onProviderSelected = ::applyProvider,
                    )
                },
            ) { bodyModifier ->
                AndroidView(
                    modifier = bodyModifier,
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            configureSearchWebView(dark)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    syncNavigationState()
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    currentUrl = url ?: currentUrl
                                    progress = 1f
                                    syncNavigationState()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    currentUrl = request?.url?.toString() ?: currentUrl
                                    return false
                                }
                            }
                            loadUrl(currentUrl)
                        }
                    },
                    update = { view ->
                        if (view.url != currentUrl) {
                            view.loadUrl(currentUrl)
                        }
                        syncNavigationState()
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    palette: SearchPalette,
    title: String,
    searchText: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OverlayHeaderBar(
        backgroundColor = palette.topBar,
        leading = {
            OverlayIconAction(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                tint = if (canGoBack) palette.primaryText else palette.secondaryText.copy(alpha = 0.45f),
                onClick = onBack,
                contentDescription = stringResource(R.string.search_overlay_back),
            )
            OverlayIconAction(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                tint = if (canGoForward) palette.primaryText else palette.secondaryText.copy(alpha = 0.45f),
                onClick = onForward,
                contentDescription = stringResource(R.string.search_overlay_forward),
            )
        },
        center = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = palette.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = searchText,
                    color = palette.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing = {
            OverlayIconAction(
                imageVector = Icons.Outlined.Refresh,
                tint = palette.secondaryText,
                onClick = onRefresh,
                contentDescription = stringResource(R.string.search_overlay_refresh),
            )
            OverlayIconAction(
                imageVector = Icons.Outlined.Settings,
                tint = palette.secondaryText,
                onClick = onOpenSettings,
                contentDescription = stringResource(R.string.search_overlay_settings),
            )
        },
    )
}

@Composable
private fun SearchBottomBar(
    palette: SearchPalette,
    activeKind: SearchKind,
    webProvider: SearchProvider,
    dictProvider: SearchProvider,
    wikiProvider: SearchProvider,
    onClose: () -> Unit,
    onBrowser: () -> Unit,
    onKindClick: (SearchKind) -> Unit,
    onKindLongPress: (SearchKind) -> Unit,
    expandedKind: SearchKind?,
    onDismissMenu: () -> Unit,
    providersForKind: (SearchKind) -> List<SearchProvider>,
    onProviderSelected: (SearchProvider, SearchKind) -> Unit,
) {
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues()
    OverlayBottomBar(backgroundColor = palette.bottomBar) {
        ToolbarIconButton(
            imageVector = Icons.Outlined.Close,
            tint = palette.iconTint,
            enabled = true,
            onClick = onClose,
            buttonSize = 48.dp,
            contentDescription = stringResource(R.string.search_overlay_close),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f).padding(bottom = navigationPadding.calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            SearchProviderButton(
                provider = webProvider,
                selected = activeKind == SearchKind.Web,
                onClick = { onKindClick(SearchKind.Web) },
                onLongClick = { onKindLongPress(SearchKind.Web) },
                menuExpanded = expandedKind == SearchKind.Web,
                menuItems = providersForKind(SearchKind.Web),
                onDismissMenu = onDismissMenu,
                onProviderSelected = { onProviderSelected(it, SearchKind.Web) },
                contentDescription = stringResource(R.string.search_overlay_web),
            )
            SearchProviderButton(
                provider = dictProvider,
                selected = activeKind == SearchKind.Dict,
                onClick = { onKindClick(SearchKind.Dict) },
                onLongClick = { onKindLongPress(SearchKind.Dict) },
                menuExpanded = expandedKind == SearchKind.Dict,
                menuItems = providersForKind(SearchKind.Dict),
                onDismissMenu = onDismissMenu,
                onProviderSelected = { onProviderSelected(it, SearchKind.Dict) },
                contentDescription = stringResource(R.string.search_overlay_dict),
            )
            SearchProviderButton(
                provider = wikiProvider,
                selected = activeKind == SearchKind.Wiki,
                onClick = { onKindClick(SearchKind.Wiki) },
                onLongClick = { onKindLongPress(SearchKind.Wiki) },
                menuExpanded = expandedKind == SearchKind.Wiki,
                menuItems = providersForKind(SearchKind.Wiki),
                onDismissMenu = onDismissMenu,
                onProviderSelected = { onProviderSelected(it, SearchKind.Wiki) },
                contentDescription = stringResource(R.string.search_overlay_wiki),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        ToolbarIconButton(
            iconRes = R.drawable.boom_win_browser,
            tint = null,
            enabled = true,
            onClick = onBrowser,
            buttonSize = 48.dp,
            contentDescription = stringResource(R.string.search_overlay_browser),
        )
    }
}

@Composable
private fun ToolbarIconButton(
    @DrawableRes iconRes: Int,
    tint: Color?,
    enabled: Boolean,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 36.dp,
    contentDescription: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            },
            update = { view ->
                view.setImageResource(iconRes)
                view.contentDescription = contentDescription
                view.isEnabled = enabled
                if (tint == null) {
                    view.clearColorFilter()
                } else {
                    view.setColorFilter(tint.toArgb())
                }
            },
        )
    }
}

@Composable
private fun ToolbarIconButton(
    imageVector: ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 36.dp,
    contentDescription: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
private fun SearchProviderButton(
    provider: SearchProvider,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    menuItems: List<SearchProvider>,
    onDismissMenu: () -> Unit,
    onProviderSelected: (SearchProvider) -> Unit,
    contentDescription: String,
) {
    Box {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(52.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Color(0xFFE5E5E8),
                            shape = RoundedCornerShape(10.dp),
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .shadow(
                        elevation = 2.dp,
                        shape = CircleShape,
                        clip = false,
                    )
                    .background(Color.White, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { view ->
                        view.setImageResource(provider.iconRes)
                        view.contentDescription = contentDescription
                        view.alpha = if (selected) 1f else 0.82f
                        view.scaleX = 2.3f
                        view.scaleY = 2.3f
                    },
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
        ) {
            menuItems.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(text = item.title, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Image(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.title,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { onProviderSelected(item) },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSearchWebView(dark: Boolean) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadsImagesAutomatically = true
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.setSupportZoom(true)
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    overScrollMode = View.OVER_SCROLL_NEVER
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
    ) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, dark)
    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        WebSettingsCompat.setForceDark(
            settings,
            if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
        )
    }
}

private fun kindForType(type: Int): SearchKind {
    return when {
        type >= BigBangSettings.TYPE_YOUDAO -> SearchKind.Dict
        type >= BigBangSettings.TYPE_WIKI -> SearchKind.Wiki
        else -> SearchKind.Web
    }
}

private fun buildSearchUrl(type: Int, text: String): String {
    val query = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
    return when (type) {
        BigBangSettings.TYPE_GOOGLE -> "https://www.google.com/search?q=$query"
        BigBangSettings.TYPE_BING -> "https://www.bing.com/search?q=$query"
        BigBangSettings.TYPE_SHENMA -> "http://m.yz.sm.cn/s?q=$query"
        BigBangSettings.TYPE_WIKI -> "http://www.baike.com/gwiki/$query"
        BigBangSettings.TYPE_BAIKE -> "http://wapbaike.baidu.com/search/word?word=$query"
        BigBangSettings.TYPE_YOUDAO -> "http://m.youdao.com/dict?q=$query"
        BigBangSettings.TYPE_KINGSOFT -> "http://www.iciba.com/$query"
        BigBangSettings.TYPE_BINGDICT -> "http://cn.bing.com/dict/?q=$query"
        BigBangSettings.TYPE_HIDICT -> "http://m.dict.cn/$query"
        else -> "https://www.baidu.com/s?wd=$query"
    }
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: ActivityNotFoundException) {
    }
}
