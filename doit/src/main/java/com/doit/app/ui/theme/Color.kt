package com.doit.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 设计令牌 · Design Tokens
 *
 * 全部颜色集中定义在 [DoitPalette] 里，[Doit] 通过 getter 代理到当前调色板。
 * 调色板挂在 mutableStateOf 上：运行时调用 `Doit.use(...)` 换一套调色板，
 * 所有读 `Doit.Xxx` 的组合会自动重组，实现动态主题色 / 夜间模式。
 *
 * 现在有两套调色板：
 * - [DoitPalette.Mint]：浅色，浅薄荷绿底 + 纯白卡片 + 薄荷绿主色 #34C7A8
 * - [DoitPalette.Midnight]：深色，深青灰底 + 薄荷绿强调色
 *
 * 关于「主色」的一族四档，用错了会很难看：
 * - [DoitPalette.accent]      #34C7A8 —— 只做**浅面**：未选中胶囊的浅底、圆点、进度环
 * - [DoitPalette.accentFill]  #2EB196 —— 实心控件的底（选中胶囊、圆形按钮、主按钮）
 * - [DoitPalette.accentInk]   #0F8069 —— 只做**字与细线**：文字、图标、1~2dp 的描边、折线
 * - [DoitPalette.onAccent]    #FFFFFF —— 压在实心控件上的字与图标（一律白字）
 * accent 太浅压不住白字（2.1:1），所以实心块单独用深一档的 accentFill（白字 2.7:1）；
 * 分类色同理，用 [fillFor] 把"白字看不清"的颜色压到够用，实底上一律白字。
 */
