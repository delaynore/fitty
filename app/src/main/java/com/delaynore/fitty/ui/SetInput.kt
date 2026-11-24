package com.delaynore.fitty.ui

import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.domain.toInput as domainInput

internal typealias SetInput = com.delaynore.fitty.domain.SetInput
internal typealias SetInputErrors = com.delaynore.fitty.domain.SetInputErrors
internal typealias SetInputField = com.delaynore.fitty.domain.SetInputField
internal fun SetResult.toInput(): SetInput = domainInput()
