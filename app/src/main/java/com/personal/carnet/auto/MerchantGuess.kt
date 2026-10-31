package com.personal.carnet.auto

/** 不用 Claude 先猜一下商户的分类；猜不到是 null */
object MerchantGuess {
    private val rules = listOf(
        "drink" to listOf("coffee", "kopi", "starbucks", "toast box", "ya kun", "luckin", "tea", "chagee", "koi", "liho", "咖啡", "奶茶", "瑞幸", "喜茶"),
        "ride" to listOf("grab", "gojek", "tada", "comfort", "taxi", "transit", "mrt", "bus", "ez-link", "simplygo", "滴滴", "地铁", "打车", "加油", "shell", "esso", "caltex"),
        "daily" to listOf("fairprice", "ntuc", "cold storage", "sheng siong", "giant", "don don donki", "watsons", "guardian", "daiso", "超市", "便利", "7-eleven", "全家", "盒马"),
        "shop" to listOf("shopee", "lazada", "amazon", "uniqlo", "taobao", "淘宝", "京东", "拼多多", "ikea", "decathlon", "muji", "apple store"),
        "sub" to listOf("netflix", "spotify", "disney", "youtube", "icloud", "apple.com", "google one", "chatgpt", "claude", "anthropic"),
        "phone" to listOf("singtel", "starhub", "m1 ", "circles", "gomo", "simba", "giga", "中国移动", "中国联通", "中国电信"),
        "fun" to listOf("cinema", "golden village", "shaw", "cathay", "klook", "ktv", "电影", "门票"),
        "food" to listOf("restaurant", "kitchen", "food", "hawker", "eat", "cafe", "bistro", "mcdonald", "kfc", "burger", "pizza", "sushi", "ramen", "hotpot", "haidilao", "饭", "面", "餐", "火锅", "海底捞", "麦当劳", "肯德基"),
    )

    fun guess(merchant: String): String? {
        val m = merchant.lowercase()
        return rules.firstOrNull { (_, words) -> words.any { it in m } }?.first
    }
}
