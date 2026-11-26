package com.itos.coui.floatingtoolbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.itos.coui.motion.CouiSpring
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class ToolbarAnimation(initial: ToolbarGroup) {
    private val waiting = ArrayDeque<ToolbarGroup>()
    private var requested: ToolbarGroup = initial
    var batch by mutableStateOf(ToolbarItems(initial, initial))
        private set
    val gap = Animatable(0f)
    val progress = CouiSpring(0f, minimumChange = 0.0001f)

    private var playing: ToolbarItems? by mutableStateOf(null)
    fun fraction(change: ToolbarItems): Float = if (playing === change) progress.value else 0f
    fun gapFraction(change: ToolbarItems): Float = if (playing === change) gap.value else 0f

    fun submit(group: ToolbarGroup) {
        if (requested == group) return
        requested = group
        if (batch.from == batch.to) {
            batch = ToolbarItems(batch.to, group)
        } else {
            waiting.addLast(group)
        }
    }

    suspend fun play(change: ToolbarItems) {
        progress.snapTo(0f)
        gap.snapTo(0f)
        playing = change
        if (change.from == change.to) return
        val timing = tween<Float>(200, easing = LinearEasing)
        gap.animateTo(0.75f, timing)
        coroutineScope {
            launch { gap.animateTo(0f, timing) }
            suspendCancellableCoroutine { continuation ->
                progress.animateTo(1f, response = 0.5f, bounce = 0.4f, onEnd = { continuation.resume(Unit) })
                continuation.invokeOnCancellation { progress.cancel() }
            }
        }
        if (batch === change) batch = ToolbarItems(change.to, waiting.removeFirstOrNull() ?: change.to)
    }

    fun dispose() = progress.cancelComplete()
}

@Composable
internal fun rememberToolbarAnimation(initial: ToolbarGroup): ToolbarAnimation {
    val animation = remember { ToolbarAnimation(initial) }
    val batch = animation.batch
    LaunchedEffect(batch) { animation.play(batch) }
    DisposableEffect(animation) { onDispose { animation.dispose() } }
    return animation
}
