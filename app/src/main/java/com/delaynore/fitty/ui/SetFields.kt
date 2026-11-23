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
    if (input.type == ExerciseType.WEIGHT_AND_REPS) {
        OutlinedTextField(
            value = input.weight,
            onValueChange = { onInput(input.copy(weight = it)) },
            enabled = enabled,
            label = if (showLabels) ({ Text("Вес, кг") }) else null,
            singleLine = true,
            isError = input.errors?.weight != null,
            supportingText = input.errors?.weight?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = weightModifier,
        )
    }
    OutlinedTextField(
        value = input.measure,
        onValueChange = { onInput(input.copy(measure = it)) },
        enabled = enabled,
        label = if (showLabels) ({ Text(input.type.measureLabel) }) else null,
        singleLine = true,
        isError = input.errors?.measure != null,
        supportingText = input.errors?.measure?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (input.type == ExerciseType.DISTANCE) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        modifier = measureModifier,
    )
}