data class DoitPalette(
    val id: String,
    val isDark: Boolean = false,
    // 纸感中性色
    val paper: Color,        // --paper
    val paperDeep: Color,    // --paper-deep（深色下兼作图表轨道）
    val surface: Color,      // --surface
    val surface2: Color,     // --surface-2
    val screen: Color,       // --screen
    val card: Color,         // --card
    // 墨色文字
    val ink: Color,          // --ink
    val inkSoft: Color,      // --ink-soft
    val inkFaint: Color,     // --ink-faint
    // 线条
    val line: Color,         // rgba(ink,.15)
    val lineSoft: Color,     // rgba(ink,.08)
    val cardBorder: Color,   // 顶级卡片描边
    val cardBorderSoft: Color, // 子任务卡片描边
    val scrim: Color,        // 弹层遮罩
    // 主题色——换肤只动这一族
    val accent: Color,       // --accent
    val accentFill: Color,   // --accent-fill（实心控件的底，配白字）
    val accentSoft: Color,   // --accent-soft
    val accentInk: Color,    // --accent-ink（主题色可读文字版）
    val onAccent: Color,     // 压在主题色上的文字 / 图标
    val onTooltip: Color,    // 图表气泡里的文字（气泡底是 tooltip，不是 accent）
    val onAction: Color,     // 左滑动作区里的图标与文字
    val knob: Color,         // 开关滑块
    val tooltip: Color,      // 图表气泡、浮层底色
    val toggleOff: Color,    // 开关关闭态轨道
    val actionBgStart: Color, // 左滑操作区渐变起点
    val actionBgEnd: Color,   // 左滑操作区渐变终点
    // 语义色
    val strike: Color,       // 完成划线的朱橘
    val danger: Color,
    val dangerBg: Color,
) {
    companion object {
        /**
         * 浅色主题：清新薄荷绿（Mint）。
         *
         * 主色取 #34C7A8 做"浅面"，实心控件（选中胶囊、圆形按钮、主按钮）用深一档的
         * [accentFill] #2EB196 —— 同一支色相，只压深 8%，白字可读性从 2.1:1 提到 2.7:1。
         * 文字/细线另走 [accentInk] #0F8069（白底 4.9:1）。
         *
         * 中性色跟着换成带蓝的深灰（主要文字 #263348），整屏不再有任何米黄成分：
         * 底色是极淡的薄荷绿 #EEF7F2，卡片纯白 #FFFFFF，靠 1dp 冷灰描边分层。
         */
        val Mint = DoitPalette(
            id = "mint",
            isDark = false,
            paper = Color(0xFFE7F3ED),
            paperDeep = Color(0xFFDCEBE4),
            surface = Color(0xFFF7FBF9),
            surface2 = Color(0xFFE9F3EE),
            screen = Color(0xFFEEF7F2),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF263348),
            inkSoft = Color(0xFF566473),
            inkFaint = Color(0xFF5F6E7E),
            line = Color(0x1F263348),
            lineSoft = Color(0x0F263348),
            cardBorder = Color(0x18263348),
            cardBorderSoft = Color(0x10263348),
            scrim = Color(0x38263348),
            accent = Color(0xFF34C7A8),
            accentFill = Color(0xFF2DAB90),
            accentSoft = Color(0xFFDFF5EE),
            accentInk = Color(0xFF0F8069),
            onAccent = Color(0xFFFFFFFF),
            onTooltip = Color(0xFFFFFFFF),
            onAction = Color(0xFFFFFFFF),
            knob = Color(0xFFFFFFFF),
            tooltip = Color(0xFF22323A),
            toggleOff = Color(0xFFD9E6E1),
            actionBgStart = Color(0xFF16A98D),
            actionBgEnd = Color(0xFF0A8370),
            strike = Color(0xFFB85A32),
            danger = Color(0xFFD2554C),
            dangerBg = Color(0xFFFDEDEA),
        )

        /**
         * 深色主题：深夜青灰（Midnight Teal）。
         * 底不是纯黑，而是带一点青的灰（H≈175°），像夜里台灯下的纸；
         * 强调色反过来用薄荷绿 #5FD3B4 —— 在深底上够亮、够清爽。
         * 注意这套里 accent 本身就是"浅色实底"，所以压在它上面的字反过来用深墨，
         * 这是「浅底配深字、深底配白字」的另一半，不是例外。
         */
        val Midnight = DoitPalette(
            id = "midnight-teal",
            isDark = true,
            paper = Color(0xFF121E1C),
            paperDeep = Color(0xFF2A3B35),   // 深色下当图表轨道，要比卡片亮一点才看得见
            surface = Color(0xFF162421),
            surface2 = Color(0xFF1F2F2B),
            screen = Color(0xFF111D1B),
            card = Color(0xFF1A2925),
            ink = Color(0xFFE9F1EC),
            inkSoft = Color(0xFFA7B8B0),
            inkFaint = Color(0xFF7C8E86),
            line = Color(0x2CE9F1EC),
            lineSoft = Color(0x16E9F1EC),
            cardBorder = Color(0x1FE9F1EC),
            cardBorderSoft = Color(0x14E9F1EC),
            scrim = Color(0xA6050C0B),
            accent = Color(0xFF5FD3B4),
            accentFill = Color(0xFF5FD3B4),
            accentSoft = Color(0xFF17362F),
            accentInk = Color(0xFF8FE5CC),
            onAccent = Color(0xFF062A22),
            onTooltip = Color(0xFFEAF4EE),
            onAction = Color(0xFFF0F7F3),
            knob = Color(0xFFF2F8F4),
            tooltip = Color(0xFF26362F),
            toggleOff = Color(0xFF2C3B36),
            actionBgStart = Color(0xFF2E6B5A),
            actionBgEnd = Color(0xFF285A4C),
            strike = Color(0xFFE39A72),
            danger = Color(0xFFE0897A),
            dangerBg = Color(0xFF38241F),
        )
    }
}

/** 极深墨：白字实在放不上去的浅彩（浅薄荷、米金）兜底用 */
private val DeepInk = Color(0xFF0B2A31)

/** 白字在 [bg] 上的对比度（WCAG）；2.6 左右是"彩色实底 + 白字"的可用底线 */
private fun whiteRatioOn(bg: Color): Float = 1.05f / (bg.luminance() + 0.05f)

