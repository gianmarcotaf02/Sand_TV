package it.sandtv.app.ui.epg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import it.sandtv.app.R
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.database.entity.EPGProgram
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom EPG Grid View for displaying program guide
 * Optimized for TV remote navigation
 */
class EPGGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Data
    private var channels: List<Channel> = emptyList()
    private var programsByChannel: Map<Long, List<EPGProgram>> = emptyMap()
    
    // Layout constants
    private val channelColumnWidth = 200f
    private val rowHeight = 80f
    private val timeHeaderHeight = 48f
    private val minuteWidth = 4f // 4px per minute = 240px per hour
    
    // Paints
    private val channelPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.card_background)
        style = Paint.Style.FILL
    }
    
    private val channelTextPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 28f
        isAntiAlias = true
    }
    
    private val programPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.brand_secondary)
        style = Paint.Style.FILL
    }
    
    private val programFocusedPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent)
        style = Paint.Style.FILL
    }
    
    private val currentTimePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.error)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    
    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 24f
        isAntiAlias = true
    }
    
    private val timeTextPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 20f
        isAntiAlias = true
    }
    
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_hint)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    
    // Navigation state
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0
    private var scrollOffsetX = 0f
    private var scrollOffsetY = 0f
    
    // Time reference
    private var baseTime: Long = 0L // Start time of the grid
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    // Listener
    var onProgramSelectedListener: ((EPGProgram) -> Unit)? = null
    var onProgramClickedListener: ((EPGProgram) -> Unit)? = null
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Set base time to current hour
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        baseTime = cal.timeInMillis
    }
    
    fun setData(channels: List<Channel>, programs: Map<Long, List<EPGProgram>>) {
        this.channels = channels
        this.programsByChannel = programs
        focusedChannelIndex = 0
        focusedProgramIndex = 0
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (timeHeaderHeight + channels.size * rowHeight).toInt().coerceAtMost(
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw time header
        drawTimeHeader(canvas)
        
        // Draw channels and programs
        channels.forEachIndexed { index, channel ->
            val y = timeHeaderHeight + index * rowHeight - scrollOffsetY
            if (y >= -rowHeight && y <= height) {
                drawChannel(canvas, channel, index, y)
                drawPrograms(canvas, channel, index, y)
            }
        }
        
        // Draw current time indicator
        drawCurrentTimeIndicator(canvas)
    }
    
    private fun drawTimeHeader(canvas: Canvas) {
        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), timeHeaderHeight, channelPaint)
        
        // Time markers every 30 minutes
        for (i in 0..12) { // 6 hours
            val time = baseTime + i * 30 * 60 * 1000L
            val x = channelColumnWidth + (i * 30 * minuteWidth) - scrollOffsetX
            
            if (x >= channelColumnWidth && x <= width) {
                val timeStr = timeFormatter.format(Date(time))
                canvas.drawText(timeStr, x, timeHeaderHeight - 10f, timeTextPaint)
                canvas.drawLine(x, timeHeaderHeight - 5f, x, height.toFloat(), gridPaint)
            }
        }
    }
    
    private fun drawChannel(canvas: Canvas, channel: Channel, index: Int, y: Float) {
        // Channel column background
        val isFocused = index == focusedChannelIndex
        val paint = if (isFocused) programFocusedPaint else channelPaint
        canvas.drawRect(0f, y, channelColumnWidth, y + rowHeight, paint)
        
        // Channel name
        val textY = y + rowHeight / 2 + channelTextPaint.textSize / 3
        canvas.drawText(
            channel.name.take(15),
            16f,
            textY,
            channelTextPaint
        )
    }
    
    private fun drawPrograms(canvas: Canvas, channel: Channel, channelIndex: Int, channelY: Float) {
        val programs = programsByChannel[channel.id] ?: return
        
        programs.forEachIndexed { programIndex, program ->
            val startX = channelColumnWidth + timeToX(program.startTime) - scrollOffsetX
            val endX = channelColumnWidth + timeToX(program.endTime) - scrollOffsetX
            
            if (endX >= channelColumnWidth && startX <= width) {
                val isFocused = channelIndex == focusedChannelIndex && programIndex == focusedProgramIndex
                val paint = if (isFocused) programFocusedPaint else programPaint
                
                val rect = RectF(
                    startX.coerceAtLeast(channelColumnWidth),
                    channelY + 4,
                    endX.coerceAtMost(width.toFloat()),
                    channelY + rowHeight - 4
                )
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                
                // Program title
                val textBounds = Rect()
                textPaint.getTextBounds(program.title, 0, program.title.length, textBounds)
                val maxWidth = rect.width() - 16
                val title = if (textBounds.width() > maxWidth) {
                    program.title.take((maxWidth / textPaint.textSize * 2).toInt()) + "…"
                } else {
                    program.title
                }
                canvas.drawText(
                    title,
                    rect.left + 8,
                    channelY + rowHeight / 2 + textPaint.textSize / 3,
                    textPaint
                )
            }
        }
    }
    
    private fun drawCurrentTimeIndicator(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        val x = channelColumnWidth + timeToX(currentTime) - scrollOffsetX
        
        if (x >= channelColumnWidth && x <= width) {
            canvas.drawLine(x, timeHeaderHeight, x, height.toFloat(), currentTimePaint)
        }
    }
    
    private fun timeToX(time: Long): Float {
        // Use Float division to avoid precision loss
        val diffMs = (time - baseTime).toFloat()
        val diffMinutes = diffMs / 1000f / 60f
        return diffMinutes * minuteWidth
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focusedChannelIndex > 0) {
                    focusedChannelIndex--
                    focusedProgramIndex = 0
                    ensureVisible()
                    notifyProgramSelected()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusedChannelIndex < channels.size - 1) {
                    focusedChannelIndex++
                    focusedProgramIndex = 0
                    ensureVisible()
                    notifyProgramSelected()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (focusedProgramIndex > 0) {
                    focusedProgramIndex--
                    ensureHorizontalVisible()
                    notifyProgramSelected()
                    invalidate()
                } else {
                    scrollOffsetX = (scrollOffsetX - 120).coerceAtLeast(0f)
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val programs = getCurrentChannelPrograms()
                if (focusedProgramIndex < programs.size - 1) {
                    focusedProgramIndex++
                    ensureHorizontalVisible()
                    notifyProgramSelected()
                    invalidate()
                } else {
                    scrollOffsetX += 120
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                getFocusedProgram()?.let { program ->
                    onProgramClickedListener?.invoke(program)
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    private fun getCurrentChannelPrograms(): List<EPGProgram> {
        if (focusedChannelIndex >= channels.size) return emptyList()
        val channel = channels[focusedChannelIndex]
        return programsByChannel[channel.id] ?: emptyList()
    }
    
    private fun getFocusedProgram(): EPGProgram? {
        val programs = getCurrentChannelPrograms()
        return programs.getOrNull(focusedProgramIndex)
    }
    
    private fun ensureVisible() {
        val targetY = focusedChannelIndex * rowHeight
        if (targetY < scrollOffsetY) {
            scrollOffsetY = targetY
        } else if (targetY + rowHeight > scrollOffsetY + height - timeHeaderHeight) {
            scrollOffsetY = targetY + rowHeight - height + timeHeaderHeight
        }
    }
    
    private fun ensureHorizontalVisible() {
        getFocusedProgram()?.let { program ->
            val startX = timeToX(program.startTime)
            val endX = timeToX(program.endTime)
            
            if (startX < scrollOffsetX) {
                scrollOffsetX = startX
            } else if (endX > scrollOffsetX + width - channelColumnWidth) {
                scrollOffsetX = endX - width + channelColumnWidth
            }
        }
    }
    
    private fun notifyProgramSelected() {
        getFocusedProgram()?.let { program ->
            onProgramSelectedListener?.invoke(program)
        }
    }
}
