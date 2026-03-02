package it.sandtv.app.ui.seriea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sandtv.app.data.model.Event
import it.sandtv.app.data.model.StandingRow
import it.sandtv.app.data.repository.SerieARepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SerieAViewModel @Inject constructor() : ViewModel() {

    private val _standings = MutableStateFlow<List<StandingRow>>(emptyList())
    val standings: StateFlow<List<StandingRow>> = _standings.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var pollingJob: Job? = null

    private val _selectedRound = MutableStateFlow<Int>(1)
    val selectedRound: StateFlow<Int> = _selectedRound.asStateFlow()
    
    val availableRounds = (1..38).toList()

    init {
        loadData()
        startLivePolling()
    }

    private fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            
            // Parallel fetch
            val standingsResult = SerieARepository.getStandings()
            
            // Step 1: Get next matches just to find the CURRENT ROUND
            val nextMatchesResult = SerieARepository.getNextMatches()

            if (standingsResult.isSuccess) {
                _standings.value = standingsResult.getOrDefault(emptyList())
            }
            
            if (nextMatchesResult.isSuccess) {
                val nextMatches = nextMatchesResult.getOrDefault(emptyList())
                
                // Find current round from the first upcoming match
                val currentRound = nextMatches.firstOrNull()?.roundInfo?.round ?: 1
                _selectedRound.value = currentRound
                
                // Step 2: Now explicitly fetch *ALL* events for this specific round (played + upcoming)
                // This prevents future matches from other rounds appearing in the list
                val roundEventsResult = SerieARepository.getEventsByRound(currentRound)
                
                if (roundEventsResult.isSuccess) {
                    _events.value = roundEventsResult.getOrDefault(emptyList())
                } else {
                    // Fallback to next matches if round fetch fails (better than nothing)
                    _events.value = nextMatches
                }
            } else {
                 // Fallback if next matches fails (e.g. offline)
                 _events.value = emptyList()
            }
            
            _loading.value = false
        }
    }
    
    fun loadStandings() {
         viewModelScope.launch {
            val result = SerieARepository.getStandings()
            if (result.isSuccess) {
                _standings.value = result.getOrDefault(emptyList())
            }
         }
    }

    fun selectRound(round: Int) {
        viewModelScope.launch {
            _loading.value = true
            _selectedRound.value = round
            val result = SerieARepository.getEventsByRound(round)
            if (result.isSuccess) {
                _events.value = result.getOrDefault(emptyList())
            }
            _loading.value = false
        }
    }

    private fun startLivePolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                // Poll for updates (refresh current round)
                if (_events.value.isNotEmpty()) {
                    val currentRound = _selectedRound.value
                    val result = SerieARepository.getEventsByRound(currentRound)
                    if (result.isSuccess) {
                        _events.value = result.getOrDefault(emptyList())
                    }
                }
                delay(30_000) // Refresh every 30 seconds
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
