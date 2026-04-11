package com.group10.smartstudytimer

object ShareBackgroundAssets {
    private val backgroundResIds = listOf(
        R.drawable.share_bg_blue,
        R.drawable.share_bg_green,
        R.drawable.share_bg_gold,
        R.drawable.share_bg_pink
    )

    fun getRandomBackgroundResId(): Int = backgroundResIds.random()
}