/**
 * 实心控件的底：保证白字压上去至少 2.6:1。
 *
 * 分类色是用户自己挑的，"生活绿 #3FBB9C""采购金 #C9A24D"这类浅彩压白字只有 2.4:1，
 * 选中时白字会糊；这里只把**实心块**的底按比例压暗（RGB 同乘系数，色相和饱和度不变），
 * 压到「白字够读」就停手 —— 薄荷只降 1~2 档，金色几乎不变。
 * 浅底、虚线描边、页面主色都不动，所以整屏还是清新的。
 */
fun fillFor(bg: Color, min: Float = 2.6f): Color {
    if (whiteRatioOn(bg) >= min) return bg
    var c = bg
    repeat(12) {
        // 每次 -5% 明度：比混墨更保色相，不会把金色混成橄榄
        c = Color(c.red * 0.95f, c.green * 0.95f, c.blue * 0.95f, c.alpha)
        if (whiteRatioOn(c) >= min) return c
    }
    return c
}

/**
 * 在实心底上挑字色：**深底一律白字**，只有浅到底的（深色模式里的薄荷绿实底）才退回深墨。
 * 阈值与 [fillFor] 对齐，两者配合的结果就是"彩色块 + 白字"。
 */
fun readableOn(bg: Color): Color {
    val whiteRatio = whiteRatioOn(bg)
    return if (whiteRatio >= 2.6f) Color(0xFFFFFFFF) else DeepInk
}

/** 同色相的"文字版"：往墨色里压，保留色相但保证对比度（分类胶囊的未选中文字用） */
fun labelOn(bg: Color, mix: Float = 0.55f): Color = lerp(bg, Doit.Ink, mix)

/**
 * 选中胶囊 / 滑块指示器的实底。
 * 主题绿直接取 [Doit.AccentFill]（浅色下已经压深一档，深色下就是那支亮薄荷）；
 * 分类色 / 优先级语义色按需压深，保证上面的白字看得清。
 */
fun chipFill(accent: Color): Color =
    if (accent == Doit.Accent) Doit.AccentFill else fillFor(accent)

/** 实心底上的字：深底白字；只有"浅到底"的（深色模式里的薄荷）才缩回深墨 */
fun chipOn(accent: Color): Color =
    if (accent == Doit.Accent) Doit.OnAccent else readableOn(fillFor(accent))

/**
 * 勾选圆 / 进度线的实心圆底。
 * 浅色：ring 是薄荷、天蓝这类浅彩，压深一档配白勾；
 * 深色：ring 本身就是亮薄荷，压在深色卡片上已经够醒目，再用深勾，不跟着压暗。
 */
fun checkFill(ring: Color): Color = if (Doit.IsDark) ring else fillFor(ring)

/**
 * 左滑动作区的语义图标色（各动作固定含义，不随主题变）。
 *
 * 一套色相均匀铺开的语义色板：暖侧 45° 黄 → 28° 橘 → 2° 红，冷侧 255° 紫 → 209° 蓝，
 * 相邻动作色相至少差 17°、明暗接近，一眼能分清谁是谁，也不会有哪个特别跳。
 * 语义对齐主流任务工具（Todoist / TickTick / Jira / Trello / Linear）的通用习惯：
 *  - 编辑用黄：修改类动作的温和提示色，不与「危险红」「完成绿」抢含义；
 *  - 优先级用橘黄：Jira/TickTick 的中高优先级旗标普遍是橙→红→黄的暖色梯度；
 *  - 分类用紫：标签 / 分类 / 文件夹语义在主流工具里普遍是紫色系（Todoist 标签、
 *    Trello 与 Linear 的 label、TickTick 的工作类标签），与「优先级」的暖色明确分开；
 *  - 子任务用蓝：Jira 的子任务图标、Asana/Trello 的「进行中 / 子步骤」都是低饱和蓝；
 *    绿色在业界和本应用里都留给「完成 / 主题色」，而且绿圆压在侧滑区的深绿底上
 *    本来就分不清，换成冷蓝后对比度与辨识度都更好；
 *  - 删除用红、换色用金，沿用原有的破坏性 / 调色语义。
 *
 * 所有动作的图标一律白色（不再有深墨 / 白两套）：圆底在 RowScopeAction 里统一经
 * [fillFor] 等比压深到白图标够读（≥2.6:1），色相与饱和度不变，仍看得清是黄 / 橘黄。
 * 压深只作用于黄、橘黄、金这几个浅底，紫 / 蓝 / 红原本就够对比度、保持原色。
 */
