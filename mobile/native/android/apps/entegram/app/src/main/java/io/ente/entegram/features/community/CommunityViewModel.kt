package io.ente.entegram.features.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.models.CommunityResult
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CommunityUiState {
    data object Idle : CommunityUiState
    data object Loading : CommunityUiState
    data object Empty : CommunityUiState
    data class Ready(
        val results: List<CommunityResult>,
        val query: String,
        val refreshing: Boolean = false,
    ) : CommunityUiState
    data class Error(val message: String) : CommunityUiState
}

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val wallClient: WallClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null
    private var suggestions: List<CommunityResult> = emptyList()

    init {
        fetchSuggestions()
    }

    private fun fetchSuggestions() {
        viewModelScope.launch {
            try {
                val results = wallClient.searchCommunity("", limit = 20)
                suggestions = results
                if (_query.value.isBlank()) {
                    if (results.isEmpty()) {
                        _uiState.value = CommunityUiState.Idle
                    } else {
                        _uiState.value = CommunityUiState.Ready(
                            results = results,
                            query = "",
                        )
                    }
                }
            } catch (_: Exception) {
                if (_query.value.isBlank()) {
                    _uiState.value = CommunityUiState.Idle
                }
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = if (suggestions.isNotEmpty()) {
                CommunityUiState.Ready(results = suggestions, query = "")
            } else {
                CommunityUiState.Idle
            }
            return
        }

        // If we already have results, mark them as refreshing instead of jumping
        // to the full loading shimmer — keeps the UI stable while the user types.
        val current = _uiState.value
        if (current is CommunityUiState.Ready) {
            _uiState.value = current.copy(refreshing = true)
        }

        searchJob = viewModelScope.launch {
            // Debounce: wait 300ms before firing the search
            delay(300)

            // Only show the full shimmer when there are no stale results to show
            if (_uiState.value !is CommunityUiState.Ready) {
                _uiState.value = CommunityUiState.Loading
            }

            try {
                val results = wallClient.searchCommunity(newQuery.trim(), limit = 50)
                if (results.isEmpty()) {
                    _uiState.value = CommunityUiState.Empty
                } else {
                    _uiState.value = CommunityUiState.Ready(
                        results = results,
                        query = newQuery.trim(),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CommunityUiState.Error(
                    e.message ?: "Something went wrong",
                )
            }
        }
    }

    fun retry() {
        val currentQuery = _query.value
        if (currentQuery.isNotBlank()) {
            // Force a full reload — clear stale results so shimmer shows
            _uiState.value = CommunityUiState.Loading
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                try {
                    val results = wallClient.searchCommunity(currentQuery.trim(), limit = 50)
                    if (results.isEmpty()) {
                        _uiState.value = CommunityUiState.Empty
                    } else {
                        _uiState.value = CommunityUiState.Ready(
                            results = results,
                            query = currentQuery.trim(),
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = CommunityUiState.Error(
                        e.message ?: "Something went wrong",
                    )
                }
            }
        }
    }
}
