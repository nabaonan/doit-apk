package com.doit.app.sync

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一次 WebDAV 操作的结果。
 * [workingUrl] 非空表示客户端自动找到了一个可用目录（用户填的路径不可用），
 * 调用方应把它回填到设置里，避免「测试通过、保存却还是错的旧地址」。
 */
data class DavResult(val message: String, val workingUrl: String? = null)

/**
 * 极简 WebDAV 客户端：只做「测试连接」和「上传备份」两件事。
 *
 * 关于 405：同步本身只需要 PUT。用 PROPFIND 去「探活」是多余的协议要求——
 * Android 的 HttpURLConnection 对非标准方法支持不稳，飞牛 fn Connect 这类
 * 中继/反代也常直接拒绝 PROPFIND；而不存在的集合（例如用户多写了一层
 * /webdav）同样回 405。所以这里统一改成 **PUT 一个探针文件**，测的就是
 * 真实上传能力，并依次尝试几个更可能正确的目录。
 */
object WebDavClient {

    /** 探针文件名：固定名字，重复测试只覆盖自己，不会越堆越多 */
    private const val PROBE_NAME = "doit-connection-test.json"
    private val PROBE_BYTES = "{\"probe\":1}".toByteArray()

    /** 把「协议名」误当成文件夹名的常见写法，飞牛 fn Connect 的根本身就是 WebDAV 根 */
    private val PROTOCOL_SEGMENTS = setOf("webdav", "dav", "web-dav")

    private fun authHeader(user: String, pass: String): String? =
        if (user.isBlank()) null
        else "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)

    private fun open(url: String, method: String, user: String, pass: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        authHeader(user, pass)?.let { conn.setRequestProperty("Authorization", it) }
        conn.setRequestProperty("User-Agent", "Doit/1.0 (Android)")
        return conn
    }

    /**
     * 生成待试的「目录」候选（都带尾斜杠），按可能性排序：
     * 1. 用户填的原样（补尾 /）
     * 2. 若最后一段是 webdav/dav 这类协议名，去掉它试父目录
     * 3. 域名根下建一个应用专属文件夹（推荐做法）
     * 4. 域名根
     */
    private fun directoryCandidates(base: String): List<String> {
        val u = URL(base)
        val origin = "${u.protocol}://${u.authority}"
        val root = "$origin/"
        val path = u.path.trimEnd('/')
        val out = linkedSetOf(base.trimEnd('/') + "/")
        val last = path.substringAfterLast('/').lowercase()
        if (last in PROTOCOL_SEGMENTS) {
            val parent = path.substringBeforeLast('/')
            out.add("$origin$parent/".replace(Regex("/+$"), "/"))
        }
        out.add("$origin/doit/")
        out.add(root)
        return out.toList()
    }

    /** 一次请求的结论：状态码，或网络层异常（折算成 -1）及其原因 */
    private data class DavReply(val code: Int, val error: String? = null)

    /** 网络层异常不直接抛出，好让调用方继续试下一个候选目录 */
    private fun request(url: String, method: String, user: String, pass: String, body: ByteArray?): DavReply =
        try {
            val conn = open(url, method, user, pass)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            conn.disconnect()
            DavReply(code)
        } catch (e: Exception) {
            DavReply(-1, e.message ?: e.javaClass.simpleName)
        }

    private fun deleteQuietly(url: String, user: String, pass: String) {
        runCatching {
            val conn = open(url, "DELETE", user, pass)
            conn.responseCode
            conn.disconnect()
        }
    }

    /** 405 / 404 / 403 等失败时，给一句能直接照着做的中文提示 */
    private fun diagnose(code: Int, host: String, base: String, err: String?): String = when (code) {
        401 -> "账号或密码不对（401）"
        403 -> "账号没有这个目录的写入权限（403）：去 NAS 后台给该账号开 WebDAV 读写权限"
        404 -> "找不到这个目录（404）：先在 NAS 文件管理里手动建好文件夹，再填它的完整地址"
        405 -> "这个地址不接受上传（405）。飞牛 fn Connect 的域名根就是 WebDAV 根，" +
            "不要再写 /webdav；请在 NAS 里建一个文件夹（如 doit），" +
            "地址填成 https://$host/doit/ 这种形式（结尾要带 /）"
        -1 -> when {
            err == null -> "连不上服务器：检查域名、端口和证书（$base）"
            err.contains("Cleartext", true) -> "系统不允许明文 http:// 传输，改用 https:// 地址"
            err.contains("certificate", true) || err.contains("SSL", true) || err.contains("trust anchor", true) ->
                "证书校验失败：NAS 用的是自签证书，请换成带有效证书的域名"
            err.contains("resolve host", true) || err.contains("UnknownHost", true) ->
                "域名解析失败：检查地址有没有写错、手机能不能上网"
            err.contains("timed out", true) || err.contains("timeout", true) ->
                "连接超时：检查端口是否放行（飞牛 WebDAV 常用 5005 / 5006）"
            else -> "连不上服务器：$err"
        }
        else -> "服务器返回 $code"
    }