object ActionColors {
    val Edit = Color(0xFFE6B62C)      // 编辑 · 黄
    val Priority = Color(0xFFF08C31)  // 优先级 · 橘黄
    val Category = Color(0xFF8A72D1)  // 分类 · 紫
    val Subtask = Color(0xFF3E8BD4)   // 子任务 · 蓝
    val Palette = Color(0xFFC9A24D)   // 换色 · 金
    val Delete = Color(0xFFDE6B66)    // 删除 · 红
}

/**
 * 全局唯一取色入口。
 * 界面里永远写 `Doit.Ink`、`Doit.Accent` 这样的令牌，不写裸 Color(0x…)；
 * 换主题时调用 [use]，所有引用处自动刷新。
 */
object Doit {
    var palette by mutableStateOf(DoitPalette.Mint)
        private set

    /** 动态切换主题调色板 */
    fun use(p: DoitPalette) {
        palette = p
    }

    /** 当前是不是深色——需要分深浅微调透明度的地方读它 */
    val IsDark get() = palette.isDark

    // 纸感中性色
    val Paper get() = palette.paper
    val PaperDeep get() = palette.paperDeep
    val Surface get() = palette.surface
    val Surface2 get() = palette.surface2
    val Screen get() = palette.screen
    val Card get() = palette.card

    // 墨色文字
    val Ink get() = palette.ink
    val InkSoft get() = palette.inkSoft
    val InkFaint get() = palette.inkFaint

    // 线条
    val Line get() = palette.line
    val LineSoft get() = palette.lineSoft
    val CardBorder get() = palette.cardBorder
    val CardBorderSoft get() = palette.cardBorderSoft
    val Scrim get() = palette.scrim

    // 主题色
    val Accent get() = palette.accent
    val AccentFill get() = palette.accentFill
    val AccentSoft get() = palette.accentSoft
    val AccentInk get() = palette.accentInk
    val OnAccent get() = palette.onAccent
    val OnTooltip get() = palette.onTooltip
    val OnAction get() = palette.onAction
    val Knob get() = palette.knob
    val Tooltip get() = palette.tooltip
    val ToggleOff get() = palette.toggleOff
    val ActionBgStart get() = palette.actionBgStart
    val ActionBgEnd get() = palette.actionBgEnd

    // 语义色
    val Strike get() = palette.strike
    val Danger get() = palette.danger
    val DangerBg get() = palette.dangerBg
}

/**
 * 滑动进度皮肤：绿色生长 / 火花四溅 / 蓝色流水。
 * 每套皮肤有浅色与深色两版填充：浅色下是纸上的淡彩，深色下换成同一色相的低明度上色，
 * 否则整块浅色会像在深底上开了一盏灯。
 */
