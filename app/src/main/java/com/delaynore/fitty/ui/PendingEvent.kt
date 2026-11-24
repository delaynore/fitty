package com.delaynore.fitty.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal inline fun <State, Event> MutableStateFlow<State>.consumePendingEvent(
    event: Event,
    pendingEvent: (State) -> Event?,
    clear: (State) -> State,
) {
    update { current -> if (pendingEvent(current) == event) clear(current) else current }
}
