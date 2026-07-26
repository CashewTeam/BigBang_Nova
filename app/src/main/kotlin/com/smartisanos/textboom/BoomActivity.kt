package com.cashewteam.novatext.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.core.view.WindowCompat
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.data.CppJiebaTokenizer
import com.cashewteam.novatext.android.domain.capture.CaptureTextBlockContract
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator
import com.cashewteam.novatext.android.service.BoomOcrLauncher
import com.cashewteam.novatext.android.service.FloatingBallService
import com.cashewteam.novatext.android.util.LogUtils

class BoomActivity : ComponentActivity() {
    private enum class PendingDiscardAction {
        EXIT_EDIT,
        DISMISS,
        NEW_INTENT,
    }

    private var boomChipPage: BoomChipPage? = null
    private lateinit var legacyContentView: View
    private lateinit var settings: BigBangSettings
    private var launchTouchX = -1
    private var launchTouchY = -1
    private var currentText = ""
    private var currentSegment: IntArray? = null
    private var manualOcrSourceToken: String? = null
    private var floatingBallHideToken: Int? = null
    private var animatedDismissRequester: (() -> Unit)? = null
    private var editMode by mutableStateOf(false)
    private var editTransitioning by mutableStateOf(false)
    private var editAllSelected by mutableStateOf(false)
    private var editSelectAllEnabled by mutableStateOf(false)
    private var editCanUndo by mutableStateOf(false)
    private var editCanRedo by mutableStateOf(false)
    private var editCommitRequest = 0
    private var pendingDiscardAction by mutableStateOf<PendingDiscardAction?>(null)
    private var pendingIncomingIntent: Intent? = null
    private var discardDismissAuthorized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BigBangSettings.get(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        launchTouchX = intent.getIntExtra("boom_startx", -1)
        launchTouchY = intent.getIntExtra("boom_starty", -1)
        manualOcrSourceToken = intent.getStringExtra(EXTRA_MANUAL_OCR_SOURCE_TOKEN)

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
        val savedEditSession = savedInstanceState?.getSerializable(EDIT_SESSION) as? BoomChipPage.EditSessionState
        boomChipPage = BoomChipPage(this, legacyContentView, false).also { page ->
            if (savedEditSession == null) {
                page.restoreSelectedState(savedInstanceState?.getSerializable(SELECTED_STATE))
            }
            page.setOnAdjacentRequestListener(object : BoomChipPage.OnAdjacentRequestListener {
                override fun onAdjacentRequest(direction: String) {
                    loadAdjacent(direction)
                }
            })
            page.setOnEditUiStateListener { allSelected, selectAllEnabled, canUndo, canRedo ->
                editAllSelected = allSelected
                editSelectAllEnabled = selectAllEnabled
                editCanUndo = canUndo
                editCanRedo = canRedo
            }
        }

        setContent {
            BigBangOverlayContent(
                contentView = legacyContentView,
                touchX = launchTouchX,
                touchY = launchTouchY,
                manualOcrSourceToken = manualOcrSourceToken,
                classicOverlayStyleEnabled = settings.isClassicOverlayStyleEnabled,
                ocrRecognizerMode = settings.ocrRecognizerMode,
                onDismissRequesterChanged = { animatedDismissRequester = it },
                onDismissRequest = { shouldDismissPage() },
                onDismissFinished = { finish() },
                showDiscardConfirmation = pendingDiscardAction != null,
                onConfirmDiscard = { confirmDiscard() },
                onSaveEditToClipboard = { saveEditToClipboardAndContinue() },
                onCancelDiscard = { cancelDiscard() },
                onOcr = { reopenManualOcr() },
                onLanguageSelected = { rerunOcrWithLanguage(it) },
                isEditMode = editMode,
                editTransitioning = editTransitioning,
                editAllSelected = editAllSelected,
                editSelectAllEnabled = editSelectAllEnabled,
                editCanUndo = editCanUndo,
                editCanRedo = editCanRedo,
                onEditMode = { enterEditMode() },
                onExitEditMode = { requestExitEditMode() },
                onShowKeyboard = { boomChipPage?.showEditorKeyboard() },
                onSelectAll = { selectAll() },
                onUndo = { boomChipPage?.undoEdit() },
                onRedo = { boomChipPage?.redoEdit() },
                onShareAll = { shareAll() },
                onMore = { showPlaceholder() },
            )
        }

        // Restore adjacent-pulled text across rotation. An edit session owns
        // its editable layout and must be restored before intent input wins.
        val savedText = savedInstanceState?.getString(SAVED_TEXT)
        val savedSegment = savedInstanceState?.getIntArray(SAVED_SEGMENT)
        if (savedEditSession != null && boomChipPage?.restoreEditSession(savedEditSession) == true) {
            currentText = savedText ?: savedEditSession.originalText
            currentSegment = savedSegment
            editMode = true
        } else if (savedText != null && savedSegment != null) {
            currentText = savedText
            currentSegment = savedSegment
            handleInitialSegmentResult(savedText, savedSegment, fromSavedState = true)
        } else {
            reinitializeFromIntent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!requestDiscardConfirmation(PendingDiscardAction.NEW_INTENT, intent)) {
            return
        }
        acceptNewIntent(intent)
    }

