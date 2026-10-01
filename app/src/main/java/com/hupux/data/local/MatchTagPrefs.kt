package com.hupux.data.local

import android.content.Context
import com.hupux.data.scraper.MatchTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 首页「今日比分」横条 + 评分页 tab 显示哪些赛事。存在 hupu_prefs 里，
 * 默认全选；至少保留一个（界面层也做拦截，这里兜底）。
 */
class MatchTagPrefs constructor(ctx: Context) {

    private val prefs = ctx.applicationContext
        .getSharedPreferences("hupu_prefs", Context.MODE_PRIVATE)

    private val _selectedTags = MutableStateFlow(read())
    val selectedTags: StateFlow<Set<MatchTag>> = _selectedTags.asStateFlow()

    private fun read(): Set<MatchTag> {
        val raw = prefs.getStringSet(KEY, null)
            ?: return MatchTag.entries.toSet()
        val parsed = raw.mapNotNull { name ->
            runCatching { MatchTag.valueOf(name) }.getOrNull()
        }.toSet()
        return parsed.ifEmpty { MatchTag.entries.toSet() }
    }

    fun setSelected(tags: Set<MatchTag>) {
        val safe = tags.ifEmpty { MatchTag.entries.toSet() }
        prefs.edit().putStringSet(KEY, safe.map { it.name }.toSet()).apply()
        _selectedTags.value = safe
    }

    fun toggle(tag: MatchTag) {
        val cur = _selectedTags.value.toMutableSet()
        if (tag in cur) {
            if (cur.size > 1) cur.remove(tag)   // 至少留一个
        } else {
            cur.add(tag)
        }
        setSelected(cur)
    }

    companion object {
        private const val KEY = "match_visible_tags"
    }
}
