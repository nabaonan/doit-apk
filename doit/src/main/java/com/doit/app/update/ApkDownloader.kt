package com.doit.app.update

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内直接下载 Release 的 apk，边下边报进度。
 *
 * 私有仓库的 asset 下载也要带 token，且 GitHub 会给 .apk 资产一个带签名的
 * 临时直链（browser_download_url），期间用 Authorization 头即可。
 *
 * 写法对齐 [com.doit.app.sync.WebDavClient]：不引第三方库，HttpURLConnection
 * 流式写磁盘，进度 0..1 即时回调给 UI。
 */
object ApkDownloader {

    private const val TAG = "DoitApkDownload"
    /** 下载时的 buffer 大小，决定进度回调的细分粒度 */
    private const val BUFFER = 8192

    /**
     * 把 [url] 下载到 [target]，返回是否成功。
     * @param onProgress 进度 0..1，会频繁回调（注意回到主线程由调用方处理）
     */
    fun download(
        url: String,
        token: String?,
        target: File,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Doit/1.0 (Android)")
        if (!token.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }

        val code = conn.responseCode
        Log.i(TAG, "GET apk -> $code")
        if (code != 200) {
            val hint = when {
                code == 401 || code == 403 || code == 404 ->
                    if (token.isNullOrBlank()) "需先填 GitHub 只读 Token 才能下载"
                    else "Token 权限不足，无法下载该 Release 的安装包"
                else -> "HTTP $code"
            }
            throw IllegalStateException(hint)
        }

        val total = conn.contentLengthLong.coerceAtLeast(0L)
        conn.inputStream.use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(BUFFER)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        // 没拿到 Content-Length 时最后强制 100%，避免 UI 停在半截
        onProgress(1f)
        Log.i(TAG, "下载完成 ${target.length()} 字节")
        Unit
    }.onFailure {
        Log.w(TAG, "下载失败", it)
    }

    /** 下载错误转成可读的一句中文（与更新检查的提示语统一用词） */
    fun friendlyError(t: Throwable): String = when {
        t is IllegalStateException -> t.message ?: "下载失败"
        t.message?.contains("resolve host", true) == true ||
            t.message?.contains("UnknownHost", true) == true -> "域名解析失败：检查网络是否通畅"
        t.message?.contains("timed out", true) == true ||
            t.message?.contains("timeout", true) == true -> "下载超时：网络较慢，请重试"
        t.message?.contains("Cleartext", true) == true -> "系统不允许明文传输，请检查下载地址"
        else -> "下载失败：${t.message ?: "未知错误"}"
    }
}
