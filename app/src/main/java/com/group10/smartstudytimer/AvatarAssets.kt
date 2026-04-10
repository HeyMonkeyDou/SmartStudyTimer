package com.group10.smartstudytimer

object AvatarAssets {
    const val CAT = "cat"
    const val CHICKEN = "chicken"
    const val PANDA = "panda"
    const val RABBIT = "rabbit"

    val avatarIds: List<String> = listOf(CAT, CHICKEN, PANDA, RABBIT)

    fun resolveAvatarId(userId: String, avatarId: String?): String {
        val normalizedAvatarId = avatarId?.trim().orEmpty()
        if (normalizedAvatarId in avatarIds) {
            return normalizedAvatarId
        }
        if (userId.isBlank()) {
            return CAT
        }
        val index = Math.floorMod(userId.hashCode(), avatarIds.size)
        return avatarIds[index]
    }

    fun getAvatarResId(avatarId: String): Int {
        return when (avatarId) {
            CAT -> R.drawable.cat
            CHICKEN -> R.drawable.chicken
            PANDA -> R.drawable.panda
            RABBIT -> R.drawable.rabbit
            else -> R.drawable.cat
        }
    }
}
