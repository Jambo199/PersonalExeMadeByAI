package com.jambo199.vividpulse.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.Chart
import com.jambo199.vividpulse.games.EpicGame
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GamesTab(val label: String, val chart: Chart?) {
    MOST_PLAYED("Most played", Chart.MOST_PLAYED),
    TOP_SELLERS("Top sellers", Chart.TOP_SELLERS),
    DEALS("Deals", Chart.DEALS),
    EPIC("Free on Epic", null),
}

/** A list that may be loading, may have failed, and may still show an older copy meanwhile. */
data class ListState<T>(
    val items: List<T>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fetchedAt: Long? = null,
)

class GamesViewModel : ViewModel() {
    private val repo = Graph.games

    private val _tab = MutableStateFlow(GamesTab.MOST_PLAYED)
    val tab: StateFlow<GamesTab> = _tab.asStateFlow()

    private val charts = Chart.entries.associateWith { c ->
        MutableStateFlow(repo.cached(c)?.let { ListState(it.games, fetchedAt = it.fetchedAt) } ?: ListState<Game>())
    }
    private val _epic = MutableStateFlow(ListState(repo.cachedEpic()))
    val epic: StateFlow<ListState<EpicGame>> = _epic.asStateFlow()

    private val _online = MutableStateFlow<Pair<Long, Long>?>(null)
    val online: StateFlow<Pair<Long, Long>?> = _online.asStateFlow()

    fun chart(c: Chart): StateFlow<ListState<Game>> = charts.getValue(c)

    init {
        load(GamesTab.MOST_PLAYED, force = false)
        viewModelScope.launch { _online.value = repo.steamOnline() }
    }

    fun select(t: GamesTab) {
        _tab.value = t
        load(t, force = false)
    }

    fun refresh() {
        load(_tab.value, force = true)
        viewModelScope.launch { repo.steamOnline()?.let { _online.value = it } }
    }

    private fun load(t: GamesTab, force: Boolean) {
        val chart = t.chart
        if (chart == null) {
            val flow = _epic
            if (flow.value.loading) return
            flow.value = flow.value.copy(loading = true, error = null)
            viewModelScope.launch {
                flow.value = try {
                    ListState(repo.epicFree(force), fetchedAt = System.currentTimeMillis())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    flow.value.copy(loading = false, error = e.friendlyMessage())
                }
            }
            return
        }
        val flow = charts.getValue(chart)
        if (flow.value.loading) return
        flow.value = flow.value.copy(loading = true, error = null)
        viewModelScope.launch {
            flow.value = try {
                val list = repo.chart(chart, force)
                ListState(list.games, fetchedAt = list.fetchedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flow.value.copy(loading = false, error = e.friendlyMessage())
            }
        }
    }
}
