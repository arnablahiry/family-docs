package com.familydocs.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object Images {
    /** Decodes with down-sampling and applies the EXIF rotation that camera photos carry. */
    fun decode(bytes: ByteArray, maxPx: Int = 2200): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }

    /** Renders the first [pages] pages of a PDF. PdfRenderer needs a seekable file, so a short-lived temp file is used. */
    fun renderPdf(context: Context, bytes: ByteArray, pages: Int = 2, widthPx: Int = 1600): List<Bitmap> {
        val tmp = File.createTempFile("pdf", ".bin", context.cacheDir)
        try {
            tmp.writeBytes(bytes)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    return (0 until minOf(pages, r.pageCount)).map { i ->
                        r.openPage(i).use { p ->
                            val h = (widthPx.toFloat() * p.height / p.width).roundToInt()
                            Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { b ->
                                Canvas(b).drawColor(Color.WHITE)
                                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    fun pages(context: Context, bytes: ByteArray, mime: String?): List<Bitmap> =
        if (mime == "application/pdf") renderPdf(context, bytes) else listOfNotNull(decode(bytes))

    fun centerSquareJpeg(src: Bitmap, size: Int = 320): ByteArray {
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return jpeg(Bitmap.createScaledBitmap(crop, size, size, true))
    }

    fun jpeg(b: Bitmap, quality: Int = 88): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
