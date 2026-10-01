package com.hupux.ui.score

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.hupux.data.model.EsportMatchScore
import com.hupux.data.model.EsportPlayerScore
import com.hupux.data.model.EsportTeamScore
import com.hupux.data.model.GameScoreBoard
import com.hupux.data.model.PlayerScorePair
import com.hupux.data.model.ScoredItem
import com.hupux.ui.components.HupuTopBar
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/** 徽章蓝（客队） */
private val BadgeBlue = Color(0xFF5B8DEF)

/** 卡片大数字蓝 */
private val ScoreBlue = Color(0xFF5AA9E6)

@Composable
fun ScoreDetailScreen(
    bizType: String,
    bizNo: String,
    onItemClick: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: ScoreDetailViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(bizType, bizNo) { vm.load(bizType, bizNo) }

    HazeScope {
    Box(Modifier.fillMaxSize().background(AppBg)) {
        HupuTopBar(
            title = state.esport?.title
                ?: state.board?.title?.ifEmpty { "评分" }
                ?: "评分",
            onBack = onBack, hazed = true,
            modifier = Modifier.zIndex(1f)
        )
        // hazeSource 挂在内容这层（顶栏的兄弟）：Haze 不允许 haze 与 hazeChild
        // 互为祖先后代，挂到外层 Box 上会直接崩
        Box(Modifier.fillMaxSize().hazeSource()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)

                state.error != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = HupuRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = { vm.load(bizType, bizNo) })
                }

                state.esport != null -> EsportScoreContent(
                    detail = state.esport!!,
                    onItemClick = onItemClick
                )

                state.board?.items.isNullOrEmpty() -> Text(
                    "这场比赛还没有评分", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center))

                else -> {
                    val board = state.board!!
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp + hupuTopBarHeight, bottom = 16.dp)
                    ) {
                        if (board.raterText.isNotEmpty()) {
                            item {
                                Text(
                                    board.raterText,
                                    fontSize = 12.sp, color = TextTertiary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }
                        }
                        // 不设 key：同名条目（如两个「裁判」）会导致 LazyColumn key 冲突崩溃
                        items(board.items) { item ->
                            ScoredItemRow(
                                item = item,
                                onClick = if (item.bizNo.isNotEmpty())
                                    { { onItemClick(item.bizType, item.bizNo) } } else null
                            )
                        }
                        item {
                            Text(
                                "点击条目可打分与评论",
                                fontSize = 11.sp, color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

// ─── 电竞：虎扑评分排版 ────────────────────────────────────────────────────

@Composable
private fun EsportScoreContent(
    detail: EsportMatchScore,
    onItemClick: (String, String) -> Unit
) {
    var selectedGame by remember(detail) { mutableIntStateOf(detail.games.size - 1) }
    val game = detail.games.getOrNull(selectedGame.coerceIn(detail.games.indices))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp + hupuTopBarHeight, bottom = 24.dp)
    ) {
        // 比赛头：主队 比分 客队
        item { MatchHeaderCard(detail) }

        // 全场评分标题行
        item { OverallTitleRow(detail) }

        // 全场评分配对行
        items(detail.pairs) { pair -> PairRow(pair) }

        if (detail.raterText.isNotEmpty()) {
            item {
                Text(
                    detail.raterText,
                    fontSize = 11.sp, color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }

        // 单局切换
        if (detail.games.size > 1) {
            item {
                GameTabRow(
                    games = detail.games,
                    selected = selectedGame,
                    onSelect = { selectedGame = it }
                )
            }
        }

        // 选中单局的双方选手卡片
        if (game != null) {
            item { TeamHeaderPill(game.home) }
            items(game.home.players) { p ->
                PlayerScoreCard(p) {
                    if (p.bizNo.isNotEmpty()) onItemClick(p.bizType, p.bizNo)
                }
            }
            item { TeamHeaderPill(game.away) }
            items(game.away.players) { p ->
                PlayerScoreCard(p) {
                    if (p.bizNo.isNotEmpty()) onItemClick(p.bizType, p.bizNo)
                }
            }
        }

        item {
            Text(
                "点击选手卡片可打分与评论",
                fontSize = 11.sp, color = TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun MatchHeaderCard(detail: EsportMatchScore) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = detail.homeLogo, contentDescription = detail.homeName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text(detail.homeName, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    if (detail.homeScore.isNotEmpty() || detail.awayScore.isNotEmpty())
                        "${detail.homeScore.ifEmpty { "-" }} - ${detail.awayScore.ifEmpty { "-" }}"
                    else detail.title,
                    fontSize = 26.sp, fontWeight = FontWeight.Black, color = TextPrimary
                )
                Text("已结束", fontSize = 11.sp, color = TextTertiary)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = detail.awayLogo, contentDescription = detail.awayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text(detail.awayName, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OverallTitleRow(detail: EsportMatchScore) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("全场评分", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("根据单局选手评分综合算出", fontSize = 11.sp, color = TextTertiary)
        }
        IconButton(onClick = {
            val text = buildString {
                append(detail.title.ifEmpty { "虎扑评分" })
                append(" 全场评分：")
                detail.pairs.forEach { (h, a) ->
                    append("${h.name}${formatScore(h.score)}-${a.name}${formatScore(a.score)} ")
                }
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text.trim())
            }
            context.startActivity(Intent.createChooser(send, "分享"))
        }) {
            Icon(Icons.Filled.Share, contentDescription = "分享", tint = TextSecondary,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PairRow(pair: PlayerScorePair) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            pair.home.name, fontSize = 14.sp, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        ScoreBadge(pair.home.score, HupuRed)
        Spacer(Modifier.width(28.dp))
        ScoreBadge(pair.away.score, BadgeBlue)
        Spacer(Modifier.width(10.dp))
        Text(
            pair.away.name, fontSize = 14.sp, color = TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScoreBadge(score: Double, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(
            formatScore(score),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun GameTabRow(
    games: List<GameScoreBoard>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        games.forEachIndexed { i, g ->
            val isSel = i == selected
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSel) CardBg else Color.Transparent,
                shadowElevation = if (isSel) 1.dp else 0.dp,
                modifier = Modifier.clickable(onClick = { onSelect(i) })
            ) {
                Text(
                    g.gameName,
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) TextPrimary else TextTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TeamHeaderPill(team: EsportTeamScore) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = CardBg,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (team.logo.isNotEmpty()) {
                AsyncImage(
                    model = team.logo, contentDescription = team.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(team.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
private fun PlayerScoreCard(
    player: EsportPlayerScore,
    onClick: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = player.avatar, contentDescription = player.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgGray)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(player.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = player.stats.ifEmpty { player.label }
                    if (sub.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(sub, fontSize = 12.sp, color = TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(5) {
                            Icon(
                                Icons.Outlined.Star, contentDescription = null,
                                tint = TextTertiary, modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatScore(player.score),
                        fontSize = 24.sp, fontWeight = FontWeight.Black, color = ScoreBlue
                    )
                    Text(
                        "${player.scoreCount} JR's评分",
                        fontSize = 11.sp, color = TextTertiary
                    )
                }
            }
            if (player.hotComment.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            player.hotComment,
                            fontSize = 13.sp,
                            color = if (dark) Color(0xFFD29A5B) else Color(0xFF9A6220),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.OpenInNew, contentDescription = null,
                            tint = TextTertiary, modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── 传统列表排版（篮球等非电竞比赛）─────────────────────────────────────────

@Composable
private fun ScoredItemRow(item: ScoredItem, onClick: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = item.avatar, contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(BgGray)
                )
                if (item.teamLogo.isNotEmpty()) {
                    AsyncImage(
                        model = item.teamLogo, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.label.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = TagBg, shape = RoundedCornerShape(999.dp)) {
                            Text(item.label, fontSize = 10.sp, color = HupuRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (item.stats.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(item.stats, fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("${item.scoreCount} 人评分")
                        if (item.commentCount > 0) append(" · ${item.commentCount} 条评论")
                    },
                    fontSize = 11.sp, color = TextTertiary
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                formatScore(item.score),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor(item.score)
            )
        }
    }
}

/** 9.0 及以上高亮，6 分以下用次要色，避免满屏红字 */
@Composable
private fun scoreColor(score: Double) = when {
    score >= 9.0 -> HotColor
    score >= 6.0 -> TextPrimary
    else         -> TextSecondary
}

private fun formatScore(score: Double): String = String.format(Locale.US, "%.1f", score)
