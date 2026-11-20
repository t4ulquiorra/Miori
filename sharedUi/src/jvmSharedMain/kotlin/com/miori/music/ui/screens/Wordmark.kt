package com.miori.music.ui.screens

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.miori.music.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource

/** ic_logo's viewport, 730 x 484. */
private const val WORDMARK_ASPECT = 730f / 484f

/**
 * The app's mark.
 */
@Composable
internal fun Wordmark(
    logoHeight: Dp,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 6.dp,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_logo),
        contentDescription = null,
        tint = color,
        modifier = modifier
            .height(logoHeight)
            .aspectRatio(WORDMARK_ASPECT),
    )
}