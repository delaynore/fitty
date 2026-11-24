package com.delaynore.fitty

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.delaynore.fitty.ui.UiEventEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiEventEffectTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun consumesBeforeDispatchingAndUsesTheLatestCallbacks() {
        val event = mutableStateOf<String?>(null)
        val generation = mutableStateOf(1)
        val calls = mutableListOf<String>()
        compose.setContent {
            val currentGeneration = generation.value
            UiEventEffect(
                event = event.value,
                onConsume = { calls += "consume:$currentGeneration:$it" },
                onEvent = { calls += "event:$currentGeneration:$it" },
            )
        }

        compose.runOnIdle {
            generation.value = 2
            event.value = "saved"
        }
        compose.waitForIdle()

        assertEquals(listOf("consume:2:saved", "event:2:saved"), calls)
    }
}
