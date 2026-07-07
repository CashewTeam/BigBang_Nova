package com.cashewteam.novatext.android.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cashewteam.novatext.android.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SmartisanSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    darkTheme: Boolean = false,
) {
    val context = LocalContext.current

    val idMask   = if (darkTheme) R.drawable.switch_ex_mask_dark   else R.drawable.switch_ex_mask
    val idBottom = if (darkTheme) R.drawable.switch_ex_bottom_dark else R.drawable.switch_ex_bottom
    val idFrame  = if (darkTheme) R.drawable.switch_ex_frame_dark  else R.drawable.switch_ex_frame
    val idFrameOn= if (darkTheme) R.drawable.switch_ex_frame_pressed_dark else R.drawable.switch_ex_frame_pressed
    val idBtn    = if (darkTheme) R.drawable.switch_ex_unpressed_dark else R.drawable.switch_ex_unpressed
    val idBtnPr  = if (darkTheme) R.drawable.switch_ex_pressed_dark else R.drawable.switch_ex_pressed

    val maskBmp   = remember(idMask)   { BitmapFactory.decodeResource(context.resources, idMask) }
    val bottomBmp = remember(idBottom) { BitmapFactory.decodeResource(context.resources, idBottom) }
    val frameBmp  = remember(idFrame)  { BitmapFactory.decodeResource(context.resources, idFrame) }
    val frameOnBmp= remember(idFrameOn){ BitmapFactory.decodeResource(context.resources, idFrameOn) }
    val btnBmp    = remember(idBtn)    { BitmapFactory.decodeResource(context.resources, idBtn) }
    val btnPrBmp  = remember(idBtnPr)  { BitmapFactory.decodeResource(context.resources, idBtnPr) }

    val bmpW = bottomBmp.width.toFloat()  // 286

    val pillBounds = remember(maskBmp) {
        val w = maskBmp.width
        val h = maskBmp.height
        val pixels = IntArray(w * h)
        maskBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var l = w; var r = 0; var t = h; var b = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x] ushr 24 > 128) {
                    if (x < l) l = x; if (x > r) r = x
                    if (y < t) t = y; if (y > b) b = y
                }
            }
        }
        intArrayOf(l, t, r - l + 1, b - t + 1)
    }

    val density = LocalDensity.current
    val dispW = with(density) { maskBmp.width.toDp() }
    val dispH = with(density) { maskBmp.height.toDp() }

    val slideRange = 88f / bmpW

    var progress by remember { mutableStateOf(if (checked) 1f else 0f) }
    var isPressed by remember { mutableStateOf(false) }

    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "pressAlpha"
    )

    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 200),
        label = "slideProgress"
    )

    LaunchedEffect(checked) { progress = if (checked) 1f else 0f }

    Canvas(
        modifier = modifier
            .size(dispW, dispH)
            .pointerInput(enabled) {
                awaitEachGesture {
                    if (!enabled) return@awaitEachGesture
                    val down = awaitFirstDown()
                    isPressed = true
                    val startX = down.position.x
                    val startP = progress
                    var dragged = false
                    var prevPos = down.position.x
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        change.consume()
                        val curX = change.position.x
                        val dx = curX - startX
                        if (abs(curX - prevPos) > 0.5f) {
                            dragged = true
                        }
                        progress = (startP + dx / size.width.toFloat()).coerceIn(0f, 1f)
                        prevPos = curX
                    } while (change.pressed)
                    isPressed = false
                    val newChecked = if (dragged) progress > 0.5f else progress < 0.5f
                    progress = if (newChecked) 1f else 0f
                    onCheckedChange?.invoke(newChecked)
                }
            },
    ) {
        val pl = pillBounds[0].toFloat(); val pt = pillBounds[1].toFloat()
        val pw = pillBounds[2].toFloat(); val ph = pillBounds[3].toFloat()

        val voff = ((size.height - bottomBmp.height) / 2f).roundToInt()
        val btnAdj = 3f
        val slideOff = (-(1f - animProgress) * slideRange * bmpW).roundToInt()
        val btnOff = (-(1f - animProgress) * slideRange * bmpW + btnAdj).roundToInt()

        val pillPath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(pl, pt, pl + pw, pt + ph, CornerRadius(ph / 2f)))
        }
        clipPath(pillPath) {
            drawImage(bottomBmp.asImageBitmap(),
                dstOffset = IntOffset(slideOff, voff),
                dstSize = IntSize(bottomBmp.width, bottomBmp.height))
        }
        drawImage(frameBmp.asImageBitmap(),
            dstOffset = IntOffset(0, voff),
            dstSize = IntSize(frameBmp.width, frameBmp.height))
        if (pressAlpha > 0.01f) {
            drawImage(frameOnBmp.asImageBitmap(),
                dstOffset = IntOffset(0, voff),
                dstSize = IntSize(frameOnBmp.width, frameOnBmp.height),
                alpha = pressAlpha)
        }
        drawImage(btnBmp.asImageBitmap(),
            dstOffset = IntOffset(btnOff, voff),
            dstSize = IntSize(btnBmp.width, btnBmp.height))
        if (pressAlpha > 0.01f) {
            drawImage(btnPrBmp.asImageBitmap(),
                dstOffset = IntOffset(btnOff, voff),
                dstSize = IntSize(btnPrBmp.width, btnPrBmp.height),
                alpha = pressAlpha)
        }
    }
}
