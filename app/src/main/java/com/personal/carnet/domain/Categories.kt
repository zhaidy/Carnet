package com.personal.carnet.domain

/** 分类先写死在代码里；icon 是 24×24 的线条图标路径 */
data class Category(val key: String, val label: String, val icon: String, val income: Boolean = false)

object Categories {
    val expense = listOf(
        Category("food", "餐饮", "M4 11h16a8 8 0 0 1-16 0zM8 4v4M12 3v5M16 4v4"),
        Category("ride", "交通", "M5 10h14a2 2 0 0 1 2 2v5H3v-5a2 2 0 0 1 2-2zM6 10l2-5h8l2 5M6 17v2M18 17v2"),
        Category("shop", "购物", "M5 8h14l-1 12H6zM9 8V6a3 3 0 0 1 6 0v2"),
        Category("daily", "日用", "M4 9h16l-1.5 10h-13zM9 9l3-5 3 5"),
        Category("drink", "饮品", "M5 8h11v6a5 5 0 0 1-10 0zM16 10h2a2 2 0 0 1 0 4h-2M8 3v2M12 3v2"),
        Category("fun", "娱乐", "M6 7h12a3 3 0 0 1 3 3v5a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-5a3 3 0 0 1 3-3zM8 11v3M6.5 12.5h3M15 12.5h2"),
        Category("home", "住房", "M4 11l8-7 8 7M6 10v10h12V10M10 20v-5h4v5"),
        Category("phone", "话费", "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2"),
        Category("sub", "订阅", "M4 6h16v11H4zM9 21h6M10.5 9.5v4l3.5-2z"),
        Category("more", "其他", "M4 12h2M11 12h2M18 12h2"),
    )

    val income = listOf(
        Category("salary", "工资", "M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM3 10h18M16 14h2", income = true),
        Category("bonus", "奖金", "M4 10h16v10H4zM3 7h18v3H3zM12 7v13M12 7c-2-4-6-3-5 0M12 7c2-4 6-3 5 0", income = true),
        Category("invest", "理财", "M4 18l5-6 4 3 7-8M15 7h5v5", income = true),
        Category("family", "家里给的", "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z", income = true),
        Category("other_in", "其他", "M4 12h2M11 12h2M18 12h2", income = true),
    )

    const val TRANSFER_ICON = "M4 8h14l-3-3M20 16H6l3 3"
    const val CARD_ICON = "M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM3 10h18M7 15h3"

    private val all = (expense + income).associateBy { it.key }

    fun of(key: String): Category = all[key] ?: expense.last()
}
