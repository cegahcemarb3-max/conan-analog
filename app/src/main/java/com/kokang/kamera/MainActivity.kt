package com.kokang.kamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kokang.kamera.CameraBodyView.Alert
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), CameraBodyView.Listener, BackDoorView.Listener, ShelfView.Listener {

    private lateinit var st: CamState
    private lateinit var root: FrameLayout
    private lateinit var body: CameraBodyView
    private lateinit var back: BackDoorView
    private lateinit var shelf: ShelfView
    private lateinit var preview: PreviewView
    private lateinit var vfOverlay: View
    private lateinit var sfx: Sfx
    private var gallery: View? = null
    private var imageCapture: ImageCapture? = null
    private val io = Executors.newSingleThreadExecutor()
    private var rewindAcc = 0f
    private var orient: OrientationEventListener? = null

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else { body.alert(Alert.ERR); toast("Camera permission denied. Enable it in Settings.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        st = CamState(this)
        Rolls.migrate(this, st)
        sfx = Sfx(this)

        root = FrameLayout(this)
        body = CameraBodyView(this).also { it.st = st; it.listener = this }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        // garis bingkai + vinyet di atas jendela bidik
        vfOverlay = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.02f; p.color = 0xCCF4F0C0.toInt()
                val ratio = st.film?.ratio ?: 1.5f
                var fw = w * 0.84f; var fh = fw / ratio
                if (fh > h * 0.84f) { fh = h * 0.84f; fw = fh * ratio }
                val x0 = (w - fw) / 2; val y0 = (h - fh) / 2; val x1 = x0 + fw; val y1 = y0 + fh
                val l = minOf(fw, fh) * 0.22f
                c.drawLine(x0, y0, x0 + l, y0, p); c.drawLine(x0, y0, x0, y0 + l, p)
                c.drawLine(x1, y0, x1 - l, y0, p); c.drawLine(x1, y0, x1, y0 + l, p)
                c.drawLine(x0, y1, x0 + l, y1, p); c.drawLine(x0, y1, x0, y1 - l, p)
                c.drawLine(x1, y1, x1 - l, y1, p); c.drawLine(x1, y1, x1, y1 - l, p)
                c.drawCircle(w / 2, h / 2, h * 0.07f, p)
                p.strokeWidth = h * 0.08f; p.color = 0x99000000.toInt()
                c.drawRect(0f, 0f, w, h, p)
            }
        }
        back = BackDoorView(this).also { it.listener = this; it.visibility = View.GONE }
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        root.addView(preview, FrameLayout.LayoutParams(10, 10))
        root.addView(vfOverlay, FrameLayout.LayoutParams(10, 10))
        root.addView(back, FrameLayout.LayoutParams(-1, -1))
        shelf = ShelfView(this).also { it.listener = this; it.visibility = View.GONE }
        root.addView(shelf, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        body.onVfLayout = { r ->
            for (v in listOf(preview, vfOverlay)) {
                v.layoutParams = FrameLayout.LayoutParams(r.width().toInt(), r.height().toInt()).apply {
                    leftMargin = r.left.toInt(); topMargin = r.top.toInt()
                }
            }
            root.post { root.requestLayout() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    gallery != null -> hideGallery()
                    shelf.visibility == View.VISIBLE -> { if (!shelf.developing) { if (shelf.selected != null) { shelf.selected = null; shelf.invalidate() } else onShelfBack() } }
                    back.visibility == View.VISIBLE -> onCloseDoor()
                    else -> finish()
                }
            }
        })

        orient = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                imageCapture?.targetRotation = when (o) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)

    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()

    private val batteryPoll = object : Runnable {
        override fun run() {
            val i = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val lvl = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            st.batteryLow = lvl >= 0 && lvl * 100 / scale.coerceAtLeast(1) <= 15
            body.postDelayed(this, 60_000)
        }
    }

    override fun onStart() { super.onStart(); orient?.enable(); body.removeCallbacks(batteryPoll); body.post(batteryPoll) }
    override fun onStop() { super.onStop(); orient?.disable(); st.save() }
    override fun onDestroy() { super.onDestroy(); sfx.release(); io.shutdown() }

    // ------------------------------------------------------------------ kamera

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            try {
                val provider = fut.get()
                val pb = Preview.Builder()
                Camera2Interop.Extender(pb).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(s: CameraCaptureSession, rq: CaptureRequest, r: TotalCaptureResult) {
                        val af = r.get(CaptureResult.CONTROL_AF_STATE)
                        val mode = r.get(CaptureResult.CONTROL_AF_MODE)
                        val ok = af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                            af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            mode == null || mode == CaptureResult.CONTROL_AF_MODE_OFF   // lensa fixed focus
                        val exp = r.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        val slow = exp != null && exp > 33_000_000L          // slower than 1/30 s
                        if (ok != st.focused || slow != st.slow) { st.focused = ok; st.slow = slow; body.postInvalidate() }
                    }
                })
                val pv = pb.build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pv, ic)
                imageCapture = ic
            } catch (e: Exception) {
                body.alert(Alert.ERR); toast("Camera failed to start: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ------------------------------------------------------------------ CameraBodyView.Listener

    override fun onWindAttempt(): Boolean {
        val f = st.film
        return when {
            f == null -> { body.alert(Alert.NOFILM); false }
            st.rewinding || st.rewound -> { body.alert(Alert.OPEN); false }
            st.cocked -> { body.alert(Alert.OK); false }
            st.frames >= f.frames -> { sfx.play("clunk", 0.6f); body.alert(Alert.REWIND); false }
            else -> true
        }
    }

    override fun onWindTick() = sfx.play("tick", 0.8f)

    override fun onCocked() {
        st.cocked = true; st.save()
        sfx.play("krek")
        body.invalidate()
    }

    override fun onShutter() {
        val f = st.film
        when {
            f == null -> body.alert(Alert.NOFILM)
            st.rewinding || st.rewound -> body.alert(Alert.OPEN)
            st.frames >= f.frames -> body.alert(Alert.REWIND)
            !st.cocked -> { sfx.play("tick", 0.5f); body.alert(Alert.WIND) }
            imageCapture == null -> body.alert(Alert.ERR)
            else -> fire(f)
        }
    }

    private fun fire(f: Film) {
        val ic = imageCapture ?: return
        st.cocked = false
        st.frames += 1
        st.save()
        sfx.play("shutter")
        body.invalidate()
        val frame = st.frames
        ic.flashMode = if (st.flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        val file = Lab.rawFile(this, st.rollId, frame, st.date)
        ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), io, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                if (frame >= f.frames) body.post { body.alert(Alert.REWIND) }
            }
            override fun onError(e: ImageCaptureException) {
                body.post { body.alert(Alert.ERR) }
            }
        })
    }

    override fun onFlash() { st.flash = !st.flash; st.save(); sfx.play("tick"); body.invalidate() }
    override fun onDate() { st.date = !st.date; st.save(); sfx.play("tick"); body.invalidate() }

    private var lastNag = 0L

    override fun onRewind(deg: Float) {
        val f = st.film
        val now = System.currentTimeMillis()
        if (f == null || st.frames < f.frames) {
            if (now - lastNag > 2500) {
                lastNag = now
                sfx.play("tick", 0.5f)
                if (f == null) body.alert(Alert.NOFILM) else body.alert(Alert.LOCK, f.frames - st.frames)
            }
            return
        }
        st.crankAngle = (st.crankAngle + deg) % 360f
        if (st.rewound) return
        if (!st.rewinding) { st.rewinding = true; st.cocked = false; rewindAcc = 0f; st.save() }
        val before = (rewindAcc / 45f).toInt()
        rewindAcc += deg
        if ((rewindAcc / 45f).toInt() != before) sfx.play("tick", 0.6f)
        val need = 360f * maxOf(1.5f, st.frames / 4f)      // satu putaran menggulung 4 frame
        st.rewindShown = (st.frames * (1f - rewindAcc / need)).coerceAtLeast(0f)
        if (rewindAcc >= need) {
            st.rewinding = false; st.rewound = true; st.rewindShown = -1f; st.save()
            sfx.play("clunk")
            body.alert(Alert.OPEN)
        }
    }

    override fun onLatchAttempt(): Boolean {
        val f = st.film
        if (f != null && !st.rewound) {
            sfx.play("tick", 0.5f)
            if (st.frames < f.frames) body.alert(Alert.LOCK, f.frames - st.frames) else body.alert(Alert.REWIND)
            return false
        }
        return true
    }

    /** Door opens. A rewound roll leaves the camera and goes to the shelf, undeveloped. */
    override fun onLatch() {
        sfx.play("door")
        val f = st.film
        back.note = "Pick a film, then LOAD THIS ROLL."
        showBack(true)
        if (f != null) {
            val shots = st.frames
            val id = st.rollId
            st.eject()
            if (shots == 0) {
                Rolls.dir(this, id).deleteRecursively()
                back.note = "Roll removed with no exposures."
            } else {
                val roll = Rolls.create(this, id, f, shots)
                showShelf(roll.id)
            }
        }
        back.invalidate()
    }

    // ------------------------------------------------------------------ BackDoorView.Listener

    override fun onLoad(f: Film) {
        st.load(f)
        sfx.play("door")
        showBack(false)
        body.alert(Alert.LOADED, f.frames)
    }

    override fun onCloseDoor() {
        sfx.play("door")
        showBack(false)
        if (st.film == null) body.alert(Alert.NOFILM)
    }

    private fun showBack(show: Boolean) {
        if (show) { back.alpha = 0f; back.visibility = View.VISIBLE; back.animate().alpha(1f).setStartDelay(120).setDuration(220).start() }
        else back.visibility = View.GONE
        body.invalidate(); vfOverlay.invalidate()
    }

    // ------------------------------------------------------------------ roll shelf

    override fun onLab() = showShelf(null)

    private fun showShelf(selectId: Long?) {
        shelf.rolls = Rolls.list(this)
        shelf.selected = shelf.rolls.firstOrNull { it.id == selectId }
        shelf.visibility = View.VISIBLE
        shelf.invalidate()
    }

    override fun onShelfBack() { shelf.visibility = View.GONE; hideGallery() }

    override fun onRename(r: Roll) {
        val input = android.widget.EditText(this).apply {
            setText(r.name); setSelection(text.length); isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(28))
        }
        android.app.AlertDialog.Builder(this).setTitle("Name / tag for this roll").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { r.name = n; r.save(); shelf.invalidate() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDelete(r: Roll) {
        val what = if (r.developed) "its ${r.prints().size} prints, including the copies saved to your phone gallery" else "its ${r.shots} undeveloped frames"
        android.app.AlertDialog.Builder(this).setTitle("Delete \"${r.name}\"?")
            .setMessage("This throws away the roll and $what. It cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Rolls.delete(this, r)
                shelf.rolls = Rolls.list(this); shelf.selected = null; shelf.invalidate()
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDevelop(r: Roll) {
        shelf.developing = true; shelf.devDone = 0; shelf.devTotal = r.shots; shelf.invalidate()
        Lab.develop(this, r,
            { i, n -> shelf.devDone = i; shelf.devTotal = n; shelf.invalidate() },
            { _ ->
                shelf.developing = false
                shelf.rolls = Rolls.list(this)
                shelf.selected = shelf.rolls.firstOrNull { it.id == r.id }
                shelf.invalidate()
                shelf.selected?.let { showGallery(it) }
            })
    }

    override fun onViewPrints(r: Roll) = showGallery(r)

    // ------------------------------------------------------------------ prints of one roll

    private fun hideGallery() { gallery?.let { root.removeView(it) }; gallery = null }

    private fun showGallery(roll: Roll) {
        hideGallery()
        val files = roll.prints()
        val dp = resources.displayMetrics.density
        val wrap = FrameLayout(this).apply { setBackgroundColor(0xFF1B1A17.toInt()); isClickable = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
        }
        head.addView(TextView(this).apply {
            text = "${roll.name}  •  ${roll.film?.fullName ?: ""}  •  ${files.size} prints"; setTextColor(0xFFFFE9A8.toInt()); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply {
            text = "CLOSE  ✕"; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(0xFFC8351F.toInt())
            setOnClickListener { hideGallery() }
        })
        col.addView(head)
        if (files.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "No prints came out of this roll."
                setTextColor(0xFFD9CDB2.toInt()); textSize = 15f; gravity = Gravity.CENTER
                setPadding((32 * dp).toInt(), (48 * dp).toInt(), (32 * dp).toInt(), 0)
            })
        } else {
            val cache = LruCache<String, Bitmap>(60)
            val grid = GridView(this).apply {
                numColumns = 4
                horizontalSpacing = (6 * dp).toInt(); verticalSpacing = (6 * dp).toInt()
                setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), (12 * dp).toInt())
                clipToPadding = false
            }
            grid.adapter = object : BaseAdapter() {
                override fun getCount() = files.size
                override fun getItem(i: Int) = files[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, cv: View?, parent: ViewGroup): View {
                    val iv = (cv as? ImageView) ?: ImageView(this@MainActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(0xFF2B2A28.toInt())
                    }
                    val cw = (parent.width - (24 + 18) * dp) / 4f
                    iv.layoutParams = ViewGroup.LayoutParams(-1, (cw * 2f / 3f).toInt().coerceAtLeast(40))
                    val f = files[i]
                    iv.tag = f.name
                    val hit = cache.get(f.name)
                    if (hit != null) iv.setImageBitmap(hit) else {
                        iv.setImageBitmap(null)
                        io.execute {
                            val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })
                            if (b != null) { cache.put(f.name, b); iv.post { if (iv.tag == f.name) iv.setImageBitmap(b) } }
                        }
                    }
                    return iv
                }
            }
            grid.setOnItemClickListener { _, _, i, _ -> showPhoto(wrap, files[i], roll) }
            col.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        wrap.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.addView(wrap, FrameLayout.LayoutParams(-1, -1))
        gallery = wrap
    }

    private fun showPhoto(wrap: FrameLayout, f: File, roll: Roll) {
        val dp = resources.displayMetrics.density
        val layer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isClickable = true }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        iv.setImageBitmap(BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 }))
        layer.addView(iv, FrameLayout.LayoutParams(-1, -1))
        layer.addView(TextView(this).apply {
            text = "${roll.name}  •  ${roll.film?.fullName ?: "Film"}  •  frame ${f.nameWithoutExtension}   (tap to go back)"
            setTextColor(0xFFFFE9A8.toInt()); textSize = 13f
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(0x99000000.toInt())
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        layer.setOnClickListener { wrap.removeView(layer) }
        wrap.addView(layer, FrameLayout.LayoutParams(-1, -1))
    }
}
