package io.ente.auth.watch

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// Auth's semantic colors; native watch typography and dimensions remain local.
internal object WatchStyle {
    val accent = Color.rgb(150, 16, 214)
    val warning = Color.rgb(246, 58, 58)
    val secondary = Color.rgb(153, 153, 153)
    val surface = Color.rgb(33, 33, 33)
    val stroke = Color.rgb(62, 62, 62)

    fun background(context: Context, color: Int = surface) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(60, 255, 255, 255)),
        GradientDrawable().apply { setColor(color); cornerRadius = context.dp(20).toFloat() },
        null,
    )
}

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun Context.watchText(value: String, size: Float, secondary: Boolean = false) = TextView(this).apply {
    text = value
    textSize = size
    gravity = Gravity.CENTER
    setTextColor(if (secondary) WatchStyle.secondary else Color.WHITE)
}

internal fun Context.watchButton(value: String, color: Int = WatchStyle.surface, action: () -> Unit) = Button(this).apply {
    text = value
    textSize = 14f
    isAllCaps = false
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setTextColor(Color.WHITE)
    background = WatchStyle.background(context, color)
    minHeight = dp(48)
    minimumHeight = dp(48)
    setPadding(dp(12), dp(8), dp(12), dp(8))
    setOnClickListener { action() }
}

internal fun LinearLayout.addSpaced(view: android.view.View, margin: Int = 8, fullWidth: Boolean = true) {
    addView(view, LinearLayout.LayoutParams(
        if (fullWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(margin) })
}
