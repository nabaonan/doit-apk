# doit

一款清新薄荷绿的待办应用（Kotlin + Jetpack Compose，原名「圈点 QUANDIAN」，第四轮更名 doit）：
浅薄荷绿底 + 纯白卡片 + 薄荷绿主色 `#34C7A8`，另一种配色是深夜青灰 + 薄荷绿的夜间模式（全部色彩走设计令牌，支持运行时换肤与深浅切换），右滑让进度从卡片最左端长出来，拖到底就完成。

- 源码仓库：<https://github.com/nabaonan/doit-apk>（只有打 `v*` tag 才触发构建：Debug 包 + 正式包 + Release，版本号由 tag 推导）
- 视觉稿：`design/prototype/index.html`（可交互 HTML 原型）
- 设计效果图：`design/圈点-APP设计稿.png`

## 已实现的设计细节

| 设计稿 | 实现 |
| --- | --- |
| 顶部「今日」+ 完成进度环 | `ProgressRing`，弧形随完成数补间动画 |
| 分类筛选块（颜色随分类） | `PillTabs` Chip 模式：未选中虚线描边 + 同色浅底，选中实色填充，指示胶囊平滑滑动 |
| 圆角待办卡片 + 圆形勾选 | 19dp 圆角、1dp 极淡描边、26dp 圆形勾选 |
| 右滑完成（进度跟手生长） | 进度层宽度 = 手指位移；滑到 **卡片宽度 55%** 上膛，松手完成 |
| 勾外进度环 + 对勾描出 | 环形进度 + `PathMeasure` 分段描边，与滑动同一个进度值 |
| 三种进度皮肤 | 绿色生长（斜纹）/ 火花四溅（火星 + 橙金）/ 蓝色流水（水纹 + 浪光），设置里可切 |
| 左滑露出四动作 | 优先级 / 分类 / 子任务 / 删除，越过动作区有阻尼，过半吸附 |
| 子任务可无限下拆 | 自引用 `parentId`，缩进递减，不画引导线 |
| 子任务全做完父任务自动勾 | `recalcUp()` 逐级向上冒泡，取消子任务同样回退 |
| 有子任务时父任务虚线圈 | 虚线圆 + 横向抖动 + 提示「子任务全部完成后会自动勾上」 |
| 完成划线 | 一条与文字同色的浅灰细线，多行每行都有（不再用黄/橘） |
| 完成即收起 | 完成后卡片淡化划线，列表尾部出现「空出来的地方，也是设计。」 |
| 轻点 ＋ 记一条 | 新建面板（分类预选、回车即收进清单） |
| 长按 ＋ 绽开四颗卫星 | 添加分类 / 语音输入 / 热力图 / 设置，沿上半弧弹性展开，只显示图标 |
| 底部面板 | 轻重缓急 / 移动分类 / 拆成几步 / 管理分类 / 删除确认 |
| 分类管理左滑 | 换颜色（8 色色板）/ 删除（里面的待办变成未分类） |
| 统计页 | 热力图（周 / 月 / 年）、环形图、折线图，三段时间联动 |
| 设置页 | 主题（跟随系统 / 浅色 / 深色）、进度皮肤、WebDAV 自动同步、关于 |

## 第二轮界面优化（8 项）

| # | 需求 | 实现要点 |
| --- | --- | --- |
| 1 | 子任务卡片尺寸固定 | `SubtaskRow` 不再按 `depth` 递减字号/内边距/勾选尺寸，只保留缩进；卡片高度恒为同一档 |
| 2 | 进度环画在圆圈边缘 | `TaskCheck` 的弧半径由 `boxPx/2 - 1.2dp` 改为 `dotR`，2.4dp 线宽直接压在轮廓线上，不再外套第二圈 |
| 3 | 优先级改成右侧 tag | 新增 `PriorityTag`：圆角 7dp、优先级色 13% 底 + 45% 描边 + 同色字；主卡片与子任务行都放最右端，替代原来的圆点＋文字 |
| 4 | 任务文字垂直居中 | 卡片首行 `Row(verticalAlignment = CenterVertically)`，勾选圆与标题同轴心；次级信息（时间/分类）另起一行下沉 |
| 5 | 分类标签只在「全部」显示 | `TopTaskRow(showCategory = state.filter == "all")`，非「全部」视图只留时间，连分隔符「·」一起去掉 |
| 6 | 年热力图铺满屏宽 | `StatSection` 拆分「标题/说明恒定 22dp 边距」与「内容插槽可全幅」；年视图 `cell = (maxWidth - gap*(cols-1)) / cols`，图例单独回到 22dp 内 |
| 7 | 圆润无衬线 + 中文日期 | 删除 `SerifFamily`，统一 `FontFamily.SansSerif`（真机为 HarmonyOS Sans），标题风格改名 `titleStyle`；顶部 kicker 改为「2026年11月13日 星期五」 |
| 8 | 图表点击高亮 + 气泡 | 新增 `TipBubble` 气泡：热力图每格可切换选中（1.6dp 墨色描边）、环形图按半径带 + 角度命中扇区、折线图按最近 x 索引；三者都以深色气泡显示「日期/分类 · N 件」，环形图选中弧加粗、折线图选中点放大并带虚线参考线 |

气泡的关键实现：零尺寸节点（`Modifier.layout { layout(0, 0) { place(x, y) } }`）配合无约束测量，才能画出父级边界而不被裁剪
——换成 `Layout(Modifier.fillMaxSize())` 会在 `verticalScroll` 收到无限高约束时崩溃。

## 第三轮界面修复（5 项）

| # | 问题 | 修法 |
| --- | --- | --- |
| 1 | 滑动时进度环被灰圈盖住 | `TaskCheck` 调整绘制顺序：先画底圈轮廓，再画进度弧，环压在上层 |
| 2 | 卡片四角有深色圆角残留 | 左滑动作区底色原本铺满整张容器，卡片哪怕移开 1px，圆角处就漏出深青色月牙。改为 `ActionBackdrop` 只画在卡片右缘之外（起点再向左让出一个圆角半径，把圆角外侧的小三角也盖住，叠在卡片下面的部分被卡片遮住） |
| 3 | 气泡尾尖露出菱形边 | `TipTail` 从「旋转 45° 的方块」改为 `Canvas` 画的等腰三角（12×6dp），底边贴气泡、尖端指锚点，方向随 `up` 翻转 |
| 4 | 月份切换缺过渡 | `RangeSwitcher` 用深色胶囊做指示器，`animateDpAsState`（300ms `EaseOutExpo`）滑到选中项，文字 `animateColorAsState`（220ms）同步过渡；内容区 `Animatable` 淡入 + 上浮 14px（260ms），换范围不再「啪」地换一屏 |
| 5 | 标题与切换栏要固定 | 页面拆成「固定头部（`PageHead` + `RangeSwitcher`）+ `weight(1f)` 的滚动内容」；内容滚起来后头部底部的 1px 细线淡入（`animateFloatAsState` 180ms），交代这一层是钉住的 |

真机复核时又补了一处：折线图的气泡原本嵌在图表的 `Box` 里，而同级的刻度文字行在它之后绘制，会把气泡压住、刻度数字从气泡里透出来。修法是把气泡移出图表 `Box`、放到刻度行**之后**绘制（同级子项按顺序叠放），并给 `TipBubble` 加 `zIndex(2f)` 兜底。

