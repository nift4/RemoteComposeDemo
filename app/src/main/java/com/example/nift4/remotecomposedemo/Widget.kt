package com.example.nift4.remotecomposedemo

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.SizeF
import android.view.LayoutInflater
import com.example.nift4.remotecomposedemo.lib.core.RemoteComposeBuffer
import com.example.nift4.remotecomposedemo.lib.core.RemoteComposeState
import com.example.nift4.remotecomposedemo.lib.core.RemoteContext
import com.example.nift4.remotecomposedemo.lib.core.operations.RootContentBehavior
import com.example.nift4.remotecomposedemo.lib.core.operations.ShaderData
import com.example.nift4.remotecomposedemo.lib.core.operations.Theme
import com.example.nift4.remotecomposedemo.lib.core.operations.paint.PaintBundle
import com.example.nift4.remotecomposedemo.lib.core.operations.utilities.AnimatedFloatExpression

var iTime = 0f
class AgslWidgetProvider : BaseWidgetProvider() {
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		iTime += 0.02f
		super.onUpdate(context, appWidgetManager, appWidgetIds)
	}

	override fun render(context: Context, size: SizeF): RemoteComposeBuffer {
		val state = RemoteComposeState()
		val buf = RemoteComposeBuffer(state)
		buf.header(size.width.toInt(), size.height.toInt(), null)
		// This throws an error but I do not care. I couldn't get the scale stuff working properly.
		buf.setRootContentBehavior(
			RootContentBehavior.NONE,
			RootContentBehavior.ALIGNMENT_START or RootContentBehavior.ALIGNMENT_TOP,
			RootContentBehavior.NONE, RootContentBehavior.NONE
		)
		buf.setTheme(Theme.UNSPECIFIED)
		val shader = state.nextId()
		ShaderData.COMPANION.apply(
			buf.buffer,
			shader,
			// shader courtesy of AkaneTan
			buf.addText("""
				uniform float height;
				uniform float width;
				uniform float iTime;
				
				vec4 main(vec2 fragCoord) {
				    vec2 xy = vec2(0, 0);
				    xy.x = width;
				    xy.y = height;
				    vec2 uv = (2.0 * fragCoord - xy.xy) / min(xy.x, xy.y);
				
				    for (float i = 1.0; i < 10.0; i ++) {
				        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + iTime);
				        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + iTime);
				    }
				    
				    vec4 fragColor = vec4( vec3(0.1) / abs( sin( iTime - uv.y - uv.x ) ), 1.0);
				    
				    return fragColor;
				}
			""".trimIndent()),
			hashMapOf(
				Pair("height", floatArrayOf(size.height)),
				Pair("width", floatArrayOf(size.width)),
				Pair("iTime", floatArrayOf(iTime))
			),
			null, null
		)
		buf.addPaint(PaintBundle().apply {
			//setColor(Color.RED)
			//setLinearGradient(intArrayOf(Color.YELLOW, Color.GREEN), floatArrayOf(0f, size.width), 0f, size.height / 2, size.width, size.height / 2,
			//	Shader.TileMode.REPEAT.ordinal)
			setShader(shader)
		})
		buf.addDrawRect(0f, 0f, size.width, size.height)
		return buf
	}
}

class WidgetProvider : BaseWidgetProvider() {
	override fun render(
		context: Context,
		size: SizeF
	): RemoteComposeBuffer {
		val cv = MyCanvas(size.width.toInt(), size.height.toInt())
		cv.drawView(LayoutInflater.from(context).inflate(R.layout.canvas_test, null))
		return cv.buf
	}
}

// This widget can display correct Unix time for up to one month
class AnimationWidgetProvider : BaseWidgetProvider() {
	override fun render(context: Context, size: SizeF): RemoteComposeBuffer {
		val state = RemoteComposeState()
		val buf = RemoteComposeBuffer(state)
		buf.header(size.width.toInt(), size.height.toInt(), null)
		// This throws an error but I do not care. I couldn't get the scale stuff working properly.
		buf.setRootContentBehavior(
			RootContentBehavior.NONE,
			RootContentBehavior.ALIGNMENT_START or RootContentBehavior.ALIGNMENT_TOP,
			RootContentBehavior.NONE, RootContentBehavior.NONE
		)
		buf.setTheme(Theme.UNSPECIFIED)
		val colon = buf.addText(":")
		val realHours = buf.addAnimatedFloat(RemoteContext.FLOAT_TIME_IN_HR, 60f,
			AnimatedFloatExpression.MOD)
		val realMinutes = buf.addAnimatedFloat(RemoteContext.FLOAT_TIME_IN_MIN, 60f,
			AnimatedFloatExpression.MOD)
		val hour = buf.createTextFromFloat(realHours, 2, 0, RemoteComposeBuffer.PAD_PRE_ZERO)
		val minute = buf.createTextFromFloat(realMinutes, 2, 0, RemoteComposeBuffer.PAD_PRE_ZERO)
		val realSeconds = buf.addAnimatedFloat(RemoteContext.FLOAT_TIME_IN_SEC, 60f,
			AnimatedFloatExpression.MOD)
		val second = buf.createTextFromFloat(realSeconds, 2, 0, RemoteComposeBuffer.PAD_PRE_ZERO)
		val hourD = buf.textMerge(hour, colon)
		val minuteD = buf.textMerge(minute, colon)
		val hourMin = buf.textMerge(hourD, minuteD)
		val time = buf.textMerge(hourMin, second)
		buf.addPaint(PaintBundle().apply {
			setTextStyle(0, Typeface.DEFAULT.weight, false)
			setTextSize(60f)
			setColor(Color.WHITE)
		})
		buf.drawTextAnchored(time, 50f, 0f, -1f, 1f, 0)
		return buf
	}
}