package com.personal.carnet.ai

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 截图缩到长边不超过 1568（Claude 看图的上限，再大也会被缩），存成 JPEG 再发，省流量也快 */
object Screenshot {
    private const val LONG_EDGE = 1568

    fun prepare(bytes: ByteArray): ImageInput {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AiException("读不了这张图")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw AiException("读不了这张图")
        val scale = LONG_EDGE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        } else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return ImageInput("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    /**
     * 选图器给的地址换成相册（MediaStore）里这张图的地址，删图要用它。
     * 云端相册里的图（只在 Google 相册、手机上没有）、认不出来的地址给 null，就不问删不删。
     */
    fun mediaUri(context: Context, uri: Uri): Uri? {
        if (uri.authority == MediaStore.AUTHORITY) {
            val segments = uri.pathSegments
            if (segments.firstOrNull() != "picker" && segments.firstOrNull() != "picker_get_content") return uri
            // content://media/picker/0/com.android.providers.media.photopicker/media/<id>：本机的图，id 就是相册里的 id
            if (segments.size < 3 || segments[segments.size - 3] != "com.android.providers.media.photopicker") return null
            val id = segments.last().toLongOrNull() ?: return null
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DocumentsContract.isDocumentUri(context, uri)) {
            return runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        }
        return null
    }
}