    override fun onDestroy() {
        boomChipPage?.release()
        super.onDestroy()
    }

    /**
     * Read the current intent extras and reinitialise the entire page.
     * Called from [onCreate] (fresh launch) and [onNewIntent] (new intent
     * delivered to an existing instance when launchMode is singleTop).
     */
    private fun reinitializeFromIntent() {
        editCommitRequest++
        editTransitioning = false
        editMode = false
        val previewText = intent.getStringExtra(EXTRA_DEBUG_PREVIEW_TEXT)
        val inputText = previewText
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (inputText.isNullOrEmpty()) {
            finish()
            return
        }

        // Reset state from the new intent
        launchTouchX = intent.getIntExtra("boom_startx", -1)
        launchTouchY = intent.getIntExtra("boom_starty", -1)
        manualOcrSourceToken = intent.getStringExtra(EXTRA_MANUAL_OCR_SOURCE_TOKEN)
        currentText = ""
        currentSegment = null

        // Clear existing chip content so initWords can rebuild from scratch
        boomChipPage?.prepareForReinit()

        // Set up adjacent session
        val hasAdjacentSession = intent.getBooleanExtra(EXTRA_ENABLE_ADJACENT_SESSION, false)
        val adjacentBefore = intent.getStringExtra(EXTRA_ADJACENT_TEXT_BEFORE)
        val adjacentAfter = intent.getStringExtra(EXTRA_ADJACENT_TEXT_AFTER)
        if (!hasAdjacentSession && adjacentBefore.isNullOrEmpty() && adjacentAfter.isNullOrEmpty()) {
            TextSessionCoordinator.clearSession()
        } else {
            populateExternalAdjacentSession(inputText, adjacentBefore, adjacentAfter)
        }

        segmentLocally(inputText)
    }

    override fun onStart() {
        super.onStart()
        if (floatingBallHideToken == null) {
            floatingBallHideToken = FloatingBallService.acquireVisibilitySuppression()
        }
        FloatingBallService.notifyBigBangShellShown()
    }

