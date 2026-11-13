package com.quandian.app.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quandian.app.BuildConfig
import com.quandian.app.data.AppSettings
import com.quandian.app.data.CompletionLogEntity
import com.quandian.app.data.SettingsRepository
import com.quandian.app.data.TaskNode
import com.quandian.app.data.TaskRepository
import com.quandian.app.data.TaskEntity
import com.quandian.app.data.buildTree
import com.quandian.app.sync.DavUrl
import com.quandian.app.sync.SyncEngine
import com.quandian.app.sync.WebDavClient
import com.quandian.app.ui.theme.FlowSkin
import com.quandian.app.ui.theme.parseHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Page { List, Stats, Settings, Calendar }

sealed interface Sheet {
    data class Priority(val taskId: Long, val isSub: Boolean) : Sheet
    data class MoveCategory(val taskId: Long) : Sheet
    data class Subtask(val taskId: Long) : Sheet
    data object NewTask : Sheet
    data object Categories : Sheet
    data class ConfirmDelete(val taskId: Long) : Sheet
    data class ConfirmCategory(val name: String) : Sheet
}

data class CategoryUi(val name: String, val color: Color, val count: Int)

data class UiState(
    val nodes: List<TaskNode> = emptyList(),
    val allNodes: List<TaskNode> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val filter: String = "all",
    val done: Int = 0,
    val total: Int = 0,
    val completedTotal: Int = 0,
    val logs: List<CompletionLogEntity> = emptyList(),
    val doneTasks: List<TaskEntity> = emptyList(),
    val skin: FlowSkin = FlowSkin.GREEN,
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
) {
    val remaining: Int get() = (total - done).coerceAtLeast(0)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val syncEngine = SyncEngine(repo, settingsRepo)

    /** 子任务组的展开 / 收起状态放在 ViewModel 里，滚动出屏幕或转屏都不会被重置 */
    val expand = com.quandian.app.ui.components.ExpandState()

    private val _filter = MutableStateFlow("all")
    private val _page = MutableStateFlow(Page.List)
    private val _sheet = MutableStateFlow<Sheet?>(null)
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val page: StateFlow<Page> = _page
    val sheet: StateFlow<Sheet?> = _sheet
    val toast = _toast.asSharedFlow()

    /** 新建任务后请求列表回顶（新任务插在第一位，但 LazyColumn 会保持原视口锚点） */
    private val _scrollTop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollTop = _scrollTop.asSharedFlow()

    /** 语音输入听到的内容，交给新建面板预填 */
    var voiceDraft: String? = null
        private set

    val uiState: StateFlow<UiState> = combine(
        repo.tasks,
        repo.categories,
        settingsRepo.settings,
        _filter,
        combine(repo.logs, repo.doneTasks) { logs, doneTasks -> logs to doneTasks },
    ) { tasks, categories, settings, filter, logsAndDone ->
        val (logs, doneTasks) = logsAndDone
        val tree = buildTree(tasks)
        val visible = if (filter == "all") tree else tree.filter { it.task.category == filter.removePrefix("cat:") }
        val doneTop = tree.count { it.task.done }
        UiState(
            nodes = visible,
            allNodes = tree,
            categories = categories.map { c ->
                CategoryUi(
                    name = c.name,
                    color = parseHex(c.colorHex),
                    count = tree.count { it.task.category == c.name },
                )
            },
            filter = filter,
            done = doneTop,
            total = tree.size,
            completedTotal = logs.size,
            logs = logs,
            doneTasks = doneTasks,
            skin = FlowSkin.from(settings.skin),
            settings = settings,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            // 老库升级后补齐同步元信息（幂等），再谈别的
            repo.ensureSyncMeta()
            // 正式包什么都不预置：首页只有「全部」，分类和待办都由用户自己建
            if (BuildConfig.DEBUG) {
                // 调试包：分类 + 示例待办 + 一年演示完成记录，打开就有内容可看
                repo.ensureSeeded()
                repo.seedDemoHistory()
            }
        }
    }

    // ---------- 导航 ----------

    fun go(page: Page) {
        _page.value = page
    }

    fun back() {
        _page.value = Page.List
    }

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    // ---------- 面板 ----------

    fun openSheet(sheet: Sheet) {
        _sheet.value = sheet
    }

    fun closeSheet() {
        _sheet.value = null
    }

    fun say(text: String) {
        _toast.tryEmit(text)
    }

    // ---------- 待办操作 ----------

    fun toggleDone(node: TaskNode) {
        if (node.hasChildren) {
            say("子任务全部完成后会自动勾上")
            return
        }
        viewModelScope.launch { repo.toggleDone(node.task.id) }
    }

    fun complete(node: TaskNode) {
        if (node.hasChildren) return
        viewModelScope.launch {
            repo.setDone(node.task.id, true)
        }
    }

    fun blocked() {
        say("子任务全部完成后会自动勾上")
    }

    fun addTask(title: String, category: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.addTask(title, category)
            say(if (category.isBlank()) "已添加" else "已添加到「$category」")
            _scrollTop.tryEmit(Unit)
        }
    }

    fun addSubtask(parentId: Long, title: String, priority: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.addSubtask(parentId, title, priority)
            say("已添加子任务" + if (priority > 0) " · " + com.quandian.app.ui.theme.Priority.fromLevel(priority).label else "")
        }
    }

    fun setPriority(taskId: Long, level: Int) {
        viewModelScope.launch {
            repo.setPriority(taskId, level)
            say(if (level > 0) "优先级 · " + com.quandian.app.ui.theme.Priority.fromLevel(level).label else "已清掉优先级")
        }
    }

    fun moveCategory(taskId: Long, category: String, childCount: Int) {
        viewModelScope.launch {
            repo.moveToCategory(taskId, category)
            say(if (childCount > 0) "已连同 $childCount 个子任务移到「$category」" else "已移到「$category」")
        }
    }

    fun deleteTask(taskId: Long, title: String, childCount: Int, root: Boolean) {
        viewModelScope.launch {
            repo.delete(taskId)
            say(if (childCount > 0) "已删掉「$title」和它下面的 $childCount 个子任务" else "已删除「$title」")
        }
    }

    // ---------- 分类 ----------

    fun addCategory(name: String) {
        viewModelScope.launch {
            repo.addCategory(name)
            say("已添加「${name.trim()}」")
        }
    }

    fun setCategoryColor(name: String, hex: String) {
        viewModelScope.launch {
            repo.setCategoryColor(name, hex)
            say("「$name」的颜色换好了")
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            repo.deleteCategory(name)
            // 正停在这个分类的筛选上时退回「全部」，否则列表会空得莫名其妙
            if (_filter.value == "cat:$name") _filter.value = "all"
            say("已删除「$name」，里面的待办变成未分类")
        }
    }

    // ---------- 设置 ----------

    fun setSkin(key: String) {
        viewModelScope.launch { settingsRepo.setSkin(key) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setDavEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setDavEnabled(enabled)
            say(if (enabled) "已开启自动同步" else "已关闭自动同步")
        }
    }

    fun saveDav(url: String, user: String, pass: String) {
        viewModelScope.launch {
            settingsRepo.setDavConfig(url, user, pass)
            say("配置已保存")
        }
    }

    fun testDav(url: String, user: String, pass: String) {
        // 界面已经拦过一道，这里再兜一次：别把明显不是地址的字符串丢给网络层
        DavUrl.validate(url)?.let { say(it); return }
        viewModelScope.launch {
            say("正在测试连接…")
            WebDavClient.test(url, user, pass).fold(
                onSuccess = { r ->
                    // 客户端自动找到可用目录时把它写回配置，设置页的输入框会跟着刷新，
                    // 这样「保存并同步」用的就是已验证过的地址。
                    r.workingUrl?.let { settingsRepo.setDavConfig(it, user, pass) }
                    say(
                        if (r.workingUrl != null) "${r.message}，地址已自动修正为 ${r.workingUrl}"
                        else r.message,
                    )
                },
                onFailure = { say(it.message ?: "连接失败") },
            )
        }
    }

    fun syncDav(url: String, user: String, pass: String) {
        DavUrl.validate(url)?.let { say(it); return }
        viewModelScope.launch {
            settingsRepo.setDavConfig(url, user, pass)
            say("正在同步…")
            syncEngine.synchronize(url, user, pass).fold(
                onSuccess = { report -> say("配置已保存 · ${report.detail}") },
                onFailure = { say(it.message ?: "同步失败") },
            )
        }
    }

    fun consumeVoice(text: String) {
        voiceDraft = text
        _sheet.value = Sheet.NewTask
    }

    fun clearVoiceDraft() {
        voiceDraft = null
    }
}
