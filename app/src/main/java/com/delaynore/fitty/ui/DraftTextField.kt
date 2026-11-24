package com.delaynore.fitty.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Composable
internal fun DraftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    // The field owns selection and IME composition. Only a new draft resets the buffer.
    var field by remember(resetKey) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            val textChanged = !field.text.equals(next.text, ignoreCase = false)
            field = next
            if (textChanged) onValueChange(next.text)
        },
        modifier = modifier,
        enabled = enabled,
        label = label,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = keyboardOptions,
    )
}
