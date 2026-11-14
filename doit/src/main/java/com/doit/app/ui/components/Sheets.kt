package com.doit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doit.app.data.NO_CATEGORY
import com.doit.app.data.TaskNode
import com.doit.app.ui.DoitIcon
import com.doit.app.ui.DoitIconKind
import com.doit.app.ui.Sheet
import com.doit.app.ui.components.PillTab
import com.doit.app.ui.components.PillTabs
import com.doit.app.ui.components.PillTabsMode
import com.doit.app.ui.tapFeedback
import com.doit.app.ui.gesture.SwipeState
import com.doit.app.ui.gesture.taskSwipe
import com.doit.app.ui.theme.ActionColors
import com.doit.app.ui.theme.PaletteColors
import com.doit.app.ui.theme.Priority
import com.doit.app.ui.theme.Doit
import com.doit.app.ui.theme.numeric
import com.doit.app.ui.theme.parseHex
import com.doit.app.ui.theme.titleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val FieldsShape = RoundedCornerShape(11.dp)

@Composable
fun DoitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onEnter: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = Doit.Ink, fontFamily = com.doit.app.ui.theme.SansFamily),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Doit.Accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onEnter?.invoke() },
            onGo = { onEnter?.invoke() },
            onSend = { onEnter?.invoke() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldsShape)
            .background(Doit.Surface2)
            .border(1.dp, Doit.Line, FieldsShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 12.5.sp, color = Doit.InkFaint)
                }
                inner()
            }
        },
    )
}

private val ActionShape = RoundedCornerShape(13.dp)

/**
 * 弹窗里的主行动按钮：整行高度 48dp，横向由 [SheetActions] 平分，
 * 手指不用瞄着一个小胶囊点，盲按也基本能中。
 */
