package com.delaynore.fitty.data.programs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class FakeProgramsRepository : ProgramsRepository {
    private val programs = MutableStateFlow(emptyMap<Long, TrainingProgram>())
    private val selectedId = MutableStateFlow<Long?>(null)

    override val homeProgram = combine(programs, selectedId) { saved, id -> saved[id] }
    override val allPrograms = combine(programs, selectedId) { saved, id ->
        saved.values.map { ProgramSelection(it.id, it.name, it.id == id) }
    }

    override fun observeProgram(id: Long) = programs.map { it[id] }

    override suspend fun selectProgram(id: Long) {
        require(id in programs.value)
        selectedId.value = id
    }

    override suspend fun saveProgram(program: TrainingProgram) {
        val id = program.id.takeIf { it > 0 } ?: ((programs.value.keys.maxOrNull() ?: 0L) + 1)
        programs.value += id to program.copy(id = id)
        if (selectedId.value == null) selectedId.value = id
    }
}
