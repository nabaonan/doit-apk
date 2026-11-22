package com.doit.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `从release的assets里取第一个apk直链`() {
        val release = """{
            "tag_name": "v1.0.7",
            "assets": [
                {"name": "doit-1.0.7-aab.aab", "browser_download_url": "https://x/aab"},
                {"name": "doit-1.0.7.apk", "browser_download_url": "https://x/doit-1.0.7.apk"}
            ]
        }"""
        assertEquals("https://x/doit-1.0.7.apk", UpdateChecker.findApkUrl(release))
    }

    @Test
    fun `release没有apk资产时返回空`() {
        assertNull(UpdateChecker.findApkUrl("""{"tag_name":"v1.0.7","assets":[]}"""))
        assertNull(UpdateChecker.findApkUrl("""{"tag_name":"v1.0.7"}"""))
    }

    @Test
    fun `asset嵌套uploader以及URL带花括号也能解析出apk`() {
        // 复现 GitHub 真实响应：asset 对象里嵌了 uploader 对象、URL 值里带 {/other_user} 模板花括号。
        val release = """{
            "tag_name": "v1.0.14",
            "assets": [
                {
                    "name": "doit-aab.aab",
                    "uploader": {"login": "x", "gists_url": "https://api.github.com/users/x/gists{/gist_id}"},
                    "browser_download_url": "https://x/aab"
                },
                {
                    "name": "doit-v1.0.14-release.apk",
                    "uploader": {"login": "bot", "repos_url": "https://api.github.com/users/bot/repos{/owner}{/repo}"},
                    "browser_download_url": "https://github.com/nabaonan/doit-apk/releases/download/v1.0.14/doit-v1.0.14-release.apk"
                }
            ],
            "body": "last release text with {braces} and [brackets]"
        }"""
        assertEquals(
            "https://github.com/nabaonan/doit-apk/releases/download/v1.0.14/doit-v1.0.14-release.apk",
            UpdateChecker.findApkUrl(release),
        )
    }

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

    @Test
    fun `没填token且404_提示需要填写只读token`() {
        val hint = UpdateChecker.authHint(404, null)
        assertTrue(hint.contains("GitHub 只读 Token"))
    }

    @Test
    fun `填了token仍404_应点明权限不足而非再次要求填写`() {
        val hint = UpdateChecker.authHint(404, "github_pat_xxx")
        assertFalse(hint.contains("填一个 GitHub 只读 Token"))
        assertTrue(hint.contains("已带 Token 仍无法访问"))
        assertTrue(hint.contains("该 Token 已勾选本仓库"))
        assertTrue(hint.contains("Contents"))
    }

    @Test
    fun `非认证错误_透传HTTP码`() {
        val hint = UpdateChecker.authHint(500, null)
        assertTrue(hint.contains("HTTP 500"))
    }
}