@Composable
private fun SheetActionButton(
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (danger) Doit.DangerBg else Doit.Surface2
    val border = if (danger) Doit.Danger.copy(alpha = 0.42f) else Doit.Line
    val fg = if (danger) Doit.Danger else Doit.InkSoft
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(ActionShape)
            .background(bg)
            .border(1.dp, border, ActionShape)
            .tapFeedback(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = fg,
            fontWeight = if (danger) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 两个等宽按钮并排：[primaryLabel] 在左（危险动作用红调），[secondaryLabel] 在右。
 * 宽度各占一半、中间只留 10dp，点哪边都是一大片，不会有"点不准"的感觉。
 */
@Composable
private fun SheetActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetActionButton(
            label = primaryLabel,
            modifier = Modifier.weight(1f),
            danger = true,
            onClick = onPrimary,
        )
        SheetActionButton(
            label = secondaryLabel,
            modifier = Modifier.weight(1f),
            onClick = onSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(
    sheet: Sheet?,
    nodes: List<TaskNode>,
    categories: List<com.doit.app.ui.CategoryUi>,
    onDismiss: () -> Unit,
    onNewTask: (String, String) -> Unit,
    onSaveTask: (Long, String, String) -> Unit,
    onSaveSubtask: (Long, String, Int) -> Unit,
    onPriority: (Long, Int) -> Unit,
    onMoveCategory: (Long, String, Int) -> Unit,
    onAddSubtask: (Long, String, Int) -> Unit,
    onAddCategory: (String) -> Unit,
    onCategoryColor: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDeleteTask: (Long, String, Int) -> Unit,
    voiceDraft: String?,
    onVoiceConsumed: () -> Unit,
) {
    if (sheet == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val target: TaskNode? = remember(sheet, nodes) {
        val id = when (sheet) {
            is Sheet.Priority -> sheet.taskId
            is Sheet.MoveCategory -> sheet.taskId
            is Sheet.Subtask -> sheet.taskId
            is Sheet.EditTask -> sheet.taskId
            is Sheet.ConfirmDelete -> sheet.taskId
            else -> null
        }
        if (id == null) null else findNode(nodes, id)
    }

    val title = when (sheet) {
        is Sheet.Priority -> "轻重缓急"
        is Sheet.MoveCategory -> "移动分类"
        is Sheet.Subtask -> "拆成几步"
        is Sheet.NewTask -> "添加待办"
        is Sheet.EditTask -> "编辑待办"
        Sheet.Categories -> "管理分类"
        is Sheet.ConfirmDelete -> "确认删除"
        is Sheet.ConfirmCategory -> "删除分类"
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = Doit.Surface,
        scrimColor = Doit.Scrim,
        shape = SheetShape,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Doit.Line),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = titleStyle, modifier = Modifier.weight(1f))
                Text("DOIT", fontSize = 9.sp, letterSpacing = 1.8.sp, color = Doit.InkFaint)
            }

            when (sheet) {
                is Sheet.NewTask -> TaskFormBody(
                    categories = categories,
                    initialText = voiceDraft ?: "",
                    defaultCategory = sheet.category,
                    voiceDraft = voiceDraft,
                    onVoiceConsumed = onVoiceConsumed,
                    onSubmit = { text, cat ->
                        onNewTask(text, cat); close()
                    },
                    onCancel = { close() },
                )

                // 顶级父任务：复刻「添加待办」表单——标题 + 分类（默认预选当前分类，仍可手动切换）；
                // 子任务：复刻「拆成几步」表单——只允许改标题与优先级，分类跟随父任务、不提供也不可设置。
                is Sheet.EditTask -> if (target?.task?.parentId == null) {
                    TaskFormBody(
                        categories = categories,
                        initialText = target?.task?.title ?: "",
                        defaultCategory = target?.task?.category ?: "",
                        voiceDraft = null,
                        onVoiceConsumed = {},
                        onSubmit = { text, cat ->
                            onSaveTask(sheet.taskId, text, cat); close()
                        },
                        onCancel = { close() },
                    )
                } else {
                    EditSubtaskBody(
                        node = target,
                        onSubmit = { text, prio ->
                            onSaveSubtask(sheet.taskId, text, prio); close()
                        },
                        onCancel = { close() },
                    )
                }

                is Sheet.Priority -> PriorityBody(
                    task = target?.task,
                    isSub = sheet.isSub,
                    onPick = { level ->
                        onPriority(sheet.taskId, level); close()
                    },
                )

                is Sheet.MoveCategory -> MoveCategoryBody(
                    node = target,
                    categories = categories,
                    onPick = { cat ->
                        onMoveCategory(sheet.taskId, cat, target?.children?.size ?: 0); close()
                    },
                )

                is Sheet.Subtask -> SubtaskBody(
                    node = target,
                    onSubmit = { text, prio ->
                        onAddSubtask(sheet.taskId, text, prio); close()
                    },
                    onCancel = { close() },
                )

                Sheet.Categories -> CategoriesBody(
                    categories = categories,
                    onAdd = onAddCategory,
                    onColor = onCategoryColor,
                    onDelete = onDeleteCategory,
                    onDone = { close() },
                )

                is Sheet.ConfirmDelete -> ConfirmDeleteBody(
                    node = target,
                    onConfirm = {
                        target?.let { onDeleteTask(it.task.id, it.task.title, it.children.size) }
                        close()
                    },
                    onKeep = { close() },
                )

                is Sheet.ConfirmCategory -> ConfirmCategoryBody(
                    name = sheet.name,
                    count = categories.firstOrNull { it.name == sheet.name }?.count ?: 0,
                    onConfirm = { onDeleteCategory(sheet.name); close() },
                    onKeep = { close() },
                )
            }
        }
    }
}

private fun findNode(nodes: List<TaskNode>, id: Long): TaskNode? {
    nodes.forEach { node ->
        if (node.task.id == id) return node
        findNode(node.children, id)?.let { return it }
    }
    return null
}

