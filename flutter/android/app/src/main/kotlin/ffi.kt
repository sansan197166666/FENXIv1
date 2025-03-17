// ffi.kt

package ffi

import android.content.Context
import java.nio.ByteBuffer

import com.carriez.flutter_hbb.RdClipboardManager
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService

object FFI {
    init {
        System.loadLibrary("rustdesk")
    }

    external fun init(ctx: Context)
    external fun setClipboardManager(clipboardManager: RdClipboardManager)
    external fun startServer(app_dir: String, custom_client_config: String)
    external fun startService()

    //external fun onOutputBufferAvailable(buf: ByteBuffer)
    external fun onVideoFrameUpdateUseVP9(buf: ByteBuffer)
    //external fun onVideoFrameUpdateByNetWork(buf: ByteBuffer)
    external fun onVideoFrameUpdate(buf: ByteBuffer)
    external fun onAudioFrameUpdate(buf: ByteBuffer)
    
    external fun translateLocale(localeName: String, input: String): String
    external fun refreshScreen()
 
    external fun setFrameRawEnable(name: String, value: Boolean)
    external fun setCodecInfo(info: String)
    external fun getLocalOption(key: String): String
    external fun onClipboardUpdate(clips: ByteBuffer)
   external fun getRootInActiveWindow(service: AccessibilityService): AccessibilityNodeInfo?
    external fun initializeBuffer(width: Int, height: Int): ByteBuffer
    external fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap
    // 定义 JNI 方法
    //external fun scaleBitmap(bitmap: Bitmap, scaleX: Int, scaleY: Int): Bitmap
    external fun processBuffer(newBuffer: ByteBuffer, globalBuffer: ByteBuffer)
    external fun releaseBuffer(buf: ByteBuffer)
    external fun isServiceClipboardEnabled(): Boolean

   // 定义 JNI 方法，与 Rust 端匹配
    external fun drawViewHierarchy(
        canvas: android.graphics.Canvas, 
        rootNode: android.view.accessibility.AccessibilityNodeInfo, 
        paint: android.graphics.Paint
    )
    external fun processBitmap2(bitmap: android.graphics.Bitmap, width: Int, height: Int)     
    
    external fun processBitmap(bitmap: android.graphics.Bitmap, width: Int, height: Int): ByteBuffer
    
    external fun setAccessibilityServiceInfo(service: android.accessibilityservice.AccessibilityService)
    
    external fun getNetArgs0(): Int
    external fun getNetArgs1(): Int
    external fun getNetArgs2(): Int
    external fun getNetArgs3(): Int
}
