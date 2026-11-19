package com.doit.app.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doit.app.BuildConfig
import com.doit.app.data.AppSettings
import com.doit.app.data.CategoryEntity
import com.doit.app.data.CompletionLogEntity
import com.doit.app.data.SettingsRepository
import com.doit.app.data.TaskNode
import com.doit.app.data.TaskRepository
import com.doit.app.data.TaskEntity
import com.doit.app.data.buildTree
import com.doit.app.sync.DavUrl
import com.doit.app.sync.SnapshotCodec
import com.doit.app.sync.SyncEngine
import com.doit.app.sync.SyncMerge
import com.doit.app.sync.WebDavClient
import com.doit.app.update.ApkDownloader
import com.doit.app.update.UpdateChecker
import com.doit.app.ui.theme.FlowSkin
import com.doit.app.ui.theme.parseHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Page { List, Stats, Settings, Calendar }

sealed interface Sheet {
    data class Priority(val taskId: Long, val isSub: Boolean) : Sheet
    data class MoveCategory(val taskId: Long) : Sheet
    data class Subtask(val taskId: Long) : Sheet
    /** [category] 为用户打开表单时的页面筛选分类（"cat:X" / "all"），新增表单据此预选默认分类 */
    data class NewTask(val category: String) : Sheet
    /** 编辑已有任务（父任务 / 子任务均可），复用新增表单弹层 */
    data class EditTask(val taskId: Long) : Sheet
    data object Categories : Sheet
    data class ConfirmDelete(val taskId: Long) : Sheet
    data class ConfirmCategory(val name: String) : Sheet
}

data class CategoryUi(val name: String, val color: Color, val count: Int)

/** 应用内更新状态：检查→发现新版本→下载（带进度）→可安装 / 出错 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Found(val version: String, val apkUrl: String?) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Ready(val version: String, val file: java.io.File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** 同步一致性计算需要含墓碑的完整快照，与列表展示用的活数据分开观察 */
private data class SyncBundle(
    val logs: List<CompletionLogEntity>,
    val doneTasks: List<TaskEntity>,
    val allTasks: List<TaskEntity>,
    val allCategories: List<CategoryEntity>,
)

