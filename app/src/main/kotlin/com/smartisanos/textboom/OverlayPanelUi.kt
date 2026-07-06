package com.cashewteam.novatext.android

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.ceil

private val OverlayBottomBarContentOffset = (-2).dp

@Composable
internal fun OverlayScene(
    scrimColor: Color,
    onDismiss: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val dismissModifier = if (onDismiss != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .then(dismissModifier),
        )
        content()
    }
}

@Composable
internal fun FloatingPanel(
    width: Dp,
    height: Dp,
    fillMax: Boolean = false,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color,
    borderColor: Color = Color.Transparent,
    shadowColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = if (fillMax) {
            modifier.fillMaxSize()
        } else {
            modifier
                .requiredWidth(width)
                .requiredHeight(height)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (shadowColor != null) {
            PanelShadow(
                shape = shape,
                shadowColor = shadowColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = shape,
            color = backgroundColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

internal data class OverlayPanelMetrics(
    val width: Dp,
    val height: Dp,
    val offsetY: Dp,
    val cornerRadius: Dp,
    val multiWindow: Boolean,
    val fullScreen: Boolean,
    val topSystemInset: Dp,
    val bottomSystemInset: Dp,
    val leftSystemInset: Dp,
    val rightSystemInset: Dp,
)

@Composable
internal fun rememberOverlayPanelMetrics(forceFullscreen: Boolean = false): OverlayPanelMetrics {
    val configuration = LocalConfiguration.current
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val inMultiWindow = activity?.isInMultiWindowMode == true
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullScreen = forceFullscreen || inMultiWindow || landscape
    val systemBarsInsets = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    val topSystemInset = if (fullScreen) {
        with(density) { ((systemBarsInsets?.top ?: 0) * 3 / 4).toDp() }
    } else {
        0.dp
    }
    val bottomSystemInset = if (fullScreen && landscape) {
        with(density) { (systemBarsInsets?.bottom ?: 0).toDp() / 3 }
    } else if (fullScreen) {
        with(density) { (systemBarsInsets?.bottom ?: 0).toDp() }
    } else {
        0.dp
    }
    val leftSystemInset = if (fullScreen) {
        with(density) { (systemBarsInsets?.left ?: 0).toDp() }
    } else {
        0.dp
    }
    val rightSystemInset = if (fullScreen) {
        with(density) { (systemBarsInsets?.right ?: 0).toDp() }
    } else {
        0.dp
    }
    val topInset = if (fullScreen) 0.dp else 72.dp
    val bottomInset = if (fullScreen) 0.dp else 56.dp
    return OverlayPanelMetrics(
        width = screenWidth,
        height = screenHeight - topInset - bottomInset,
        offsetY = if (fullScreen) 0.dp else 10.dp,
        cornerRadius = if (fullScreen) 0.dp else 30.dp,
        multiWindow = inMultiWindow,
        fullScreen = fullScreen,
        topSystemInset = topSystemInset,
        bottomSystemInset = bottomSystemInset,
        leftSystemInset = leftSystemInset,
        rightSystemInset = rightSystemInset,
    )
}

@Composable
internal fun ApplyOverlaySystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    darkIcons: Boolean,
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = statusBarColor.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
    }
}

internal fun BoxScope.overlayPanelPlacement(
    metrics: OverlayPanelMetrics,
): Modifier {
    return Modifier
        .align(Alignment.Center)
        .offset(y = metrics.offsetY)
}

@Composable
internal fun OverlayPanelScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        topBar()
        content(Modifier.weight(1f).fillMaxWidth())
        bottomBar()
    }
}

@Composable
internal fun OverlayHeaderBar(
    backgroundColor: Color,
    topInset: Dp = 0.dp,
    leftInset: Dp = 0.dp,
    rightInset: Dp = 0.dp,
    leading: @Composable RowScope.() -> Unit,
    center: @Composable BoxScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp + topInset)
            .background(backgroundColor)
            .absolutePadding(
                left = 14.dp + leftInset,
                top = topInset,
                right = 14.dp + rightInset,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = leading,
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
            content = center,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

@Composable
internal fun OverlayBottomBar(
    backgroundColor: Color,
    bottomInset: Dp = 0.dp,
    leftInset: Dp = 0.dp,
    rightInset: Dp = 0.dp,
    leading: @Composable BoxScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp + bottomInset)
            .background(backgroundColor)
            .absolutePadding(
                left = 14.dp + leftInset,
                right = 14.dp + rightInset,
                bottom = bottomInset,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .requiredWidth(72.dp)
                .offset(y = OverlayBottomBarContentOffset),
            contentAlignment = Alignment.CenterStart,
            content = leading,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .offset(y = OverlayBottomBarContentOffset),
            contentAlignment = Alignment.Center,
            content = center,
        )
        Box(
            modifier = Modifier
                .requiredWidth(72.dp)
                .offset(y = OverlayBottomBarContentOffset),
            contentAlignment = Alignment.CenterEnd,
            content = trailing,
        )
    }
}

@Composable
internal fun OverlayIconAction(
    iconRes: Int,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .requiredWidth(36.dp)
            .requiredHeight(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                view.setColorFilter(tint.toArgb())
            },
        )
    }
}

@Composable
internal fun OverlayIconAction(
    imageVector: ImageVector,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .requiredWidth(36.dp)
            .requiredHeight(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
private fun PanelShadow(
    shape: Shape,
    shadowColor: Color,
    modifier: Modifier = Modifier,
) {
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
