package com.group10.smartstudytimer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object BestRecordShareImageGenerator {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    fun generateShareImageUri(
        context: Context,
        displayName: String,
        avatarId: String,
        bestRecord: BestStudyRecord
    ) = runCatching {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(context, canvas)
        drawOverlay(canvas)
        drawAvatar(context, canvas, avatarId)
        drawTexts(canvas, displayName, bestRecord)

        val shareDirectory = File(context.cacheDir, "shares").apply { mkdirs() }
        val imageFile = File(shareDirectory, "best_record_share.png")
        FileOutputStream(imageFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    private fun drawBackground(context: Context, canvas: Canvas) {
        val background = requireNotNull(
            ContextCompat.getDrawable(context, ShareBackgroundAssets.getRandomBackgroundResId())
        )
        background.setBounds(0, 0, WIDTH, HEIGHT)
        background.draw(canvas)
    }

    private fun drawOverlay(canvas: Canvas) {
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#1AFFFFFF"), Color.parseColor("#660D1321")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), overlayPaint)

        val contentCard = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2FFFFFF")
            setShadowLayer(40f, 0f, 12f, Color.parseColor("#33000000"))
        }
        canvas.drawRoundRect(
            RectF(64f, 300f, WIDTH - 64f, HEIGHT - 140f),
            48f,
            48f,
            contentCard
        )

        val accentCard = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF7FBFF")
        }
        canvas.drawRoundRect(
            RectF(124f, 940f, WIDTH - 124f, 1390f),
            42f,
            42f,
            accentCard
        )
    }

    private fun drawAvatar(context: Context, canvas: Canvas, avatarId: String) {
        val avatarBitmap = BitmapFactory.decodeResource(
            context.resources,
            AvatarAssets.getAvatarResId(avatarId)
        )
        val avatarSize = 280f
        val centerX = WIDTH / 2f
        val centerY = 500f
        val avatarRect = RectF(
            centerX - avatarSize / 2f,
            centerY - avatarSize / 2f,
            centerX + avatarSize / 2f,
            centerY + avatarSize / 2f
        )

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(26f, 0f, 12f, Color.parseColor("#26000000"))
        }
        canvas.drawCircle(centerX, centerY, avatarSize / 2f + 20f, shadowPaint)

        val shader = BitmapShader(avatarBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(
            avatarRect.width() / avatarBitmap.width,
            avatarRect.height() / avatarBitmap.height
        )
        val scaledWidth = avatarBitmap.width * scale
        val scaledHeight = avatarBitmap.height * scale
        val dx = avatarRect.left + (avatarRect.width() - scaledWidth) / 2f
        val dy = avatarRect.top + (avatarRect.height() - scaledHeight) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawOval(avatarRect, avatarPaint)
        avatarBitmap.recycle()
    }

    private fun drawTexts(
        canvas: Canvas,
        displayName: String,
        bestRecord: BestStudyRecord
    ) {
        val safeName = TextUtils.ellipsize(
            displayName.ifBlank { "Just a User" },
            android.text.TextPaint().apply {
                textSize = 78f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            },
            760f,
            TextUtils.TruncateAt.END
        ).toString()

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6FFFFFF")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SMARTSTUDYTIMER", WIDTH / 2f, 140f, brandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1D2A3A")
            textSize = 78f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(safeName, WIDTH / 2f, 760f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5B6778")
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Best study record", WIDTH / 2f, 835f, subtitlePaint)

        val scoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF617085")
            textSize = 46f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("FOCUS SCORE", WIDTH / 2f, 1065f, scoreLabelPaint)

        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF132238")
            textSize = 184f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(bestRecord.focusScore.toString(), WIDTH / 2f, 1245f, scorePaint)

        val dateLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7A8797")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Completed on ${bestRecord.completedAt}", WIDTH / 2f, 1335f, dateLabelPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Stay focused. Keep your streak alive.", WIDTH / 2f, HEIGHT - 70f, footerPaint)
    }
}