## 第四轮：交互与视觉一致性（7 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 分类未选中也要能看出颜色 | 新增 `components/PillTabs.kt` 的 **Chip 模式**：未选中 = 该分类色的虚线描边（`drawBehind` + `PathEffect.dashPathEffect(4.5/3dp)`）+ 同色 10% 浅底；选中才换成实色填充 + 白字 |
| 2 | tab 切换要有过渡动画，且统一胶囊形 | 同一组件的两种形态共用 `RoundedCornerShape(999.dp)`、`EaseOutExpo` 缓动与 320ms 时长：**Equal 模式**（等高等分、深色指示胶囊滑到选中项）；**Chip 模式**（宽度自适应、指示胶囊用 `Animatable` 同时补间 x 与宽度，颜色也在两个分类色之间过渡） |
| 3 | 长按加号的卫星按钮只留图标 | `FabOrbit` 删掉标签 `Text`，图标 20→22dp；说明文字改走 `Modifier.semantics { contentDescription }`，无障碍不丢。`Satellite` 枚举去掉无用的 `labelAbove` |
| 4 | 封装通用 tab 组件 + 修设置页外观无动画 | `PillTabs(tabs, selectedKey, onSelect, mode, height, gap)` 成为唯一入口：首页分类（Chip）、统计页时间范围（Equal）、设置页外观皮肤（Equal，带渐变圆点）三处全部换用它，原来各自的实现删掉 |
| 5 | 设置页顶栏固定 | 与统计页同构：`Column { 固定头部(PageHead) ; 1px 细线(随滚动淡入) ; Column(weight(1f).verticalScroll(...)) }` |
| 6 | 所有按钮要有点击反馈 | `Modifier.clickableNoRipple` 改名并重写为 `tapFeedback`：形状内 `ripple` + 按下 11% 压暗（`drawWithContent` 画在内容之上，所以压暗能罩住整个元素，不会出现"只有文字缩小、底板不动"）。27 处调用全部切换过来，整屏遮罩用 `rippleColor = null` |
| 7 | 应用名改为 doit | `strings.xml` 的 `app_name`、debug 的 `resValue`（doit · 调试）、新建面板右上角拉丁字 `QUANDIAN → DOIT`、设置页页脚 `doit · 把清单，写在一张纸上。`；`applicationId` 保持 `com.quandian.app` 不动，避免用户升级时数据丢失 |

关于按压效果的一个坑：缩放要求 `graphicsLayer` 位于修饰符链首，而 `tapFeedback` 总是拼在 `clip/background` 之后，缩放只会作用到文字、背景不跟着缩，反而更奇怪。所以最终改成"水波纹 + 压暗"，两者都在节点内部绘制，与链序无关。

## 第五轮：圆圈居中 + 主题色收敛（2 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 顶级卡片的圆圈要相对整卡垂直居中，且内容增减时仍居中 | 卡片内容由「一列（标题行 + 元信息行）」重构为「一横行：圆圈列 \| 文字列」。`Row(Modifier.height(IntrinsicSize.Min))` 让行高由文字列撑开，圆圈列 `fillMaxHeight() + wrapContentHeight(CenterVertically)` 撑满这个高度再居中；`IntrinsicSize.Min` 每帧按子项真实内容测量，标题换行、元信息增减、系统字号变化都会重新算高度，圆圈自动跟随。点击热区仍只有圆圈本身（`fillMaxHeight` 的只是外层定位盒） |
| 2 | 统一主题色、去掉散落硬编码、换成清新明亮的绿、支持动态换肤 | `theme/Color.kt` 重写为 **`QDPalette` 数据类 + `QD` 代理对象**：所有色值集中在 `QDPalette.FreshGreen`，`QD.Ink` / `QD.Accent` 等令牌改为 getter 代理到 `mutableStateOf` 的当前调色板，界面调用写法完全不变；`QD.use(palette)` 即可整包换肤，读令牌的组合自动重组。新主题 `accent` = `#22C55E`（原来的 `#3D9B8F` 墨绿与 `QD.Ink` 近黑选中态全部收敛到它），`Theme.kt` 的 Material 色板也从 `val` 改成函数 `lightScheme()`，同样跟随令牌 |

### 颜色令牌一览

> 这一节记录当前的两套调色板；第五、七轮表格里写的 `FreshGreen` / `WarmSage` 已被本轮取代。

| 令牌 | 浅色 `Sage` | 深色 `Midnight` | 用在哪 |
| --- | --- | --- | --- |
| `Accent` | `#55805F` 鼠尾草绿（HSL 134/20%/42%） | `#79C9A0` 薄荷绿 | FAB、选中胶囊、进度环、统计折线 |
| `AccentSoft` / `AccentInk` | `#E7EDE3` / `#2F6044` | `#1E3A31` / `#9CDDB9` | Material 主色容器 / 主题色文字 |
| `OnAccent` | 白 | `#0B1F17` 深墨 | 压在主色上的文字与图标 |
| `OnTooltip` / `OnAction` / `Knob` | 白 / 白 / 白 | `#EAF4EE` / `#F0F7F3` / `#F2F8F4` | 气泡文字、左滑动作图标、开关滑块 |
| `Screen` / `Card` | `#F7F5EF` / `#FFFEFA` | `#111D1B` / `#1A2925` | 页面底 / 卡片 |
| `Surface` / `Surface2` | `#FCFBF6` / `#F1EFE7` | `#162421` / `#1F2F2B` | 子页面、输入框与浅色 chip |
| `Ink` / `InkSoft` / `InkFaint` | `#2E2C26` / `#646155` / `#7E7868` | `#E9F1EC` / `#A7B8B0` / `#7C8E86` | 正文 / 次要 / 提示 |
| `CardBorder` / `CardBorderSoft` | `#122E2C26` / `#0F2E2C26` | `#1FE9F1EC` / `#14E9F1EC` | 卡片描边 |
| `Scrim` | `#422E2C26` | `#A6050C0B` | 弹层遮罩 |
| `Tooltip` | `#2E2C26` | `#26362F` | 统计气泡本体与三角尾尖 |
| `ToggleOff` / `DangerBg` | `#E2DCCC` / `#FAF0E9` | `#2C3B36` / `#38241F` | 开关关闭态 / 危险 chip 底 |
| `ActionBgStart`→`ActionBgEnd` | `#5C8B6B→#48745A` | `#3C6A52→#325946` | 左滑露出的操作区渐变 |
| `ActionColors.*` | 蓝/紫/绿/金/红 | 同左（语义色不随主题变） | 左滑四动作与分类管理动作图标 |

三类按语义保留的例外：`FlowSkin` 三套皮肤（各自有浅色与深色两版填充）、`Priority` 五档语义色、`FillLayer` 里的白色高光斜纹（按深浅自动降 alpha：0.42 → 0.14）。

> 已存在的分类颜色是用户数据（DataStore/Room 里存的是十六进制），不会随调色板改写；新建分类从 `PaletteColors` 取色。

同时被统一掉的"黑 vs 墨绿"混用：统计页时间范围与设置页外观的选中胶囊（原 `QD.Ink` → `QD.Accent`）、设置页「保存并同步」主按钮（原 `QD.Ink` → `QD.Accent`）、首页提示条（原 `QD.Ink` → `QD.Accent`）、面板选中 chip（原 `QD.Ink` → `QD.Accent`）、热力图/环形图的选中描边（原 `QD.Ink` → `QD.Accent`）、`tapFeedback` 默认水波纹色（原 `QD.Ink` → `QD.Accent`）。原 `QD.Ok` 与 `Accent` 重复，合并进 `Accent`。

