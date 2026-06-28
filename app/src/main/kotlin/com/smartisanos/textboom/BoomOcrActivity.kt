package com.cashewteam.novatext.android

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.service.BoomActivityLauncher
import com.cashewteam.novatext.android.util.LogUtils
import com.google.mlkit.vision.text.Text as MlKitText

class BoomOcrActivity : ComponentActivity() {
    private lateinit var settings: BigBangSettings
    private val handler by lazy { Handler(mainLooper) }
    private var preparedBitmap: Bitmap? = null
    private var ocrBitmap: Bitmap? = null
    private var ocrText: String = ""
    private var touchX = 0f
    private var touchY = 0f
    private var fullscreen = false
    private var callerPackage: String? = null
    private var offset: IntArray = intArrayOf(0, 0)
    private var selectionContainer: OcrSelectionContainer? = null
    private var stage by mutableStateOf(OcrStage.Selecting)
    private var ocrStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || sBoomCancel) {
            finish()
            return
        }
        settings = BigBangSettings.get(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        touchX = readTouchCoordinate("boom_startx", true)
        touchY = readTouchCoordinate("boom_starty", false)
        fullscreen = intent.getBooleanExtra("boom_fullscreen", false)
        callerPackage = intent.getStringExtra("caller_pkg")
        offset = intArrayOf(
            intent.getIntExtra("boom_offsetx", 0),
            intent.getIntExtra("boom_offsety", 0),
        )

        prepareOcr()
        if (isFinishing) return

        setContent {
            OcrOverlayScreen(
                settings = settings,
                stage = stage,
                bitmap = preparedBitmap,
                onBack = { stopOcr() },
                onOpenLanguageMenu = { },
                onStartOcr = { startOcr() },
                onQrClick = {
                    Toast.makeText(this, R.string.ocr_qr_placeholder, Toast.LENGTH_SHORT).show()
                },
                onCancelLoading = { stopOcr() },
                onContainerReady = { selectionContainer = it },
            )
        }
    }

    override fun onDestroy() {
        sBoomCancel = false
        ocrStarted = false
        recycleBitmap(ocrBitmap, false)
        ocrBitmap = null
        recycleBitmap(preparedBitmap, true)
        preparedBitmap = null
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        stopOcr()
    }

    fun post(run: Runnable?) {
        if (run != null) {
            handler.post(run)
        }
    }

    fun cancelOcr() {
        stopOcr()
    }

    private fun startOcr() {
        val bitmap = preparedBitmap ?: run {
            showImageUnavailableAndFinish()
            return
        }
        if (ocrStarted) return
        val container = selectionContainer ?: return
        val selectionRect = container.getSelectionRectInBitmap()
        if (selectionRect.width() <= 0 || selectionRect.height() <= 0) {
            Toast.makeText(this, R.string.ocr_image_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        recycleBitmap(ocrBitmap, false)
        ocrBitmap = Bitmap.createBitmap(
            bitmap,
            selectionRect.left,
            selectionRect.top,
            selectionRect.width(),
            selectionRect.height(),
        )
        ocrStarted = true
        stage = OcrStage.Recognizing
        MlKitOcrEngine.recognize(ocrBitmap!!, settings.ocrRecognizerMode)
            .addOnSuccessListener(this, this::handleOcrSuccess)
            .addOnFailureListener(this) { throwable ->
                LogUtils.e("ML Kit OCR failed", throwable)
                if (!isFinishing) {
                    Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                }
                ocrStarted = false
                stage = OcrStage.Selecting
            }
    }

    private fun handleOcrSuccess(result: MlKitText?) {
        if (isFinishing) return
        ocrText = result?.text?.trim().orEmpty()
        if (ocrText.isEmpty()) {
            Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            ocrStarted = false
            stage = OcrStage.Selecting
            return
        }
        BoomActivityLauncher.openText(this, ocrText, touchX.toInt(), touchY.toInt(), false, false)
        ocrStarted = false
        finish()
    }

    private fun stopOcr() {
        ocrStarted = false
        finish()
    }

    private fun prepareOcr() {
        val imageUri = readImageUri()
        if (imageUri == null) {
            showImageUnavailableAndFinish()
            return
        }
        try {
            val bitmap = MlKitOcrEngine.decodeBitmap(this, imageUri)
            if (bitmap == null) {
                showImageUnavailableAndFinish()
                return
            }
            preparedBitmap = if (shouldAdjustScreenshot()) adjustScreenshotFor(bitmap) else bitmap
        } catch (exception: Exception) {
            LogUtils.e("Failed to decode OCR image", exception)
            showImageUnavailableAndFinish()
        }
    }

    private fun readImageUri(): Uri? {
        if (Intent.ACTION_SEND == intent.action) {
            val extra = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (extra != null) return extra
        }
        val value = intent.getStringExtra(EXTRA_OCR_IMAGE_URI)
        return if (TextUtils.isEmpty(value)) null else Uri.parse(value)
    }

    private fun readTouchCoordinate(extraName: String, horizontal: Boolean): Float {
        if (intent.hasExtra(extraName)) {
            return intent.getIntExtra(extraName, 0).toFloat()
        }
        return if (horizontal) resources.displayMetrics.widthPixels / 2f else resources.displayMetrics.heightPixels / 2f
    }

    private fun shouldAdjustScreenshot(): Boolean {
        return !callerPackage.isNullOrEmpty() || offset[0] != 0 || offset[1] != 0
    }

    private fun showImageUnavailableAndFinish() {
        Toast.makeText(this, R.string.ocr_image_unavailable, Toast.LENGTH_SHORT).show()
        stopOcr()
    }

    private fun adjustScreenshotFor(screenshot: Bitmap): Bitmap {
        val w = resources.getInteger(R.integer.screen_width)
        val h = resources.getInteger(R.integer.screen_height)
        val statusBarHeight = resources.getInteger(R.integer.status_bar_height)
        var top = statusBarHeight
        var bottom = 0
        var left = 0
        var right = 0
        if (offset[0] == 0 && offset[1] == 0) {
            if (PKG_GALLERY == callerPackage && !fullscreen) {
                top = resources.getInteger(R.integer.gallery_top)
                bottom = resources.getInteger(R.integer.gallery_bottom)
            }
        } else {
            val scaleFactor = offset[1] / h.toFloat()
            val sideH = offset[1]
            val sideW = (scaleFactor * w).toInt()
            if (PKG_GALLERY == callerPackage && !fullscreen) {
                val galleryTop = (resources.getInteger(R.integer.gallery_top) * (1 - scaleFactor)).toInt()
                val galleryBottom = (resources.getInteger(R.integer.gallery_bottom) * (1 - scaleFactor)).toInt()
                top = sideH + galleryTop
                bottom = galleryBottom
            } else {
                top = sideH + ((1 - scaleFactor) * statusBarHeight).toInt()
            }
            if (offset[0] == 0) {
                right = sideW
            } else {
                left = sideW
            }
        }
        val sourceWidth = screenshot.width
        val sourceHeight = screenshot.height
        if (sourceWidth <= left + right || sourceHeight <= top + bottom) {
            return screenshot
        }
        val aw = (sourceWidth - left - right) / SCALE_SCREENSHOT
        val ah = (sourceHeight - top - bottom) / SCALE_SCREENSHOT
        if (aw <= 0 || ah <= 0) {
            return screenshot
        }
        val bitmap = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(
            screenshot,
            Rect(left, top, sourceWidth - right, sourceHeight - bottom),
            Rect(0, 0, aw, ah),
            paint,
        )
        screenshot.recycle()
        return bitmap
    }

    private fun recycleBitmap(bitmap: Bitmap?, allowPrepared: Boolean) {
        if (bitmap == null || bitmap.isRecycled) return
        if (!allowPrepared && bitmap === preparedBitmap) return
        bitmap.recycle()
    }

    companion object {
        const val EXTRA_OCR_IMAGE_URI = "ocr_image_uri"
        private const val PKG_GALLERY = "com.android.gallery3d"
        const val SCALE_SCREENSHOT = 2

        @JvmField
        var sBoomCancel: Boolean = false

        private var instance: BoomOcrActivity? = null

        @JvmStatic
        fun getInstance(): BoomOcrActivity? = instance
    }
}

private enum class OcrStage {
    Selecting,
    Recognizing,
}

private data class OcrLanguageOption(
    val title: String,
    val value: String,
)

private data class OcrPalette(
    val background: Color,
    val bar: Color,
    val barBorder: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentSoft: Color,
    val button: Color,
    val buttonText: Color,
)

@Composable
private fun OcrOverlayScreen(
    settings: BigBangSettings,
    stage: OcrStage,
    bitmap: Bitmap?,
    onBack: () -> Unit,
    onOpenLanguageMenu: () -> Unit,
    onStartOcr: () -> Unit,
    onQrClick: () -> Unit,
    onCancelLoading: () -> Unit,
    onContainerReady: (OcrSelectionContainer) -> Unit,
) {
    val palette = OcrPalette(
        background = Color.Black.copy(alpha = 0.70f),
        bar = Color(0xE61B1F24),
        barBorder = Color(0x66363E47),
        text = Color(0xFFF4F7FA),
        secondaryText = Color(0xFFB4BEC9),
        accent = Color(0xFF6FA0FF),
        accentSoft = Color(0x223E7BFF),
        button = Color(0xFF5F86F4),
        buttonText = Color.White,
    )
    val context = LocalContext.current
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val options = remember {
        listOf(
            OcrLanguageOption(context.getString(R.string.ocr_mode_chinese), BigBangSettings.OCR_MODE_CHINESE),
            OcrLanguageOption(context.getString(R.string.ocr_mode_japanese), BigBangSettings.OCR_MODE_JAPANESE),
            OcrLanguageOption(context.getString(R.string.ocr_mode_korean), BigBangSettings.OCR_MODE_KOREAN),
            OcrLanguageOption(context.getString(R.string.ocr_mode_latin), BigBangSettings.OCR_MODE_LATIN),
        )
    }
    val currentLanguage = options.firstOrNull { it.value == settings.ocrRecognizerMode } ?: options.first()
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues()

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bar)
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.requiredWidth(48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        OverlayIconAction(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            tint = palette.text,
                            onClick = onBack,
                            contentDescription = stringResource(R.string.ocr_action_cancel),
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.ocr_select_region_title),
                                color = palette.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = currentLanguage.title,
                                color = palette.secondaryText,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.requiredWidth(48.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box {
                            OverlayIconAction(
                                imageVector = Icons.Outlined.Settings,
                                tint = palette.text,
                                onClick = {
                                    languageMenuExpanded = true
                                    onOpenLanguageMenu()
                                },
                                contentDescription = stringResource(R.string.ocr_action_language),
                            )
                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false },
                                containerColor = Color(0xFF20252B),
                            ) {
                                options.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.title,
                                                color = if (option.value == settings.ocrRecognizerMode) palette.text else palette.secondaryText,
                                            )
                                        },
                                        leadingIcon = {
                                            if (option.value == settings.ocrRecognizerMode) {
                                                Icon(
                                                    imageVector = Icons.Outlined.CheckCircle,
                                                    contentDescription = null,
                                                    tint = palette.accent,
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.size(24.dp))
                                            }
                                        },
                                        onClick = {
                                            settings.setOcrRecognizerMode(option.value)
                                            languageMenuExpanded = false
                                        },
                                    )
                                    if (index != options.lastIndex) {
                                        HorizontalDivider(color = Color(0x223E7BFF))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.barBorder)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (stage == OcrStage.Selecting && bitmap != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            OcrSelectionContainer(ctx).also(onContainerReady).apply {
                                bindBitmap(bitmap)
                            }
                        },
                        update = { container ->
                            onContainerReady(container)
                            container.bindBitmap(bitmap)
                        },
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color(0xCC202226),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .padding(horizontal = 24.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = palette.accent,
                                strokeWidth = 3.dp,
                            )
                            Text(
                                text = stringResource(R.string.ocr_recognize),
                                color = palette.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                            Text(
                                text = stringResource(R.string.ocr_cancel),
                                color = palette.secondaryText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.barBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bar)
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        top = 12.dp,
                        bottom = 12.dp + navigationPadding.calculateBottomPadding(),
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OcrBottomSecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.ocr_action_qr),
                    icon = Icons.Outlined.QrCodeScanner,
                    palette = palette,
                    enabled = stage == OcrStage.Selecting,
                    onClick = onQrClick,
                )
                OcrBottomPrimaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.ocr_action_recognize),
                    palette = palette,
                    enabled = stage == OcrStage.Selecting && bitmap != null,
                    onClick = onStartOcr,
                )
            }
        }

        if (stage == OcrStage.Recognizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancelLoading,
                    ),
            )
        }
    }
}

