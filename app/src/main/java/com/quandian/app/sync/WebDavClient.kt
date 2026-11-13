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
            val normalized = normalize(url)
            val code = try {
                val conn = open(normalized, "PROPFIND", user, pass)
                conn.setRequestProperty("Depth", "0")
                val c = conn.responseCode
                if (c == 401 || c == 403) throw IOException("账号或密码不对（$c）")
                conn.disconnect()
                c
            } catch (e: java.net.ProtocolException) {
                // 少数实现不允许自定义方法，退化成 HEAD 探活
                val conn = open(normalized, "HEAD", user, pass)
                val c = conn.responseCode
                if (c == 401 || c == 403) throw IOException("账号或密码不对（$c）")
                conn.disconnect()
                c
            }
            if (code in 200..399) "连接成功 · ${URL(normalized).host}"
            else throw IOException("服务器返回 $code")
        }
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
