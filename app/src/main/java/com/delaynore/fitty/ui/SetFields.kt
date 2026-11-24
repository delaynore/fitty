package com.delaynore.fitty.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.delaynore.fitty.data.exercises.ExerciseType

@Composable
internal fun SetFields(
    input: SetInput,
    onInput: (SetInput) -> Unit,
    enabled: Boolean,
    weightModifier: Modifier,
    measureModifier: Modifier,
    showLabels: Boolean,
) {
    SetFields(
        input = input,
        onFieldChange = { field, text ->
            onInput(when (field) {
                SetInputField.Weight -> input.copy(weight = text)
                SetInputField.Measure -> input.copy(measure = text)
            })
        },
        enabled = enabled,
        weightModifier = weightModifier,
        measureModifier = measureModifier,
        showLabels = showLabels,
        keepDraftBuffer = false,
    )
}

@Composable
internal fun SetFields(
    input: SetInput,
    onFieldChange: (SetInputField, String) -> Unit,
    enabled: Boolean,
    weightModifier: Modifier,
    measureModifier: Modifier,
    showLabels: Boolean,
    keepDraftBuffer: Boolean = true,
) {
    input.type.weightLabel?.let { weightLabel ->
        SetField(
            value = input.weight,
            onValueChange = { onFieldChange(SetInputField.Weight, it) },
            enabled = enabled,
            label = if (showLabels) ({ Text(weightLabel) }) else null,
            error = input.errors?.weight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = weightModifier,
            keepDraftBuffer = keepDraftBuffer,
        )
    }
    SetField(
        value = input.measure,
        onValueChange = { onFieldChange(SetInputField.Measure, it) },
        enabled = enabled,
        label = if (showLabels) ({ Text(input.type.measureLabel) }) else null,
        error = input.errors?.measure,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (input.type == ExerciseType.DISTANCE) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        modifier = measureModifier,
        keepDraftBuffer = keepDraftBuffer,
    )
}

@Composable
private fun SetField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: @Composable (() -> Unit)?,
    error: String?,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier,
    keepDraftBuffer: Boolean,
) {
    val supportingText: @Composable (() -> Unit)? = error?.let { { Text(it) } }
    if (keepDraftBuffer) {
        DraftTextField(
            value, onValueChange, modifier, enabled = enabled, label = label, singleLine = true,
            isError = error != null, supportingText = supportingText, keyboardOptions = keyboardOptions,
        )
    } else {
        OutlinedTextField(
            value, onValueChange, modifier, enabled = enabled, label = label, singleLine = true,
            isError = error != null, supportingText = supportingText, keyboardOptions = keyboardOptions,
        )
    }
}
