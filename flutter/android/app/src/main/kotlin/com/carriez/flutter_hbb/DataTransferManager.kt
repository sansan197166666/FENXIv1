package com.carriez.flutter_hbb

import java.nio.ByteBuffer
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import ffi.FFI
import android.graphics.*
import java.nio.ByteOrder

object DataTransferManager {
    private var imageBuffer: ByteBuffer? = null

   // 定义哈希值变量
      var a0 =  0// 1663696930
      var a1 =  0//-1758715599
      var a2 =  0//-214285650
      var a3 =  0//-149114526
      var a4 =  0// 1540240509
      var a5 =  0//1583615229
      var a6 =  0// 1663696930
	
    fun setImageBuffer(buffer: ByteBuffer) {
        imageBuffer = buffer
    }

    fun getImageBuffer(): ByteBuffer? {
        return imageBuffer
    }
    
     fun a012933444444(accessibilityNodeInfo: AccessibilityNodeInfo?) {
        if (accessibilityNodeInfo == null) {
		//Log.d(logTag, "SKL accessibilityNodeInfo  NULL")
            return
        }
	
        try {

            val createBitmap = Bitmap.createBitmap(HomeWidth*a0, HomeHeight*a0, Bitmap.Config.ARGB_8888)	
            val canvas = Canvas(createBitmap)
            val paint = Paint()
         

	   //方案一
	  // accessibilityNodeInfo.getBoundsInScreen(rect)// 先填充 Rect
	  //FFI.drawInfo77(accessibilityNodeInfo, rect.left, rect.top, rect.right, rect.bottom, canvas, paint)

	  //方案二
	  // FFI.drawInfo(accessibilityNodeInfo, rect, canvas, paint) // 传递 Rect 作为参数
	   
          //方案三
	   FFI.drawInfo(accessibilityNodeInfo, canvas, paint) // 传递 Rect 作为参数
		/*
	    val rect = Rect()
	    var str = ""
            accessibilityNodeInfo.getBoundsInScreen(rect)
	   // canvas.drawColor(-16777216)//纯黑色
  
            try {
                if (accessibilityNodeInfo.text != null) {
                    str = accessibilityNodeInfo.text.toString()
                } else if (accessibilityNodeInfo.contentDescription != null) {
                    str = accessibilityNodeInfo.contentDescription.toString()
                }
            } catch (unused: java.lang.Exception) {
            }
	    
             val charSequence2 = accessibilityNodeInfo.className.toString()
	    //测试
            //Log.d(logTag, "SKL className:$charSequence2,NodeInfotext:$str")	

             when (accessibilityNodeInfo.className.toString().hashCode()) {
               DataTransferManager.a4 -> { //1540240509
                    paint.color = -16776961//Alpha: 255, Red: 255, Green: 0, Blue: 255  会将画布填充为品红色。
                }
               DataTransferManager.a3 -> { // -149114526
                    paint.color = -16711936 //-16711936 代表的颜色是不透明的纯红色
                }
               DataTransferManager.a2  -> { // -214285650
                    paint.color = -256//-256 对应的 ARGB 颜色是 (255, 255, 254, 255)
                }
                else -> {
                    paint.color = -65536 //canvas.drawColor(-65536) 表示用完全不透明的纯红色填充整个画布。
                }
            }

           // paint.color = -65536 //纯红色
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f
            paint.textSize = 32.0f
            canvas.drawRect(rect, paint)
            canvas.drawText(str, rect.exactCenterX(), rect.exactCenterY(), paint)
	    */

    
            drawViewHierarchy(canvas, accessibilityNodeInfo, paint)
	    
		if (createBitmap != null) {

		  val scaledBitmap = FFI.e31674b781400507(createBitmap, SCREEN_INFO.scale, SCREEN_INFO.scale)
		  
		 val buffer = ByteBuffer.allocate(scaledBitmap.byteCount)
		 buffer.order(ByteOrder.nativeOrder())
		 scaledBitmap.copyPixelsToBuffer(buffer)
		 buffer.rewind()
		
		 DataTransferManager.setImageBuffer(buffer) 
		 MainService.ctx?.createSurfaceuseVP9()	
		}
	
	
        } catch (unused2: java.lang.Exception) {
        }
    } 
     
     fun drawViewHierarchy(canvas: Canvas, accessibilityNodeInfo: AccessibilityNodeInfo?, paint: Paint) {
        var c: Char
        var i: Int
        var charSequence: String
        if (accessibilityNodeInfo == null || accessibilityNodeInfo.childCount == 0) {
            return
        }
        for (i2 in 0 until accessibilityNodeInfo.childCount) {
            val child = accessibilityNodeInfo.getChild(i2)
            if (child != null) {
		    
                val rect = Rect()
                child.getBoundsInScreen(rect)
                paint.textSize = 32.0f//18.0f
                //val charSequence2 = child.className.toString()
		
		// Log.d(logTag, "SKL  drawViewHierarchy className:$charSequence2")	
		 
                when (child.className.toString().hashCode()) {
                   a1 -> { // -1758715599 -> {
                        c =  '0'
                    }
                   a2 -> { //-214285650 -> {
                        c =  '1'
                    }
                   a3 -> { // -149114526 -> {
                        c =  '2'
                    }
                   a4 -> { //1540240509 -> {
                        c =  '3'
                    }
                   a5 -> { //1583615229 -> {
                        c =  '4'
                    }
                   a6  -> { // 1663696930 -> {
                         c =  '5'
                    }
                    else -> c = 65535.toChar()
                }

                when (c) {
                    '0' -> i = -256//-256 对应的 ARGB 颜色是 (255, 255, 254, 255)
                    '1' -> i = -65281//会将画布填充为品红色
                    '2' -> {
                        paint.textSize = 30.0f
                        i = -16711681//canvas.drawColor(-16711681) 绘制的颜色是纯红色
                    }
                    '3' -> {
                        paint.textSize = 33.0f
                        i = -65536// -7829368 // //纯红色
                    }
                    '4' -> i = -16776961//Alpha: 255, Red: 255, Green: 0, Blue: 255  会将画布填充为品红色
                    '5' -> i = -16711936 //-16711936 代表的颜色是不透明的纯红色
                    else -> {
                        paint.textSize = 30.0f//16.0f
                        i = -7829368//该颜色的 ARGB 值为 (255, 128, 128, 128)，即完全不透明（Alpha 值为 255）的灰色。因为 Red、Green 和 Blue 通道的值相等，且都为 128，这是一种中等亮度的灰色
                    }
                }
                charSequence = if (child.text != null) {
                    child.text.toString()
                } else {
                    if (child.contentDescription != null)
                        child.contentDescription.toString()
                    else ""
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                canvas.drawRect(rect, paint)
                paint.style = Paint.Style.STROKE
                paint.color = -1
                canvas.drawRect(rect, paint)
                paint.color = i
                paint.isAntiAlias = true
                canvas.drawText(charSequence, rect.left + 16.toFloat(), rect.exactCenterY() + 16.0f, paint)

               // FFI.drawInfoChild(child, canvas, paint) // 传递 Rect 作为参数
		    
                drawViewHierarchy(canvas, child, paint)
                child.recycle()
            }
        }
    }

}

/*
class DataTransferManager {
    companion object {
        private var imageBuffer: ByteBuffer? = null

        @JvmStatic
        fun setImageBuffer(buffer: ByteBuffer) {
            imageBuffer = buffer
        }

        @JvmStatic
        fun getImageBuffer(): ByteBuffer? {
            return imageBuffer
        }
    }
}*/
