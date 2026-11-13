package com.quandian.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quandian.app.data.TaskEntity
import com.quandian.app.ui.QIcon
import com.quandian.app.ui.QIconKind
import com.quandian.app.ui.components.MetaRow
import com.quandian.app.ui.components.PriorityTag
import com.quandian.app.ui.components.StrikeText
import com.quandian.app.ui.components.TaskCheck
import com.quandian.app.ui.tapFeedback
import com.quandian.app.ui.theme.FlowSkin
import com.quandian.app.ui.theme.Priority
import com.quandian.app.ui.theme.QD
import com.quandian.app.ui.theme.titleStyle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 月视图：上方日历，默认当月；某天有已完成的待办时在那天下方点一个小圆点；
 * 选中某天，下方用与首页列表同一套卡片样式列出当天完成的待办。
 */
@Composable
fun CalendarPage(state: UiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    var month: YearMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selected: LocalDate? by remember(today) { mutableStateOf<LocalDate?>(today) }

    val doneByDay: Map<LocalDate, List<TaskEntity>> = remember(state.doneTasks) {
        state.doneTasks.mapNotNull { t -> t.completedAt?.let { dayOf(it) to t } }
            .groupBy({ it.first }, { it.second })
    }

    // 切月时：选中日若已不在当月，自动落到当月「有完成记录的最近一天」，否则当月 1 号
    fun shift(delta: Long) {
        val next = month.plusMonths(delta)
        month = next
        val stillHere = selected?.takeIf { YearMonth.from(it) == next }
        selected = stillHere ?: run {
            doneByDay.keys
                .filter { YearMonth.from(it) == next }
                .sortedDescending()
                .firstOrNull() ?: next.atDay(1)
        }
    }

    Column(modifier.fillMaxSize().background(QD.Surface)) {
        // ---------- 固定标题 + 月份切换 ----------
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 10.dp)) {
            PageHead(title = "月视图", latin = "CALENDAR", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            MonthBar(
                month = month,
                onPrev = { shift(-1) },
                onNext = { shift(1) },
                onToday = { month = YearMonth.from(today); selected = today },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QD.Line))

        // ---------- 日历网格 ----------
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            DayHeaderRow()
            Spacer(Modifier.height(8.dp))
            MonthGrid(
                month = month,
                today = today,
                selected = selected,
                doneByDay = doneByDay,
            ) { selected = it }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QD.LineSoft))

        // ---------- 选中日完成的待办 ----------
        val day = selected
        val list: List<TaskEntity> = if (day != null) doneByDay[day].orEmpty() else emptyList()
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (day != null) "${day.monthValue}月${day.dayOfMonth}日 还没有完成的事" else "选一天看看",
                    fontSize = 13.sp,
                    color = QD.InkFaint,
                    letterSpacing = 0.6.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(list, key = { it.id }) { task ->
                    CompletedCard(task = task, skin = state.skin, modifier = Modifier.padding(bottom = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthBar(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${month.year}年${month.monthValue}月", style = titleStyle.copy(fontSize = 17.sp))
        Spacer(Modifier.weight(1f))
        Text(
            "回到今天",
            fontSize = 12.sp,
            color = QD.AccentInk,
            letterSpacing = 0.4.sp,
            modifier = Modifier
                .tapFeedback { onToday() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(6.dp))
        ChevronButton(dir = -1, onClick = onPrev)
        Spacer(Modifier.width(4.dp))
        ChevronButton(dir = 1, onClick = onNext)
    }
}

@Composable
private fun ChevronButton(dir: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(QD.Surface2)
            .border(1.dp, QD.CardBorderSoft, RoundedCornerShape(10.dp))
            .tapFeedback { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // Chevron 默认朝下，左/右翻月分别旋转 90°/-90°
        QIcon(
            kind = QIconKind.Chevron,
            size = 16.dp,
            tint = QD.InkSoft,
            stroke = 2.dp,
            modifier = Modifier.graphicsLayer { rotationZ = if (dir < 0) 90f else -90f },
        )
    }
}

@Composable
private fun DayHeaderRow() {
    Row(Modifier.fillMaxWidth()) {
        "一二三四五六日".forEach { d ->
            Text(
                d.toString(),
                fontSize = 10.sp,
                color = QD.InkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    doneByDay: Map<LocalDate, List<TaskEntity>>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val startDow = first.dayOfWeek.value - 1 // 周一 = 0
    val daysInMonth = month.lengthOfMonth()
    val cells = ((startDow + daysInMonth + 6) / 7) * 7
    val startDate = first.minusDays(startDow.toLong())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(cells / 7) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { c ->
                    val idx = r * 7 + c
                    val date = startDate.plusDays(idx.toLong())
                    val inMonth = idx in startDow until startDow + daysInMonth
                    val isSel = inMonth && selected == date
                    val hasTasks = inMonth && doneByDay.containsKey(date)
                    val isToday = inMonth && date == today
                    DayCell(
                        date = date,
                        inMonth = inMonth,
                        isSelected = isSel,
                        hasTasks = hasTasks,
                        isToday = isToday,
                        onClick = { if (inMonth) onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    hasTasks: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberColor = when {
        isSelected -> QD.Ink
        isToday -> QD.AccentInk
        inMonth -> QD.InkSoft
        else -> QD.InkFaint.copy(alpha = 0.4f)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(11.dp))
            .then(
                if (isSelected) {
                    Modifier.background(QD.AccentSoft)
                        .border(1.4.dp, QD.AccentInk, RoundedCornerShape(11.dp))
                } else {
                    Modifier
                },
            )
            .then(if (inMonth) Modifier.tapFeedback { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 13.sp,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = numberColor,
            )
            Spacer(Modifier.height(3.dp))
            // 有完成记录才点一个小圆点；否则放一个等高的占位，保证每天的数字垂直居中一致
            if (hasTasks) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) QD.AccentInk else QD.Accent),
                )
            } else {
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

/**
 * 选中日下方的一张已完成卡片：与首页列表卡同一套排版（圆角 / 内边距 / 勾选圈 / 删除线 / 元信息），
 * 只是不再带侧滑动作区——月视图是「看」的，删除仍在首页列表里做。
 */
@Composable
private fun CompletedCard(task: TaskEntity, skin: FlowSkin, modifier: Modifier = Modifier) {
    val prio = Priority.fromLevel(task.priority)
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QD.Card)
            .border(1.dp, QD.CardBorder, shape),
    ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 13.dp, vertical = 10.dp)
                    .alpha(0.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            TaskCheck(done = true, locked = false, p = 0f, ring = skin.ring)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StrikeText(
                    text = task.title,
                    style = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = QD.Ink,
                        letterSpacing = 0.1.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    done = true,
                )
                Spacer(Modifier.height(3.dp))
                MetaRow(task)
            }
            if (prio != Priority.NONE) {
                Spacer(Modifier.width(8.dp))
                PriorityTag(prio, compact = true)
            }
        }
    }
}

/** 完成时间戳 → 当地日期，和统计页同源的换算 */
private fun dayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
