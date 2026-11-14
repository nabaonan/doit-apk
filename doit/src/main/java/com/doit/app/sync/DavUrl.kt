package com.doit.app.sync

/**
 * WebDAV 服务器地址的格式校验。
 * 返回 null 表示格式没问题，否则返回一句能直接念给用户听的错因。
 *
 * 只做「格式」判断：不去连服务器，也不把地址补全成 https://
 * （补全是客户端的行为，用户填错了得让他自己看见，而不是被悄悄改掉）。
 */
object DavUrl {

    private val IPv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** 至少要有一个点，每段 1~63 个字母数字或连字符，且不能以连字符开头结尾 */
    private val Domain = Regex(
        "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$",
    )

    fun validate(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return "请填写服务器地址"
        if (s.any { it.isWhitespace() }) return "服务器地址里不能有空格"
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            return "地址要以 http:// 或 https:// 开头"
        }

        val rest = s.substringAfter("://")
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isBlank()) return "地址里缺少域名"
        if (authority.contains('@')) return "账号密码请填在下面两栏"

        val host = authority.substringBefore(':').lowercase()
        if (host.isBlank()) return "地址里缺少域名"

        if (authority.contains(':')) {
            val port = authority.substringAfter(':')
            if (port.isEmpty() || port.length > 5 || port.any { !it.isDigit() }) return "端口号不对"
            if (port.toInt() !in 1..65535) return "端口号超出范围"
        }

        if (host == "localhost") return null
        if (IPv4.matches(host)) {
            return if (host.split('.').all { it.toInt() in 0..255 }) null else "IP 地址超出范围"
        }
        if (!Domain.matches(host)) return "域名格式不对，比如 dav.example.com"
        return null
    }

    fun isValid(raw: String): Boolean = validate(raw) == null
}
