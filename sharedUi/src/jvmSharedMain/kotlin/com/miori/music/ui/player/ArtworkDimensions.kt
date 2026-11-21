package com.miori.music.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tablet threshold for player sizing.
 */
private val TABLET_PLAYER_MIN_WIDTH = 700.dp

/**
 * Whether the full-bleed artwork toggle should be offered in Settings.
 *
 * A window narrow enough that the player fills it is where the setting
 * acts: edge to edge there means the artwork *is* the screen.
 */
fun fullBleedArtworkAvailable(windowWidth: Dp): Boolean =
    playerFillsWindow(windowWidth) || tabletSizedPlayer(windowWidth)

/**
 * Whether a player given the whole of a window this wide is still narrow enough
 * to run its artwork edge to edge.
 */
fun playerFillsWindow(windowWidth: Dp): Boolean =
    windowWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

/**
 * Whether the player is tablet-sized — see [TABLET_PLAYER_MIN_WIDTH].
 */
fun tabletSizedPlayer(windowWidth: Dp): Boolean =
    windowWidth >= TABLET_PLAYER_MIN_WIDTH
