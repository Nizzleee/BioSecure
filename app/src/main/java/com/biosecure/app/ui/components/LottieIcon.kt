package com.biosecure.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun LottieIcon(
    assetName: String,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    isPlaying: Boolean = true
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(assetName)
    )
    LottieAnimation(
        composition = composition,
        iterations = iterations,
        isPlaying = isPlaying,
        modifier = modifier
    )
}
