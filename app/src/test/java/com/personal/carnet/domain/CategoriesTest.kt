package com.personal.carnet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun 自定义分类的名字和图标在key里() {
        val key = Categories.customKey("宠物", "pet", income = false)
        assertEquals("c:pet:宠物", key)
        val c = Categories.of(key)
        assertEquals("宠物", c.label)
        assertEquals(Categories.customIcons["pet"], c.icon)
        assertFalse(c.income)
        // 收入
        assertTrue(Categories.of(Categories.customKey("红包", "gift", income = true)).income)
        // 没见过的图标用星星
        assertEquals(Categories.customIcons["star"], Categories.of("c:unknown:别的").icon)
        // 内置的照旧
        assertEquals("购物", Categories.of("shop").label)
        // 认不出的还是「其他」
        assertEquals("其他", Categories.of("c::").label)
    }
}
