package com.doit.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doit.app.data.TaskNode
import com.doit.app.ui.components.Breather
import com.doit.app.ui.components.FabOrbit
import com.doit.app.ui.components.PillTab
import com.doit.app.ui.components.PillTabs
import com.doit.app.ui.components.PillTabsMode
import com.doit.app.ui.components.ProgressRing
import com.doit.app.ui.components.Satellite
import com.doit.app.ui.components.SheetHost
import com.doit.app.ui.components.TaskCallbacks
import com.doit.app.ui.components.TopTaskRow
import com.doit.app.ui.theme.Doit
import com.doit.app.ui.theme.SansFamily
import com.doit.app.ui.theme.displayStyle
import kotlinx.coroutines.delay
import java.time.LocalDate

private val WEEKDAYS_CN = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    var menuOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // 侧滑 / 返回键：子页面（统计、设置）只退回到首页，不退出应用。
    // 弹层开着时交给 SheetHost 自己的返回处理（它注册在后，优先级更高），先关弹层。
    BackHandler(enabled = page != Page.List && sheet == null) {
        vm.back()
    }

    LaunchedEffect(Unit) {
        vm.toast.collect { message ->
            toast = message
            delay(1900)
            if (toast == message) toast = null
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) {
        vm.scrollTop.collect {
            listState.animateScrollToItem(0)
        }
    }

    val callbacks = remember(vm) {
        TaskCallbacks(
            onToggle = { node -> vm.toggleDone(node) },
            onComplete = { node -> vm.complete(node) },
            onBlocked = { vm.blocked() },
            onEdit = { node -> vm.openSheet(Sheet.EditTask(node.task.id)) },
            onPriority = { node -> vm.openSheet(Sheet.Priority(node.task.id, node.task.parentId != null)) },
            onCategory = { node -> vm.openSheet(Sheet.MoveCategory(node.task.id)) },
            onSubtask = { node -> vm.openSheet(Sheet.Subtask(node.task.id)) },
            onDelete = { node ->
                if (node.hasChildren) {
                    vm.openSheet(Sheet.ConfirmDelete(node.task.id))
                } else {
                    vm.deleteTask(node.task.id, node.task.title, 0, node.task.parentId == null)
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Doit.Screen)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ListHeader(
                state = state,
                onFilter = vm::setFilter,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 24.dp, end = 24.dp, top = 12.dp, bottom = 150.dp,
                ),
            ) {
                items(state.nodes, key = { it.task.id }) { node ->
                    TopTaskRow(
                        node = node,
                        skin = state.skin,
                        haptics = haptics,
                        cb = callbacks,
                        expand = vm.expand,
                        showCategory = state.filter == "all",
                        // 切分类时列表跟着淡入淡出 / 归位，而不是"啪"地换一批卡片
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(220),
                            fadeOutSpec = tween(160),
                            placementSpec = spring(stiffness = 700f, dampingRatio = 0.9f),
                        ),
                    )
                }
                if (state.done > 0) {
                    item("breather") { Breather(completedCount = state.done) }
                }
            }
        }

        Box(Modifier.fillMaxSize().navigationBarsPadding()) {
            FabOrbit(
                expanded = menuOpen,
                onExpandedChange = { open ->
                    menuOpen = open
                    if (open) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onTap = {
                    menuOpen = false
                    // 带着当前所在分类开表单：分类默认预选它（在「全部」时默认不分类）
                    vm.openSheet(Sheet.NewTask(state.filter))
                },
                onSatellite = { sat ->
                    menuOpen = false
                    when (sat) {
                        Satellite.Category -> vm.openSheet(Sheet.Categories)
                        Satellite.Calendar -> vm.go(Page.Calendar)
                        Satellite.Stats -> vm.go(Page.Stats)
                        Satellite.Settings -> vm.go(Page.Settings)
                    }
                },
            )
        }

        // 全屏子页面：统计 / 设置
        AnimatedVisibility(
            visible = page == Page.Stats,
            enter = slideInHorizontally(tween(340)) { it },
            exit = slideOutHorizontally(tween(300)) { it },
        ) {
            Box(Modifier.fillMaxSize().background(Doit.Surface).statusBarsPadding()) {
                StatsPage(state = state, onBack = vm::back)
            }
        }

        AnimatedVisibility(
            visible = page == Page.Settings,
            enter = slideInHorizontally(tween(340)) { it },
            exit = slideOutHorizontally(tween(300)) { it },
        ) {
            Box(Modifier.fillMaxSize().background(Doit.Surface).statusBarsPadding()) {
                SettingsPage(
                    settings = state.settings,
                    currentSkin = state.skin,
                    onBack = vm::back,
                    onSkin = vm::setSkin,
                    onTheme = vm::setThemeMode,
                    onDavEnabled = vm::setDavEnabled,
                    onTest = vm::testDav,
                    onSaveSync = vm::syncDav,
                    onCheckUpdate = vm::checkUpdate,
                    onGithubToken = vm::setGithubToken,
                    onToast = vm::say,
                )
            }
        }

        AnimatedVisibility(
            visible = page == Page.Calendar,
            enter = slideInHorizontally(tween(340)) { it },
            exit = slideOutHorizontally(tween(300)) { it },
        ) {
            Box(Modifier.fillMaxSize().background(Doit.Surface).statusBarsPadding()) {
                CalendarPage(state = state, onBack = vm::back)
            }
        }

        SheetHost(
            sheet = sheet,
            nodes = state.allNodes,
            categories = state.categories,
            onDismiss = vm::closeSheet,
            onNewTask = { text, cat -> vm.addTask(text, cat) },
            onSaveTask = { id, text, cat -> vm.updateTask(id, text, cat) },
            onPriority = { id, level -> vm.setPriority(id, level) },
            onMoveCategory = { id, cat, n -> vm.moveCategory(id, cat, n) },
            onAddSubtask = { id, text, prio -> vm.addSubtask(id, text, prio) },
            onAddCategory = vm::addCategory,
            onCategoryColor = vm::setCategoryColor,
            onDeleteCategory = vm::deleteCategory,
            onDeleteTask = { id, title, n -> vm.deleteTask(id, title, n, true) },
            voiceDraft = vm.voiceDraft,
            onVoiceConsumed = vm::clearVoiceDraft,
        )

        // 提示条
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(240)),
            ) {
                Box(
                    Modifier
                        .padding(bottom = 150.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Doit.AccentFill)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(
                        toast ?: "",
                        fontSize = 11.5.sp,
                        color = Doit.OnAccent,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListHeader(state: UiState, onFilter: (String) -> Unit) {
    val today = LocalDate.now()
    val kicker = "${today.year}年${today.monthValue}月${today.dayOfMonth}日 星期${WEEKDAYS_CN[today.dayOfWeek.value - 1]}"

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(6.dp))
        Text(
            kicker,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
            color = Doit.InkFaint,
            fontFamily = SansFamily,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日", style = displayStyle, modifier = Modifier.weight(1f))
            // 右上角进度恒按「今日全部任务」统计：已完成 + 未完成都计入分母，分子是今日完成的，
            // 与当前切到的分类标签解耦；非今日完成的历史/未来项已被 showInToday 排除。
            ProgressRing(done = state.todayDone, total = state.todayTotal)
        }
        Text(
            // 空清单（正式包首次启动就是这样）先教用户下一步，别对着"今天都放下了"发呆
            when {
                state.total == 0 -> "还没有待办，点右下角加一件。"
                state.remaining > 0 -> "还有 ${state.remaining} 件事，慢慢来。"
                else -> "今天都放下了，休息吧。"
            },
            fontSize = 12.5.sp,
            color = Doit.InkSoft,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(14.dp))

        PillTabs(
            tabs = buildList {
                add(PillTab(key = "all", label = "全部", badge = state.todayTotal, accent = Doit.Accent))
                state.categories.forEach { c ->
                    add(PillTab(key = "cat:${c.name}", label = c.name, badge = c.count, accent = c.color))
                }
            },
            selectedKey = state.filter,
            onSelect = onFilter,
            mode = PillTabsMode.Chip,
            height = 34.dp,
            gap = 7.dp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Doit.Line))
    }
}