三类按语义保留的例外：`FlowSkin` 三套皮肤的渐变色（皮肤本身就是色彩方案）、`Priority` 五档语义色、`FillLayer` 里的白色高光斜纹（装饰性 alpha 叠加）。

> 已存在的分类颜色是用户数据（DataStore/Room 里存的是十六进制），不会被这次调色板调整改写；新建分类从新的 `PaletteColors` 取色。

## 第六轮：弹框内的选择器统一为胶囊（3 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 「移动分类」弹框用胶囊展示分类，带切换动画，并显示分类颜色 | `MoveCategoryBody` 从竖向列表改成 `PillTabs(mode = Chip, wrap = true)`：未选中虚线描边 + 分类色 10% 浅底，选中实色填充 + 白字，尾随该分类下的件数 |
| 2 | 「设置优先级」同样用胶囊 | `PriorityBody` 用同一组件，`accent` 取 `Priority.color`（紧急红 / 高橙 / 普通金 / 低青 / 不设灰），选中即实色填充 |
| 3 | 添加子任务 / 设置分类时也要有切换动画 | `SubtaskBody` 的优先级、`NewTaskBody` 的分类都换成 `PillTabs`；弹框里选中态由面板自己持有，先让胶囊滑过去（`delay(280)`）再回调并收起，否则动画会一闪而过 |

胶囊组件本身新增了 `wrap` 参数：`ChipPillTabs` 的布局在「单行横向滚动」与 `FlowRow` 换行之间切换，指示胶囊的 `boundsInParent` + `Animatable` 补间逻辑两版共用，所以弹框里的手感和首页分类栏完全一致。顺手删掉了不再有调用方的 `QdChip.dotColor` 与 `priorityOptions` 空壳函数。

## 第七轮：文案精简 + 暖调配色（2 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 把「移动到分类」这类说法再精简，减少认知负担 | 面板标题从比喻式的「归到哪个篮子」改成「移动分类」，省略介词、直接说动作；说明文案拆短（`换个分类，卡片标签跟着变`，有子任务时才追加「下面的 N 个子任务一起过去」），脚注改成「点一下就移过去，立刻生效。」 |
| 2 | 主题色（含按钮默认色）换成柔和、不刺眼的清新绿，整体走暖色系 | `QDPalette.FreshGreen` → `QDPalette.WarmSage`：`accent` 从 `#22C55E`（HSL 142/71%/45%，荧光感明显）改成 `#4A8A62`（约 33% 饱和、明度 41%），与白色文字对比度从 2.3:1 提到 4.2:1；纸底/卡片/输入框/线条/墨色文字同步加一层黄（`#F8F4E9` / `#FEFCF5` / `#F4EFE3` / `#33302A`），避免"冷白 + 鲜绿"的偏色 |

同源的绿色也一起调了：`FlowSkin.GREEN` 的环与三段填充、`HeatColors` 五阶、`PaletteColors` 首个色、`ActionColors.Subtask`、`ActionBgStart/End`；`Priority.NONE` 跟随新的 `InkFaint`。种子数据里旧青绿 `#3D9B8F`（运动）换成 `#5F9184`，生活改用 `#5E9E74`。

## 第八轮：降饱和鼠尾草绿 + 提对比 + 夜间模式（3 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 降低薄荷绿饱和度，换成更柔和的鼠尾草绿 | `QDPalette.Sage` 的 `accent` 从 `#4A8A62`（HSL 142/30%/42%）降到 **`#55805F`（HSL 134/20%/42%）**：色相往黄绿一侧挪 8°，饱和度砍掉三分之一，看起来更像"纸上的颜料"而不是屏幕上的荧光绿。同源的绿一起降：`AccentSoft` `#E7EDE3`、`ActionBgStart/End` `#5C8B6B→#48745A`、`FlowSkin.GREEN` 的环与三段填充、`HeatColors` 五阶、`PaletteColors` 首色 |
| 2 | 增强主色与文字对比度，同时保持清爽轻盈 | 主色压深一档：白字压在主色上的对比度 **4.12 → 4.53:1**（刚过 AA 正文线）；主题色文字 `AccentInk` `#356B4B → #2F6044`，对纸底 **5.69 → 6.69:1**。中性色同步"提亮纸底 + 加深文字"：`Screen` `#F8F4E9 → #F7F5EF`、`Card` `#FEFCF5 → #FFFEFA`、`Ink` `#33302A → #2E2C26`（对卡片 13.8:1）、`InkFaint` `#A49D8B → #7E7868`（原 2.9:1 的"分类 · 时间"小字升到 **4.36:1**）。整套对比度用 WCAG 公式逐对核算过，纸底仍然偏暖、绿仍然低饱和，所以不显笨重 |
| 3 | 做夜间模式：深青灰背景 + 薄荷绿强调色 | 新增 `QDPalette.Midnight`（`isDark = true`）：底 `#111D1B` / 卡片 `#1A2925` / 次级面 `#162421` 都带 175° 的青调，不是纯黑；强调色反过来用薄荷绿 `#79C9A0`（对底 8.78:1），压在上面的文字改成深墨 `#0B1F17`（对绿 8.73:1）。`Theme.kt` 的 `lightScheme()` 之外补了 `darkScheme()`，`QuandianTheme(dark = …)` 里按 `ThemeMode`（跟随系统 / 浅色 / 深色，存 DataStore）装调色板，并用 `WindowCompat` 把状态栏、导航栏图标一起反色 |

夜间模式不是一个滤镜，几处专门为深色补的语义令牌与分支：

- `OnTooltip` / `OnAction` / `Knob`：气泡文字、左滑动作图标、开关滑块——它们在浅色下都是白，但深色下气泡与动作区仍是深底，滑块要留在绿轨道上，所以单独给值（`#EAF4EE` / `#F0F7F3` / `#F2F8F4`），而不是复用 `OnAccent`（深色下它是深墨，压上去会看不见）。
- `FlowSkin`：枚举里拆成 `light*` / `dark*` 两组填充，`ring` / `fillStart` / `fillMid` / `fillEnd` 改成按 `QD.IsDark` 取值的计算属性——深色下不再是三块发光的浅色，而是同色相的低明度上色；`FillLayer` 的白色高光斜纹 alpha 从 0.42 降到 0.14。
- `HeatColors`：改成按深浅切换的 getter，深色五阶 `#17241F → #79C9A0`；环形图轨道改用 `PaperDeep`（深色下是 `#2A3B35`，比卡片亮一点才看得见）。
- 根节包一层 `CompositionLocalProvider(LocalContentColor provides QD.Ink)`：`Text` 没显式写色的地方（比如「今日」大标题）在深色下不会变成黑字。

