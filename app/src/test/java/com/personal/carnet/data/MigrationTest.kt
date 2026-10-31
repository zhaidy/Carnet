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

    /** 第 3 版加的列：在第 2 版的表上补上这些列后，列名、类型、能不能空要和第 3 版导出的一样 */
    @Test
    fun 第三版加的列和导出的一样() {
        fun columns(version: Int, table: String): Map<String, Pair<String, Boolean>> {
            val json = JSONObject(File("schemas/com.personal.carnet.data.CarnetDatabase/$version.json").readText())
            val entities = json.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                if (e.getString("tableName") != table) continue
                val fields = e.getJSONArray("fields")
                return (0 until fields.length()).map { fields.getJSONObject(it) }
                    .associate { it.getString("columnName") to (it.getString("affinity") to it.optBoolean("notNull")) }
            }
            error("没有表 $table")
        }
        val added = CarnetDatabase.MIGRATION_2_3_SQL.map { sql ->
            val m = Regex("""ALTER TABLE `(\w+)` ADD COLUMN `(\w+)` (\w+)( NOT NULL)?""").find(sql)!!.groupValues
            Triple(m[1], m[2], m[3] to m[4].isNotEmpty())
        }
        for (table in listOf("ledgers", "entries", "settlements")) {
            val expected = columns(3, table)
            val migrated = columns(2, table) + added.filter { it.first == table }.associate { it.second to it.third }
            assertEquals(table, expected, migrated)
        }
    }

    @Test
    fun 第二版加的固定收支表和导出的一样() {
        assertEquals(createSql(2, "recurring_rules"), CarnetDatabase.RECURRING_TABLE_SQL)
    }
}
