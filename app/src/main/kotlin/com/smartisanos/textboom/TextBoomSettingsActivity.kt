package com.cashewteam.novatext.android

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.data.JiebaWarmUpTracker
import com.cashewteam.novatext.android.service.BoomActivityLauncher
import com.cashewteam.novatext.android.service.FloatingBallService
import com.cashewteam.novatext.android.service.NovaTextAccessibilityService
import kotlin.math.ceil

class TextBoomSettingsActivity : ComponentActivity() {
    private lateinit var settings: BigBangSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BigBangSettings.get(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        val searchOptions = loadOptions(
            R.array.text_boom_search_ways,
            R.array.text_boom_search_values,
            R.array.text_boom_search_icons,
        )
        val dictionaryOptions = loadOptions(
            R.array.big_bang_dict_name,
            R.array.big_bang_dict_value,
            R.array.big_bang_dict_icon,
        )

        setContent {
            BigBangSettingsTheme {
                SettingsScreen(
                    settings = settings,
                    searchOptions = searchOptions,
                    dictionaryOptions = dictionaryOptions,
                    onOpenPreview = { openBigBangPreview(it) },
                    onOpenOverlayPermission = { openOverlayPermission() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onStartFloatingBall = { startFloatingBall() },
                    onStopFloatingBall = { stopFloatingBall() },
                    onResetFloatingBall = { resetFloatingBall() },
                )
            }
        }
    }

    private fun loadOptions(
        @ArrayRes titleRes: Int,
        @ArrayRes valueRes: Int,
        @ArrayRes iconRes: Int,
    ): List<OptionItem> {
        val titles = resources.getStringArray(titleRes)
        val values = resources.getIntArray(valueRes)
        val icons = resources.obtainTypedArray(iconRes)
        return try {
            titles.indices.map { index ->
                OptionItem(
                    title = titles[index],
                    value = values[index],
                    iconRes = icons.getResourceId(index, 0),
                )
            }
        } finally {
            icons.recycle()
        }
    }

    private fun openBigBangPreview(text: String) {
        settings.setDebugPreviewText(text)
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        BoomActivityLauncher.openText(
            context = this,
            text = text,
            touchX = width / 2,
            touchY = height / 2,
            isPreview = true,
        )
    }

    private fun openOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startFloatingBall() {
        FloatingBallService.start(this)
    }

    private fun stopFloatingBall() {
        FloatingBallService.stop(this)
    }

    private fun resetFloatingBall() {
        FloatingBallService.resetPosition(this)
    }
}

private data class OptionItem(
    val title: String,
    val value: Int,
    @DrawableRes val iconRes: Int,
)

private data class PermissionState(
    val overlayGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val floatingBallRunning: Boolean,
)

private data class SettingsPalette(
    val background: Color,
    val stripe: Color,
    val topBar: Color,
    val topBarText: Color,
    val card: Color,
    val cardInset: Color,
    val cardBorder: Color,
    val shadow: Color,
    val accent: Color,
    val accentSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
)

@Composable
private fun BigBangSettingsTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = if (dark) {
        SettingsPalette(
            background = Color(0xFF121417),
            stripe = Color.White.copy(alpha = 0.02f),
            topBar = Color(0xFF171B20),
            topBarText = Color(0xFFF3F5F7),
            card = Color(0xFF1C2127),
            cardInset = Color(0xFF20262D),
            cardBorder = Color(0xFF2C333B),
            shadow = Color(0xFF000000),
            accent = Color(0xFF79A8FF),
            accentSoft = Color(0x223E7BFF),
            textPrimary = Color(0xFFF3F5F7),
            textSecondary = Color(0xFF9EA7B3),
            divider = Color(0xFF2B3138),
        )
    } else {
        SettingsPalette(
            background = Color(0xFFF1F2F4),
            stripe = Color.Black.copy(alpha = 0.02f),
            topBar = Color.White,
            topBarText = Color(0xFF20242A),
            card = Color(0xFFFDFDFE),
            cardInset = Color(0xFFF5F7FA),
            cardBorder = Color(0xFFE6E8EC),
            shadow = Color(0xFF52606D),
            accent = Color(0xFF5D91FF),
            accentSoft = Color(0x1F5D91FF),
            textPrimary = Color(0xFF20242A),
            textSecondary = Color(0xFF6F7883),
            divider = Color(0xFFE8EBEF),
        )
    }

