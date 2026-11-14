package com.quandian.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.quandian.app.BuildConfig
import com.quandian.app.data.AppSettings
import com.quandian.app.sync.DavUrl
import com.quandian.app.ui.components.PillTab
import com.quandian.app.ui.components.PillTabs
import com.quandian.app.ui.components.PillTabsMode
import com.quandian.app.ui.theme.FlowSkin
import com.quandian.app.ui.theme.QD
import com.quandian.app.ui.theme.SansFamily
import com.quandian.app.ui.theme.ThemeMode
import com.quandian.app.ui.theme.numeric
import com.quandian.app.ui.theme.titleStyle

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
    onGithubToken: (String) -> Unit,
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
    // 检查更新用的 GitHub Token，失焦时保存
    var githubToken by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    val scroll = rememberScrollState()

    fun checkUrl(): Boolean {
        urlTouched = true
        val err = DavUrl.validate(url)
        if (err != null) {
            onToast(err)
            return false
        }
        return true
    }

    // 内容滚起来之后，标题栏底部浮出一条细线，和统计页保持同一种层级暗示
    val stuck by animateFloatAsState(
        targetValue = if (scroll.value > 4) 1f else 0f,
        animationSpec = tween(180),
        label = "settingsHeaderLine",
    )

    Column(modifier.fillMaxSize().background(QD.Surface)) {
        // ---------- 固定在顶部的标题 ----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 10.dp),
        ) {
            PageHead(title = "设置", latin = "SETTINGS", onBack = onBack)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QD.Line.copy(alpha = 0.55f * stuck)))

        // ---------- 可滚动的内容 ----------
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 30.dp),
        ) {
            // ---------- 主题 ----------
            SettingSection(title = "主题", caption = "清新绿 · 深夜青灰") {
                PillTabs(
                    tabs = ThemeMode.entries.map { mode ->
                        PillTab(key = mode.key, label = mode.label, accent = QD.Accent)
                    },
                    selectedKey = settings.themeMode,
                    onSelect = onTheme,
                    mode = PillTabsMode.Equal,
                    height = 34.dp,
                    gap = 7.dp,
                    indicatorColor = QD.Accent,
                )
            }

            // ---------- 外观 ----------
            SettingSection(title = "外观", caption = "右滑完成时的进度手感") {
                PillTabs(
                    tabs = FlowSkin.entries.map { skin ->
                        PillTab(
                            key = skin.key,
                            label = skin.label,
                            accent = QD.Accent,
                            dot = Brush.linearGradient(listOf(skin.fillStart, skin.fillEnd)),
                        )
                    },
                    selectedKey = currentSkin.key,
                    onSelect = onSkin,
                    mode = PillTabsMode.Equal,
                    height = 34.dp,
                    gap = 7.dp,
                    indicatorColor = QD.Accent,
                )
            }

            // ---------- 同步 ----------
            SettingSection(title = "同步", caption = "WebDAV · 在多台设备之间保持一致") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("WebDAV 自动同步", fontSize = 13.sp, color = QD.Ink, modifier = Modifier.weight(1f))
                    QdSwitch(checked = settings.davEnabled) { on ->
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
                                color = QD.Danger,
                                modifier = Modifier.padding(start = 13.dp, top = 5.dp),
                            )
                        }
                        Text(
                            "填 NAS 里已建好的文件夹，结尾带 /。飞牛 fn Connect 的域名根就是 WebDAV 根，不要再写 /webdav。",
                            fontSize = 10.sp,
                            color = QD.InkFaint,
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
                            color = QD.InkFaint,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                if (!settings.davEnabled) {
                    Text(
                        "开启后填写服务器地址与账号，清单就能在多台设备之间保持一致。",
                        fontSize = 10.5.sp,
                        color = QD.InkFaint,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // ---------- 关于 ----------
            SettingSection(title = "关于", caption = "一款清新绿的待办", last = true) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("当前版本", fontSize = 13.sp, color = QD.Ink, modifier = Modifier.weight(1f))
                    Text("v${BuildConfig.VERSION_NAME}", style = numeric(12).copy(fontSize = 12.sp), color = QD.InkSoft)
                }
                Spacer(Modifier.height(6.dp))
                SettingField(
                    value = githubToken,
                    onChange = { if (it.length <= 200) githubToken = it },
                    onFocusChange = { focused -> if (!focused) onGithubToken(githubToken) },
                    placeholder = "GitHub Token（可选，私有仓库检查更新用）",
                    password = true,
                )
                Text(
                    "项目是私有仓库，匿名调用 GitHub API 会被拒绝；填一个只读 TOKEN（contents:read）即可正常检查更新，留空也能点，但会提示失败。",
                    fontSize = 10.sp,
                    color = QD.InkFaint,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 13.dp, top = 6.dp, end = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                PillButton("检查更新", primary = false, modifier = Modifier.fillMaxWidth()) { onCheckUpdate() }
                Text(
                    "doit · 把清单，写在一张纸上。",
                    fontSize = 10.5.sp,
                    color = QD.InkFaint,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    caption: String,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 20.dp),
    ) {
        Text(title, style = titleStyle.copy(fontSize = 14.5.sp))
        Text(caption, fontSize = 11.sp, color = QD.InkFaint, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        content()
        if (!last) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(QD.LineSoft))
        }
    }
}

@Composable
private fun QdSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val offset by animateFloatAsState(if (checked) 16f else 0f, tween(340), label = "switchThumb")
    val track by animateColorAsState(
        targetValue = if (checked) QD.Accent else QD.ToggleOff,
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
                .background(QD.Knob),
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
        targetValue = if (invalid) QD.Danger else QD.Line,
        animationSpec = tween(180),
        label = "fieldLine",
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 12.5.sp,
            color = QD.Ink,
            fontFamily = SansFamily,
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            imeAction = ImeAction.Next,
        ),
        cursorBrush = SolidColor(if (invalid) QD.Danger else QD.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (invalid) QD.DangerBg.copy(alpha = 0.45f) else QD.Card)
            .border(if (invalid) 1.3.dp else 1.dp, line, shape)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 12.5.sp, color = QD.InkFaint)
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
    // 不可点时两套按钮都退回「灰底灰字」，不靠透明度糊过去
    val bg = when {
        !enabled -> QD.Surface2
        primary -> QD.AccentFill
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> QD.InkFaint
        primary -> QD.OnAccent
        else -> QD.InkSoft
    }
    val lineColor = when {
        !enabled -> QD.LineSoft
        primary -> QD.AccentFill
        else -> QD.Line
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
