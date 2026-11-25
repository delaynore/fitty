package com.delaynore.fitty.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStarted

@Composable
internal fun <Event : Any> UiEventEffect(
    event: Event?,
    onConsume: (Event) -> Unit,
    onEvent: (Event) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnConsume by rememberUpdatedState(onConsume)
    val latestOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(event, lifecycle) {
        event ?: return@LaunchedEffect
        lifecycle.withStarted {
            latestOnConsume(event)
            latestOnEvent(event)
        }
    }
}