    MaterialTheme(content = {
        CompositionPalette(palette = palette, content = content)
    })
}

@Composable
private fun CompositionPalette(
    palette: SettingsPalette,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsPalette provides palette) {
        content()
    }
}

private val LocalSettingsPalette =
    androidx.compose.runtime.staticCompositionLocalOf<SettingsPalette> {
        error("SettingsPalette not provided")
    }

@Composable
private fun BlurredShadow(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val shadowColor = LocalSettingsPalette.current.shadow.copy(alpha = 0.5f)
    Box(
        modifier = modifier.drawWithCache {
            val blurPx = 16.dp.toPx()
            val offsetYPx = 5.dp.toPx()
            val padding = ceil(blurPx * 2f + offsetYPx).toInt()
            val bitmapWidth = ceil(size.width + padding * 2f).toInt().coerceAtLeast(1)
            val bitmapHeight = ceil(size.height + padding * 2f).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val shadowPath = shape.createOutlinePath(Size(size.width, size.height), layoutDirection, this)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shadowColor.toArgb()
                style = Paint.Style.FILL
                setShadowLayer(blurPx, 0f, offsetYPx, shadowColor.toArgb())
            }
            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }

            canvas.save()
            canvas.translate(padding.toFloat(), padding.toFloat())
            canvas.drawPath(shadowPath.asAndroidPath(), shadowPaint)
            canvas.drawPath(shadowPath.asAndroidPath(), clearPaint)
            canvas.restore()

            onDrawWithContent {
                drawIntoCanvas { target ->
                    target.nativeCanvas.drawBitmap(bitmap, -padding.toFloat(), -padding.toFloat(), null)
                }
                drawContent()
            }
        },
    )
}

private fun Shape.createOutlinePath(
    size: Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
): Path {
    return when (val outline = createOutline(size, layoutDirection, density)) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
}

@Composable
private fun rememberStripeBrush(stripeColor: Color): Brush {
    val density = LocalDensity.current
    return remember(stripeColor, density) {
        val stripeWidth = with(density) { 2.dp.toPx() }
        val gap = with(density) { 2.dp.toPx() }
        val patternWidth = stripeWidth + gap
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to stripeColor,
                stripeWidth / patternWidth to stripeColor,
                stripeWidth / patternWidth to Color.Transparent,
                1f to Color.Transparent,
            ),
            startX = 0f,
            endX = patternWidth,
            tileMode = TileMode.Repeated,
        )
    }
}

