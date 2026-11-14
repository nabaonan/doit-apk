package com.quandian.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `远程比本地新则提示发现新版本`() {
        val (has, msg) = UpdateChecker.decide("v1.0.7", "1.0.6")
        assertTrue(has)
        assertTrue(msg.contains("1.0.7"))
    }

    @Test
    fun `远程与本地相同则提示已是最新`() {
        val (has, msg) = UpdateChecker.decide("v1.0.6", "1.0.6")
        assertFalse(has)
        assertTrue(msg.contains("已是最新版本"))
    }

    @Test
    fun `本地比远程新仍视为已是最新`() {
        val (has, msg) = UpdateChecker.decide("v1.0.5", "1.0.6")
        assertFalse(has)
        assertTrue(msg.contains("已是最新版本"))
    }

    @Test
    fun `三位版本逐段比较`() {
        assertTrue(UpdateChecker.compareVersions("1.0.10", "1.0.9") > 0)
        assertTrue(UpdateChecker.compareVersions("1.2.0", "1.10.0") < 0) // 1 < 10 分段比较
        assertEquals(0, UpdateChecker.compareVersions("1.2.3", "1.2.3"))
    }

    @Test
    fun `本地调试版带后缀也能正确比较`() {
        // 调试包 VERSION_NAME 形如 1.0.6-debug，本地版本应解析为 1.0.6
        val (has, msg) = UpdateChecker.decide("v1.0.6", "1.0.6-debug")
        assertFalse(has)
        assertTrue(msg.contains("已是最新版本"))
        assertTrue(msg.contains("1.0.6"))
        assertTrue(UpdateChecker.compareVersions("1.0.7", "1.0.6-debug") > 0)
    }
}
