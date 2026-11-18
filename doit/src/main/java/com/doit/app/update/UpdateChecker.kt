package com.doit.app.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 拉取最新版并做语义版本比较，替代原来“写死当前就是最新”的本地判断。
 *
 * 项目仓库是私有的，匿名调用 GitHub API 会被拒（401/403/404），
 * 因此在设置里提供一个可选 [token]（拥有 contents:read 的 PAT 即可），
 * 带上后才能真正读到私有仓库的 Release。
 */
object UpdateChecker {

    private const val REPO = "nabaonan/doit-apk"
    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASE_URL = "https://github.com/$REPO/releases"
    private const val TAG = "DoitUpdate"

    /**
     * [hasUpdate] 是否有新版本；[latestVersion] 远程最新版本号（不带 v 前缀，可能为空）；
     * [message] 直接作为界面的提示文案。
     */
    data class Result(
        val hasUpdate: Boolean,
        val latestVersion: String? = null,
        val releaseUrl: String = RELEASE_URL,
        val message: String,
    )

    suspend fun check(currentVersion: String, token: String?): Result = withContext(Dispatchers.IO) {
        try {
            val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Doit/1.0 (Android)")
            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            Log.i(TAG, "GET $RELEASES_API -> $code")
            if (code != 200) {
                // GitHub 对「token 无权访问的私有仓库」「token 无效」「仓库不存在」都会返回 401/403/404，
                // 故意不暴露私有仓库是否存在。因此这里要区分两种情形给不同提示：
                // - 没填 token：引导去设置里填
                // - 填了 token 仍失败：token 有效但缺该仓库权限，或已过期/被撤销
                return@withContext Result(false, message = "检查更新失败（${authHint(code, token)}）")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tag = JSONObject(body).optString("tag_name", "").trim()
            if (tag.isBlank()) return@withContext Result(false, message = "远程返回异常，稍后再试")
            val (has, message) = decide(tag, currentVersion)
            return@withContext Result(has, tag.removePrefix("v"), message = message)
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e)
            Result(false, message = "检查更新失败：${e.message ?: "网络错误"}")
        }
    }

    /**
     * 纯决策逻辑（可单测）：给定远程 tag（可能带 v 前缀）与本地版本号，
     * 返回是否需要更新以及对应的提示文案。比较按 x.y.z 数值进行，
     * 前缀（如 -debug，本地调试版 VERSION_NAME 带此后缀）与无法解析的段忽略。
     */
    internal fun decide(remoteTag: String, currentVersion: String): Pair<Boolean, String> {
        val remote = remoteTag.trim().removePrefix("v")
        val local = currentVersion.trim().removePrefix("v")
        return if (compareVersions(remote, local) > 0) {
            true to "发现新版本 v$remote，可到 GitHub Release 下载"
        } else {
            false to "已是最新版本 v$local"
        }
    }

    /**
     * 认证失败提示：区分「还没填 token」和「填了 token 仍失败」两种场景。
     * GitHub 对无权访问的私有仓库统一回 401/403/404，所以填了 token 还失败
     * 几乎都是 token 缺该仓库权限（或已过期），直接点明这一点，别再让用户误以为没存上。
     */
    internal fun authHint(code: Int, token: String?): String = when (code) {
        401, 403, 404 ->
            if (token.isNullOrBlank()) {
                "私有仓库需在「设置」里填一个 GitHub 只读 Token，之后才能读到 Release"
            } else {
                "已带 Token 仍无法访问：请确认该 Token 已勾选本仓库、且含 Contents 读取权限"
            }
        else -> "HTTP $code"
    }

    /** 按 x.y.z 数值比较，remote 比 local 新返回正数。 */
    internal fun compareVersions(remote: String, local: String): Int {
        val a = parse(remote)
        val b = parse(local)
        for (i in 0..2) {
            val diff = a[i].compareTo(b[i])
            if (diff != 0) return diff
        }
        return 0
    }

    private fun parse(v: String): IntArray {
        val m = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(v)
        return if (m != null) {
            intArrayOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } else {
            intArrayOf(0, 0, 0)
        }
    }
}