## 第九轮：主色再提亮 + 分类色回到胶囊 + 切换动效（v1.2.0，3 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 主题绿再浅一些（参考稿 `#34C7A8` 薄荷绿） | `QDPalette.Mint` 的 `accent` `#368467 → #34C7A8`（配参考稿的按压色 `#2BB89A` 做流量皮肤）。绿变浅之后白字压不住（2.13:1），所以绿的用法拆成三档：`Accent` 只做**面**（胶囊、圆钮、进度填充）、`AccentInk` `#0F8069` 做**字与细线**（白底 4.87:1，统计页折线、返回、选中描边都换过去）、`OnAccent` `#0C3A31` 是压在绿面上的深墨（5.93:1）。中性色同步换成参考稿的蓝灰系：`Ink` `#263348`、`InkSoft` `#566473`、`InkFaint` `#5F6E7E`（对底 4.78:1），底 `#EEF7F2`、卡片纯白 `#FFFFFF` |
| 2 | 分类胶囊恢复"选中＝分类色、未选＝分类色浅底" | 删掉上一版"整排统一软绿、分类色只留虚线"的妥协，`PillTab.accent` 重新决定一切：未选中 `accent@16%` + 虚线描边、选中 `accent` 实底。字色按**白字对比度**自动判（`readableOn()`）：白字能到 3:1 就用白（`#DE5B55` 紧急、`#9186C9` 阅读、`#4E9C78` 生活），到不了就用深墨（`#34C7A8` 薄荷、`#5B9BD5` 天蓝、`#C9A24D` 米金）。未选中文字用 `labelOn()` 把分类色往墨色里压 55%，保留色相又能读 |
| 3 | 首页分类切换要顺滑自然 | ① `ChipPillTabs` 从 `tween(320, EaseOutExpo)` 换成**弹簧**（位置 `stiffness 640 / damping 0.86`，宽度 `480 / 0.97`），宽度慢半拍到位，连点也能从当前速度接上去；② 加 `stretch`：切换时胶囊先按距离拉长最多 12%，再用低阻尼弹回，看起来是被"推"过去；③ 未选中底色改用**透明度**而不是混色，胶囊从胶囊背后经过时能透出来，不再"钻到纸背后消失"；④ 选中项的文字/数字延后 60ms 再翻色，不再"字先在浅底上变白"；⑤ 选中的胶囊若被滚出视口，横向自动滚回来；⑥ `LazyColumn` 列表项加 `animateItem`，切分类时卡片淡入淡出、归位而不是"啪"地换一批 |

## 第十轮：气泡对齐 + 返回手势 + 实底白字（v1.3.0，3 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 统计页「近一周」热力图气泡和选中的格子对不上，气泡应显示在选中格上方 | 病因在 `HeatWeek`：7 列是从「6 天前 → 今天」从左往右排的，锚点却用 `i = 距今天数` 算 x，等于把锚点镜像了（点今天，气泡挂在 6 天前上方）。改成 `col = 6 - ago`。顺手把 `TipBubble` 从「整体跟着锚点、贴边就整体 clamp」改成 `Layout` 双节点摆放：**尾尖永远钉在格子中心**，贴边时只让气泡本体往内收（左右各留 8dp），点最左/最右那一格也不会指偏；月/年视图与折线图的气泡共用这套逻辑 |
| 2 | 进入统计页后侧滑返回不应退出应用，应回到跳转前的页面 | `MainScreen` 加 `BackHandler(enabled = page != Page.List && sheet == null) { vm.back() }`：统计 / 设置页的侧滑或返回键只退回首页；弹层开着时交给 `SheetHost` 自己的返回处理（注册在后、优先级更高）先关弹层；只有在首页才让系统退出应用 |
| 3 | 主题色按钮文字用白色；凡是深色底，文字都用白色 | 主色一族从三档扩到四档：新增 `accentFill`（实心控件的底）`#2DAB90`，`onAccent` 由深墨 `#0C3A31` 改成**白字**。分类胶囊同理，新增 `fillFor()`：按比例压暗（RGB 同乘 0.95，色相与饱和度不变）直到白字能到 2.6:1，`readableOn()` 阈值同步降到 2.6 —— 选中项一律白字，浅彩（生活绿 `#3FBB9C`、采购金 `#C9A24D`）只降一两档明度，不会变成橄榄。FAB、弹框提交按钮、设置页主按钮、提示条、时长胶囊全部换 `accentFill`。深色模式反向：那里的 accent 本身就是亮薄荷，压在它上面的字仍用深墨（`OnAccent`） |

## 第十一轮：点卡不勾选 + 环随手退 + 正式包零内置数据（v1.4.0，3 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 点任务卡片不要直接标记完成，快速完成只走左侧圆圈 | `TaskRow.kt` 里主卡 `TopTaskRow` 与子任务 `SubtaskRow` 的卡片本体 `tapFeedback` 去掉 `onToggle`，只保留「已左滑展开时点一下收回去」；勾选入口只剩左侧圆圈（子任务有未完成步骤时仍是抖动 + 提示）。顺带也去掉了"点卡片会抖一下"的旧逻辑——点卡片已经不代表"要完成"了 |
| 2 | 右滑时环形进度跟手变化，滑到一半松手要随卡片回退 | 环本来就由 `SwipeState.p` 驱动（`p = 位移 / (卡宽 × 0.55)`），问题在松手回退：原来先 `animate(fill)` 再 `animate(p)` 串行跑，环总比卡片慢半拍。改成两条 `launch` 并行，填充层和进度环同速缩回。另外把手势循环里的 `dx` 累加提到"抬指判断"之前——快速滑动时系统常把最后一段 MOVE 和 UP 合批成一个事件，旧写法先 `break` 会把这大半程位移整段丢掉，快滑就勾不上（实测 `input swipe … 400ms` 完全无反应）。现在抬手事件也会走一次结算，快滑与慢滑都能完成 |
| 3 | 正式包不要内置模拟数据，调试包可以保留 | `TaskRepository` 拆成两个入口：`ensureDefaultCategory()`（只建默认分类「生活」，正式包用）与 `ensureSeeded()`（分类 + 六条示例待办，仅调试包用）。`MainViewModel.init` 按 `BuildConfig.DEBUG` 分流：调试包再额外写 `seedDemoHistory()` 造一年完成记录；正式包首次启动是纯空白列表（0/0，只有「全部 0」和「生活」两个胶囊），所有内容由用户自己建（v1.7.0 起 `ensureDefaultCategory()` 也已移除，正式包连默认分类都不建，首页只剩「全部」——见第十四轮） |


