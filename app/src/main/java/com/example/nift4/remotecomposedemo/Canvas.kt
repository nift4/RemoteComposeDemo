package com.example.nift4.remotecomposedemo

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DrawFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Matrix44
import android.graphics.Mesh
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.text.MeasuredText
import android.util.Log
import android.view.View
import com.example.nift4.remotecomposedemo.lib.core.RemoteComposeBuffer
import com.example.nift4.remotecomposedemo.lib.core.RemoteComposeState
import com.example.nift4.remotecomposedemo.lib.core.operations.DrawTextAnchored
import com.example.nift4.remotecomposedemo.lib.core.operations.RootContentBehavior
import com.example.nift4.remotecomposedemo.lib.core.operations.Theme
import com.example.nift4.remotecomposedemo.lib.core.operations.paint.PaintBundle

/*
 * A Canvas that renders Views to RemoteComposeBuffer. Or crashes, most of the time.
 *
 * This allows you to do pretty cool things already. This doesn't do the awesome variable and
 * animations system justice, though. Please go write RemoteCompose-optimized widgets!
 */
class MyCanvas(val w: Int, val h: Int) : Canvas() {
	private val unmodifiedPaint = Paint().apply {
		if (colorFilter !is PorterDuffColorFilter?)
			unsupported("The default paint is using a weird color filter?")
		if (shader != null && shader !is RuntimeShader && shader !is LinearGradient &&
			shader !is SweepGradient && shader !is RadialGradient)
			unsupported("The default paint is using a weird shader?")
	}
	val st = RemoteComposeState()
	val buf = RemoteComposeBuffer(st)
	private val region = Region(0, 0, w, h)
	private val boundsCache = RectF()

	init {
		buf.header(w, h, null)
		buf.setTheme(Theme.UNSPECIFIED)
		// This throws an error but I do not care. I couldn't get the scale stuff working properly.
		buf.setRootContentBehavior(RootContentBehavior.NONE,
			RootContentBehavior.ALIGNMENT_START or RootContentBehavior.ALIGNMENT_TOP,
			RootContentBehavior.NONE, RootContentBehavior.NONE)
	}

	private fun withPaint(paint: Paint?, block: () -> Unit) {
		if (paint != null)
			buf.addPaint(PaintBundle().also {
				it.applyPaintIfDifferent(paint, unmodifiedPaint)
			})
		block()
		if (paint != null)
			buf.addPaint(PaintBundle().also {
				it.applyPaintIfDifferent(unmodifiedPaint, paint)
			})
	}

