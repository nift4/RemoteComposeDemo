package com.example.nift4.remotecomposedemo

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import com.example.nift4.remotecomposedemo.lib.core.RemoteComposeBuffer
import org.lsposed.hiddenapibypass.HiddenApiBypass

abstract class BaseWidgetProvider : AppWidgetProvider() {
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		for (appWidgetId in appWidgetIds) {
			val views = appWidgetManager.createWidgetInSizes(context, appWidgetId) {
				if (it == null) {
					return@createWidgetInSizes RemoteViews(context.packageName, R.layout.unsupported_launcher)
				}
				RemoteViews(render(context, it).toDrawInstructions())
			}
			appWidgetManager.updateAppWidget(appWidgetId, views)
		}
	}

	override fun onAppWidgetOptionsChanged(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
		newOptions: Bundle?
	) {
		onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
	}

	fun update(context: Context) {
		val awm = AppWidgetManager.getInstance(context)
		onUpdate(context, awm, awm.getAppWidgetIds(ComponentName(context, this.javaClass)))
	}

	abstract fun render(context: Context, size: SizeF): RemoteComposeBuffer
}

fun RemoteComposeBuffer.toDrawInstructions() =
	RemoteViews.DrawInstructions.Builder(listOf(buffer.buffer)).build()

fun AppWidgetManager.createWidgetInSizes(context: Context, appWidgetId: Int, creator: (SizeF?) -> RemoteViews): RemoteViews {
	val sizes =
		BundleCompat.getParcelableArrayList<SizeF>(
			getAppWidgetOptions(appWidgetId),
			AppWidgetManager.OPTION_APPWIDGET_SIZES,
			SizeF::class.java
		).let { if (it.isNullOrEmpty()) null else it }
	return if (!sizes.isNullOrEmpty()) {
		RemoteViews(sizes.associateWith { creator(SizeF(it.width.dpToPx(context), it.height.dpToPx(context))) })
	} else creator(null)
}

private fun Float.dpToPx(context: Context) = TypedValue.applyDimension(
	TypedValue.COMPLEX_UNIT_DIP,
	this,
	context.resources.displayMetrics
)

class MainActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val h = Handler(Looper.getMainLooper())
		val providers = listOf(WidgetProvider(), AgslWidgetProvider(), AnimationWidgetProvider())
		h.post(object : Runnable {
			override fun run() {
				providers.forEach { it.update(this@MainActivity) }
				h.postDelayed(this, 1000 / 60) // 60fps
			}
		})
		/*setContentView(RemoteComposePlayer(this).apply {
			setDebug(1)
			setTheme(Theme.UNSPECIFIED)
			val stBuf: RemoteComposeBuffer = renderComposeWidget(SizeF(100f, 100f))
			doOnLayout {
				setDocument(RemoteComposeDocument(ByteArrayInputStream(stBuf.buffer.buffer)))
			}
		})*/
		//finish()
	}
}

class Application : Application() {
	override fun onCreate() {
		super.onCreate()
		HiddenApiBypass.addHiddenApiExemptions("")
	}
}