    suspend fun test(url: String, user: String, pass: String): Result<DavResult> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Result.failure(IOException("先填一下服务器地址"))
        runCatching {
            val base = normalize(url)
            val host = URL(base).host
            val candidates = directoryCandidates(base)
            var lastCode = -1
            var lastError: String? = null
            for (dir in candidates) {
                val reply = request(dir + PROBE_NAME, "PUT", user, pass, PROBE_BYTES)
                val code = reply.code
                if (code in 200..299) {
                    deleteQuietly(dir + PROBE_NAME, user, pass)
                    val corrected = dir != candidates.first()
                    return@runCatching DavResult(
                        message = if (corrected) "连接成功 · $host（已自动找到可用路径）"
                        else "连接成功 · $host",
                        workingUrl = if (corrected) dir else null,
                    )
                }
                // 凭据问题跟路径无关，没必要再试其它目录
                if (code == 401 || code == 403) throw IOException(diagnose(code, host, base, reply.error))
                lastCode = code
                lastError = reply.error
            }
            throw IOException(diagnose(lastCode, host, base, lastError))
        }
    }

    suspend fun upload(
        url: String,
        user: String,
        pass: String,
        fileName: String,
        json: String,
    ): Result<DavResult> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Result.failure(IOException("先填一下服务器地址"))
        runCatching {
            val base = normalize(url)
            val host = URL(base).host
            val candidates = directoryCandidates(base)
            val bytes = json.toByteArray()
            var lastCode = -1
            var lastError: String? = null
            for (dir in candidates) {
                val reply = request(dir + fileName, "PUT", user, pass, bytes)
                val code = reply.code
                if (code in 200..299) {
                    val corrected = dir != candidates.first()
                    return@runCatching DavResult(
                        message = "同步完成 · ${json.length} 字节",
                        workingUrl = if (corrected) dir else null,
                    )
                }
                if (code == 401 || code == 403) throw IOException(diagnose(code, host, base, reply.error))
                lastCode = code
                lastError = reply.error
            }
            throw IOException(diagnose(lastCode, host, base, lastError))
        }
    }

    /** 用户填的地址规范化成一个「带尾斜杠的目录」，下载和上传必须落在同一个目录 */
    fun directoryOf(raw: String): String = normalize(raw).trimEnd('/') + "/"

    /**
     * 读取远程快照。404/410 表示远程还没有备份文件（返回 null）；
     * 其它非 2xx 一律报错，绝不当成「远程为空」，否则会拿本地数据覆盖掉看不懂的内容。
     */
    suspend fun fetch(dir: String, user: String, pass: String, fileName: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = dir.trimEnd('/') + "/" + fileName
                val conn = open(target, "GET", user, pass)
                val code = conn.responseCode
                val host = URL(target).host
                when {
                    code == 404 || code == 410 -> {
                        conn.disconnect()
                        null
                    }
                    code == 401 || code == 403 -> {
                        conn.disconnect()
                        throw IOException(diagnose(code, host, target, null))
                    }
                    code in 200..299 -> {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        conn.disconnect()
                        text
                    }
                    else -> {
                        conn.disconnect()
                        throw IOException("读取远程数据失败（$code）")
                    }
                }
            }
        }

    /** 把合并后的快照写到指定目录（目录已由 [test] 探测过） */
    suspend fun put(dir: String, user: String, pass: String, fileName: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = dir.trimEnd('/') + "/" + fileName
                val reply = request(target, "PUT", user, pass, text.toByteArray())
                if (reply.code !in 200..299) {
                    throw IOException(diagnose(reply.code, URL(target).host, target, reply.error))
                }
                Unit
            }
        }

    private fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }
}