	private fun PaintBundle.applyPaintIfDifferent(paint: Paint, unmodifiedPaint: Paint) {
		// These paint flags are being checked using helper methods
		val supportedPaintFlags = Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or
				Paint.DITHER_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.UNDERLINE_TEXT_FLAG or
				Paint.SUBPIXEL_TEXT_FLAG or Paint.FAKE_BOLD_TEXT_FLAG or Paint.STRIKE_THRU_TEXT_FLAG
		if (paint.color != unmodifiedPaint.color)
			this.setColor(paint.color)
		if (paint.alpha != unmodifiedPaint.alpha)
			this.setAlpha(paint.alpha.toFloat())
		if (paint.strokeWidth != unmodifiedPaint.strokeWidth)
			this.setStrokeWidth(paint.strokeWidth)
		if (paint.strokeMiter != unmodifiedPaint.strokeMiter)
			this.setStrokeMiter(paint.strokeMiter)
		if (paint.strokeCap != unmodifiedPaint.strokeCap)
			this.setStrokeCap(paint.strokeCap.ordinal)
		if (paint.strokeJoin != unmodifiedPaint.strokeJoin)
			this.setStrokeJoin(paint.strokeJoin.ordinal)
		if (paint.isAntiAlias != unmodifiedPaint.isAntiAlias)
			this.setAntiAlias(paint.isAntiAlias)
		if (paint.isFilterBitmap != unmodifiedPaint.isFilterBitmap)
			this.setFilterBitmap(paint.isFilterBitmap)
		if (paint.blendMode != unmodifiedPaint.blendMode)
			this.setBlendMode(paint.blendMode?.ordinal ?: PaintBundle.BLEND_MODE_NULL)
		if (paint.style != unmodifiedPaint.style)
			this.setStyle(paint.style.ordinal)
		if (paint.textSize != unmodifiedPaint.textSize)
			this.setTextSize(paint.textSize)
		if (paint.typeface != unmodifiedPaint.typeface)
			this.setTextStyle(when {
				paint.typeface == null -> 0
				paint.typeface.systemFontFamilyName == Typeface.DEFAULT.systemFontFamilyName
						&& Typeface.DEFAULT.systemFontFamilyName != null -> 0
				paint.typeface.systemFontFamilyName?.startsWith("sans-serif") == true -> 1
				paint.typeface.systemFontFamilyName?.startsWith("serif") == true -> 2
				paint.typeface.systemFontFamilyName?.startsWith("monospace") == true -> 3
				else -> unsupported("this typeface (${paint.typeface.systemFontFamilyName}) is not supported")
			}, paint.typeface?.weight ?: Typeface.DEFAULT.weight, paint.typeface?.isItalic == true)
		if (paint.colorFilter != unmodifiedPaint.colorFilter) {
			if (paint.colorFilter is PorterDuffColorFilter?) {
				val colorFilter = paint.colorFilter as PorterDuffColorFilter?
				if (colorFilter != null) {
					val color = colorFilter.javaClass.declaredMethods.first { it.name == "getColor" }
						.invoke(colorFilter) as Int
					val mode = colorFilter.javaClass.declaredMethods.first { it.name == "getMode" }
						.invoke(colorFilter) as PorterDuff.Mode
					this.setColorFilter(color, mode.ordinal)
				} else {
					// effectively null
					this.setColorFilter(Color.BLACK, PorterDuff.Mode.DST.ordinal)
				}
			} else
				unsupported("changing colorFilter to ${paint.colorFilter.javaClass.name} is not supported")
		}
		if (paint.shader != unmodifiedPaint.shader) {
			if (paint.shader is RuntimeShader?) {
				val shader = paint.shader as RuntimeShader?
				if (shader != null) {
					/* we can't use this approach because we can't get the data out of RuntimeShader
					val id = st.nextId()
					ShaderData.COMPANION.apply(buf.buffer,
						id,
						buf.addText("agsl source here"),
						null, <-- float uniforms
						null, <-- int uniforms
						null) <-- bitmap uniforms
					this.setShader(id)
					*/
					unsupported("RuntimeShader is not supported")
				} else this.setShader(0)
			} else if (paint.shader is LinearGradient) {
				val shader = paint.shader as LinearGradient
				val mX0 = shader.javaClass.declaredFields.first { it.name == "mX0" }
				mX0.isAccessible = true
				val x0 = mX0.get(shader) as Float
				val mX1 = shader.javaClass.declaredFields.first { it.name == "mX1" }
				mX1.isAccessible = true
				val x1 = mX1.get(shader) as Float
				val mY0 = shader.javaClass.declaredFields.first { it.name == "mY0" }
				mY0.isAccessible = true
				val y0 = mY0.get(shader) as Float
				val mY1 = shader.javaClass.declaredFields.first { it.name == "mY1" }
				mY1.isAccessible = true
				val y1 = mY1.get(shader) as Float
				val mPositions = shader.javaClass.declaredFields.first { it.name == "mPositions" }
				mPositions.isAccessible = true
				val positions = mPositions.get(shader) as FloatArray?
				val mTileMode = shader.javaClass.declaredFields.first { it.name == "mTileMode" }
				mTileMode.isAccessible = true
				val tileMode = mTileMode.get(shader) as Shader.TileMode
				val mColorLongs = shader.javaClass.declaredFields.first { it.name == "mColorLongs" }
				mColorLongs.isAccessible = true
				val colors = (mColorLongs.get(shader) as LongArray).map { Color.valueOf(it).toArgb() }
				this.setLinearGradient(colors.toIntArray(), positions, x0, y0, x1, y1, tileMode.ordinal)
			} else if (paint.shader is RadialGradient)
				unsupported("RadialGradient is currently not implemented") // TODO
			else if (paint.shader is SweepGradient)
				unsupported("SweepGradient is currently not implemented") // TODO
			else
				unsupported("changing shader is not supported")
		}
		if (paint.textLocale != unmodifiedPaint.textLocale)
			unsupported("changing textLocale is not supported")
		if (paint.wordSpacing != unmodifiedPaint.wordSpacing)
			unsupported("changing wordSpacing is not supported")
		if (paint.endHyphenEdit != unmodifiedPaint.endHyphenEdit)
			unsupported("changing endHyphenEdit is not supported")
		if (paint.isDither != unmodifiedPaint.isDither)
			unsupported("changing isDither is not supported")
		if (paint.isUnderlineText != unmodifiedPaint.isUnderlineText)
			unsupported("changing isUnderlineText is not supported")
		if (paint.isLinearText != unmodifiedPaint.isLinearText)
			unsupported("changing isLinearText is not supported")
		if (paint.isElegantTextHeight != unmodifiedPaint.isElegantTextHeight)
			unsupported("changing isElegantTextHeight is not supported")
		if (paint.isFakeBoldText != unmodifiedPaint.isFakeBoldText)
			unsupported("changing isFakeBoldText is not supported")
		if (paint.isStrikeThruText != unmodifiedPaint.isStrikeThruText)
			unsupported("changing isStrikeThruText is not supported")
		if (paint.isSubpixelText != unmodifiedPaint.isSubpixelText)
			unsupported("changing isSubpixelText is not supported")
		if (paint.fontFeatureSettings != unmodifiedPaint.fontFeatureSettings)
			unsupported("changing fontFeatureSettings is not supported")
		if (paint.fontVariationSettings != unmodifiedPaint.fontVariationSettings)
			unsupported("changing fontVariationSettings is not supported")
		if (paint.hinting != unmodifiedPaint.hinting)
			unsupported("changing hinting is not supported")
		// if (paint.letterSpacing != unmodifiedPaint.letterSpacing)
		//	unsupported("changing letterSpacing is not supported")
		if (paint.maskFilter != unmodifiedPaint.maskFilter)
			unsupported("changing maskFilter is not supported")
		if (paint.pathEffect != unmodifiedPaint.pathEffect)
			unsupported("changing pathEffect is not supported")
		if (paint.startHyphenEdit != unmodifiedPaint.startHyphenEdit)
			unsupported("changing startHyphenEdit is not supported")
		if (paint.textScaleX != unmodifiedPaint.textScaleX)
			unsupported("changing textScaleX is not supported")
		if (paint.textSkewX != unmodifiedPaint.textSkewX)
			unsupported("changing textSkewX is not supported")
		if (paint.shadowLayerRadius != unmodifiedPaint.shadowLayerRadius)
			unsupported("changing shadowLayerRadius is not supported")
		if (paint.shadowLayerColorLong != unmodifiedPaint.shadowLayerColorLong)
			unsupported("changing shadowLayerColorLong is not supported")
		if (paint.shadowLayerDx != unmodifiedPaint.shadowLayerDx)
			unsupported("changing shadowLayerDx is not supported")
		if (paint.shadowLayerDy != unmodifiedPaint.shadowLayerDy)
			unsupported("changing shadowLayerDy is not supported")
		if (paint.flags and supportedPaintFlags.inv() != unmodifiedPaint.flags and supportedPaintFlags.inv())
			unsupported("set unsupported flags ${paint.flags} on paint")
	}