@Composable
private fun SheetDesc(text: String) {
    Text(text, fontSize = 11.5.sp, lineHeight = 19.sp, color = Doit.InkFaint, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SheetNote(text: String) {
    Box(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .border(0.dp, Color.Transparent),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Doit.LineSoft))
            Text(
                text,
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
                color = Doit.InkFaint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun TaskFormBody(
    categories: List<com.doit.app.ui.CategoryUi>,
    initialText: String,
    defaultCategory: String,
    voiceDraft: String?,
    onVoiceConsumed: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    // 默认选中「当前所在分类」：顶部切到某分类后点 +，这里就预选它；顶部在「全部」时默认「不分类」。
    // 每次打开都会按当时的页面分类重新预选（remember 的 key 带上 defaultCategory），
    // 手动仍可随时用下面的胶囊切到任意分类（含「不分类」），加完再开仍回到正确默认值。
    var cat by remember(defaultCategory, categories) {
        mutableStateOf(initialNewTaskCategory(defaultCategory, categories))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (voiceDraft != null) onVoiceConsumed()
        delay(320)
        runCatching { focus.requestFocus() }
    }

    Spacer(Modifier.height(14.dp))
    DoitTextField(
        value = text,
        onValueChange = { if (it.length <= 30) text = it },
        placeholder = "比如：给妈妈回个电话",
        focusRequester = focus,
        onEnter = { if (text.isNotBlank()) onSubmit(text, cat) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (categories.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        PillTabs(
            tabs = buildList {
                add(PillTab(key = NO_CATEGORY, label = "不分类", accent = Doit.InkFaint))
                categories.forEach { c -> add(PillTab(key = c.name, label = c.name, accent = c.color)) }
            },
            selectedKey = cat,
            onSelect = { cat = it },
            mode = PillTabsMode.Chip,
            wrap = true,
            height = 34.dp,
            gap = 7.dp,
        )
    } else {
        SheetNote("还没有分类。想归类的话，先到「管理分类」里建一个。")
    }
    CancelSaveBar(
        onCancel = onCancel,
        onSave = { if (text.isNotBlank()) onSubmit(text, cat) },
        saveEnabled = text.isNotBlank(),
        saveLabel = "保存",
    )
}

/** 解析表单默认分类：入参可能是页面筛选串（"all" / "cat:X"）或任务自身的分类名（"X" / ""）。 */
private fun initialNewTaskCategory(preferred: String, categories: List<com.doit.app.ui.CategoryUi>): String {
    val stripped = preferred.removePrefix("cat:")
    val name = if (stripped == "all") NO_CATEGORY else stripped
    return if (name != NO_CATEGORY && categories.any { it.name == name }) name else NO_CATEGORY
}

@Composable
private fun PriorityBody(task: com.doit.app.data.TaskEntity?, isSub: Boolean, onPick: (Int) -> Unit) {
    val current = task?.priority ?: 0
    // 同分类：先让胶囊滑到位，再回调收起面板
    var picked by remember(task?.id) { mutableStateOf(current) }
    val scope = rememberCoroutineScope()

    SheetDesc(
        if (isSub) "这一小步也可以有自己的轻重。"
        else "优先级只影响排序与颜色，不会挪动它现在的位置。",
    )
    Spacer(Modifier.height(14.dp))
    // 与分类胶囊同一套交互：优先级色即胶囊色，切换时滑动过渡
    PillTabs(
        tabs = Priority.entries.filter { it != Priority.NONE }.reversed().map { p ->
            PillTab(key = p.level.toString(), label = p.label, accent = p.color)
        } + PillTab(key = "0", label = "不设", accent = Doit.InkFaint),
        selectedKey = picked.toString(),
        onSelect = { key ->
            val level = key.toInt()
            if (level != picked) {
                picked = level
                scope.launch {
                    delay(280)
                    onPick(level)
                }
            }
        },
        mode = PillTabsMode.Chip,
        wrap = true,
        height = 34.dp,
        gap = 7.dp,
    )
    SheetNote(if (isSub) "子任务的优先级会以行尾的小标签显示。" else "列表里会在卡片最右侧以标签显示优先级。")
}

@Composable
private fun MoveCategoryBody(
    node: TaskNode?,
    categories: List<com.doit.app.ui.CategoryUi>,
    onPick: (String) -> Unit,
) {
    // 选中态由面板自己持有：先让胶囊滑过去，再回调并收起，否则过渡动画一闪就没了
    var picked by remember(node) { mutableStateOf(node?.task?.category ?: categories.firstOrNull()?.name ?: "") }
    val scope = rememberCoroutineScope()
    val childCount = node?.children?.size ?: 0

    SheetDesc(
        if (childCount > 0) "换个分类，卡片标签跟着变，下面的 $childCount 个子任务一起过去。"
        else "换个分类，卡片标签跟着变。",
    )
    Spacer(Modifier.height(14.dp))
    if (categories.isEmpty()) {
        SheetNote("还没有分类。先到「管理分类」里建一个，再回来移动。")
        return
    }
    // 与首页顶部分类栏同一套胶囊：未选中虚线描边 + 分类色浅底，选中实色填充，滑动过渡
    PillTabs(
        tabs = categories.map { c ->
            PillTab(key = c.name, label = c.name, badge = c.count, accent = c.color)
        },
        selectedKey = picked,
        onSelect = { name ->
            if (name != picked) {
                picked = name
                scope.launch {
                    delay(280)
                    onPick(name)
                }
            }
        },
        mode = PillTabsMode.Chip,
        wrap = true,
        height = 34.dp,
        gap = 7.dp,
    )
    SheetNote("点一下就移过去，立刻生效。")
}

@Composable
private fun SubtaskBody(node: TaskNode?, onSubmit: (String, Int) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var prio by remember { mutableStateOf(Priority.MID.level) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(320)
        runCatching { focus.requestFocus() }
    }
    SheetDesc(
        if (node?.hasChildren == true) "这一步还能继续往下拆。它的下一步全部做完时，它也会自动勾上。"
        else "子任务全部做完，这件事会自动勾上，不用再手动点。",
    )
    SheetDesc("写好内容，选好轻重，点保存就加一条：")
    Spacer(Modifier.height(12.dp))
    DoitTextField(
        value = text,
        onValueChange = { if (it.length <= 24) text = it },
        placeholder = "比如：先列好提纲",
        focusRequester = focus,
        onEnter = { if (text.isNotBlank()) onSubmit(text, prio) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    PillTabs(
        tabs = Priority.entries.filter { it != Priority.NONE }.reversed().map { p ->
            PillTab(key = p.level.toString(), label = p.label, accent = p.color)
        } + PillTab(key = "0", label = "不设", accent = Doit.InkFaint),
        selectedKey = prio.toString(),
        onSelect = { prio = it.toInt() },
        mode = PillTabsMode.Chip,
        wrap = true,
        height = 34.dp,
        gap = 7.dp,
    )
    SheetNote("保存后立即生效并收起面板；想继续加，再点一次「子任务」就好。删除请到卡片上左滑。")
    CancelSaveBar(
        onCancel = onCancel,
        onSave = { if (text.isNotBlank()) onSubmit(text, prio) },
        saveEnabled = text.isNotBlank(),
        saveLabel = "保存",
    )
}

/**
 * 编辑子任务：与「拆成几步」（添加子任务）同一交互——只能改标题和轻重缓急，没有分类。
 * 子任务的分类始终跟随父任务，这里既不显示也不可单独设置；预填当前标题与优先级。
 */
@Composable
private fun EditSubtaskBody(node: TaskNode?, onSubmit: (String, Int) -> Unit, onCancel: () -> Unit) {
    val task = node?.task
    var text by remember(task?.id) { mutableStateOf(task?.title ?: "") }
    var prio by remember(task?.id) { mutableStateOf(task?.priority ?: Priority.MID.level) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(320)
        runCatching { focus.requestFocus() }
    }
    SheetDesc("子任务的分类始终跟随父任务，这里不需要、也不能单独设置。")
    SheetDesc("只能改标题和轻重缓急，改好点保存：")
    Spacer(Modifier.height(12.dp))
    DoitTextField(
        value = text,
        onValueChange = { if (it.length <= 24) text = it },
        placeholder = "子任务标题",
        focusRequester = focus,
        onEnter = { if (text.isNotBlank()) onSubmit(text, prio) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    PillTabs(
        tabs = Priority.entries.filter { it != Priority.NONE }.reversed().map { p ->
            PillTab(key = p.level.toString(), label = p.label, accent = p.color)
        } + PillTab(key = "0", label = "不设", accent = Doit.InkFaint),
        selectedKey = prio.toString(),
        onSelect = { prio = it.toInt() },
        mode = PillTabsMode.Chip,
        wrap = true,
        height = 34.dp,
        gap = 7.dp,
    )
    CancelSaveBar(
        onCancel = onCancel,
        onSave = { if (text.isNotBlank()) onSubmit(text, prio) },
        saveEnabled = text.isNotBlank(),
        saveLabel = "保存",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesBody(
    categories: List<com.doit.app.ui.CategoryUi>,
    onAdd: (String) -> Unit,
    onColor: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDone: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var paletteFor by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    if (paletteFor != null) {
        val name = paletteFor!!
        val current = categories.firstOrNull { it.name == name }?.color
        SheetDesc("给「$name」挑个颜色。")
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 8,
        ) {
            PaletteColors.forEach { hex ->
                val color = parseHex(hex)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (current == color) 2.dp else 0.dp,
                            // 色板上任何一个色块都可能是主色本身，选中环用墨色才不会被色块吃掉
                            color = if (current == color) Doit.Ink else Color.Transparent,
                            shape = CircleShape,
                        )
                        .tapFeedback {
                            onColor(name, hex); paletteFor = null
                        },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlineButton("返回分类列表") { paletteFor = null }
        return
    }

    SheetDesc("按住分类卡片往左滑：换颜色，或删除。")
    Spacer(Modifier.height(12.dp))
    if (categories.isEmpty()) {
        Text(
            "还没有分类。下面输入一个名字就能建，建好后首页会多一个胶囊。",
            fontSize = 11.sp,
            lineHeight = 18.sp,
            color = Doit.InkFaint,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { c ->
            CategoryManageCard(
                category = c,
                onColor = { paletteFor = c.name },
                onDelete = { onDelete(c.name) },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    DoitTextField(
        value = text,
        onValueChange = { if (it.length <= 8) text = it },
        placeholder = "新分类，回车添加",
        focusRequester = focus,
        // 回车仍可快速连加多个分类；点底部「保存」则加完并收起面板
        onEnter = { if (text.isNotBlank()) { onAdd(text); text = "" } },
        modifier = Modifier.fillMaxWidth(),
    )
    SheetNote("删除分类不会删掉里面的待办，它们会变成未分类。")
    CancelSaveBar(
        onCancel = onDone,
        onSave = { if (text.isNotBlank()) { onAdd(text); onDone() } },
        saveEnabled = text.isNotBlank(),
        saveLabel = "保存",
    )
}

/** 分类卡片：左滑露出 颜色 / 删除 */
@Composable
private fun CategoryManageCard(
    category: com.doit.app.ui.CategoryUi,
    onColor: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    // 和待办卡片同一套槽宽 / 间距：两个按钮之间留出同样的空隙
    val actionPx = with(density) { ActionMetrics.widthOf(2).toPx() }
    val state = remember { SwipeState(actionWidthPx = actionPx, blocked = false, enabled = true) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Doit.ActionBgStart, Doit.ActionBgEnd)))
            .taskSwipe(
                state = state,
                scope = scope,
                onComplete = {},
                onBlocked = {},
                onHaptic = {},
                allowComplete = false,
            ),
    ) {
        Row(
            Modifier
                .matchParentSize()
                .padding(end = ActionMetrics.Edge),
            horizontalArrangement = Arrangement.spacedBy(ActionMetrics.Gap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowScopeAction(null, DoitIconKind.Palette, ActionColors.Palette) {
                state.reset(); onColor()
            }
            RowScopeAction(null, DoitIconKind.Trash, ActionColors.Delete) {
                state.reset(); onDelete()
            }
        }
        Row(
            Modifier
                .offset { IntOffset(state.offset.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Doit.Card)
                .border(1.dp, Doit.CardBorder, RoundedCornerShape(14.dp))
                .tapFeedback { state.reset() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(category.color))
            Spacer(Modifier.width(10.dp))
            Text(category.name, fontSize = 13.sp, color = Doit.Ink, modifier = Modifier.weight(1f))
            Text("${category.count} 件", style = numeric(10).copy(fontWeight = FontWeight.Normal), color = Doit.InkFaint)
        }
    }
}

@Composable
private fun ConfirmDeleteBody(node: TaskNode?, onConfirm: () -> Unit, onKeep: () -> Unit) {
    val n = node?.children?.size ?: 0
    SheetDesc("「${node?.task?.title ?: ""}」下面还挂着 $n 个子任务，删掉它，这些子任务会一起消失。")
    SheetActions(
        primaryLabel = "一起删掉",
        onPrimary = onConfirm,
        secondaryLabel = "先留着",
        onSecondary = onKeep,
    )
    SheetNote("删除不会进回收站，找不回来。")
}

@Composable
private fun ConfirmCategoryBody(name: String, count: Int, onConfirm: () -> Unit, onKeep: () -> Unit) {
    SheetDesc("「$name」分类下还有 $count 件待办，删除后它们会变成未分类。")
    SheetActions(
        primaryLabel = "删除分类",
        onPrimary = onConfirm,
        secondaryLabel = "先留着",
        onSecondary = onKeep,
    )
    SheetNote("删除分类不会删掉待办，只是换了个归属。")
}

/**
 * 弹框底部统一的「取消 / 保存」操作条：左右各占一半、中间留间距，
 * 保存为实色主题色（AccentFill），取消为描边次按钮。与确认弹框的 [SheetActions]
 * 同一套尺寸（48dp 高、圆角矩形），手指不用瞄着小胶囊点。
 */
@Composable
private fun CancelSaveBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveLabel: String = "保存",
) {
    Spacer(Modifier.height(18.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(ActionShape)
                .border(1.dp, Doit.Line, ActionShape)
                .tapFeedback { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "取消",
                fontSize = 13.sp,
                color = Doit.InkSoft,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(ActionShape)
                .background(if (saveEnabled) Doit.AccentFill else Doit.Line)
                .tapFeedback(enabled = saveEnabled) { onSave() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                saveLabel,
                fontSize = 13.sp,
                color = if (saveEnabled) Doit.OnAccent else Doit.InkFaint,
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Doit.Line, RoundedCornerShape(999.dp))
            .tapFeedback { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, color = Doit.InkSoft, textAlign = TextAlign.Center)
    }
}