## 第十二轮：卡片排版统一 + 展开收起（v1.5.0，5 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 优先级（紧急程度）要贴在卡片右侧、垂直居中 | 原来 `PriorityTag` 塞在标题那一行里，卡片有「分类 · 时间」第二行时就显得偏上。现在整张卡是一个 `Row(verticalAlignment = CenterVertically)`，标签和展开按钮一起挪到文字列外面（`CardTrailing`），行高仍由 `IntrinsicSize.Min` 决定，所以标签的圆心永远落在卡片垂直中线上 |
| 2 | 子任务卡片太小，要和父任务一样大，只靠缩进区分 | `SubtaskRow` 丢掉原来那套「13dp 圆角 + 12.5sp 字 + 11×8 内边距 + 24dp 圆圈」的小号版式，改用与父卡完全相同的 `CardShape`（19dp）、13/10 内边距、15sp/21sp 标题、34/26 圆圈和 `MetaRow`（分类跟父卡一致，所以只显示时间，不重复分类名）；左滑动作按钮从 28dp 提到 40dp。层级现在只靠 20dp 缩进 + 左侧那条引导线 |
| 3 | 点父任务展开/收起子任务，并显示展开按钮 | 新增 `ExpandState`（`mutableStateMapOf` 只记「被收起的 id」，没记过的默认展开，新加的子任务不会莫名藏着），放在 `MainViewModel` 里，滚出视口或转屏都不会丢。卡片本体的点击在「动作区未展开」时就是切换展开；右侧加一个 21dp 圆底箭头（新增 `QIconKind.Chevron`），用 `animateFloatAsState` 转 −90°：展开朝下、收起朝右。子任务组用 `AnimatedVisibility(expandVertically / shrinkVertically + fade)` 展开收起，不是「啪」地跳一下 |
| 4 | 删除线看着是两条（一深一浅），应该只有一条 | `StrikeText` 原来故意画两条错位细线（0.85 alpha 上移 0.6dp + 0.42 alpha 下移 0.9dp）做手绘感，缩小看就是双线。现在只画一条 1.3dp、alpha 0.8、压在行中线上，多行标题每行一条 |
| 5 | 新建弹框把「收进清单」改成「添加」，标题和描述尽量精简 | 面板标题「记一件要做的事」→「添加待办」；`SubmitButton` 默认文案「收进清单」→「添加」；删掉「写一句就好，它会出现在清单最上面。」「先归个类，之后左滑也能改：」两行说明——输入框占位（比如：给妈妈回个电话）和分类胶囊本身已经说明一切。保存成功的提示条同步从「已收进「X」清单」改成「已添加到「X」」 |

## 第十三轮：同步开关门控 + 地址校验 + 卡片右侧重排（v1.6.0，3 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | WebDAV 只有开启时才显示输入框和按钮；没填完按钮不可点；地址要校验格式，不合法就标红 + 提示 | 整块「服务器地址 / 账号 / 密码 / 两个按钮 / 上次同步」包在 `AnimatedVisibility(settings.davEnabled)` 里，关掉开关用 `shrinkVertically + fadeOut` 收起，只剩一行灰字说明「开启后填写服务器地址与账号……」。按钮可用性由 `canSubmit = 三栏都非空` 决定，不可点时退回灰底灰字（不是靠透明度糊）；地址格式交给新增的 `sync/DavUrl`：必须 `http://` / `https://` 开头、域名至少一个点且每段 1~63 位合法字符、端口 1~65535、`localhost` 与 IPv4 放行（并校验每段 ≤ 255）、地址里自带账号密码会提示分栏填。**错误只在失焦或点按钮之后才出现**（边打字边飘红太吵），出现时是红边框 + 红底色 + 框下一行红字，同时底部弹一句提示；改对后立刻恢复。点按钮前 `checkUrl()` 再拦一次，`MainViewModel.testDav / syncDav` 也各兜一道，坏地址不会进网络层 |
| 2 | 去掉子任务前面的竖线 | `SubtaskGroup` 删掉那层 `Canvas` 引导线，层级只靠缩进：一级 20dp、二级再缩 14dp。子任务卡本身尺寸、内边距、圆圈都与父卡一致，所以缩进一眼就能看出从属关系 |
| 3 | 父任务的子任务数量显示到右下角，展开按钮挪到最右，和优先级对调位置 | 数量从标题行里搬出来，放进「分类 · 时间」那一行（`Row { MetaRow; weight(1f); ChildCount }`），既贴右下角又不占标题宽度；`CardTrailing` 顺序改成「优先级标签 → 展开箭头」，箭头成为卡片最右缘的元素。优先级标签同时改用 `compact` 版式（内边距 6/2dp、9sp），配合更省横向空间；标题列仍是 `weight(1f)`，长标题最多两行后省略 |

## 第十四轮：去掉默认分类 + 优先级换色 + 数量归位 + 动作按钮等距（4 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 去掉默认的「生活」分类，正式包只留「全部」 | `ensureDefaultCategory()` 整个删掉，正式包首次启动**一条预置数据都不写**：没有分类、没有示例待办，首页只有内置的「全部」胶囊。没归类的待办 `category` 存空串（`NO_CATEGORY`），`MetaRow` 遇到空串只显示时间、不显示「·」分隔点。删除分类不再把待办塞回「生活」，而是清空归属变成未分类；正停在被删分类的筛选上会自动退回「全部」。`NewTaskBody` 在没有分类时不再画一排空胶囊，改成一行提示「还没有分类。想归类的话，先到『管理分类』里建一个」；`MoveCategoryBody` 同理。顺手补了空清单的引导文案：总数 0 时首页副标题是「还没有待办，点右下角加一件」，不再对着空列表说「今天都放下了」 |
| 2 | 高和普通要用不同颜色：普通蓝、高橘、紧急红 | `Priority` 的色相按冷→暖重排：低 `#4FBF9E` 青绿 / 普通 `#4A86D6` 蓝 / 高 `#E08A34` 橘 / 紧急 `#DE5B55` 红 / 不设 `#5F6E7E` 灰。相邻两档既差色相也差明度，卡片上的 tag、优先级面板的胶囊、统计图例都跟着换，不用逐处改 |
| 3 | 子任务数量显示到展开按钮的下方 | `ChildCount` 并进 `CardTrailing`：右端变成一个竖向小组件 `Column { ExpandChevron; 2dp; "2/3" }`，数字在箭头正下方居中，与箭头一起垂直居中于卡片；标题行右下角不再放数字，长标题因此多出约 24dp 的可用宽度 |
| 4 | 管理分类左滑的两个按钮太挤，且父任务/子任务右滑露出按钮的间距不一致 | 新增 `ActionMetrics`（`Slot 38dp` / `Gap 5dp` / `Edge 6dp` / 图标圆 26dp、图标 13dp）作为唯一尺寸表：`RowScopeAction` 不再各自传宽度，父卡四动作、子卡三动作、分类管理卡两动作都用同一套槽宽，行内用 `Arrangement.spacedBy(Gap, End)` 拉出等距，卡片让位距离改由 `ActionMetrics.widthOf(count)` 算出——滑开时按钮刚好露完、不多不少。分类卡原来两个 28dp 圆紧贴在一起（间距 0），现在和待办卡一样是 5dp 槽间隙 + 12dp 圆内留白 |

## 发版与同步排障（第十八轮）

- **一键发版 `./release.sh`**：读取最近一个 tag 自动 +0.0.1 打新 tag 并推送，触发 CI 构建发布。
  - `./release.sh` / `patch`：`v1.0.0 → v1.0.1`；`minor`：`v1.0.1 → v1.1.0`；`major`：`v1.1.0 → v2.0.0`；也可 `./release.sh 1.2.3` 直接指定。
  - `--dry-run` 只预览不操作；工作区有未提交改动会中止（`--force` 可跳过）；会校验本地/远端是否已存在同名 tag。
- **WebDAV 测试连接 405**：见下一节（第十九轮已彻底重写探测方式，第十八轮的「补斜杠重试」方案已被替换）。

## 第十九轮：WebDAV 405 根治（v1.0.2）

**根因**：`dav.<xxx>.fnos.net` 是飞牛 fn Connect 的 WebDAV 专用域名，**域名根 `/` 本身就是 WebDAV 根**；很多人照着「WebDAV」四个字又补了一层 `/webdav`，这个目录在 NAS 里并不存在，PROPFIND 到不存在的集合就回 405。另外 Android 的 `HttpURLConnection` 对 `PROPFIND` 这类非标准方法支持不稳，中继/反代也常直接拒绝，而同步本身**只需要 PUT**。

