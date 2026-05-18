package com.vaultdex.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaultdex.app.VaultDexApp
import com.vaultdex.app.data.db.NoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val db      = (application as VaultDexApp).database
    private val noteDao = db.noteDao()

    private val _uiState = MutableStateFlow(NotesUiState(isLoading = true))
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // VULN-B: Observing plaintext notes from unencrypted Room database
            noteDao.getAllNotes().collect { notes ->
                _uiState.value = NotesUiState(notes = notes, isLoading = false)
            }
        }
    }

    fun addNote(title: String, content: String, category: String) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Title cannot be empty")
            return
        }
        viewModelScope.launch {
            // VULN-B: Inserts note as plaintext into unencrypted SQLite table
            noteDao.insertNote(
                NoteEntity(
                    title    = title,
                    content  = content,
                    category = category
                )
            )
            _uiState.value = _uiState.value.copy(message = "Note saved")
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