    override fun onStop() {
        FloatingBallService.releaseVisibilitySuppression(floatingBallHideToken)
        floatingBallHideToken = null
        super.onStop()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun shouldDismissPage(): Boolean {
        if (discardDismissAuthorized) {
            discardDismissAuthorized = false
            return true
        }
        // A dirty edit session takes priority over clearing a transient chip
        // selection: an explicit close must always preserve the user's chance
        // to keep or discard the edited text.
        if (boomChipPage?.isEditDirty == true) {
            return requestDiscardConfirmation(PendingDiscardAction.DISMISS)
        }
        if (boomChipPage?.handleClick() == true) {
            return false
        }
        return true
    }

    fun requestAnimatedDismissFromLegacy() {
        if (requestDiscardConfirmation(PendingDiscardAction.DISMISS)) {
            animatedDismissRequester?.invoke() ?: finish()
        }
    }

    /** New capture text never overwrites an unsaved editor without an explicit choice. */
    private fun acceptNewIntent(nextIntent: Intent) {
        setIntent(nextIntent)
        reinitializeFromIntent()
    }

    private fun requestDiscardConfirmation(
        action: PendingDiscardAction,
        incomingIntent: Intent? = null,
    ): Boolean {
        if (boomChipPage?.isEditDirty != true) {
            return true
        }
        pendingDiscardAction = action
        pendingIncomingIntent = incomingIntent
        return false
    }

    private fun confirmDiscard() {
        completePendingDiscardAction()
    }

    private fun saveEditToClipboardAndContinue() {
        val text = boomChipPage?.editText ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(this, R.string.bigbang_edit_saved_clipboard, Toast.LENGTH_SHORT).show()
        completePendingDiscardAction()
    }

    private fun completePendingDiscardAction() {
        val action = pendingDiscardAction
        val nextIntent = pendingIncomingIntent
        pendingDiscardAction = null
        pendingIncomingIntent = null
        when (action) {
            PendingDiscardAction.EXIT_EDIT -> discardEditModeAndReturn()

            PendingDiscardAction.DISMISS -> {
                // The animation callback asks shouldDismissPage once more. Keep
                // this single-use permit so the dialog is not shown twice.
                discardDismissAuthorized = true
                animatedDismissRequester?.invoke() ?: finish()
            }

            PendingDiscardAction.NEW_INTENT -> {
                if (nextIntent != null) {
                    acceptNewIntent(nextIntent)
                }
            }

            null -> Unit
        }
    }

    private fun cancelDiscard() {
        pendingDiscardAction = null
        pendingIncomingIntent = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN
            && event.repeatCount == 0
            && editMode
            && !editTransitioning
            && (event.isCtrlPressed || event.isMetaPressed)
        ) {
            val page = boomChipPage
            val handled = when (event.keyCode) {
                KeyEvent.KEYCODE_A -> page?.selectAllForEditShortcut() ?: false
                KeyEvent.KEYCODE_C -> page?.copyEditSelection() ?: false
                KeyEvent.KEYCODE_X -> page?.cutEditSelection() ?: false
                KeyEvent.KEYCODE_V -> page?.pasteEditClipboard() ?: false
                KeyEvent.KEYCODE_Z -> if (event.isShiftPressed) {
                    page?.redoEdit() ?: false
                } else {
                    page?.undoEdit() ?: false
                }
                KeyEvent.KEYCODE_Y -> page?.redoEdit() ?: false
                else -> false
            }
            if (handled || isEditShortcutKey(event.keyCode)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isEditShortcutKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_A
            || keyCode == KeyEvent.KEYCODE_C
            || keyCode == KeyEvent.KEYCODE_X
            || keyCode == KeyEvent.KEYCODE_V
            || keyCode == KeyEvent.KEYCODE_Y
            || keyCode == KeyEvent.KEYCODE_Z
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
    }

    private fun showPlaceholder() {
        Toast.makeText(this, R.string.bigbang_action_placeholder, Toast.LENGTH_SHORT).show()
    }

    private fun enterEditMode() {
        if (editTransitioning || boomChipPage?.enterEditMode() != true) {
            return
        }
        editMode = true
    }

    private fun requestExitEditMode() {
        if (!editMode || editTransitioning) {
            return
        }
        if (requestDiscardConfirmation(PendingDiscardAction.EXIT_EDIT)) {
            exitEditMode()
        }
    }

    private fun discardEditModeAndReturn() {
        val page = boomChipPage ?: return
        val segment = currentSegment ?: return
        editCommitRequest++
        editTransitioning = false
        if (page.discardEditMode(segment, currentText)) {
            editMode = false
        } else {
            Toast.makeText(this, R.string.bigbang_edit_discard_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitEditMode() {
        val page = boomChipPage ?: return
        if (!editMode || editTransitioning) {
            return
        }
        editTransitioning = true
        if (!page.beginEditCommit()) {
            editTransitioning = false
            return
        }
        val text = page.editText ?: run {
            page.cancelEditCommit()
            editTransitioning = false
            return
        }
        if (text.isBlank()) {
            // cppjieba intentionally has no segment for whitespace-only text; keep the edit result.
            if (page.commitEditMode(intArrayOf(-1))) {
                currentText = text
                currentSegment = intArrayOf(-1)
                editMode = false
            } else {
                page.cancelEditCommit()
                Toast.makeText(this, R.string.bigbang_edit_commit_failed, Toast.LENGTH_SHORT).show()
            }
            editTransitioning = false
            return
        }
        val request = ++editCommitRequest
        Thread {
            try {
                val segment = CppJiebaTokenizer.get(this).segment(text)
                runOnUiThread {
                    if (isFinishing || request != editCommitRequest || !page.isEditMode) {
                        return@runOnUiThread
                    }
                    if (segment == null || segment.isEmpty() || !page.commitEditMode(segment)) {
                        page.cancelEditCommit()
                        Toast.makeText(this, R.string.bigbang_edit_commit_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        currentText = text
                        currentSegment = segment
                        editMode = false
                    }
                    editTransitioning = false
                }
            } catch (e: RuntimeException) {
                LogUtils.e(TAG, "edit segmentation failed")
                LogUtils.e(e.message, e)
                runOnUiThread {
                    if (!isFinishing && request == editCommitRequest && page.isEditMode) {
                        page.cancelEditCommit()
                        Toast.makeText(this, R.string.bigbang_edit_commit_failed, Toast.LENGTH_SHORT).show()
                        editTransitioning = false
                    }
                }
            }
        }.start()
    }

    private fun reopenManualOcr() {
        val source = ManualOcrSourceStore.get(manualOcrSourceToken) ?: return
        BoomOcrLauncher.open(
            context = this,
            imageUri = source.imageUri,
            touchX = source.touchX,
            touchY = source.touchY,
            fullscreen = source.fullscreen,
            callerPackage = source.callerPackage,
            offsetX = source.offsetX,
            offsetY = source.offsetY,
            manualOcrSourceToken = source.token,
        )
    }

    private fun rerunOcrWithLanguage(mode: String) {
        val source = ManualOcrSourceStore.get(manualOcrSourceToken) ?: return
        val replayMode = source.replayMode ?: return
        BoomOcrLauncher.replayWithLanguage(
            context = this,
            sourceToken = source.token,
            touchX = source.touchX,
            touchY = source.touchY,
            mode = mode,
            replayMode = replayMode,
        )
    }

    private fun segmentLocally(text: String) {
        if (DBG) {
            LogUtils.d(TAG, "text=$text")
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

    /**
     * Build a [TextSessionCoordinator] session from externally supplied text so that
     * the "炸了又炸" (adjacent pull) feature works when a third-party app provides
     * before/after paragraphs via intent extras.
     *
     * Each line (split by `\n`) in the adjacent text becomes a separate paragraph
     * so that sliding up/down loads one line at a time, mimicking the original
     * accessibility-based behaviour where each on-screen paragraph is a distinct
     * [CaptureTextBlockContract].
     */
    private fun populateExternalAdjacentSession(
        mainText: String,
        adjacentBefore: String?,
        adjacentAfter: String?,
    ) {
        val hasBefore = !adjacentBefore.isNullOrEmpty()
        val hasAfter = !adjacentAfter.isNullOrEmpty()
        if (!hasBefore && !hasAfter) return

        val paragraphs = mutableListOf<CaptureTextBlockContract>()
        var lineIndex = 0

        // Split before text by newlines; each non-blank line becomes a paragraph.
        // Incrementing top values ensure ParagraphWindow.mergeAdjacent sorts
        // lines in the correct reading order when short lines are batched.
        if (hasBefore) {
            adjacentBefore!!.lines().filter { it.isNotBlank() }.forEach { line ->
                paragraphs += CaptureTextBlockContract(
                    text = line.trimEnd(),
                    left = 0.0, top = lineIndex.toDouble(), right = 0.0, bottom = lineIndex.toDouble(),
                    confidence = 1.0,
                )
                lineIndex++
            }
        }

        val mainIndex = paragraphs.size
        paragraphs += CaptureTextBlockContract(
            text = mainText,
            left = 0.0, top = lineIndex.toDouble(), right = 0.0, bottom = lineIndex.toDouble(),
            confidence = 1.0,
        )
        lineIndex++

        if (hasAfter) {
            adjacentAfter!!.lines().filter { it.isNotBlank() }.forEach { line ->
                paragraphs += CaptureTextBlockContract(
                    text = line.trimEnd(),
                    left = 0.0, top = lineIndex.toDouble(), right = 0.0, bottom = lineIndex.toDouble(),
                    confidence = 1.0,
                )
                lineIndex++
            }
        }

        TextSessionCoordinator.replaceSession(
            paragraphs = paragraphs,
            initialIndex = mainIndex,
            source = "external",
            debugMessage = "channel=external; paragraphs=${paragraphs.size}; adjacent=before=$hasBefore,after=$hasAfter",
        )
    }

    private fun handleInitialSegmentResult(text: String, result: IntArray?, fromSavedState: Boolean = false) {
        if (result == null || result.isEmpty()) {
            Log.e(TAG, "Segmentation fails for text=$text")
            finish()
            return
        }
        val selectedCharIndex = if (fromSavedState) -1 else intent.getIntExtra(EXTRA_SELECTED_CHAR_INDEX, -1)
        val touchIndex = if (selectedCharIndex >= 0) selectedCharIndex
            else if (fromSavedState) -1 else intent.getIntExtra("boom_index", -1)
        val touchedX = if (fromSavedState) -1 else intent.getIntExtra("boom_startx", -1)
        val touchedY = if (fromSavedState) -1 else intent.getIntExtra("boom_starty", -1)
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
        if (selectedCharIndex >= 0) {
            boomChipPage?.selectTouchedWord()
        }
    }

    private fun loadAdjacent(direction: String) {
        if (editMode || boomChipPage?.isEditMode == true) {
            boomChipPage?.finishAdjacentPull()
            return
        }
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
                    if (isFinishing || editMode || boomChipPage?.isEditMode == true) {
                        boomChipPage?.finishAdjacentPull()
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
                    val charOffset = if (direction == "before") adjacentText.length + 1 else 0
                    val replaced = boomChipPage?.replaceWords(
                        merged.segment,
                        merged.text,
                        merged.targetWordIndex,
                        charOffset,
                    ) == true
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
                targetWordIndex = 0,
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
                targetWordIndex = wordCount(baseSegment),
            )
        }
    }

    private fun wordCount(segment: IntArray): Int {
        return splitSegment(segment).words.size / 2
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
        val targetWordIndex: Int,
    )

    override fun onSaveInstanceState(outState: Bundle) {
        boomChipPage?.captureEditSession()?.let { outState.putSerializable(EDIT_SESSION, it) }
            ?: boomChipPage?.captureSelectedState()?.let { outState.putSerializable(SELECTED_STATE, it) }
        if (currentSegment != null) {
            outState.putString(SAVED_TEXT, currentText)
            outState.putIntArray(SAVED_SEGMENT, currentSegment)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val DBG = true
        const val EXTRA_DEBUG_PREVIEW_TEXT = "extra_debug_preview_text"
        const val EXTRA_ENABLE_ADJACENT_SESSION = "extra_enable_adjacent_session"
        const val EXTRA_MANUAL_OCR_SOURCE_TOKEN = "extra_manual_ocr_source_token"
        const val EXTRA_ADJACENT_TEXT_BEFORE = "extra_adjacent_text_before"
        const val EXTRA_ADJACENT_TEXT_AFTER = "extra_adjacent_text_after"
        const val EXTRA_SELECTED_CHAR_INDEX = "extra_selected_char_index"
        const val ACTION_BIGBANG_TEXT = "com.cashewteam.novatext.android.action.BIGBANG_TEXT"

        private const val TAG = "BoomActivity"
        private const val SELECTED_STATE = "selected_state"
        private const val EDIT_SESSION = "edit_session"
        private const val SAVED_TEXT = "saved_text"
        private const val SAVED_SEGMENT = "saved_segment"
    }
}

@Composable
private fun BigBangOverlayContent(
    contentView: View,
    touchX: Int,
    touchY: Int,
    manualOcrSourceToken: String?,
    classicOverlayStyleEnabled: Boolean,
    ocrRecognizerMode: String,
    onDismissRequesterChanged: ((() -> Unit)?) -> Unit,
    onDismissRequest: () -> Boolean,
    onDismissFinished: () -> Unit,
    showDiscardConfirmation: Boolean,
    onConfirmDiscard: () -> Unit,
    onSaveEditToClipboard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onOcr: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    isEditMode: Boolean,
    editTransitioning: Boolean,
    editAllSelected: Boolean,
    editSelectAllEnabled: Boolean,
    editCanUndo: Boolean,
    editCanRedo: Boolean,
    onEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onShowKeyboard: () -> Unit,
    onSelectAll: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShareAll: () -> Unit,
    onMore: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val panelMetrics = rememberOverlayPanelMetrics(
        forceFullscreen = classicOverlayStyleEnabled || isEditMode,
    )
    val panelBackground = if (dark) Color(0xFF171B20) else Color(0xFFF3F3F4)
    val panelBorder = if (dark) Color(0xFF2E353E) else Color(0xFFD7D7DA)
    val topBarColor = if (dark) Color(0xFF1D2126) else Color.White
    val bottomBarColor = if (dark) Color(0xFF1D2126) else Color.White
    val scrimColor = if (dark) Color.Black.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.48f)
    val shadowColor = Color.Black.copy(alpha = 0.5f)
    val panelShape = androidx.compose.foundation.shape.RoundedCornerShape(panelMetrics.cornerRadius)
    var panelVisible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var panelBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val enterProgress by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bigbang_panel_enter",
        finishedListener = {
            if (dismissing && it == 0f) {
                onDismissFinished()
            }
        },
    )
    val scrimProgress by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
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
    val manualOcrRevision by ManualOcrSourceStore.revisionFlow().collectAsState()
    val ocrSource = remember(manualOcrSourceToken, manualOcrRevision) {
        ManualOcrSourceStore.get(manualOcrSourceToken)
    }
    val ocrEnabled = ocrSource != null
    val languageEnabled = ocrSource?.replayMode != null
    val activeOcrMode = ocrSource?.ocrMode ?: ocrRecognizerMode
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val languageOptions = listOf(
        stringResource(R.string.ocr_mode_chinese) to BigBangSettings.OCR_MODE_CHINESE,
        stringResource(R.string.ocr_mode_japanese) to BigBangSettings.OCR_MODE_JAPANESE,
        stringResource(R.string.ocr_mode_korean) to BigBangSettings.OCR_MODE_KOREAN,
        stringResource(R.string.ocr_mode_latin) to BigBangSettings.OCR_MODE_LATIN,
    )
    val requestDismiss = {
        if (!dismissing && onDismissRequest()) {
            languageMenuExpanded = false
            dismissing = true
            panelVisible = false
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            languageMenuExpanded = false
        }
    }

    DisposableEffect(requestDismiss) {
        onDismissRequesterChanged(requestDismiss)
        onDispose {
            onDismissRequesterChanged(null)
        }
    }

    LaunchedEffect(Unit) {
        panelVisible = true
    }

    BackHandler(onBack = requestDismiss)
    ApplyOverlaySystemBars(
        statusBarColor = if (panelMetrics.fullScreen) topBarColor else Color.Transparent,
        navigationBarColor = if (panelMetrics.fullScreen) bottomBarColor else Color.Transparent,
        darkIcons = !dark,
    )
    OverlayScene(scrimColor = scrimColor.copy(alpha = scrimColor.alpha * scrimProgress), onDismiss = requestDismiss) {
        FloatingPanel(
            width = panelMetrics.width,
            height = panelMetrics.height,
            fillMax = panelMetrics.fullScreen,
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
                        backgroundColor = topBarColor,
                        topInset = panelMetrics.topSystemInset,
                        leftInset = panelMetrics.leftSystemInset,
                        rightInset = panelMetrics.rightSystemInset,
                        contentHeight = if (isEditMode) 48.dp else 52.dp,
                        horizontalPadding = if (isEditMode) 12.dp else 14.dp,
                        leadingItemSpacing = if (isEditMode) 12.dp else 8.dp,
                        trailingItemSpacing = if (isEditMode) 11.dp else 8.dp,
                        leading = {
                            if (isEditMode) {
                                OverlayIconAction(
                                    iconRes = R.drawable.action_bar_back_selector,
                                    tint = null,
                                    invertAssetColors = dark,
                                    enabled = !editTransitioning,
                                    onClick = onExitEditMode,
                                    contentDescription = stringResource(R.string.bigbang_action_exit_edit),
                                )
                                OverlayIconAction(
                                    iconRes = R.drawable.action_bar_close_selector,
                                    tint = null,
                                    invertAssetColors = dark,
                                    enabled = !editTransitioning,
                                    onClick = requestDismiss,
                                    contentDescription = stringResource(R.string.bigbang_action_close),
                                )
                            } else {
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
                            }
                        },
                        center = {
                            androidx.compose.material3.Text(
                                text = stringResource(R.string.bigbang_overlay_title),
                                color = if (dark) Color(0xFFF2F5F8) else Color(0xFFD1CCC6),
                                fontSize = if (isEditMode) 18.sp else 20.sp,
                                fontWeight = if (isEditMode) FontWeight.Bold else FontWeight.SemiBold,
                            )
                        },
                        trailing = {
                            if (isEditMode) {
                                OverlayIconAction(
                                    iconRes = R.drawable.action_bar_choosetext_selector,
                                    tint = null,
                                    invertAssetColors = dark,
                                    enabled = editSelectAllEnabled && !editTransitioning,
                                    onClick = onSelectAll,
                                    contentDescription = stringResource(
                                        if (editAllSelected) {
                                            R.string.bigbang_action_cancel_select_all
                                        } else {
                                            R.string.bigbang_action_select_all
                                        },
                                    ),
                                )
                            }
                            if (isEditMode) {
                                OverlayIconAction(
                                    iconRes = R.drawable.action_bar_share_selector,
                                    tint = null,
                                    invertAssetColors = dark,
                                    enabled = !editTransitioning,
                                    onClick = onShareAll,
                                    contentDescription = stringResource(R.string.bigbang_action_share_all),
                                )
                            } else {
                                OverlayIconAction(
                                    imageVector = Icons.Outlined.Share,
                                    tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF6C6760),
                                    enabled = !editTransitioning,
                                    onClick = onShareAll,
                                    contentDescription = stringResource(R.string.bigbang_action_share_all),
                                )
                            }
                            if (!isEditMode) {
                                OverlayIconAction(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    tint = if (dark) Color(0xFFD7DEE7) else Color(0xFF6F6962),
                                    onClick = onMore,
                                    contentDescription = stringResource(R.string.bigbang_action_more),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    OverlayBottomBar(
                        backgroundColor = bottomBarColor,
                        bottomInset = panelMetrics.bottomSystemInset,
                        leftInset = panelMetrics.leftSystemInset,
                        rightInset = panelMetrics.rightSystemInset,
                        contentHeight = if (isEditMode) 48.dp else 52.dp,
                        horizontalPadding = if (isEditMode) 12.dp else 14.dp,
                        leading = {
                            if (!isEditMode) {
                                OverlayIconAction(
                                    imageVector = Icons.Outlined.DocumentScanner,
                                    tint = if (ocrEnabled) {
                                        if (dark) Color(0xFFF2F5F8) else Color(0xFF8D8983)
                                    } else {
                                        if (dark) Color(0x66F2F5F8) else Color(0x668D8983)
                                    },
                                    enabled = ocrEnabled,
                                    onClick = onOcr,
                                    contentDescription = stringResource(R.string.bigbang_action_ocr),
                                )
                            }
                        },
                        center = {
                            if (isEditMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OverlayIconAction(
                                        iconRes = R.drawable.action_bar_undo_selector,
                                        tint = null,
                                        invertAssetColors = dark,
                                        enabled = editCanUndo && !editTransitioning,
                                        onClick = onUndo,
                                        contentDescription = stringResource(R.string.bigbang_action_undo),
                                    )
                                    OverlayIconAction(
                                        iconRes = R.drawable.action_bar_revoke_selector,
                                        tint = null,
                                        invertAssetColors = dark,
                                        enabled = editCanRedo && !editTransitioning,
                                        onClick = onRedo,
                                        contentDescription = stringResource(R.string.bigbang_action_redo),
                                    )
                                }
                            } else {
                                OverlayIconAction(
                                    iconRes = R.drawable.boom_cancel,
                                    tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF8D8983),
                                    onClick = requestDismiss,
                                    contentDescription = stringResource(R.string.search_overlay_close),
                                )
                            }
                        },
                        trailing = {
                            if (isEditMode) {
                                OverlayIconAction(
                                    iconRes = R.drawable.action_bar_keyboard_selector,
                                    tint = null,
                                    invertAssetColors = dark,
                                    enabled = !editTransitioning,
                                    onClick = onShowKeyboard,
                                    contentDescription = stringResource(R.string.bigbang_action_keyboard),
                                )
                            } else {
                                Box {
                                    OverlayIconAction(
                                        imageVector = Icons.Outlined.Language,
                                        tint = if (languageEnabled) {
                                            if (dark) Color(0xFFF2F5F8) else Color(0xFF8D8983)
                                        } else {
                                            if (dark) Color(0x66F2F5F8) else Color(0x668D8983)
                                        },
                                        enabled = languageEnabled,
                                        onClick = { languageMenuExpanded = true },
                                        contentDescription = stringResource(R.string.bigbang_action_language),
                                    )
                                    DropdownMenu(
                                        expanded = languageMenuExpanded,
                                        onDismissRequest = { languageMenuExpanded = false },
                                        containerColor = if (dark) Color(0xFF20252B) else Color.White,
                                    ) {
                                        languageOptions.forEach { (title, value) ->
                                            DropdownMenuItem(
                                                text = {
                                                    androidx.compose.material3.Text(
                                                        text = title,
                                                        color = if (dark) Color(0xFFF2F5F8) else Color(0xFF3B3B3B),
                                                    )
                                                },
                                                onClick = {
                                                    languageMenuExpanded = false
                                                    onLanguageSelected(value)
                                                },
                                                enabled = value != activeOcrMode,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
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
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.bigbang_edit_discard_title),
                )
            },
            text = {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.bigbang_edit_discard_message),
                )
            },
            confirmButton = {
                // Keep the destructive choice at the far left, followed by
                // continuing the session and the non-destructive clipboard exit.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onConfirmDiscard) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.bigbang_edit_discard_confirm),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onCancelDiscard) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.bigbang_edit_discard_cancel),
                        )
                    }
                    TextButton(onClick = onSaveEditToClipboard) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.bigbang_edit_discard_save_clipboard),
                        )
                    }
                }
            },
        )
    }
}
