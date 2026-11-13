package com.quandian.app.sync

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 WebDAV 客户端：只做「测试连接」和「上传备份」两件事，
 * 足够把清单同步到坚果云 / Nextcloud 这类支持 WebDAV 的盘上。
 */
object WebDavClient {

    private fun authHeader(user: String, pass: String): String? =
        if (user.isBlank()) null
        else "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)

    private fun open(url: String, method: String, user: String, pass: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        authHeader(user, pass)?.let { conn.setRequestProperty("Authorization", it) }
        conn.setRequestProperty("User-Agent", "Quandian/1.0 (Android)")
        return conn
    }

    suspend fun test(url: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Result.failure(IOException("先填一下服务器地址"))
        runCatching {
            val base = normalize(url)
            val host = URL(base).host
            // 依次尝试：原样 → 末尾补一个 /。
            // 飞牛 fnOS 等实现里，对集合根路径缺斜杠会直接回 405，
            // 补上 / 之后再 PROPFIND 通常就能通过。
            val attempts = listOf(base, base.trimEnd('/') + "/").distinct()
            var authFailed = false
            var lastCode = 0
            for (candidate in attempts) {
                val code = probe(candidate, user, pass)
                if (code == 401 || code == 403) { authFailed = true; continue }
                if (code in 200..399) {
                    val autoSlash = candidate.endsWith("/") && !base.endsWith("/")
                    return@withContext Result.success(
                        if (autoSlash) "连接成功 · $host（已自动补末尾 /）" else "连接成功 · $host",
                    )
                }
                lastCode = code
            }
            if (authFailed) throw IOException("账号或密码不对（401/403）")
            if (lastCode == 405) {
                throw IOException(
                    "服务器拒绝了 WebDAV 探测（405）。" +
                        "建议：① 地址结尾补一个 /；② 先在 NAS 文件管理里建好同步文件夹，" +
                        "再填成 https://…/webdav/doit 这类子路径；③ 确认账号用的是 WebDAV 专用账号。",
                )
            }
            throw IOException("服务器返回 $lastCode")
        }
    }

    /** 发一次 PROPFIND（Depth:0）探测目录；个别实现不允许自定义方法时退化成 HEAD */
    private fun probe(url: String, user: String, pass: String): Int = try {
        val conn = open(url, "PROPFIND", user, pass)
        conn.setRequestProperty("Depth", "0")
        val c = conn.responseCode
        conn.disconnect()
        c
    } catch (e: java.net.ProtocolException) {
        val conn = open(url, "HEAD", user, pass)
        val c = conn.responseCode
        conn.disconnect()
        c
    }

    suspend fun upload(
        url: String,
        user: String,
        pass: String,
        fileName: String,
        json: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Result.failure(IOException("先填一下服务器地址"))
        runCatching {
            val target = normalize(url).trimEnd('/') + "/" + fileName
            val conn = open(target, "PUT", user, pass)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = json.toByteArray()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299 || code == 201 || code == 204) "同步完成 · ${json.length} 字节"
            else throw IOException("上传失败（$code）")
        }
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }
}
