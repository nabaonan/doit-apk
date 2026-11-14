package com.doit.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DavUrlTest {

    @Test
    fun `正常的 WebDAV 地址放行`() {
        assertNull(DavUrl.validate("https://dav.example.com"))
        assertNull(DavUrl.validate("https://dav.example.com/dav/"))
        assertNull(DavUrl.validate("http://192.168.1.20:5005/dav"))
        assertNull(DavUrl.validate("https://pan.jianguoyun.com/fe/"))
        assertNull(DavUrl.validate("  https://dav.example.com  ")) // 两端空格会被 trim
        assertNull(DavUrl.validate("HTTPS://Dns.Example.COM/dav"))
        assertNull(DavUrl.validate("http://localhost:8080"))
    }

    @Test
    fun `缺少协议头的不算合法`() {
        assertNotNull(DavUrl.validate("dav.example.com"))
        assertNotNull(DavUrl.validate("dav.example.com/dav"))
        assertNotNull(DavUrl.validate("ftp://dav.example.com"))
        assertNotNull(DavUrl.validate("//dav.example.com"))
    }

    @Test
    fun `缺域名或域名乱写会被拦下`() {
        assertNotNull(DavUrl.validate("https://"))
        assertNotNull(DavUrl.validate("https:///dav"))
        assertNotNull(DavUrl.validate("https://dav"))
        assertNotNull(DavUrl.validate("https://da v.example.com"))
        assertNotNull(DavUrl.validate("https://例子.中国"))
        assertNotNull(DavUrl.validate("https://dav..example.com"))
    }

    @Test
    fun `端口和 IP 越界会被拦下`() {
        assertNotNull(DavUrl.validate("https://dav.example.com:abc"))
        assertNotNull(DavUrl.validate("https://dav.example.com:70000"))
        assertNotNull(DavUrl.validate("https://dav.example.com:0"))
        assertNotNull(DavUrl.validate("http://300.1.1.1:80"))
    }

    @Test
    fun `地址里带账号密码会被提示分开填`() {
        val err = DavUrl.validate("https://me:pw@dav.example.com")
        assertEquals("账号密码请填在下面两栏", err)
    }

    @Test
    fun `空地址给出明确提示`() {
        assertEquals("请填写服务器地址", DavUrl.validate(""))
        assertEquals("请填写服务器地址", DavUrl.validate("   "))
    }
}
