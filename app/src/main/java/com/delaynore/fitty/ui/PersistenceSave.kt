package com.delaynore.fitty.ui

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs a repository write inside [this] scope while guarding against concurrent saves and
 * mapping `false` results or [SQLiteException] through [onFailure].
 */
internal inline fun <S> CoroutineScope.launchPersistenceSave(
    state: MutableStateFlow<S>,
    isBusy: (S) -> Boolean,
    crossinline onStart: (S) -> S,
    crossinline onFailure: (S) -> S,
    crossinline onFinish: (S) -> S,
    crossinline operation: suspend () -> Boolean,
) {
    if (isBusy(state.value)) return
    state.update { onStart(it) }
    launch {
        var failed = false
        try {
            failed = !operation()
        } catch (_: SQLiteException) {
            failed = true
        } finally {
            state.update { current ->
                val finished = onFinish(current)
                if (failed) onFailure(finished) else finished
            }
        }
    }
}