- **探测改为真实上传**：`WebDavClient.test()` 不再发 PROPFIND，而是 PUT 一个 `doit-connection-test.json` 探针，测的就是实际同步能力；成功后 DELETE 清理。
- **自动找可用目录**：依次尝试「原地址（补 `/`）→ 去掉 `webdav`/`dav` 这类误填段后的父目录 → `<域名>/doit/ → 域名根」，哪个能写就用哪个，并把可用地址回填进设置（`DavResult.workingUrl` → `settingsRepo.setDavConfig`），避免「测试通过、保存却还是错的旧地址」。
- **`upload()` 同样走候选重试**，直接点「保存并同步」也会自动纠错。
- **错误提示按场景翻译**：401 账号密码、403 无写入权限、404 目录不存在、405 给出飞牛专用地址写法；网络层异常再按明文被禁 / 证书不受信 / 域名解析失败 / 连接超时分别提示。
- **允许明文 HTTP**：`AndroidManifest.xml` 加 `usesCleartextTraffic="true"`，局域网 `http://NAS_IP:5005` 的 WebDAV 不再被系统静默拦掉（这是之前一个隐藏 bug）。
- **设置页地址提示**：placeholder 改为 `https://dav.example.com/doit/`，输入框下常驻一行说明「填 NAS 里已建好的文件夹，结尾带 /，飞牛 fn Connect 不要再写 /webdav」。

**飞牛用户正确配置**：① 在 NAS 文件管理里手动建一个文件夹（如 `doit`）；② 地址填 `https://dav.<你的ID>.fnos.net:443/doit/`（结尾带 `/`，**不要** `/webdav`）；③ 账号密码用有该文件夹读写权限的 NAS 账号；④ 点「测试连接」，新版会自动确认可用路径并回填。

**验证**：本机起了一个模拟飞牛行为的 WebDAV 服务（`/webdav/*` 回 405、根路径可写），在模拟器上实测「测试连接」→ 第 1 次 PUT 405、第 2 次 PUT 根路径 201、DELETE 清理，地址栏自动变为根路径；「保存并同步」成功上传 1663 字节的真实快照 JSON。截图见 `screenshots/r18_webdav_autofix.png`、`r18_webdav_synced.png`。

## 第二十轮：WebDAV 双向同步（v1.0.3）

**修复的问题**：旧实现只有 `exportSnapshot() → PUT`，是**本地全量覆盖远程**。新手机装好 APP、本地还是空库时点一次「保存并同步」，就会把远程已有清单抹掉。

### 数据模型先补齐（Room v1 → v2）

| 字段 | 作用 |
| --- | --- |
| `uid` | 跨设备稳定的 UUID。本地自增 `id` 在两台设备上都从 1 开始，不能用来认记录；父子关系在快照里也改用 `parentUid` |
| `updatedAt` | 每次编辑（新增 / 完成 / 取消完成 / 改优先级 / 改分类 / 删除）都刷新；`0` 表示时间戳缺失（老数据） |
| `deletedAt` | 删除墓碑。删除不再物理删行，否则「删掉了」这件事没法传给另一台设备；所有业务查询都过滤墓碑 |

迁移用 `MIGRATION_1_2`：老行现场生成 `uid = lower(hex(randomblob(16)))` 并建唯一索引，`updatedAt` 退化成 `createdAt`。启动时 `ensureSyncMeta()` 幂等兜底。设置里新增机器可读的 `lastSyncAt` / `syncBase` / `syncRevision` / `deviceId`，与给人看的 `lastSync` 文案分开存。

### 同步流程（`SyncEngine`）

找可用目录 → GET 远程 → 读本地（含墓碑）→ 逐记录合并 → 落地本地 → 必要时 PUT 回远程 → 保存状态。整轮成功才写 `lastSyncAt / base / revision`，中途失败不留半截状态；`Mutex` 串行化，连点两次不会并发写。远程文件读不懂时**直接中止**，绝不当成空文件覆盖。

### 合并规则（`SyncMerge`，纯 Kotlin、27 个 JVM 单测；删除/冲突细则见第二十一轮）

策略判定：

| 情况 | 处理 |
| --- | --- |
| 远程没有备份 | 发布本地 |
| **缺 `lastSyncAt` + 远程已有数据 + 本地无历史数据** | **以远程为准**，不回写覆盖 |
| 缺 `lastSyncAt` 但本地已有自己建的数据 | 并集合并，两边都不丢 |
| 其余 | 常规双向三方合并 |

逐记录规则（合并是**并集**，不是替换）：

1. 只在单边出现的记录一律保留 —— 这是「不丢远程记录」的根本保证。
2. 有 `base` 时做**字段级三方合并**：某字段只有一边相对 base 变了就取该边；两边改的是**不同字段**则两者都保留（例如本地改标题、远程改优先级，合并后两个改动都在）。
3. 同一字段两边都改且值不同 → 取 `effectiveTime`（删除 > 编辑 > 完成 > 创建）较新的一方；**时间相同或都缺失 → 远程优先**，裁决确定性、可重复。
4. `base` 不会凭空丢失：`SyncEngine` 在上次同步的 base 损坏/缺失、且远程版本没有比本机更新时，会把远程规范化成 base 补上；确实无法补齐才退化为用 `lastSyncAt` 判断。
5. 时间戳缺失用 `completedAt` / `createdAt` 兜底，仍是 0 就按「无时间戳」走第 3 条的确定性规则。
6. 删除 vs 编辑：见下一轮修订 —— 删除发生在上次同步之后且对方没有同步后的编辑时即生效，旧版无时间戳记录也不再被无条件复活。

### 幂等与版本状态

`fingerprint()` 只编码记录内容（按 uid / name 排序、长度前缀），不看 `revision / exportedAt / deviceId / lastSyncAt`。内容没变就不发 PUT，`revision` 也不虚增；`base` 存规范化后的合并结果。v1 旧备份可读（`id → v1-<id>`、时间戳记缺失），首次同步会把它无损升级成 v2。

**实测**（模拟器 + 本地模拟 WebDAV，远程预置一份旧版 v1 备份含 A/B 两条）：新装正式包空库首次同步 → 请求序列 `探针 PUT → DELETE → GET → PUT(升级为 v2)`，远程 A/B **一条没少**并拉回本地；第二次同步只有 `探针 PUT → DELETE → GET`，**没有写远程**，`revision` 保持 1；本地新增一条后再同步 → 远程变成 3 条（本地新增 + A + B），`revision=2`。另测 v1 数据库升级到 v2，老数据完整、无崩溃。截图 `screenshots/r19_first_sync_pulled.png`、`r19_db_migration.png`。

## 第二十一轮：同步后本地改动不再被远程覆盖（v1.0.4）

**用户反馈**：把远程数据同步下来、在本机改了一些，再点「保存并同步」，远程旧数据又被拉回本机，本机改动丢了。

复现后定位到三个真实缺陷（都在模拟器上用 mock WebDAV 复现过）：

