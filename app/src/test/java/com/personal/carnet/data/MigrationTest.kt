package com.personal.carnet.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 手写的迁移 SQL 必须和 Room 导出的表结构一模一样，否则老用户升级时会崩 */
class MigrationTest {

    private fun createSql(version: Int, table: String): String {
        val json = JSONObject(File("schemas/com.personal.carnet.data.CarnetDatabase/$version.json").readText())
        val entities = json.getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            if (e.getString("tableName") == table) return e.getString("createSql").replace("\${TABLE_NAME}", table)
        }
        error("没有表 $table")
    }

    @Test
    fun 第二版加的固定收支表和导出的一样() {
        assertEquals(createSql(2, "recurring_rules"), CarnetDatabase.RECURRING_TABLE_SQL)
    }
}
