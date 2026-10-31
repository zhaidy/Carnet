package com.personal.carnet.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private fun release(build: Int, body: String, apk: Boolean = true, draft: Boolean = false) = """
        {"tag_name":"build-$build","draft":$draft,"prerelease":false,"body":${org.json.JSONObject.quote(body)},
         "assets":[${if (apk) """{"name":"carnet.apk","browser_download_url":"https://x/$build.apk"}""" else ""}]}
    """.trimIndent()

    @Test
    fun 挑最新的并把跳过的版本说明都带上() {
        val json = "[" + listOf(release(7, "- 加了导入\n- 修了汇率"), release(6, "- 固定支出"), release(5, "- 旧的")).joinToString(",") + "]"
        val info = UpdateChecker.pickUpdate(json, current = 5)!!
        assertEquals(7L, info.build)
        assertEquals("https://x/7.apk", info.apkUrl)
        assertEquals(listOf("加了导入", "修了汇率", "固定支出"), info.changes)
    }

    @Test
    fun 已经是最新的或者没有安装包就不提示() {
        assertNull(UpdateChecker.pickUpdate("[" + release(5, "- x") + "]", current = 5))
        assertNull(UpdateChecker.pickUpdate("[" + release(6, "- x", apk = false) + "]", current = 5))
        assertNull(UpdateChecker.pickUpdate("[" + release(6, "- x", draft = true) + "]", current = 5))
    }
}