enum class FlowSkin(
    val key: String,
    val label: String,
    private val lightRing: Color,
    private val lightFillStart: Color,
    private val lightFillMid: Color,
    private val lightFillEnd: Color,
    private val darkRing: Color,
    private val darkFillStart: Color,
    private val darkFillMid: Color,
    private val darkFillEnd: Color,
) {
    GREEN(
        key = "green",
        label = "绿色生长",
        lightRing = Color(0xFF2BB89A),
        lightFillStart = Color(0xFFE9F9F3),
        lightFillMid = Color(0xFFCFF1E6),
        lightFillEnd = Color(0xFFADE7D7),
        darkRing = Color(0xFF5FD3B4),
        darkFillStart = Color(0xFF14332C),
        darkFillMid = Color(0xFF1A3E33),
        darkFillEnd = Color(0xFF21493C),
    ),
    SPARK(
        key = "spark",
        label = "火花四溅",
        lightRing = Color(0xFFC2601F),
        lightFillStart = Color(0xFFFFF0CF),
        lightFillMid = Color(0xFFFFC964),
        lightFillEnd = Color(0xFFFF8B3D),
        darkRing = Color(0xFFE8A063),
        darkFillStart = Color(0xFF3A2A17),
        darkFillMid = Color(0xFF4A351C),
        darkFillEnd = Color(0xFF5A4022),
    ),
    WATER(
        key = "water",
        label = "蓝色流水",
        lightRing = Color(0xFF2A6B8F),
        lightFillStart = Color(0xFFE2F1FB),
        lightFillMid = Color(0xFFB8DDF3),
        lightFillEnd = Color(0xFF8CC9E9),
        darkRing = Color(0xFF6FB6DA),
        darkFillStart = Color(0xFF16303C),
        darkFillMid = Color(0xFF1B3B49),
        darkFillEnd = Color(0xFF204757),
    );

    val ring: Color get() = if (Doit.IsDark) darkRing else lightRing
    val fillStart: Color get() = if (Doit.IsDark) darkFillStart else lightFillStart
    val fillMid: Color get() = if (Doit.IsDark) darkFillMid else lightFillMid
    val fillEnd: Color get() = if (Doit.IsDark) darkFillEnd else lightFillEnd

    companion object {
        fun from(key: String?): FlowSkin = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

/**
 * 优先级：色点 + 文案，与视觉稿 PRIOS 对齐。
 * 枚举常量在类加载时求值，取不到 Doit 的运行时调色板，因此这里写死语义色；
 * 它们本来也不随主题变（低/普通/高/紧急 的色相是固定语义）。
 * NONE 的值与浅色调色板的 inkFaint 保持一致。
 *
 * 色相按"冷 → 暖"排：低·青绿 / 普通·蓝 / 高·橘 / 紧急·红，
 * 相邻两档既差色相也差明度，一眼就能分出轻重，不靠读字。
 */
enum class Priority(val level: Int, val key: String, val label: String, val color: Color) {
    NONE(0, "", "不设", Color(0xFF5F6E7E)),
    LOW(1, "low", "低", Color(0xFF4FBF9E)),
    MID(2, "mid", "普通", Color(0xFF4A86D6)),
    HIGH(3, "high", "高", Color(0xFFE08A34)),
    URGENT(4, "urgent", "紧急", Color(0xFFDE5B55));

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}

/** 分类配色：新分类从色板里按顺序取色（薄荷绿打头，整体清爽） */
val PaletteColors = listOf(
    "#3FBB9C", "#5B9BD5", "#C9A24D", "#9186C9",
    "#2BB89A", "#E0857A", "#5A7F95", "#64B0C9",
)

fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
}.getOrDefault(Color(0xFF3FBB9C))

/** 热力图 5 阶绿色（深浅两套，随调色板切换；最深一阶与主色同源） */
private val HeatLight = listOf(
    Color(0xFFE3F7F0),
    Color(0xFFC4EFE2),
    Color(0xFF93E2CC),
    Color(0xFF58D0B2),
    Color(0xFF2BB89A),
)

private val HeatDark = listOf(
    Color(0xFF22322C),   // 空格子：比页面底色亮一档，否则整片热力图在深色下"消失"
    Color(0xFF244038),
    Color(0xFF2E6352),
    Color(0xFF3F9276),
    Color(0xFF5FD3B4),
)

val HeatColors: List<Color> get() = if (Doit.IsDark) HeatDark else HeatLight