| # | 缺陷 | 后果 |
| --- | --- | --- |
| 1 | 从旧版 v1 备份拉回的记录 `updatedAt = 0`，旧删除规则要求「对方有有效时间戳」才允许删除 | 本机删掉的任务 / 分类被远程**复活**，合并结果整条写回本地，看起来就像远程又把数据同步了回来 |
| 2 | 逐字段合并时，若 `base` 丢失或远程返回的是**上次同步之前的旧副本**，旧值会被判成「远程新改动」 | 本机改动被远程旧值回滚 |
| 3 | 状态里的 `syncBase` 一旦损坏，只剩 `lastSyncAt` 可用，时间戳相同或缺失时会走到「远程优先」 | 本机改动被判负 |

### 修复

- **本地脏数据优先（硬保证）**：`SyncMerge` 新增按记录内容（忽略 `createdAt/updatedAt`）的指纹。只要本机相对 `base` 改过、而远程与 `base` 完全一致，就**整条以本机为准**，不再逐字段走时间裁决 —— 哪怕这条记录是时间戳为 0 的老数据，也不会被远程覆盖。分类同样处理。
- **删除规则改用「上次成功同步」做基线**：删除时间晚于 `lastSyncAt`、且活记录相对 `base` 没有同步后的编辑 → 删除生效；双方都动过则仍要求删除时间严格更晚。从未同步过（`lastSyncAt=0`）时保持保守，保留活记录。删除被拒且活记录是本机时，本机编辑原样保留。
- **远程旧副本防护**：`isStaleRemote()` 依据 `schemaVersion / revision / exportedAt`（10 分钟时钟容差）判断 GET 到的是不是比上次成功同步更旧的文件。命中就用 `base` 顶替远程参与合并并**强制重新 PUT**，提示「远程是旧副本 · 已保留本机改动并重新发布」。远程版本比本机新时绝不误判。
- **base 自动兜底**：`SyncEngine` 在 `syncBase` 损坏、且远程版本没有领先本机时，把远程规范化成 base，保证「远程没动过 → 本机改动必胜」这条规则永远有依据。

### 回归结果（模拟器 + mock WebDAV，全新安装）

| 场景 | 结果 |
| --- | --- |
| 首次拉取远程 v1 数据 → 本机改标题 / 勾选完成 / 改优先级 / 新增任务 / 删任务 / 删分类 → 再同步 | 本机 5 类改动**全部保留**，远程同步成同一份；墓碑正确传到远程 |
| 无改动再同步 | 只有 `探针 PUT → DELETE → GET`，**没有备份 PUT**，`revision` 不变（幂等） |
| 把远程换回旧的 v1 文件 + 本机再改一次 → 同步 | 提示「远程是旧副本」，本机改动保留，远程被重新发布为 v2、`revision` 递增 |
| 手动删掉 DataStore 里的 `syncBase` + 本机改动 → 同步 | base 自动补齐，本机改动保留、墓碑不丢 |

单测从 15 个增加到 **27 个**，覆盖本地改动优先、无时间戳记录删除、旧副本识别 / 强制发布 / 版本更新不误判、base 兜底等分支。

## 技术栈

- Kotlin 2.0.21 · Jetpack Compose（BOM 2024.12.01）· Material 3
- Room 2.6.1（KSP）本地持久化，离线优先
- DataStore Preferences 保存设置（皮肤、WebDAV 配置、上次同步）
- 手势：`pointerInput` + `awaitEachGesture` 自绘双向滑动，未使用第三方库
- 语音输入：系统 `RecognizerIntent`（无需自建识别服务）
- WebDAV：`HttpURLConnection` 实现 PUT 探针探活 + GET 下载 + PUT 备份 + DELETE 清理，无额外网络库（不用 PROPFIND）；双向合并为纯 Kotlin 实现，快照编解码用系统 `org.json`，兼容 v1 旧备份

## 目录结构

```
Quandian/
├── app/src/main/java/com/quandian/app/
│   ├── MainActivity.kt             # 边到边、语音输入 launcher
│   ├── data/                       # Room 实体 / DAO / 仓库 / 种子数据
│   ├── sync/
│   │   ├── WebDavClient.kt         # 探活 / 下载 / 上传（含 405 自动纠错）
│   │   ├── SyncModels.kt           # 快照模型（纯 Kotlin）
│   │   ├── SyncMerge.kt            # 双向三方合并 + 内容指纹
│   │   ├── SnapshotCodec.kt        # v2 编解码，兼容 v1 旧备份
│   │   ├── SyncEngine.kt           # 同步编排与状态落盘
│   │   └── DavUrl.kt               # 地址格式校验
│   └── ui/
│       ├── MainScreen.kt           # 今日清单 + 页面切换 + 提示条
│       ├── MainViewModel.kt        # UiState / 面板 / 动作
│       ├── StatsPage.kt            # 热力图 / 环形图 / 折线图
│       ├── SettingsPage.kt         # 外观 / 同步 / 关于
│       ├── QIcon.kt                # 24×24 网格手绘线性图标
│       ├── gesture/SwipeState.kt   # 双向滑动与上膛判定
│       ├── components/             # 卡片 / 勾选 / 面板 / FAB 卫星
│       └── theme/                  # 设计令牌（配色、字体、皮肤）
├── .github/workflows/android.yml   # CI：测试 → Lint → APK
└── design/                         # 视觉稿与效果图（原始素材）
```

## 构建与安装

```bash
# 环境：JDK 17 + Android SDK（compileSdk 35）
./gradlew assembleDebug          # 真机调试包
./gradlew assembleRelease        # 正式包（自动找 keystore/ 里的证书，找不到就用 debug 证书签名）
./gradlew testDebugUnitTest      # 单元测试（任务树 / 统计序列 / 地址校验 / 双向合并 15 例）
./gradlew lintDebug              # Lint 静态检查
```

**版本号不用手改**：`app/build.gradle.kts` 从 git tag 推导——打 `v1.2.3` 就是
`versionName=1.2.3`、`versionCode=10203`（`major*10000 + minor*100 + patch`，随版本单调递增）；
当前工作区没有 tag 时回退到 `1.0.0 / 10000`，保证任何情况都能出包。
发版只要 `git tag v1.2.3 && git push origin v1.2.3`。

安装到手机：

```bash
adb install -r app/build/outputs/apk/debug/doit-1.0.3-debug.apk
```

- 调试包包名 `com.quandian.app.debug`，可与正式包共存，桌面显示「doit · 调试」
- `minSdk 26`（Android 8.0）/ `targetSdk 35`

## 交付物

```
dist/
├── doit-1.0.3-debug.apk     # 调试包，包名 com.quandian.app.debug，桌面显示「doit · 调试」，可与正式包共存（含示例分类/待办 + 一年演示记录）
├── doit-1.0.3-release.apk   # 正式包，包名 com.quandian.app（keystore/quandian-release.jks 签名），零内置数据
└── SHA256SUMS.txt               # 校验和

screenshots/                     # 验收截图：01–15 模拟器（1080×2400 / Android 15），real_* 真机
                                 # opt_01–opt_11 第二轮 8 项优化逐项证据（含图表气泡），real_opt_* 真机复核
                                 # fix_01–fix_07 第三轮 5 项修复逐项证据
                                 # real_fix_01–real_fix_10 第三轮真机（华为 NAM-AL00）复核
                                 # r5_* 圆圈居中 / 主题色收敛，r6_* 弹框胶囊，r7_* 暖调配色
                                 # r8_* 鼠尾草绿 / 对比度 / 夜间模式（浅色与深色各页 + 深色气泡）
                                 # r9_* 清新绿，r10_* 主色提亮 + 分类胶囊 + 切换动效，r11_* 气泡对齐 / 返回手势 / 实底白字
                                 # r12_* 点卡不勾选 / 右滑环随手退 / 正式包空列表
                                 # r13_* 卡片排版统一（标签居中 / 子任务同尺寸 / 展开按钮 / 单条删除线 / 精简新建面板）
                                 # r14_* 同步开关门控 / 地址校验标红 / 去子任务竖线 / 数量右下角 + 箭头最右
                                 # r15_* 去默认分类（只剩「全部」）/ 优先级蓝橘红 / 数量挪到箭头下方 / 动作按钮等距
                                 # r16_* 删除确认弹窗等宽大按钮，r17_* 删除线浅灰 + 完成百分比
                                 # r18_* WebDAV 405 自动纠错：地址回填 / 同步成功
                                 # r19_* 首次同步不覆盖远程（远程数据拉回）/ v1→v2 数据库迁移
```