@Composable
private fun ApplySystemBars() {
    val palette = LocalSettingsPalette.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
        window.statusBarColor = palette.topBar.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = palette.background.toArgb()
        }
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: BigBangSettings,
    searchOptions: List<OptionItem>,
    dictionaryOptions: List<OptionItem>,
    onOpenPreview: (String) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartFloatingBall: () -> Unit,
    onStopFloatingBall: () -> Unit,
    onResetFloatingBall: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    ApplySystemBars()
    val stripeBrush = rememberStripeBrush(palette.stripe)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection = LocalLayoutDirection.current
    val presetLabels = remember(context, layoutDirection) {
        context.resources.getStringArray(R.array.debug_preset_text_labels).toList()
    }
    val presetTexts = remember(context, layoutDirection) {
        context.resources.getStringArray(R.array.debug_preset_texts).toList()
    }
    var previewText by rememberSaveable { mutableStateOf(settings.debugPreviewText) }
    var selectedPresetIndex by rememberSaveable {
        mutableIntStateOf(presetTexts.indexOf(settings.debugPresetText).coerceAtLeast(0))
    }
    var selectedSearch by rememberSaveable { mutableIntStateOf(settings.webSearchType) }
    var selectedDictionary by rememberSaveable { mutableIntStateOf(settings.dictSearchType) }
    val warmUpState by JiebaWarmUpTracker.getStateFlow().collectAsState(
        initial = JiebaWarmUpTracker.getCurrentState(),
    )
    val floatingBallRunning by FloatingBallService.getActiveStateFlow().collectAsState(
        initial = FloatingBallService.isActive(),
    )
    val currentPermissionState = {
        PermissionState(
            overlayGranted = canDrawOverlays(context),
            accessibilityEnabled = isAccessibilityServiceEnabled(context),
            floatingBallRunning = floatingBallRunning,
        )
    }
    var permissionState by remember {
        mutableStateOf(currentPermissionState())
    }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val listTopPadding = with(density) { topBarHeightPx.toDp() } + 10.dp

    DisposableEffect(floatingBallRunning) {
        permissionState = currentPermissionState()
        onDispose { }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = currentPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .background(stripeBrush),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = listTopPadding, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsSectionCard {
                    PermissionSection(
                        state = permissionState,
                        onOpenOverlayPermission = onOpenOverlayPermission,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onStartFloatingBall = onStartFloatingBall,
                        onStopFloatingBall = onStopFloatingBall,
                        onResetFloatingBall = onResetFloatingBall,
                    )
                }
            }

            item {
                SettingsSectionCard {
                    DebugSection(
                        previewText = previewText,
                        selectedPresetIndex = selectedPresetIndex,
                        presetLabels = presetLabels,
                        warmUpState = warmUpState,
                        onPresetSelected = { index ->
                            val text = presetTexts[index]
                            selectedPresetIndex = index
                            previewText = text
                            settings.setDebugPresetText(text)
                            settings.setDebugPreviewText(text)
                        },
                        onPreviewTextChange = {
                            previewText = it
                            settings.setDebugPreviewText(it)
                        },
                        onPreviewClick = {
                            settings.setDebugPreviewText(previewText)
                            onOpenPreview(previewText)
                        },
                    )
                }
            }

            item {
                SettingsSectionCard {
                    OptionSection(
                        title = stringResource(R.string.default_search_way),
                        subtitle = stringResource(R.string.settings_search_summary),
                        options = searchOptions,
                        selectedValue = selectedSearch,
                        onSelect = {
                            selectedSearch = it
                            settings.setWebSearchType(it)
                        },
                    )
                }
            }

            item {
                SettingsSectionCard {
                    OptionSection(
                        title = stringResource(R.string.default_dict),
                        subtitle = stringResource(R.string.settings_dict_summary),
                        options = dictionaryOptions,
                        selectedValue = selectedDictionary,
                        onSelect = {
                            selectedDictionary = it
                            settings.setDictSearchType(it)
                        },
                    )
                }
            }
        }

        SettingsTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeightPx = it.height },
        )
    }
}

private fun canDrawOverlays(context: android.content.Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceComponent = ComponentName(context, NovaTextAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val expected = serviceComponent.flattenToString()
    val expectedShort = serviceComponent.flattenToShortString()
    return enabledServices.split(':').any { value ->
        val normalized = value.trim()
        normalized.equals(expected, ignoreCase = true) ||
            normalized.equals(expectedShort, ignoreCase = true)
    }
}

@Composable
private fun SettingsTopBar(modifier: Modifier = Modifier) {
    val palette = LocalSettingsPalette.current
    val shape = RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        BlurredShadow(shape = shape, modifier = Modifier.matchParentSize())
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = shape,
            color = palette.topBar,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.text_boom_settings),
                    color = palette.topBarText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalSettingsPalette.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        shape = shape,
        color = palette.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSection(
    previewText: String,
    selectedPresetIndex: Int,
    presetLabels: List<String>,
    warmUpState: Int,
    onPresetSelected: (Int) -> Unit,
    onPreviewTextChange: (String) -> Unit,
    onPreviewClick: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.debug_preset_text_label),
            color = palette.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.settings_debug_summary),
            color = palette.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        WarmUpBadge(state = warmUpState)

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            presetLabels.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = index == selectedPresetIndex,
                    onClick = { onPresetSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = presetLabels.size,
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = palette.accentSoft,
                        activeContentColor = palette.textPrimary,
                        activeBorderColor = palette.accent.copy(alpha = 0.45f),
                        inactiveContainerColor = palette.cardInset,
                        inactiveContentColor = palette.textSecondary,
                        inactiveBorderColor = palette.cardBorder,
                    ),
                    modifier = Modifier.height(42.dp),
                ) {
                    Text(
                        text = item,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        OutlinedTextField(
            value = previewText,
            onValueChange = onPreviewTextChange,
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            minLines = 5,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = palette.textPrimary,
                lineHeight = 23.sp,
            ),
            placeholder = {
                Text(
                    text = stringResource(R.string.debug_preview_text_hint),
                    color = palette.textSecondary,
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = palette.cardInset,
                unfocusedContainerColor = palette.cardInset,
                disabledContainerColor = palette.cardInset,
                focusedIndicatorColor = palette.accent,
                unfocusedIndicatorColor = palette.cardBorder,
                cursorColor = palette.accent,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                focusedPlaceholderColor = palette.textSecondary,
                unfocusedPlaceholderColor = palette.textSecondary,
            ),
        )

        ShadowedPrimaryButton(
            text = stringResource(R.string.debug_preview_button),
            onClick = onPreviewClick,
        )
    }
}