	fun drawView(v: View) {
		if (v.isAttachedToWindow)
			throw IllegalArgumentException("this view is attached to a window!")
		v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
		v.layout(0, 0, width, height)
		v.draw(this)
	}

	override fun clipOutPath(path: Path): Boolean {
		unsupported("clipOutPath is not supported")
	}

	override fun clipOutRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
		unsupported("clipOutRect is not supported")
	}

	override fun clipOutRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
		return clipOutRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
	}

	override fun clipOutRect(rect: Rect): Boolean {
		return clipOutRect(rect.left, rect.top, rect.right, rect.bottom)
	}

	override fun clipOutRect(rect: RectF): Boolean {
		return clipOutRect(rect.left, rect.top, rect.right, rect.bottom)
	}

	override fun clipOutShader(shader: Shader) {
		unsupported("clipOutShader is not supported")
	}

	@Suppress("deprecation")
	override fun clipPath(path: Path): Boolean {
		buf.addClipPath(buf.addPathData(Path(path)))
		path.computeBounds(boundsCache, true)
		return region.op(boundsCache.left.toInt(), boundsCache.top.toInt(),
			boundsCache.right.toInt(), boundsCache.bottom.toInt(), Region.Op.INTERSECT)
	}

	override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
		buf.addClipRect(left, top, right, bottom)
		return region.op(left.toInt(), top.toInt(), right.toInt(), bottom.toInt(), Region.Op.INTERSECT)
	}

	override fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
		return clipRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
	}

	override fun clipRect(rect: Rect): Boolean {
		return clipRect(rect.left, rect.top, rect.right, rect.bottom)
	}

	override fun clipRect(rect: RectF): Boolean {
		return clipRect(rect.left, rect.top, rect.right, rect.bottom)
	}

	override fun clipShader(shader: Shader) {
		unsupported("clipShader is not supported")
	}

	override fun concat(m: Matrix44?) {
		unsupported("concat is not supported")
	}

	override fun concat(matrix: Matrix?) {
		unsupported("concat is not supported")
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun clipPath(path: Path, op: Region.Op): Boolean {
		return if (op == Region.Op.INTERSECT)
			clipPath(path)
		else if (op == Region.Op.DIFFERENCE)
			clipOutPath(path)
		else
			unsupported("deprecated clip ops are not supported")
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun clipRect(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		op: Region.Op
	): Boolean {
		return if (op == Region.Op.INTERSECT)
			clipRect(left, top, right, bottom)
		else if (op == Region.Op.DIFFERENCE)
			clipOutRect(left, top, right, bottom)
		else
			unsupported("deprecated clip ops are not supported")
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun clipRect(rect: Rect, op: Region.Op): Boolean {
		return clipRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), op)
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun clipRect(rect: RectF, op: Region.Op): Boolean {
		return clipRect(rect.left, rect.top, rect.right, rect.bottom, op)
	}

	override fun disableZ() {
		// do nothing
	}

	override fun drawARGB(a: Int, r: Int, g: Int, b: Int) {
		drawPaint(Paint().also { it.color = Color.argb(a, r, g, b) })
	}

	override fun drawArc(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		startAngle: Float,
		sweepAngle: Float,
		useCenter: Boolean,
		paint: Paint
	) {
		if (useCenter)
			unsupported("drawArc with useCenter true is not supported")
		withPaint(paint) {
			buf.addDrawArc(left, top, right, bottom, startAngle, sweepAngle)
		}
	}

	override fun drawArc(
		oval: RectF,
		startAngle: Float,
		sweepAngle: Float,
		useCenter: Boolean,
		paint: Paint
	) {
		drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, paint)
	}

	override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
		withPaint(paint) {
			buf.addDrawBitmap(bitmap, left, top, left + bitmap.width, top + bitmap.height, null)
		}
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun drawBitmap(
		colors: IntArray,
		offset: Int,
		stride: Int,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		hasAlpha: Boolean,
		paint: Paint?
	) {
		drawBitmap(colors, offset, stride, x.toFloat(), y.toFloat(), width, height, hasAlpha, paint)
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun drawBitmap(
		colors: IntArray,
		offset: Int,
		stride: Int,
		x: Float,
		y: Float,
		width: Int,
		height: Int,
		hasAlpha: Boolean,
		paint: Paint?
	) {
		drawBitmap(Bitmap.createBitmap(colors, offset, stride, width, height, Bitmap.Config.ARGB_8888)
			.also { it.setHasAlpha(hasAlpha) }, x, y, paint)
	}

	override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
		unsupported("the matrix drawBitmap overload is not supported")
	}

	override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
		withPaint(paint) {
			buf.drawBitmap(
				bitmap, bitmap.width, bitmap.height, src?.left ?: 0, src?.top ?: 0,
				src?.right ?: bitmap.width, src?.bottom ?: bitmap.height, dst.left,
				dst.top, dst.right, dst.bottom, null
			)
		}
	}

	override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
		withPaint(paint) {
			buf.drawBitmap(
				bitmap, bitmap.width, bitmap.height, src?.left ?: 0, src?.top ?: 0,
				src?.right ?: bitmap.width, src?.bottom ?: bitmap.height, dst.left.toInt(),
				dst.top.toInt(), dst.right.toInt(), dst.bottom.toInt(), null
			)
		}
	}

	override fun drawBitmapMesh(
		bitmap: Bitmap,
		meshWidth: Int,
		meshHeight: Int,
		verts: FloatArray,
		vertOffset: Int,
		colors: IntArray?,
		colorOffset: Int,
		paint: Paint?
	) {
		unsupported("drawBitmapMesh is not supported")
	}

	override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
		withPaint(paint) {
			buf.addDrawCircle(cx, cy, radius)
		}
	}

	override fun drawColor(color: Int) {
		drawColor(color, PorterDuff.Mode.SRC_OVER)
	}

	override fun drawColor(color: Int, mode: BlendMode) {
		drawPaint(Paint().also {
			it.setColor(color)
			it.blendMode = mode
		})
	}

	override fun drawColor(color: Int, mode: PorterDuff.Mode) {
		drawPaint(Paint().also {
			it.setColor(color)
			it.xfermode = PorterDuffXfermode(mode)
		})
	}

	override fun drawColor(color: Long, mode: BlendMode) {
		drawPaint(Paint().also {
			it.setColor(color)
			it.blendMode = mode
		})
	}

	override fun drawColor(color: Long) {
		drawPaint(Paint().also {
			it.setColor(color)
			it.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
		})
	}

	override fun drawDoubleRoundRect(
		outer: RectF,
		outerRadii: FloatArray,
		inner: RectF,
		innerRadii: FloatArray,
		paint: Paint
	) {
		unsupported("this overload of drawDoubleRoundRect is not supported")
	}

	override fun drawDoubleRoundRect(
		outer: RectF,
		outerRx: Float,
		outerRy: Float,
		inner: RectF,
		innerRx: Float,
		innerRy: Float,
		paint: Paint
	) {
		drawRoundRect(outer, outerRx, outerRy, paint)
		drawRoundRect(inner, innerRx, innerRy, paint)
	}

	override fun drawGlyphs(
		glyphIds: IntArray,
		glyphIdOffset: Int,
		positions: FloatArray,
		positionOffset: Int,
		glyphCount: Int,
		font: Font,
		paint: Paint
	) {
		unsupported("drawGlyphs is not supported")
	}

	override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
		withPaint(paint) {
			buf.addDrawLine(startX, startY, stopX, stopY)
		}
	}

	override fun drawLines(pts: FloatArray, paint: Paint) {
		drawLines(pts, 0, pts.size, paint)
	}

	override fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
		for (i in 0..<(count / 4)) {
			drawLine(pts[offset + i * 4], pts[offset + i * 4 + 1], pts[offset + i * 4 + 2], pts[offset + i * 4 + 3], paint)
		}
	}

	override fun drawMesh(mesh: Mesh, blendMode: BlendMode?, paint: Paint) {
		unsupported("drawMesh is not supported")
	}

	override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		withPaint(paint) {
			buf.addDrawOval(left, top, right, bottom)
		}
	}

	override fun drawOval(oval: RectF, paint: Paint) {
		drawOval(oval.left, oval.top, oval.right, oval.bottom, paint)
	}

	override fun drawPaint(paint: Paint) {
		drawRect(0f, 0f, Float.MAX_VALUE, Float.MAX_VALUE, paint)
	}

	override fun drawPatch(patch: NinePatch, dst: Rect, paint: Paint?) {
		unsupported("drawPatch is not supported")
	}

	override fun drawPatch(patch: NinePatch, dst: RectF, paint: Paint?) {
		unsupported("drawPatch is not supported")
	}

	override fun drawPath(path: Path, paint: Paint) {
		withPaint(paint) {
			buf.addDrawPath(Path(path))
		}
	}

	override fun drawPicture(picture: Picture) {
		unsupported("drawPicture is not supported")
	}

	override fun drawPicture(picture: Picture, dst: Rect) {
		unsupported("drawPicture is not supported")
	}

	override fun drawPicture(picture: Picture, dst: RectF) {
		unsupported("drawPicture is not supported")
	}

	override fun drawPoint(x: Float, y: Float, paint: Paint) {
		if (paint.strokeCap == Paint.Cap.ROUND)
			drawCircle(x, y, paint.strokeWidth, paint)
		else
			drawRect(x, y, x + paint.strokeWidth, y + paint.strokeWidth, paint)
	}

	override fun drawPoints(pts: FloatArray, paint: Paint) {
		drawPoints(pts, 0, pts.size, paint)
	}

	override fun drawPoints(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
		for (i in 0..<(count shl 1)) {
			drawPoint(pts[offset + (i shr 1)], pts[offset + (i shr 1) + 1], paint)
		}
	}

	override fun drawRGB(r: Int, g: Int, b: Int) {
		drawPaint(Paint().also { it.color = Color.rgb(r, g, b) })
	}

	override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
		withPaint(paint) {
			buf.addDrawRect(left, top, right, bottom)
		}
	}

	override fun drawRect(r: Rect, paint: Paint) {
		drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), paint)
	}

	override fun drawRect(rect: RectF, paint: Paint) {
		drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
	}

	override fun drawRenderNode(renderNode: RenderNode) {
		unsupported("hardware acceleration is disabled")
	}

	override fun drawRoundRect(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		rx: Float,
		ry: Float,
		paint: Paint
	) {
		withPaint(paint) {
			buf.addDrawRoundRect(left, top, right, bottom, rx, ry)
		}
	}

	override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
		drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint)
	}

	override fun drawText(
		text: CharArray,
		index: Int,
		count: Int,
		x: Float,
		y: Float,
		paint: Paint
	) {
		drawText(text.concatToString(), index, count, x, y, paint)
	}

	override fun drawText(
		text: CharSequence,
		start: Int,
		end: Int,
		x: Float,
		y: Float,
		paint: Paint
	) {
		drawText(text.toString(), start, end, x, y, paint)
	}

	override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
		drawText(text.substring(start, end), x, y, paint)
	}

	override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
		withPaint(paint) {
			buf.drawTextAnchored(
				text, x, y, -1f, Float.NaN,
				if (paint.textAlign == Paint.Align.RIGHT) DrawTextAnchored.ANCHOR_TEXT_RTL else 0
			)
		}
	}

	override fun drawTextOnPath(
		text: CharArray,
		index: Int,
		count: Int,
		path: Path,
		hOffset: Float,
		vOffset: Float,
		paint: Paint
	) {
		drawTextOnPath(text.concatToString().substring(index, index + count), path, hOffset, vOffset, paint)
	}

	override fun drawTextOnPath(
		text: String,
		path: Path,
		hOffset: Float,
		vOffset: Float,
		paint: Paint
	) {
		withPaint(paint) {
			buf.addDrawTextOnPath(text, path, hOffset, vOffset)
		}
	}

	override fun drawTextRun(
		text: CharArray,
		index: Int,
		count: Int,
		contextIndex: Int,
		contextCount: Int,
		x: Float,
		y: Float,
		isRtl: Boolean,
		paint: Paint
	) {
		drawTextRun(text.concatToString(), index, count, contextIndex, contextCount, x, y, isRtl, paint)
	}

	override fun drawTextRun(
		text: CharSequence,
		start: Int,
		end: Int,
		contextStart: Int,
		contextEnd: Int,
		x: Float,
		y: Float,
		isRtl: Boolean,
		paint: Paint
	) {
		withPaint(paint) {
			buf.addDrawTextRun(text.toString(), start, end, contextStart, contextEnd, x, y, isRtl)
		}
	}

	override fun drawTextRun(
		text: MeasuredText,
		start: Int,
		end: Int,
		contextStart: Int,
		contextEnd: Int,
		x: Float,
		y: Float,
		isRtl: Boolean,
		paint: Paint
	) {
		unsupported("drawTextRun with MeasuredText is not supported")
	}

	override fun drawVertices(
		mode: VertexMode,
		vertexCount: Int,
		verts: FloatArray,
		vertOffset: Int,
		texs: FloatArray?,
		texOffset: Int,
		colors: IntArray?,
		colorOffset: Int,
		indices: ShortArray?,
		indexOffset: Int,
		indexCount: Int,
		paint: Paint
	) {
		unsupported("drawVertices is not supported")
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun drawPosText(
		text: CharArray,
		index: Int,
		count: Int,
		pos: FloatArray,
		paint: Paint
	) {
		unsupported("drawPosText is not supported")
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun drawPosText(text: String, pos: FloatArray, paint: Paint) {
		unsupported("drawPosText is not supported")
	}

	override fun enableZ() {
		// do nothing as we don't support RenderNodes
	}

	override fun getClipBounds(bounds: Rect): Boolean {
		bounds.set(region.bounds)
		return !region.isEmpty
	}

	override fun getDensity(): Int {
		return Bitmap.DENSITY_NONE
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun getMatrix(ctm: Matrix) {
		// do nothing
	}

	override fun getDrawFilter(): DrawFilter? {
		return null
	}

	override fun getHeight(): Int {
		return h
	}

	override fun getMaximumBitmapHeight(): Int {
		return Int.MAX_VALUE
	}

	override fun getMaximumBitmapWidth(): Int {
		return Int.MAX_VALUE
	}

	override fun getSaveCount(): Int {
		return saveCount.size
	}

	override fun getWidth(): Int {
		return w
	}

	override fun isHardwareAccelerated(): Boolean {
		return false
	}

	override fun isOpaque(): Boolean {
		return false
	}

	override fun quickReject(left: Float, top: Float, right: Float, bottom: Float): Boolean {
		return false
	}

	override fun quickReject(path: Path): Boolean {
		return false
	}

	override fun quickReject(rect: RectF): Boolean {
		return false
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun quickReject(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		type: EdgeType
	): Boolean {
		return false
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun quickReject(path: Path, type: EdgeType): Boolean {
		return false
	}

	@Deprecated("")
	@Suppress("deprecation")
	override fun quickReject(rect: RectF, type: EdgeType): Boolean {
		return false
	}

	private var saveCount = arrayListOf<Region>()

	override fun restore() {
		buf.addMatrixRestore()
		region.set(saveCount.removeAt(saveCount.size - 1))
	}

	override fun restoreToCount(saveCount: Int) {
		while (this.saveCount.size > saveCount)
			restore()
	}

	override fun save(): Int {
		buf.addMatrixSave()
		return saveCount.size.also { saveCount.add(Region(region)) }
	}

	@Deprecated("")
	override fun saveLayer(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		paint: Paint?,
		saveFlags: Int
	): Int {
		return saveLayer(left, top, right, bottom, paint)
	}

	override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
		return saveLayer(bounds!!.left, bounds.top, bounds.right, bounds.bottom, paint)
	}

	@Deprecated("")
	override fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int {
		return saveLayer(bounds, paint)
	}

	override fun saveLayer(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		paint: Paint?
	): Int {
		unsupported("saveLayer is not supported")
	}

	override fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int {
		return saveLayerAlpha(bounds!!.left, bounds.top, bounds.right, bounds.bottom, alpha)
	}

	override fun saveLayerAlpha(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		alpha: Int
	): Int {
		return saveLayer(left, top, right, bottom, Paint().also { it.alpha = alpha })
	}

	override fun scale(sx: Float, sy: Float) {
		buf.addMatrixScale(sx, sy)
	}

	override fun setBitmap(bitmap: Bitmap?) {
		unsupported("setBitmap is not supported")
	}

	override fun setDensity(density: Int) {
		unsupported("setDensity is not supported")
	}

	override fun setDrawFilter(filter: DrawFilter?) {
		unsupported("setDrawFilter is not supported")
	}

	override fun setMatrix(matrix: Matrix?) {
		unsupported("sorry, setMatrix is currently not supported")
	}

	override fun skew(sx: Float, sy: Float) {
		buf.addMatrixSkew(sx, sy)
	}

	@Deprecated("")
	override fun saveLayerAlpha(bounds: RectF?, alpha: Int, saveFlags: Int): Int {
		return saveLayerAlpha(bounds, alpha)
	}

	@Deprecated("")
	override fun saveLayerAlpha(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		alpha: Int,
		saveFlags: Int
	): Int {
		return saveLayerAlpha(left, top, right, bottom, alpha)
	}

	override fun translate(dx: Float, dy: Float) {
		buf.addMatrixTranslate(dx, dy)
	}

	override fun rotate(degrees: Float) {
		buf.addMatrixRotate(degrees, Float.NaN, Float.NaN)
	}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun unsupported(msg: String): Nothing {
		val ex = UnsupportedOperationException(msg)
		Log.e("MyCanvas", Log.getStackTraceString(ex))
		throw ex
	}
}