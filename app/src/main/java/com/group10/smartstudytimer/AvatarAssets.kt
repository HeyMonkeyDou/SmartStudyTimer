package com.group10.smartstudytimer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import java.io.InputStream

object AvatarAssets {
    private const val AVATARS_DIR = "avatars"
    private const val DEFAULT_AVATAR_ID = "cat"
    private val fallbackAvatarIds = listOf("cat", "chicken", "panda", "rabbit")

    fun listAvatarIds(context: Context): List<String> {
        val assetFileNames = runCatching { context.assets.list(AVATARS_DIR).orEmpty().toList() }
            .getOrDefault(emptyList())
        val avatarIds = assetFileNames
            .mapNotNull { fileName ->
                fileName.substringBeforeLast('.', "").trim().takeIf { it.isNotBlank() }
            }
            .distinct()
            .sorted()
        return avatarIds.ifEmpty { fallbackAvatarIds }
    }

    fun defaultAvatarId(context: Context): String {
        return listAvatarIds(context).firstOrNull() ?: DEFAULT_AVATAR_ID
    }

    fun resolveAvatarId(userId: String, avatarId: String?): String {
        val normalizedAvatarId = avatarId?.trim().orEmpty()
        if (normalizedAvatarId.isNotBlank()) {
            return normalizedAvatarId
        }
        if (userId.isBlank()) {
            return DEFAULT_AVATAR_ID
        }
        val index = Math.floorMod(userId.hashCode(), fallbackAvatarIds.size)
        return fallbackAvatarIds[index]
    }

    fun bindAvatar(imageView: ImageView, avatarId: String) {
        val drawable = getAvatarDrawable(imageView.context, avatarId)
        imageView.setImageDrawable(drawable)
    }

    fun getAvatarDrawable(context: Context, avatarId: String): Drawable {
        val bitmap = getAvatarBitmap(context, avatarId)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun getAvatarBitmap(context: Context, avatarId: String): Bitmap {
        val resolvedAvatarId = avatarId.trim().ifBlank { defaultAvatarId(context) }
        openAvatarStream(context, resolvedAvatarId).use { input ->
            if (input != null) {
                return requireNotNull(BitmapFactory.decodeStream(input))
            }
        }

        val fallbackId = defaultAvatarId(context)
        openAvatarStream(context, fallbackId).use { input ->
            if (input != null) {
                return requireNotNull(BitmapFactory.decodeStream(input))
            }
        }

        error("No avatar assets found in $AVATARS_DIR")
    }

    private fun openAvatarStream(context: Context, avatarId: String): InputStream? {
        val fileName = runCatching {
            context.assets.list(AVATARS_DIR)
                .orEmpty()
                .firstOrNull { it.substringBeforeLast('.', "") == avatarId }
        }.getOrNull() ?: return null

        return runCatching { context.assets.open("$AVATARS_DIR/$fileName") }.getOrNull()
    }
}