@Composable
private fun PermissionSection(
    state: PermissionState,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartFloatingBall: () -> Unit,
    onStopFloatingBall: () -> Unit,
    onResetFloatingBall: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    val primaryActionText = when {
        !state.overlayGranted -> stringResource(R.string.permission_overlay_action)
        !state.accessibilityEnabled -> stringResource(R.string.permission_accessibility_action)
        state.floatingBallRunning -> stringResource(R.string.permission_stop_floating_ball)
        else -> stringResource(R.string.permission_start_floating_ball)
    }
    val primaryAction = when {
        !state.overlayGranted -> onOpenOverlayPermission
        !state.accessibilityEnabled -> onOpenAccessibilitySettings
        state.floatingBallRunning -> onStopFloatingBall
        else -> onStartFloatingBall
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.permission_section_title),
            color = palette.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.permission_section_summary),
            color = palette.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        PermissionStatusRow(
            title = stringResource(R.string.permission_overlay_title),
            granted = state.overlayGranted,
        )
        PermissionStatusRow(
            title = stringResource(R.string.permission_accessibility_title),
            granted = state.accessibilityEnabled,
        )
        PermissionStatusRow(
            title = stringResource(R.string.permission_floating_ball_title),
            granted = state.floatingBallRunning,
            grantedText = stringResource(R.string.permission_enabled),
            deniedText = stringResource(R.string.permission_disabled),
        )
        ShadowedPrimaryButton(
            text = primaryActionText,
            onClick = primaryAction,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.permission_overlay_action),
                onClick = onOpenOverlayPermission,
            )
            SecondaryActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.permission_accessibility_action),
                onClick = onOpenAccessibilitySettings,
            )
        }
        if (state.floatingBallRunning) {
            SecondaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.permission_reset_floating_ball),
                onClick = onResetFloatingBall,
            )
        }
    }
}

@Composable
private fun WarmUpBadge(state: Int) {
    val palette = LocalSettingsPalette.current
    val statusText = when (state) {
        JiebaWarmUpTracker.STATE_RUNNING -> R.string.debug_warm_up_status_running
        JiebaWarmUpTracker.STATE_READY -> R.string.debug_warm_up_status_ready
        JiebaWarmUpTracker.STATE_FAILED -> R.string.debug_warm_up_status_failed
        else -> R.string.debug_warm_up_status_idle
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = palette.accentSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = when (state) {
                            JiebaWarmUpTracker.STATE_FAILED -> Color(0xFFF07070)
                            else -> palette.accent
                        },
                        shape = CircleShape,
                    ),
            )
            Text(
                text = stringResource(statusText),
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    grantedText: String = stringResource(R.string.permission_granted),
    deniedText: String = stringResource(R.string.permission_missing),
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (granted) palette.accentSoft else palette.cardInset,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (granted) palette.accent.copy(alpha = 0.35f) else palette.cardBorder,
            ),
        ) {
            Text(
                text = if (granted) grantedText else deniedText,
                color = if (granted) palette.textPrimary else palette.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ShadowedPrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    val buttonShape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
        ),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingsPalette.current
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = palette.cardInset,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OptionSection(
    title: String,
    subtitle: String,
    options: List<OptionItem>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Column {
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = palette.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardInset,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
        ) {
            Column {
                options.forEachIndexed { index, item ->
                    OptionRow(
                        item = item,
                        selected = item.value == selectedValue,
                        onClick = { onSelect(item.value) },
                    )
                    if (index != options.lastIndex) {
                        HorizontalDivider(
                            color = palette.divider,
                            modifier = Modifier.padding(start = 64.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    item: OptionItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = palette.card,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = item.title,
            color = palette.textPrimary,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        SelectionIndicator(selected = selected)
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    val palette = LocalSettingsPalette.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = if (selected) palette.accentSoft else palette.card,
                shape = CircleShape,
            )
            .padding(7.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.accent, CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.cardBorder, CircleShape),
            )
        }
    }
}
