package com.personal.carnet.ui

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Category

/** 自己加的分类：存的是 key（里面带着图标和名字），改了界面马上跟着变 */
object CustomCategories {
    private val keys = mutableStateListOf<String>()

    fun load(context: Context) {
        keys.clear()
        keys += prefs(context).getString(KEY, "").orEmpty().split("\n").filter { Categories.isCustom(it) }
    }

    fun expense(): List<Category> = keys.map(Categories::of).filter { !it.income }
    fun income(): List<Category> = keys.map(Categories::of).filter { it.income }
    fun all(): List<Category> = keys.map(Categories::of)

    /** 返回新分类的 key；同名的就用已有的 */
    fun add(context: Context, name: String, icon: String, income: Boolean): String {
        val key = Categories.customKey(name, icon, income)
        val existing = keys.firstOrNull { Categories.of(it).label == name.trim() && Categories.of(it).income == income }
        if (existing != null) return existing
        keys += key
        save(context)
        return key
    }

    fun remove(context: Context, key: String) {
        keys.remove(key)
        save(context)
    }

    private fun save(context: Context) = prefs(context).edit().putString(KEY, keys.joinToString("\n")).apply()

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private const val KEY = "custom_categories"
}
