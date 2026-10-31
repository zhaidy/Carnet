package com.personal.carnet.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiException(message: String) : Exception(message)

/** 用哪个模型、key 是什么；存在本机 */
interface ClaudeSettings {
    val apiKey: String
    val model: String
}

/**
 * 调 Claude 的 Messages API，用 output_config.format 按 JSON Schema 返回 JSON。
 * 记账里用到的都是小活（认通知、猜分类、一句话记账），effort 用 low，回得快也便宜。
 */
class ClaudeClient(private val settings: ClaudeSettings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = settings.apiKey.isNotBlank()

    suspend fun json(prompt: String, schema: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val key = settings.apiKey.trim()
        if (key.isBlank()) throw AiException("还没有填 Claude API Key")
        val model = settings.model
        val builder = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", key)
            .header("anthropic-version", API_VERSION)
        if (supportsFallback(model)) builder.header("anthropic-beta", FALLBACK_BETA)
        val request = builder.post(requestBody(model, prompt, schema).toString().toRequestBody("application/json".toMediaType())).build()

        val (code, body) = try {
            http.newCall(request).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw AiException("连不上 Claude：${e.message ?: "网络出错"}")
        }
        if (code !in 200..299) throw AiException(errorMessage(code, body))
        parseReply(body)
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2026-10-21"
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** 可选的模型（API 名 to 显示名） */
        val MODELS = listOf(
            "claude-opus-5-5" to "Claude Opus 5.5",
            "claude-sonnet-5-5" to "Claude Sonnet 5.5",
            "claude-haiku-5-5" to "Claude Haiku 5.5（最便宜）",
        )
        const val DEFAULT_MODEL = "claude-opus-5-5"

        /** Haiku 没有服务端的拒答兜底 */
        fun supportsFallback(model: String) = !model.startsWith("claude-haiku")

        fun requestBody(model: String, prompt: String, schema: JSONObject): JSONObject = JSONObject()
            .put("model", model)
            .put("max_tokens", 4000)
            .put(
                "output_config",
                JSONObject()
                    .put("effort", "low")
                    .put("format", JSONObject().put("type", "json_schema").put("schema", schema)),
            )
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .apply { if (supportsFallback(model)) put("fallbacks", "default") }

        /** 只取最后一个 text 块里的 JSON；先看 stop_reason，拒答和截断要说清楚 */
        fun parseReply(body: String): JSONObject {
            val reply = JSONObject(body)
            when (reply.optString("stop_reason")) {
                "refusal" -> throw AiException("Claude 拒绝了这次请求")
                "max_tokens" -> throw AiException("Claude 的回复太长被截断了")
            }
            val content = reply.optJSONArray("content") ?: throw AiException("Claude 没有返回内容")
            val text = (0 until content.length()).map { content.getJSONObject(it) }
                .lastOrNull { it.optString("type") == "text" }?.optString("text")
                ?: throw AiException("Claude 没有返回文字")
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                throw AiException("Claude 返回的不是 JSON")
            }
        }

        fun errorMessage(code: Int, body: String): String {
            val detail = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
            return when (code) {
                401 -> "Claude API Key 不对"
                402 -> "Claude 余额不足，到 platform.claude.com 充值"
                429 -> "请求太频繁或额度用完了，等一会再试"
                in 500..599 -> "Claude 服务器出错了（$code），等一会再试"
                else -> "Claude 出错了（$code）" + if (detail.isNotBlank()) "：$detail" else ""
            }
        }
    }
}
