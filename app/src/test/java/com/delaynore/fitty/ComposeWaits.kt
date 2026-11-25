package com.delaynore.fitty

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

internal fun ComposeTestRule.awaitNode(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
}

internal fun ComposeTestRule.awaitText(text: String) = awaitNode(hasText(text))
