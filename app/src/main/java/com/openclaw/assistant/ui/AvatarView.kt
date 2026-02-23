package com.openclaw.assistant.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.openclaw.assistant.R

enum class AvatarState { IDLE, LISTENING, SPEAKING, THINKING }

/**
 * Self-contained floating avatar view.
 * Add to layout as <com.openclaw.assistant.ui.AvatarView />.
 * Internally renders ic_avatar + mouth indicator overlay.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val faceImage: ImageView
    private val mouthBar: View

    private var currentState = AvatarState.IDLE
    private var idleAnimSet: AnimatorSet? = null
    private var activeAnimSet: AnimatorSet? = null
    private var thinkingAnim: ObjectAnimator? = null

    init {
        // Face
        faceImage = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_avatar)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(faceImage)

        // Mouth indicator (shown during SPEAKING)
        val dp = context.resources.displayMetrics.density
        mouthBar = View(context).apply {
            val w = (28 * dp).toInt()
            val h = (6 * dp).toInt()
            layoutParams = LayoutParams(w, h).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = (12 * dp).toInt()
            }
            setBackgroundResource(R.drawable.mouth_shape)
            visibility = GONE
        }
        addView(mouthBar)

        startIdleAnimation()
    }

    fun setState(state: AvatarState) {
        if (state == currentState) return
        currentState = state
        stopAllAnimations()
        when (state) {
            AvatarState.IDLE      -> startIdleAnimation()
            AvatarState.LISTENING -> startListeningAnimation()
            AvatarState.SPEAKING  -> startSpeakingAnimation()
            AvatarState.THINKING  -> startThinkingAnimation()
        }
    }

    fun setAmplitude(amplitude: Float) {
        if (currentState == AvatarState.SPEAKING) {
            mouthBar.scaleY = amplitude.coerceIn(0f, 1f)
        }
    }

    // ── Animations ──────────────────────────────────────────

    private fun startIdleAnimation() {
        faceImage.alpha = 0.88f
        faceImage.clearColorFilter()
        val floatY  = prop(faceImage, "translationY", 0f, -7f).dur(2600)
        val breathX = prop(faceImage, "scaleX",       1f, 1.04f).dur(2600)
        val breathY = prop(faceImage, "scaleY",       1f, 1.04f).dur(2600)
        idleAnimSet = AnimatorSet().apply { playTogether(floatY, breathX, breathY); start() }
    }

    private fun startListeningAnimation() {
        faceImage.setColorFilter(Color.argb(60, 0, 220, 80))
        val px = prop(faceImage, "scaleX", 1f, 1.14f).dur(850)
        val py = prop(faceImage, "scaleY", 1f, 1.14f).dur(850)
        val pa = prop(faceImage, "alpha",  1f, 0.65f).dur(850)
        activeAnimSet = AnimatorSet().apply { playTogether(px, py, pa); start() }
    }

    private fun startSpeakingAnimation() {
        faceImage.setColorFilter(Color.argb(70, 80, 140, 255))
        mouthBar.visibility = VISIBLE
        val mouth = prop(mouthBar,  "scaleY",    0f, 1f).dur(170).linear()
        val nod   = prop(faceImage, "rotationX", 0f, 8f).dur(380)
        activeAnimSet = AnimatorSet().apply { playTogether(mouth, nod); start() }
    }

    private fun startThinkingAnimation() {
        faceImage.setColorFilter(Color.argb(60, 255, 170, 0))
        thinkingAnim = ObjectAnimator.ofFloat(faceImage, "rotation", 0f, 360f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopAllAnimations() {
        idleAnimSet?.cancel(); idleAnimSet = null
        activeAnimSet?.cancel(); activeAnimSet = null
        thinkingAnim?.cancel(); thinkingAnim = null
        faceImage.apply {
            animate().cancel()
            scaleX = 1f; scaleY = 1f; rotation = 0f
            rotationX = 0f; translationY = 0f; alpha = 1f
            clearColorFilter()
        }
        mouthBar.visibility = GONE
    }

    // ── Builder helper ────────────────────────────────────────
    private fun prop(target: Any, property: String, vararg values: Float) =
        ObjectAnimator.ofFloat(target, property, *values).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode  = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

    private fun ObjectAnimator.dur(ms: Long) = apply { duration = ms }
    private fun ObjectAnimator.linear()       = apply { interpolator = LinearInterpolator() }
}
