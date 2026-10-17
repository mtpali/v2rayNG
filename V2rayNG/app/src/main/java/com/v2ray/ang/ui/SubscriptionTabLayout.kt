package com.v2ray.ang.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.tabs.TabLayout
import com.v2ray.ang.R

/** Uses Android's long-press timing for actions; a brief hold then drag moves by GUID. */
class SubscriptionTabLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TabLayout(context, attrs) {
    var onSubscriptionActions: ((String) -> Unit)? = null
    var onSubscriptionMove: ((String, String) -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var source: String? = null
    private var target: String? = null
    private var downX = 0f
    private var downY = 0f
    private var armed = false
    private var dragging = false
    private var actionsShown = false
    private var edgeDirection = 0

    /** TalkBack and keyboard expose the same operations as the touch long press. */
    fun bindActions(tab: Tab) {
        if ((tab.tag as? String).isNullOrBlank()) return
        ViewCompat.replaceAccessibilityAction(tab.view,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            context.getString(R.string.subscription_actions)) { _, _ ->
            (tab.tag as? String)?.let { onSubscriptionActions?.invoke(it) }
            true
        }
        tab.view.setOnKeyListener { _, key, event ->
            if (key != KeyEvent.KEYCODE_MENU && !(key == KeyEvent.KEYCODE_F10 && event.isShiftPressed)) false
            else {
                if (event.action == KeyEvent.ACTION_UP) (tab.tag as? String)?.let { onSubscriptionActions?.invoke(it) }
                true
            }
        }
    }
    private val arm = Runnable {
        if (source != null) armed = true
    }
    private val showActions = Runnable {
        val id = source
        if (id != null && !dragging) {
            armed = false
            actionsShown = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            onSubscriptionActions?.invoke(id)
        }
    }
    private val scrollDrag = object : Runnable {
        override fun run() {
            if (!dragging || edgeDirection == 0) return
            scrollBy((edgeDirection * 12 * resources.displayMetrics.density).toInt(), 0)
            val location = IntArray(2).also { getLocationOnScreen(it) }
            updateTarget(location[0] + if (edgeDirection < 0) 1f else width - 1f)
            postDelayed(this, 32)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (actionsShown) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) resetGesture()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                source = tabAt(event.rawX)?.tag as? String
                if (source.isNullOrBlank()) source = null
                downX = event.rawX; downY = event.rawY
                if (source != null) {
                    postDelayed(arm, ViewConfiguration.getTapTimeout().toLong())
                    postDelayed(showActions, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val moved = kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop
                if (armed && source != null && moved) {
                    dragging = true
                    removeCallbacks(showActions)
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateTarget(event.rawX)
                    return true
                }
                if (moved && !armed) resetGesture()
            }
            MotionEvent.ACTION_UP -> {
                if (actionsShown) {
                    resetGesture()
                    return true
                }
                resetGesture()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> resetGesture()
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (actionsShown) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) resetGesture()
            return true
        }
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                updateTarget(event.rawX)
                val edge = 40 * resources.displayMetrics.density
                val next = when { event.x < edge -> -1; event.x > width - edge -> 1; else -> 0 }
                if (next != edgeDirection) {
                    edgeDirection = next
                    removeCallbacks(scrollDrag)
                    if (next != 0) post(scrollDrag)
                }
            }
            MotionEvent.ACTION_UP -> {
                val from = source; val to = target
                resetGesture()
                if (from != null && to != null && from != to) onSubscriptionMove?.invoke(from, to)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> resetGesture()
        }
        return true
    }

    private fun tabAt(rawX: Float): Tab? = (0 until tabCount).mapNotNull(::getTabAt).firstOrNull {
        val rect = Rect()
        it.view.getGlobalVisibleRect(rect) && rawX >= rect.left && rawX <= rect.right
    }

    private fun updateTarget(rawX: Float) {
        val next = tabAt(rawX)?.tag as? String
        if (!next.isNullOrBlank() && next != target) {
            target = next
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        (0 until tabCount).mapNotNull(::getTabAt).forEach {
            it.view.alpha = when (it.tag) { source -> 0.5f; target -> 0.75f; else -> 1f }
        }
    }

    private fun resetGesture() {
        removeCallbacks(arm); removeCallbacks(showActions); removeCallbacks(scrollDrag)
        (0 until tabCount).mapNotNull(::getTabAt).forEach { it.view.alpha = 1f }
        source = null; target = null; armed = false; dragging = false; actionsShown = false; edgeDirection = 0
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() { resetGesture(); super.onDetachedFromWindow() }
}
