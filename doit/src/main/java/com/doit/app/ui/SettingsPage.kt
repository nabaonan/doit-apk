package com.doit.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doit.app.BuildConfig
import com.doit.app.data.AppSettings
import com.doit.app.sync.DavUrl
import com.doit.app.ui.components.PillTab
import com.doit.app.ui.components.PillTabs
import com.doit.app.ui.components.PillTabsMode
import com.doit.app.ui.theme.FlowSkin
import com.doit.app.ui.theme.Doit
import com.doit.app.ui.theme.SansFamily
import com.doit.app.ui.theme.ThemeMode
import com.doit.app.ui.theme.numeric
import com.doit.app.ui.theme.titleStyle

/** 设置页顶部的分类胶囊：切换后下方内容区随之切换 */
private enum class SettingsTab(val key: String, val label: String) {
    APPEARANCE("appearance", "外观"),
    SYNC("sync", "同步"),
    ABOUT("about", "关于"),
}

@Composable
fun SettingsPage(
    settings: AppSettings,
    currentSkin: FlowSkin,
    onBack: () -> Unit,
    onSkin: (String) -> Unit,
    onTheme: (String) -> Unit,
    onDavEnabled: (Boolean) -> Unit,
    onTest: (String, String, String) -> Unit,
    onSaveSync: (String, String, String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    updateState: UpdateState,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember(settings.davUrl) { mutableStateOf(settings.davUrl) }
    var user by remember(settings.davUser) { mutableStateOf(settings.davUser) }
    var pass by remember(settings.davPass) { mutableStateOf(settings.davPass) }
    // 地址要等「失焦」或「点了按钮」之后才标红——边打字边飘红会像在骂人
    var urlTouched by remember { mutableStateOf(false) }
    val urlError = if (url.isBlank()) null else DavUrl.validate(url)
    val urlInvalid = urlTouched && urlError != null
    // 三栏都填了才让按钮亮起来
    val canSubmit = url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    // 顶部分类胶囊：当前选中的分类
    var tab by remember { mutableStateOf(SettingsTab.APPEARANCE) }

    fun checkUrl(): Boolean {
        urlTouched = true
        val err = DavUrl.validate(url)
        if (err != null) {
            onToast(err)
            return false
        }
        return true
    }

    Column(modifier.fillMaxSize().background(Doit.Surface)) {
        // ---------- 固定在顶部的标题 ----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 8.dp),
        ) {
            PageHead(title = "设置", latin = "SETTINGS", onBack = onBack)
        }

        // ---------- 固定顶部的分类胶囊（外观 / 同步 / 关于）----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 2.dp),
        ) {
            PillTabs(
                tabs = SettingsTab.entries.map { PillTab(key = it.key, label = it.label, accent = Doit.Accent) },
                selectedKey = tab.key,
                onSelect = { key -> SettingsTab.entries.firstOrNull { it.key == key }?.let { tab = it } },
                mode = PillTabsMode.Equal,
                height = 38.dp,
                gap = 7.dp,
                indicatorColor = Doit.Accent,
            )
        }

        // ---------- 按分类切换的内容（淡入 + 轻微上浮，切换自然）----------
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 22 })
                    .togetherWith(fadeOut(tween(130)) + slideOutVertically(tween(170)) { -it / 22 })
            },
            label = "settingsTabContent",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { t ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 30.dp),
            ) {
                when (t) {
                    SettingsTab.APPEARANCE -> {
                        // ---------- 主题 ----------
            SettingSection(title = "主题", caption = "清新绿 · 深夜青灰") {
                PillTabs(
                    tabs = ThemeMode.entries.map { mode ->
                        PillTab(key = mode.key, label = mode.label, accent = Doit.Accent)
                    },
                    selectedKey = settings.themeMode,
                    onSelect = onTheme,
                    mode = PillTabsMode.Equal,
                    height = 34.dp,
                    gap = 7.dp,
                    indicatorColor = Doit.Accent,
                )
            }

            // ---------- 外观 ----------
            SettingSection(title = "外观", caption = "右滑完成时的进度手感") {
                PillTabs(
                    tabs = FlowSkin.entries.map { skin ->
                        PillTab(
                            key = skin.key,
                            label = skin.label,
                            accent = Doit.Accent,
                            dot = Brush.linearGradient(listOf(skin.fillStart, skin.fillEnd)),
                        )
                    },
                    selectedKey = currentSkin.key,
                    onSelect = onSkin,
                    mode = PillTabsMode.Equal,
                    height = 34.dp,
                    gap = 7.dp,
                    indicatorColor = Doit.Accent,
                )
            }

                    }
                    SettingsTab.SYNC -> {
                        // ---------- 同步 ----------
                        SettingSection(title = "同步", caption = "WebDAV · 在多台设备之间保持一致") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("WebDAV 自动同步", fontSize = 13.sp, color = Doit.Ink, modifier = Modifier.weight(1f))
                    DoitSwitch(checked = settings.davEnabled) { on ->
                        urlTouched = false
                        onDavEnabled(on)
                    }
                }

                // 只有打开同步才需要填服务器：关着的时候连输入框都不出现
                AnimatedVisibility(
                    visible = settings.davEnabled,
                    enter = expandVertically(tween(240)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        SettingField(
                            value = url,
                            onChange = { if (it.length <= 300) url = it },
                            placeholder = "服务器地址 · https://dav.example.com/doit/",
                            invalid = urlInvalid,
                            onFocusChange = { focused ->
                                // 离开输入框才判红，边打字边飘红会像在骂人
                                if (!focused && url.isNotBlank()) {
                                    urlTouched = true
                                    urlError?.let(onToast)
                                }
                            },
                        )
                        urlError?.takeIf { urlInvalid }?.let { msg ->
                            Text(
                                msg,
                                fontSize = 10.5.sp,
                                color = Doit.Danger,
                                modifier = Modifier.padding(start = 13.dp, top = 5.dp),
                            )
                        }
                        Text(
                            "填 NAS 里已建好的文件夹，结尾带 /。飞牛 fn Connect 的域名根就是 WebDAV 根，不要再写 /webdav。",
                            fontSize = 10.sp,
                            color = Doit.InkFaint,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 13.dp, top = 6.dp, end = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingField(
                            value = user,
                            onChange = { if (it.length <= 120) user = it },
                            placeholder = "账号",
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingField(
                            value = pass,
                            onChange = { if (it.length <= 120) pass = it },
                            placeholder = "密码",
                            password = true,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(
                                "测试连接",
                                primary = false,
                                enabled = canSubmit,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (checkUrl()) onTest(url.trim(), user.trim(), pass)
                            }
                            PillButton(
                                "保存并同步",
                                primary = true,
                                enabled = canSubmit,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (checkUrl()) onSaveSync(url.trim(), user.trim(), pass)
                            }
                        }
                        Text(
                            settings.lastSync,
                            fontSize = 10.5.sp,
                            color = Doit.InkFaint,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                if (!settings.davEnabled) {
                    Text(
                        "开启后填写服务器地址与账号，清单就能在多台设备之间保持一致。",
                        fontSize = 10.5.sp,
                        color = Doit.InkFaint,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

                    }
                    SettingsTab.ABOUT -> {
                        // ---------- 关于 ----------
                        SettingSection(title = "关于", caption = "一款清新绿的待办", last = true) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("当前版本", fontSize = 13.sp, color = Doit.Ink, modifier = Modifier.weight(1f))
                    Text("v${BuildConfig.VERSION_NAME}", style = numeric(12).copy(fontSize = 12.sp), color = Doit.InkSoft)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "仓库已公开，检查更新可直接联网获取最新版本，无需任何 Token。",
                    fontSize = 10.sp,
                    color = Doit.InkFaint,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
                when (updateState) {
                    is UpdateState.Checking -> PillButton(
                        label = "检查中…", primary = false, enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {}

                    is UpdateState.Found -> {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "发现新版本 v${updateState.version}",
                                fontSize = 12.sp,
                                color = Doit.AccentInk,
                                fontFamily = SansFamily,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                            )
                            PillButton("下载并安装", primary = true, modifier = Modifier.fillMaxWidth()) {
                                onDownload()
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "正在下载 v${updateState.version}… ${(updateState.progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Doit.InkSoft,
                                fontFamily = SansFamily,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                            )
                            // 线性进度条
                            LinearProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                color = Doit.AccentFill,
                                trackColor = Doit.LineSoft,
                            )
                        }
                    }

                    is UpdateState.Ready -> Text(
                        "下载完成，正在打开安装器…",
                        fontSize = 12.sp,
                        color = Doit.AccentInk,
                        fontFamily = SansFamily,
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    is UpdateState.Error -> Text(
                        updateState.message,
                        fontSize = 12.sp,
                        color = Doit.Danger,
                        fontFamily = SansFamily,
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    UpdateState.Idle -> PillButton("检查更新", primary = false, modifier = Modifier.fillMaxWidth()) {
                        onCheckUpdate()
                    }
                }
                Text(
                    "doit · 把清单，写在一张纸上。",
                    fontSize = 10.5.sp,
                    color = Doit.InkFaint,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                    }
                    }
                }
            }
        }
    }
}

/**
 * 设置页分区容器：与主页任务卡同一套卡片语言（19dp 圆角 · Doit.Card 底 · 1dp CardBorder 描边 · 无阴影）。
 * 标题 + 说明收进卡内作首行，内容贴卡内边距；分区靠卡片本身 + 卡间间距区分，不再用分隔线。
 */
@Composable
private fun SettingSection(
    title: String,
    caption: String,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Doit.Card)
                .border(1.dp, Doit.CardBorder, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(title, style = titleStyle.copy(fontSize = 14.5.sp))
            Text(caption, fontSize = 11.sp, color = Doit.InkFaint, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun DoitSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val offset by animateFloatAsState(if (checked) 16f else 0f, tween(340), label = "switchThumb")
    val track by animateColorAsState(
        targetValue = if (checked) Doit.Accent else Doit.ToggleOff,
        animationSpec = tween(260),
        label = "switchTrack",
    )
    Box(
        Modifier
            .size(40.dp, 24.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(track)
            .tapFeedback { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = (3 + offset).dp, y = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Doit.Knob),
        )
    }
}

@Composable
private fun SettingField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    invalid: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val shape = RoundedCornerShape(11.dp)
    val line by animateColorAsState(
        targetValue = if (invalid) Doit.Danger else Doit.Line,
        animationSpec = tween(180),
        label = "fieldLine",
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 12.5.sp,
            color = Doit.Ink,
            fontFamily = SansFamily,
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            imeAction = ImeAction.Next,
        ),
        cursorBrush = SolidColor(if (invalid) Doit.Danger else Doit.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (invalid) Doit.DangerBg.copy(alpha = 0.45f) else Doit.Card)
            .border(if (invalid) 1.3.dp else 1.dp, line, shape)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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

@Composable
private fun PillButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    // 主按钮恒为实色主题色，表单没填好也不退回浅灰绿；和弹框底部「保存」的
    // 胶囊主按钮、顶部选中的分类胶囊同一套语言。次按钮仍用描边/浅底。
    val bg = when {
        primary -> Doit.AccentFill
        !enabled -> Doit.Surface2
        else -> Color.Transparent
    }
    val fg = when {
        primary -> Doit.OnAccent
        !enabled -> Doit.InkFaint
        else -> Doit.InkSoft
    }
    val lineColor = when {
        primary -> Doit.AccentFill
        !enabled -> Doit.LineSoft
        else -> Doit.Line
    }
    Box(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, lineColor, shape)
            .then(if (enabled) Modifier.tapFeedback { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = fg,
            textAlign = TextAlign.Center,
            fontFamily = SansFamily,
        )
    }
}
