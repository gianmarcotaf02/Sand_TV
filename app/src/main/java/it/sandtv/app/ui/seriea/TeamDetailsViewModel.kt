package it.sandtv.app.ui.seriea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sandtv.app.data.model.Event
import it.sandtv.app.data.model.TeamDetailsResponse
import it.sandtv.app.data.repository.SerieARepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeamDetailsViewModel @Inject constructor() : ViewModel() {

    private val _teamDetails = MutableStateFlow<TeamDetailsResponse?>(null)
    val teamDetails: StateFlow<TeamDetailsResponse?> = _teamDetails.asStateFlow()

    private val _nextEvents = MutableStateFlow<List<Event>>(emptyList())
    val nextEvents: StateFlow<List<Event>> = _nextEvents.asStateFlow()

    private val _lastEvents = MutableStateFlow<List<Event>>(emptyList())
    val lastEvents: StateFlow<List<Event>> = _lastEvents.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadTeamData(teamId: Long) {
        viewModelScope.launch {
            _loading.value = true
            
            // Load all data in parallel
            val detailsJob = launch {
                val result = SerieARepository.getTeamDetails(teamId)
                if (result.isSuccess) {
                    _teamDetails.value = result.getOrNull()
                }
            }
            
            val nextJob = launch {
                val result = SerieARepository.getTeamNextEvents(teamId)
                if (result.isSuccess) {
                    _nextEvents.value = result.getOrDefault(emptyList())
                }
            }
            
            val lastJob = launch {
                val result = SerieARepository.getTeamLastEvents(teamId)
                if (result.isSuccess) {
                    _lastEvents.value = result.getOrDefault(emptyList())
                }
            }
            
            // Wait for all
            detailsJob.join()
            nextJob.join()
            lastJob.join()
            
            _loading.value = false
        }
    }
}
