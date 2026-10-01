package com.hupux.ui.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.MatchDay
import com.hupux.data.model.MatchSchedule
import com.hupux.data.model.EsportMatchScore
import com.hupux.data.model.MatchScoreBoard
import com.hupux.data.scraper.HupuMatchScraper
import com.hupux.data.local.MatchTagPrefs
import com.hupux.data.scraper.MatchTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScoreUiState(
    val tag: MatchTag = MatchTag.NBA,
    val days: List<MatchDay> = emptyList(),
    /** 虎扑给的定位锚点 matchId，界面据此滚到今天附近；为空表示没给 */
    val anchorMatchId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class ScoreViewModel constructor(
    private val scraper: HupuMatchScraper,
    tagPrefs: MatchTagPrefs
) : ViewModel() {

    private val _state = MutableStateFlow(ScoreUiState())
    val state = _state.asStateFlow()

    /** 设置里勾选的赛事（顺序按 MatchTag 定义），tab 只显示这些 */
    val visibleTags: StateFlow<List<MatchTag>> = tagPrefs.selectedTags
        .map { tags -> MatchTag.entries.filter { it in tags } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MatchTag.entries.toList())

    /** 已加载过的分区缓存，切 tab 时不重复联网 */
    private val cache = mutableMapOf<MatchTag, MatchSchedule>()

    init { load(MatchTag.NBA) }

    fun selectTag(tag: MatchTag) {
        if (_state.value.tag == tag && _state.value.days.isNotEmpty()) return
        val cached = cache[tag]
        if (cached != null) {
            _state.update {
                it.copy(
                    tag           = tag,
                    days          = cached.days,
                    anchorMatchId = cached.anchorMatchId,
                    isLoading     = false,
                    error         = null
                )
            }
        } else {
            load(tag)
        }
    }

    fun load(tag: MatchTag = _state.value.tag) {
        _state.update { it.copy(tag = tag, isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { scraper.fetchSchedule(tag) }
                .onSuccess { schedule ->
                    cache[tag] = schedule
                    _state.update {
                        if (it.tag != tag) it            // 期间又切了 tab，丢弃本次结果
                        else it.copy(
                            days          = schedule.days,
                            anchorMatchId = schedule.anchorMatchId,
                            isLoading     = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (it.tag != tag) it
                        else it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                }
        }
    }
}

// ─── 评分详情 ────────────────────────────────────────────────────────────────

data class ScoreDetailUiState(
    val board: MatchScoreBoard? = null,
    /** 电竞比赛走单局结构时非空，此时界面用虎扑评分排版 */
    val esport: EsportMatchScore? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class ScoreDetailViewModel constructor(
    private val scraper: HupuMatchScraper
) : ViewModel() {

    private val _state = MutableStateFlow(ScoreDetailUiState())
    val state = _state.asStateFlow()

    private var loadedKey: String? = null

    fun load(bizType: String, bizNo: String) {
        val key = "$bizType:$bizNo"
        val cur = _state.value
        if (loadedKey == key && (cur.board != null || cur.esport != null)) return
        loadedKey = key
        _state.value = ScoreDetailUiState(isLoading = true)
        viewModelScope.launch {
            // 电竞比赛（lol/lpl/kog…）优先走单局结构
            val esport = runCatching { scraper.fetchEsportMatchScore(bizType, bizNo) }.getOrNull()
            if (esport != null) {
                _state.value = ScoreDetailUiState(esport = esport, isLoading = false)
                return@launch
            }
            runCatching { scraper.fetchScoreBoard(bizType, bizNo) }
                .onSuccess { _state.value = ScoreDetailUiState(board = it, isLoading = false) }
                .onFailure {
                    _state.value = ScoreDetailUiState(
                        isLoading = false, error = it.message ?: "加载失败")
                }
        }
    }
}
