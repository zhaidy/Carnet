package com.personal.carnet.ai

import com.personal.carnet.auto.MerchantGuess
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeClientTest {

    private val schema = JSONObject().put("type", "object")

    @Test
    fun 请求带结构化输出和拒答兜底() {
        val body = ClaudeClient.requestBody("claude-opus-5-5", "hi", schema)
        assertEquals("claude-opus-5-5", body.getString("model"))
        val config = body.getJSONObject("output_config")
        assertEquals("low", config.getString("effort"))
        assertEquals("json_schema", config.getJSONObject("format").getString("type"))
        assertEquals("default", body.getString("fallbacks"))
        assertFalse(body.has("temperature"))
    }

    @Test
    fun 没有图时内容就是一段文字() {
        val message = ClaudeClient.requestBody("claude-opus-5-5", "hi", schema).getJSONArray("messages").getJSONObject(0)
        assertEquals("hi", message.getString("content"))
    }

    @Test
    fun 带截图时图在前文字在后() {
        val body = ClaudeClient.requestBody("claude-opus-5-5", "认一下", schema, listOf(ImageInput("image/jpeg", "QUJD")))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(2, content.length())
        val image = content.getJSONObject(0)
        assertEquals("image", image.getString("type"))
        assertEquals("base64", image.getJSONObject("source").getString("type"))
        assertEquals("image/jpeg", image.getJSONObject("source").getString("media_type"))
        assertEquals("QUJD", image.getJSONObject("source").getString("data"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
        assertEquals("认一下", content.getJSONObject(1).getString("text"))
    }

    @Test
    fun haiku没有兜底() {
        assertFalse(ClaudeClient.requestBody("claude-haiku-5-5", "hi", schema).has("fallbacks"))
    }

    @Test
    fun 只取最后一个文字块() {
        val reply = """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"category\":\"food\"}"}]}"""
        assertEquals("food", ClaudeClient.parseReply(reply).getString("category"))
    }

    @Test
    fun 拒答和截断说清楚() {
        val refused = runCatching { ClaudeClient.parseReply("""{"stop_reason":"refusal","content":[]}""") }.exceptionOrNull()
        assertEquals("Claude 拒绝了这次请求", refused?.message)
        val cut = runCatching { ClaudeClient.parseReply("""{"stop_reason":"max_tokens","content":[{"type":"text","text":"{"}]}""") }.exceptionOrNull()
        assertTrue(cut is AiException)
    }

    @Test
    fun 出错码() {
        assertEquals("Claude API Key 不对", ClaudeClient.errorMessage(401, "{}"))
        assertEquals("Claude 出错了（400）：bad", ClaudeClient.errorMessage(400, """{"error":{"message":"bad"}}"""))
    }

    @Test
    fun 不用Claude先猜商户() {
        assertEquals("drink", MerchantGuess.guess("TOAST BOX @ Raffles"))
        assertEquals("ride", MerchantGuess.guess("Grab* Ride"))
        assertEquals("daily", MerchantGuess.guess("FairPrice Finest"))
        assertEquals(null, MerchantGuess.guess("ABC Pte Ltd"))
    }
}
