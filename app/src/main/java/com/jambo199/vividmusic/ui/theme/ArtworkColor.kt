package com.jambo199.vividmusic.ui.theme

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jambo199.vividmusic.data.ArtworkLoader
import com.jambo199.vividmusic.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dominant colour of the artwork, for tinting the player background. */
@Composable
fun rememberArtworkColor(song: Song?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(null, song?.key) {
        value = song?.let {
            withContext(Dispatchers.Default) {
                try {
                    val request = ImageRequest.Builder(context).data(it).size(160).allowHardware(false).build()
                    val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
                    val palette = Palette.from(ArtworkLoader.ensureSoftware(bitmap)).maximumColorCount(16).generate()
                    val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                    swatch?.rgb?.let { rgb -> Color(rgb) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    return color
}

/**
 * A colour from the artwork that's usable as a theme seed, or null for grey or missing art
 * (a grey seed would wash the whole app out).
 */
@Composable
fun rememberArtworkSeed(song: Song?): Color? {
    val color = rememberArtworkColor(song) ?: return null
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    return color.takeIf { hsl[1] >= 0.15f }
}
