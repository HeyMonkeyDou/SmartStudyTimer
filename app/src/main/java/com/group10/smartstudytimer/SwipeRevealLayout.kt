package com.group10.smartstudytimer

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A FrameLayout that reveals action buttons when the user swipes left.
 *
 * Child layout order expected in XML:
 *   Child 0 – actions container (behind, right-aligned)
 *   Child 1 – front container  (normal item content, slides left on swipe)
 */
class SwipeRevealLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var actionsView: View? = null
    private var frontView: View? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var isOpen = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var isDragging = false

    /** Called whenever this item opens (used by the fragment to close the previous one). */
    var onOpenListener: (() -> Unit)? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onFinishInflate() {
        super.onFinishInflate()
        check(childCount >= 2) { "SwipeRevealLayout needs exactly 2 children" }
        actionsView = getChildAt(0)
        frontView   = getChildAt(1)
    }

    private val actionsWidth: Int get() = actionsView?.width ?: 0

    // ── Public API ───────────────────────────────────────────────────────────

    fun isOpen() = isOpen

    fun open(animate: Boolean) {
        isOpen = true
        animateFront(-actionsWidth.toFloat(), animate)
        onOpenListener?.invoke()
    }

    fun close(animate: Boolean) {
        isOpen = false
        animateFront(0f, animate)
    }

    // ── Touch handling ───────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startRawX  = ev.rawX
                startRawY  = ev.rawY
                isDragging = false

                if (isOpen) {
                    // When open: intercept taps on the front area so we can close the menu.
                    // Taps on the actions area pass through to the action buttons.
                    val frontRight = (width + (frontView?.translationX ?: 0f)).toInt()
                    if (ev.x.toInt() < frontRight) return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.rawX - startRawX)
                val dy = abs(ev.rawY - startRawY)
                if (dx > touchSlop && dx > dy) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val fv = frontView ?: return false
        val aw = actionsWidth

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startRawX  = ev.rawX
                startRawY  = ev.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startRawX
                val dy = abs(ev.rawY - startRawY)
                if (!isDragging && abs(dx) > touchSlop && abs(dx) > dy) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isDragging && aw > 0) {
                    val base = if (isOpen) -aw.toFloat() else 0f
                    fv.translationX = (base + dx).coerceIn(-aw.toFloat(), 0f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val dx = ev.rawX - startRawX
                if (isDragging && aw > 0) {
                    val threshold = aw * 0.4f
                    if (isOpen) {
                        if (dx > threshold) close(true) else open(true)
                    } else {
                        if (-dx > threshold) open(true) else close(true)
                    }
                    isDragging = false
                } else if (isOpen) {
                    // Tap on front area when open → close menu
                    close(true)
                }
            }
        }
        return true
    }

    // ── Animation ────────────────────────────────────────────────────────────

    private fun animateFront(target: Float, animate: Boolean) {
        val fv = frontView ?: return
        if (animate) {
            ValueAnimator.ofFloat(fv.translationX, target).apply {
                duration = 200
                addUpdateListener { fv.translationX = it.animatedValue as Float }
                start()
            }
        } else {
            fv.translationX = target
        }
    }
}
