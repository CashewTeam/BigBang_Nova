package com.cashewteam.novatext.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.cashewteam.novatext.android.data.CppJiebaTokenizer
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator
import com.cashewteam.novatext.android.util.LogUtils

class BoomActivity : ComponentActivity() {
    private var boomChipPage: BoomChipPage? = null
    private lateinit var legacyContentView: View
    private var launchTouchX = -1
    private var launchTouchY = -1
    private var currentText = ""
    private var currentSegment: IntArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        launchTouchX = intent.getIntExtra("boom_startx", -1)
        launchTouchY = intent.getIntExtra("boom_starty", -1)

        legacyContentView = layoutInflater.inflate(R.layout.boom_activity_layout, null, false)
        legacyContentView.findViewById<View>(R.id.boom_page).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            if (intent.getBooleanExtra(OcrLaunchActivity.EXTRA_SKIP_LEGACY_FADE_IN, false)) {
                visibility = View.VISIBLE
                alpha = 1f
            } else {
                BoomAnimator.makeFadeIn(this, BoomAnimator.BOOM_DURATION)
            }
        }
        boomChipPage = BoomChipPage(this, legacyContentView, false).also { page ->
            page.restoreSelectedState(savedInstanceState?.getSerializable(SELECTED_STATE))
            page.setOnAdjacentRequestListener(object : BoomChipPage.OnAdjacentRequestListener {
                override fun onAdjacentRequest(direction: String) {
                    loadAdjacent(direction)
                }
            })
        }

        setContent {
            BigBangOverlayContent(
                contentView = legacyContentView,
                touchX = launchTouchX,
                touchY = launchTouchY,
                onDismiss = { dismissPage() },
                onEditMode = { showPlaceholder() },
                onSelectAll = { selectAll() },
                onShareAll = { shareAll() },
                onMore = { showPlaceholder() },
            )
        }

        val previewText = intent.getStringExtra(EXTRA_DEBUG_PREVIEW_TEXT)
        val inputText = previewText ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        if (inputText.isNullOrEmpty()) {
            finish()
            return
        }
        if (!intent.getBooleanExtra(EXTRA_ENABLE_ADJACENT_SESSION, false)) {
            TextSessionCoordinator.clearSession()
        }
        segmentLocally(inputText)
    }

    private fun dismissPage() {
        if (boomChipPage?.handleClick() != true) {
            finish()
        }
    }

    private fun selectAll() {
        boomChipPage?.selectAll()
    }

    private fun shareAll() {
        val shareText = boomChipPage?.originalText ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        })
        finish()
    }

    private fun showPlaceholder() {
        Toast.makeText(this, R.string.bigbang_action_placeholder, Toast.LENGTH_SHORT).show()
    }

    private fun segmentLocally(text: String) {
        if (DBG) {
            Log.d(TAG, "text=$text")
        }
        Thread {
            try {
                val result = CppJiebaTokenizer.get(this).segment(text)
                runOnUiThread {
                    if (!isFinishing) {
                        handleInitialSegmentResult(text, result)
                    }
                }
            } catch (e: RuntimeException) {
                LogUtils.e(TAG, "local segmentation failed")
                LogUtils.e(e.message, e)
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun handleInitialSegmentResult(text: String, result: IntArray?) {
        if (result == null || result.isEmpty()) {
            Log.e(TAG, "Segmentation fails for text=$text")
            finish()
            return
        }
        val touchIndex = intent.getIntExtra("boom_index", -1)
        val touchedX = intent.getIntExtra("boom_startx", -1)
        val touchedY = intent.getIntExtra("boom_starty", -1)
        if (boomChipPage?.initWords(result, text, touchIndex, touchedX, touchedY) != true) {
            val log = buildString {
                result.forEach {
                    append(it)
                    append(", ")
                }
            }
            Log.w(TAG, "No words left after segment, input=$text, output=$log")
            if (intent.getStringExtra("boom_image") != null) {
                Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }
        currentText = text
        currentSegment = result
    }

    private fun loadAdjacent(direction: String) {
        val adjacentText = TextSessionCoordinator.peekAdjacentText(direction)?.trim().orEmpty()
        val baseSegment = currentSegment
        if (adjacentText.isEmpty() || baseSegment == null || currentText.isEmpty()) {
            boomChipPage?.finishAdjacentPull()
            return
        }
        Thread {
            try {
                val adjacentSegment = CppJiebaTokenizer.get(this).segment(adjacentText)
                runOnUiThread {
                    if (isFinishing) {
                        return@runOnUiThread
                    }
                    if (adjacentSegment == null || adjacentSegment.isEmpty()) {
                        boomChipPage?.finishAdjacentPull()
                        return@runOnUiThread
                    }
                    val merged = mergeSegmentedText(
                        direction = direction,
                        baseText = currentText,
                        baseSegment = baseSegment,
                        adjacentText = adjacentText,
                        adjacentSegment = adjacentSegment,
                    )
                    val replaced = boomChipPage?.replaceWords(merged.segment, merged.text) == true
                    if (!replaced) {
                        boomChipPage?.finishAdjacentPull()
                        return@runOnUiThread
                    }
                    TextSessionCoordinator.loadAdjacent(direction)
                    currentText = merged.text
                    currentSegment = merged.segment
                }
            } catch (e: RuntimeException) {
                LogUtils.e(TAG, "adjacent segmentation failed")
                LogUtils.e(e.message, e)
                runOnUiThread { boomChipPage?.finishAdjacentPull() }
            }
        }.start()
    }

    private fun mergeSegmentedText(
        direction: String,
        baseText: String,
        baseSegment: IntArray,
        adjacentText: String,
        adjacentSegment: IntArray,
    ): SegmentedText {
        val separator = "\n"
        return if (direction == "before") {
            SegmentedText(
                text = adjacentText + separator + baseText,
                segment = mergeSegments(
                    first = adjacentSegment,
                    firstOffset = 0,
                    second = baseSegment,
                    secondOffset = adjacentText.length + separator.length,
                ),
            )
        } else {
            SegmentedText(
                text = baseText + separator + adjacentText,
                segment = mergeSegments(
                    first = baseSegment,
                    firstOffset = 0,
                    second = adjacentSegment,
                    secondOffset = baseText.length + separator.length,
                ),
            )
        }
    }

    private fun mergeSegments(
        first: IntArray,
        firstOffset: Int,
        second: IntArray,
        secondOffset: Int,
    ): IntArray {
        val firstSplit = splitSegment(first)
        val secondSplit = splitSegment(second)
        return buildList {
            addAll(shiftPairs(firstSplit.words, firstOffset))
            addAll(shiftPairs(secondSplit.words, secondOffset))
            add(-1)
            addAll(shiftPairs(firstSplit.punctuations, firstOffset))
            addAll(shiftPairs(secondSplit.punctuations, secondOffset))
        }.toIntArray()
    }

    private fun splitSegment(segment: IntArray): SegmentParts {
        val separatorIndex = segment.indexOfFirst { it == -1 }
        if (separatorIndex < 0) {
            return SegmentParts(words = segment.toList(), punctuations = emptyList())
        }
        return SegmentParts(
            words = segment.take(separatorIndex),
            punctuations = segment.drop(separatorIndex + 1),
        )
    }

    private fun shiftPairs(values: List<Int>, offset: Int): List<Int> {
        if (offset == 0) return values
        return values.map { it + offset }
    }

    private data class SegmentParts(
        val words: List<Int>,
        val punctuations: List<Int>,
    )

    private data class SegmentedText(
        val text: String,
        val segment: IntArray,
    )

    override fun onSaveInstanceState(outState: Bundle) {
        boomChipPage?.captureSelectedState()?.let {
            outState.putSerializable(SELECTED_STATE, it)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val DBG = true
        const val EXTRA_DEBUG_PREVIEW_TEXT = "extra_debug_preview_text"
        const val EXTRA_ENABLE_ADJACENT_SESSION = "extra_enable_adjacent_session"

        private const val TAG = "BoomActivity"
        private const val SELECTED_STATE = "selected_state"
    }
}

@Composable
private fun BigBangOverlayContent(
    contentView: View,
    touchX: Int,
    touchY: Int,
    onDismiss: () -> Unit,
    onEditMode: () -> Unit,
    onSelectAll: () -> Unit,
    onShareAll: () -> Unit,
    onMore: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val panelMetrics = rememberOverlayPanelMetrics()
    val panelBackground = if (dark) Color(0xFF171B20) else Color(0xFFF3F3F4)
    val panelBorder = if (dark) Color(0xFF2E353E) else Color(0xFFD7D7DA)
    val scrimColor = if (dark) Color.Black.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.48f)
    val shadowColor = Color.Black.copy(alpha = 0.5f)
    val panelShape = androidx.compose.foundation.shape.RoundedCornerShape(panelMetrics.cornerRadius)
    var enterAnimationStarted by remember { mutableStateOf(false) }
    var panelBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val enterProgress by animateFloatAsState(
        targetValue = if (enterAnimationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bigbang_panel_enter",
    )
    val scrimProgress by animateFloatAsState(
        targetValue = if (enterAnimationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "bigbang_scrim_enter",
    )
    val transformOrigin = remember(panelBounds, touchX, touchY) {
        val bounds = panelBounds
        if (bounds == null || touchX < 0 || touchY < 0) {
            TransformOrigin.Center
        } else {
            TransformOrigin(
                pivotFractionX = ((touchX - bounds.left) / bounds.width).coerceIn(0f, 1f),
                pivotFractionY = ((touchY - bounds.top) / bounds.height).coerceIn(0f, 1f),
            )
        }
    }
    val panelScale = 0.84f + (0.16f * enterProgress)

    LaunchedEffect(Unit) {
        enterAnimationStarted = true
    }

    BackHandler(onBack = onDismiss)
    OverlayScene(scrimColor = scrimColor.copy(alpha = scrimColor.alpha * scrimProgress), onDismiss = onDismiss) {
        FloatingPanel(
            width = panelMetrics.width,
            height = panelMetrics.height,
            modifier = overlayPanelPlacement(panelMetrics)
                .onGloballyPositioned { coordinates ->
                    panelBounds = coordinates.boundsInWindow()
                }
                .graphicsLayer {
                    alpha = enterProgress
                    scaleX = panelScale
                    scaleY = panelScale
                    this.transformOrigin = transformOrigin
                },
            shape = panelShape,
            backgroundColor = panelBackground,
            borderColor = panelBorder,
            shadowColor = if (panelMetrics.multiWindow) null else shadowColor,
        ) {
            OverlayPanelScaffold(
                topBar = {
                    OverlayHeaderBar(
                        backgroundColor = if (dark) Color(0xFF1D2126) else Color.White,
                        leading = {
                            OverlayIconAction(
                                imageVector = Icons.Outlined.Edit,
                                tint = if (dark) Color(0xFFD7DEE7) else Color(0xFF6F6962),
                                onClick = onEditMode,
                                contentDescription = stringResource(R.string.bigbang_action_edit),
                            )
                            OverlayIconAction(
                                imageVector = Icons.Outlined.SelectAll,
                                tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF6C6760),
                                onClick = onSelectAll,
                                contentDescription = stringResource(R.string.bigbang_action_select_all),
                            )
                        },
                        center = {
                            androidx.compose.material3.Text(
                                text = stringResource(R.string.bigbang_overlay_title),
                                color = if (dark) Color(0xFFF2F5F8) else Color(0xFFD1CCC6),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        trailing = {
                            OverlayIconAction(
                                imageVector = Icons.Outlined.Share,
                                tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF6C6760),
                                onClick = onShareAll,
                                contentDescription = stringResource(R.string.bigbang_action_share_all),
                            )
                            OverlayIconAction(
                                imageVector = Icons.Outlined.MoreHoriz,
                                tint = if (dark) Color(0xFFD7DEE7) else Color(0xFF6F6962),
                                onClick = onMore,
                                contentDescription = stringResource(R.string.bigbang_action_more),
                            )
                        },
                    )
                },
                bottomBar = {
                    OverlayBottomBar(
                        backgroundColor = if (dark) Color(0xFF1D2126) else Color.White,
                    ) {
                        OverlayIconAction(
                            iconRes = R.drawable.boom_cancel,
                            tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF8D8983),
                            onClick = onDismiss,
                            contentDescription = stringResource(R.string.search_overlay_close),
                        )
                    }
                },
            ) { bodyModifier ->
                Column(modifier = bodyModifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 4.dp),
                        factory = {
                            contentView
                        },
                    )
                }
            }
        }
    }
}