真机验收环境：华为 NAM-AL00（1080×2340 / density 480，Android 12 底座的 HarmonyOS）。逐项验过：主界面渲染、右滑完成、左滑四动作、优先级/分类/子任务面板、新建任务置顶、分类管理、删除确认、统计页周/月/年三种视图；第二轮 8 项优化装包复核过中文日期、无衬线字形、右侧优先级 tag、热力图点击气泡；第三轮 5 项修复在真机上逐条复核过进度环层级、左滑四角干净度、三种图表气泡尾尖、滚动时头部固定、周/年视图布局。

> 华为/鸿蒙真机调试两个坑：① 需在开发者选项里开「通过 USB 安装应用」（会要求登录华为帐号），否则 `INSTALL_FAILED_ABORTED: User rejected permissions`；② 系统屏蔽第三方应用的 logcat，`adb logcat` 看不到自己应用的日志，调试时改用屏上探针或崩溃日志文件。

调试包首次启动会写入示例分类、六条示例待办和一年的示例完成记录，方便真机核对卡片形态与统计页；正式包**不写任何预置内容**——连默认分类都不建，首页只有「全部」一个内置胶囊，列表与统计页从空白开始，分类和待办全由用户自己创建。

## 签名

`app/build.gradle.kts` 依次尝试：环境变量 `QUANDIAN_KEYSTORE` → `keystore.properties` → `keystore/quandian-release.jks`，都没有则回退 debug 签名，保证任何机器都能出可安装的包。

本地生成正式证书：

```bash
keytool -genkeypair -v -keystore keystore/quandian-release.jks \
  -alias quandian -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass quandian -keypass quandian -dname "CN=Quandian, O=Quandian, C=CN"
```

CI 打正式包时通过仓库 Secrets 提供：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。

## CI

`.github/workflows/android.yml`

- **只在打 `v*` tag 时触发**：普通 `push main` / PR 不会跑，省 Actions 额度
- tag 构建：单测 → Lint → `assembleDebug`（artifact `doit-debug-apk`）→ 正式包（artifact `doit-release-apk`，
  并以 `doit-<tag>-release.apk` 挂到 GitHub Releases）
- **日常发版用仓库根目录的 `./release.sh`**：自动自增版本 → 打 tag → 推送，其余不需要手动 `git tag`。
- 需要手动验证时用 `workflow_dispatch`，那种情况没有 tag，版本号回退 `1.0.0`

CI 不经过 wrapper：`gradle/actions/setup-gradle` 直接装官方 Gradle 8.11.1 并调用 `gradle`，
因为 `gradle-wrapper.properties` 的 `distributionUrl` 指向腾讯镜像，境外 runner 拉 130MB 常常慢到超时。

依赖仓库也按环境隔离（`settings.gradle.kts`）：本机走阿里云镜像提速，CI（`CI=true`）只用
`google()` / `mavenCentral()` / `gradlePluginPortal()`。原因是 GitHub runner 从境外访问
`maven.aliyun.com` 时，镜像对不存在的 artifact（比如 KSP 的 pom-only plugin marker 的 `.jar`）
会返回 **502 Bad Gateway** 而不是 404，Gradle 会把它当成仓库故障并禁用该仓库，配置阶段直接报
`Could not resolve all artifacts`，构建还没开始就红了。

Gradle 产物统一命名成 `doit-<versionName>-<buildType>.apk`（`app/build.gradle.kts` 的 `androidComponents.onVariants`），
本地 `assembleDebug` 与 CI 下到的文件名一致，不用再猜 `app-debug.apk` 是哪个版本。

## 说明

- **折线图按月**：近一年的折线按**时间顺序回溯 12 个月**（去年 12 月 → 当月），不是按 `monthValue` 分组，跨年时标签不会错位；见 `yearMonthlySeries()` 与 `StatsSeriesTest`。
- **折线图无面积填充**：视觉稿里只有一条线加数据点，不做渐变填充。
- **新建任务置顶**：新任务 `sortOrder = min - 1` 插在第一位，插入后主动回顶（`MainViewModel.scrollTop`），避免 `LazyColumn` 保持旧视口锚点让新任务藏在视口上方。
- **字体**：全局系统无衬线（`FontFamily.SansSerif`，华为/鸿蒙上落到 HarmonyOS Sans，模拟器为 Noto Sans），标题、数字、正文同一族，不再使用衬线。不打包字体文件，APK 更轻、渲染更稳。
- **顶部日期**：全中文「2026年11月13日 星期五」，星期用 `WEEKDAYS_CN` 映射，不出现英文缩写。
- **完成度三处统一**：删除线颜色改为与已完成文字同款的浅灰（`QD.Ink`，随删除态整卡 0.5 透明度一起变淡，不再单独用黄/橘）；父任务卡片的展开按钮正下方、以及「今日」右上角进度环，都改成显示完成百分比（如 `67%`、`0%`）而不是 `2/3` 这种数量，两者共用 `percentText()`，观感一致。`screenshots/r17_percent_ui.png`
- **弹窗里的主次动作等宽**：「确认删除」「删除分类」两个按钮由 `SheetActions` 统一渲染——左右各占一半（`weight(1f)` + 10dp 间隔）、48dp 高、13dp 圆角，左侧危险动作用 `DangerBg` + 红字，右侧「先留着」用 `Surface2` + 淡描边；不再是小胶囊并排，盲按也点得准。`screenshots/r16_confirm_delete.png`
- **示例数据只在调试包**：`BuildConfig.DEBUG` 为真时才 `ensureSeeded()` + `seedDemoHistory()`（六条示例待办 + 一年完成记录）；正式包一条预置数据都不写（连默认分类都没有），列表与统计页从零开始。见 `TaskRepository` 与 `MainViewModel.init`。
- **两套配色**：浅色 `QDPalette.Mint`（浅薄荷绿底 + 纯白卡片 + `#34C7A8`）与深色 `QDPalette.Midnight`（深夜青灰 + 薄荷），设置页「主题」三档可切；所有颜色只从 `QD` 令牌取，换肤不改界面代码。
- **白字规则**：凡是"实心彩色块"（选中胶囊、FAB、主按钮、提示条）一律白字，底走 `accentFill` 或 `fillFor()`——后者按比例压暗浅彩，直到白字对比度 ≥ 2.6:1，色相不变；反过来，深色模式里 accent 本身是亮薄荷，压在它上面的字才用深墨 `OnAccent`。
