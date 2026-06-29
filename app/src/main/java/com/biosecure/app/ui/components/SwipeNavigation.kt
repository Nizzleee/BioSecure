package com.biosecure.app.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavController
import com.biosecure.app.ui.navigation.Screen

fun Modifier.swipeToNavigate(
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    threshold: Float = 80f
): Modifier = this.pointerInput(Unit) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            when {
                totalDrag < -threshold -> onSwipeLeft()
                totalDrag > threshold -> onSwipeRight()
            }
            totalDrag = 0f
        },
        onHorizontalDrag = { _, amount -> totalDrag += amount }
    )
}

fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Login.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