data class UiState(
    val nodes: List<TaskNode> = emptyList(),
    val allNodes: List<TaskNode> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val filter: String = "all",
    val done: Int = 0,
    val total: Int = 0,
    val completedTotal: Int = 0,
    /** 今日待办里可见的顶级任务总数（未整体完成 + 今日完成的），「全部」徽标按它统计 */
    val todayTotal: Int = 0,
    /** 今日待办里已完成（整体完成且完成于今天）的顶级任务数，右上角进度环分子 */
    val todayDone: Int = 0,
    val logs: List<CompletionLogEntity> = emptyList(),
    val doneTasks: List<TaskEntity> = emptyList(),
    val skin: FlowSkin = FlowSkin.GREEN,
    val settings: AppSettings = AppSettings(),
    /** 本地快照与上次成功同步的 base 是否内容一致；true=绿勾云，false=本地有未同步改动 */
    val cloudConsistent: Boolean = true,
    val loading: Boolean = true,
) {
    val remaining: Int get() = (total - done).coerceAtLeast(0)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val syncEngine = SyncEngine(repo, settingsRepo)

    /** 子任务组的展开 / 收起状态放在 ViewModel 里，滚动出屏幕或转屏都不会被重置 */
    val expand = com.doit.app.ui.components.ExpandState()

    private val _filter = MutableStateFlow("all")
    private val _page = MutableStateFlow(Page.List)
    private val _sheet = MutableStateFlow<Sheet?>(null)
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)

    val page: StateFlow<Page> = _page
    val sheet: StateFlow<Sheet?> = _sheet
    val toast = _toast.asSharedFlow()
    /** 应用内更新状态：检查 / 发现新版 / 下载进度 / 可安装 / 出错 */
    val updateState: StateFlow<UpdateState> = _updateState

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
        combine(repo.logs, repo.doneTasks, repo.allTasksSync, repo.allCategoriesSync) { l, d, at, ac ->
            SyncBundle(l, d, at, ac)
        },
    ) { tasks, categories, settings, filter, bundle ->
        val (logs, doneTasks, allTasksSync, allCategoriesSync) = bundle
        val tree = buildTree(tasks)
        // 今日待办：只按最顶级的完成状态过滤——未整体完成照常展示（子任务全部保留），
        // 整体完成且完成日期不是今天的顶级任务才从首页移除（历史完成记录进月视图）。
        val today = LocalDate.now()
        val todayNodes = tree.filter { showInToday(it, today) }
        val visible = if (filter == "all") todayNodes else todayNodes.filter { it.task.category == filter.removePrefix("cat:") }
        val doneTop = visible.count { it.task.done }
        UiState(
            nodes = visible,
            allNodes = tree,
            categories = categories.map { c ->
                CategoryUi(
                    name = c.name,
                    color = parseHex(c.colorHex),
                    count = todayNodes.count { it.task.category == c.name },
                )
            },
            filter = filter,
            done = doneTop,
            total = visible.size,
            todayTotal = todayNodes.size,
            todayDone = todayNodes.count { it.task.done },
            completedTotal = logs.size,
            logs = logs,
            doneTasks = doneTasks,
            skin = FlowSkin.from(settings.skin),
            settings = settings,
            cloudConsistent = cloudConsistent(allTasksSync, allCategoriesSync, settings),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** 本地当前快照（含墓碑）与上次成功同步的 base 做内容指纹对比：一致=绿勾云，否则需要再同步 */
    private fun cloudConsistent(
        allTasks: List<TaskEntity>,
        allCategories: List<CategoryEntity>,
        settings: AppSettings,
    ): Boolean {
        val baseJson = settings.syncBase
        // 还没同步出 base（从未成功同步过）→ 视为不一致，提示用户本地有东西待同步
        val base = if (baseJson.isBlank()) null else SnapshotCodec.decode(baseJson)
        if (base == null) return false
        // base 是 encode 落盘的，本地也走一遍 encode→decode，让两端进同一条规范化路径，
        // 否则 decode 的「timeLabel 空串 → 刚刚」等归一化会让偶然的空字段造成误判不一致。
        val localRaw = SnapshotCodec.fromEntities(allTasks, allCategories)
        val local = SnapshotCodec.decode(SnapshotCodec.encode(SyncMerge.canonical(localRaw))) ?: return false
        return SyncMerge.fingerprint(local) == SyncMerge.fingerprint(base)
    }

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
            say("已添加子任务" + if (priority > 0) " · " + com.doit.app.ui.theme.Priority.fromLevel(priority).label else "")
        }
    }

    fun setPriority(taskId: Long, level: Int) {
        viewModelScope.launch {
            repo.setPriority(taskId, level)
            say(if (level > 0) "优先级 · " + com.doit.app.ui.theme.Priority.fromLevel(level).label else "已清掉优先级")
        }
    }

    fun moveCategory(taskId: Long, category: String, childCount: Int) {
        viewModelScope.launch {
            repo.moveToCategory(taskId, category)
            say(if (childCount > 0) "已连同 $childCount 个子任务移到「$category」" else "已移到「$category」")
        }
    }

    fun updateTask(taskId: Long, title: String, category: String) {
        viewModelScope.launch {
            repo.updateTask(taskId, title, category)
            say("已更新「${title.trim()}」")
        }
    }

    /** 编辑子任务：只能改标题与优先级，分类跟随父任务不由这里设置 */
    fun updateSubtask(taskId: Long, title: String, priority: Int) {
        viewModelScope.launch {
            repo.updateSubtask(taskId, title, priority)
            say("已更新「${title.trim()}」")
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

    /** 真正去 GitHub Releases 拉最新版本比对，不再写死“已是最新”；发现新版把详情写进 updateState。
     *  仓库公开后匿名即可访问，无需 Token。 */
    fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val result = UpdateChecker.check(BuildConfig.VERSION_NAME, null)
            when {
                result.hasUpdate -> {
                    val version = result.latestVersion ?: ""
                    _updateState.value = UpdateState.Found(version, result.apkUrl)
                    say(result.message)
                }
                else -> {
                    _updateState.value = UpdateState.Idle
                    say(result.message)
                }
            }
        }
    }

    /** 下载已发现的新版 apk 到私有目录，带进度；下完自动跳到安装器 */
    fun downloadAndInstall() {
        val state = _updateState.value as? UpdateState.Found ?: return
        val url = state.apkUrl
        if (url.isNullOrBlank()) {
            say("该版本没有打包 app，请到 Release 页手动下载")
            return
        }
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(state.version, 0f)
            val app = getApplication<Application>()
            val token = settingsRepo.current().githubToken.ifBlank { null }
            val target = File(app.cacheDir, "doit-${state.version}.apk")
            val result = withContext(Dispatchers.IO) {
                ApkDownloader.download(
                    url = url,
                    token = token,
                    target = target,
                    onProgress = { p -> _updateState.value = UpdateState.Downloading(state.version, p) },
                )
            }
            result.onSuccess {
                _updateState.value = UpdateState.Ready(state.version, target)
                installApk(target)
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(ApkDownloader.friendlyError(e))
            }
        }
    }

    /** 打开系统安装器去安装已下载的 apk。需要 REQUEST_INSTALL_PACKAGES 权限（Manifest 已声明） */
    private fun installApk(file: File) {
        runCatching {
            val app = getApplication<Application>()
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(intent)
        }.onFailure {
            say("打开安装器失败：${it.message ?: "请到 Release 页手动下载"}")
        }
    }

    fun setDavEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setDavEnabled(enabled)
            if (!enabled) settingsRepo.setDavVerified(false)
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
                    settingsRepo.setDavVerified(true)
                    say(
                        if (r.workingUrl != null) "${r.message}，地址已自动修正为 ${r.workingUrl}"
                        else r.message,
                    )
                },
                onFailure = {
                    settingsRepo.setDavVerified(false)
                    say(it.message ?: "连接失败")
                },
            )
        }
    }

    fun syncDav(url: String, user: String, pass: String) {
        DavUrl.validate(url)?.let { say(it); return }
        viewModelScope.launch {
            settingsRepo.setDavConfig(url, user, pass)
            say("正在同步…")
            syncEngine.synchronize(url, user, pass).fold(
                onSuccess = { report ->
                    settingsRepo.setDavVerified(true)
                    say("配置已保存 · ${report.detail}")
                },
                onFailure = {
                    settingsRepo.setDavVerified(false)
                    say(it.message ?: "同步失败")
                },
            )
        }
    }

    fun consumeVoice(text: String) {
        voiceDraft = text
        _sheet.value = Sheet.NewTask("")
    }

    fun clearVoiceDraft() {
        voiceDraft = null
    }
}

/**
 * 首页「今日待办」的顶级任务展示判定：
 * - 未整体完成（顶级任务自己没勾上，或其子任务没全做完）→ 始终展示。
 * - 整体完成（顶级任务 + 全部子任务都完成）→ 只有完成日期是「今天」才留在今日待办；
 *   完成于今天之前（或完成时间缺失 / 异常日期）的都从首页移除，历史完成记录只看月视图。
 * 只作用于顶级任务，子任务不做单独过滤，因此凡是展示的顶级任务其全部子任务都会完整带出。
 */
private fun showInToday(node: TaskNode, today: LocalDate): Boolean {
    val fullyDone = node.task.done && (!node.hasChildren || node.allChildrenDone)
    if (!fullyDone) return true
    val completedDate = node.task.completedAt?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    return completedDate == today
}
