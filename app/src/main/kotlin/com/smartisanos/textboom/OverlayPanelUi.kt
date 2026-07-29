package com.cashewteam.novatext.android

import android.app.Activity
import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
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

private val OverlayBottomBarContentOffset = (-2).dp
private val InvertAssetColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

internal fun effectiveOverlayBottomInset(
    systemInset: Dp,
    imeInset: Dp,
    editMode: Boolean,
): Dp = if (editMode) maxOf(systemInset, imeInset) else systemInset

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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (shadowColor == null) {
                        Modifier
                    } else {
                        // A bitmap shadow was recreated at every intermediate
                        // helper height. Keep the same soft shadow on a render
                        // layer so resizing stays on the GPU.
                        Modifier.graphicsLayer {
                            shadowElevation = 16.dp.toPx()
                            this.shape = shape
                            ambientShadowColor = shadowColor
                            spotShadowColor = shadowColor
                        }
                    },
                )
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
    val imeBottomInset: Dp,
    val leftSystemInset: Dp,
    val rightSystemInset: Dp,
) {
    internal fun effectiveBottomInset(editMode: Boolean): Dp {
        return effectiveOverlayBottomInset(bottomSystemInset, imeBottomInset, editMode)
    }
}

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
    // Compose observes IME insets as animation frames. Keeping this value in
    // the panel metrics makes the bottom bar and AndroidView share one usable
    // height instead of letting the legacy layer subtract the keyboard again.
    val imeBottomInset = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
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
        imeBottomInset = imeBottomInset,
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
    contentHeight: Dp = 52.dp,
    horizontalPadding: Dp = 14.dp,
    leadingItemSpacing: Dp = 8.dp,
    trailingItemSpacing: Dp = 8.dp,
    leading: @Composable RowScope.() -> Unit,
    center: @Composable BoxScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(contentHeight + topInset)
            .background(backgroundColor)
            .absolutePadding(
                left = horizontalPadding + leftInset,
                top = topInset,
                right = horizontalPadding + rightInset,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(leadingItemSpacing),
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
            horizontalArrangement = Arrangement.spacedBy(trailingItemSpacing, Alignment.End),
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
    contentHeight: Dp = 52.dp,
    horizontalPadding: Dp = 14.dp,
    leading: @Composable BoxScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(contentHeight + bottomInset)
            .background(backgroundColor)
            .absolutePadding(
                left = horizontalPadding + leftInset,
                right = horizontalPadding + rightInset,
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
    tint: Color?,
    invertAssetColors: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    Box(
        modifier = Modifier
            .requiredWidth(36.dp)
            .requiredHeight(36.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.92f else 1f
                scaleY = if (pressed) 0.92f else 1f
            }
            .clickable(
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
                view.isEnabled = enabled
                view.isPressed = pressed
                view.contentDescription = contentDescription
                if (tint == null && invertAssetColors) {
                    // Edit top/bottom assets were authored for a white bar.
                    // Reverse them in dark mode without affecting cursor/tool buttons.
                    view.colorFilter = InvertAssetColorFilter
                } else if (tint == null) {
                    // Original selector PNGs already contain their precise color and disabled alpha.
                    view.clearColorFilter()
                } else {
                    view.setColorFilter(tint.toArgb())
                }
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    Box(
        modifier = Modifier
            .requiredWidth(36.dp)
            .requiredHeight(36.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.92f else 1f
                scaleY = if (pressed) 0.92f else 1f
            }
            .clickable(
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