@Composable
private fun OcrBottomSecondaryButton(
    modifier: Modifier,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: OcrPalette,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) palette.text else palette.secondaryText.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = if (enabled) palette.text else palette.secondaryText.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OcrBottomPrimaryButton(
    modifier: Modifier,
    text: String,
    palette: OcrPalette,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) palette.button else palette.button.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = palette.buttonText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private class OcrSelectionContainer(context: android.content.Context) : FrameLayout(context) {
    private val imageView = ImageView(context).apply {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val selectionView = OcrSelectionView(context).apply {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    private var boundBitmap: Bitmap? = null

    init {
        addView(imageView)
        addView(selectionView)
        clipChildren = false
        clipToPadding = false
    }

    fun bindBitmap(bitmap: Bitmap) {
        if (boundBitmap === bitmap) {
            post(::updateSelectionBounds)
            return
        }
        boundBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        post {
            updateSelectionBounds()
            selectionView.resetSelection()
        }
    }

    fun getSelectionRectInBitmap(): Rect {
        val bitmap = boundBitmap ?: return Rect()
        return selectionView.getSelectionRectInBitmap(bitmap.width, bitmap.height)
    }

    private fun updateSelectionBounds() {
        val drawable: Drawable = imageView.drawable ?: return
        val bounds = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        val matrix = Matrix(imageView.imageMatrix)
        matrix.mapRect(bounds)
        bounds.offset(imageView.paddingLeft.toFloat(), imageView.paddingTop.toFloat())
        selectionView.setImageBounds(bounds)
    }
}
