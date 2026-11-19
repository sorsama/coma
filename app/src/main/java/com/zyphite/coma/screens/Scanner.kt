package com.zyphite.coma.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.LocalClock
import com.zyphite.coma.ui.LocalReduceMotion
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.W6
import com.zyphite.coma.ui.frac
import com.zyphite.coma.ui.press
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

private const val CAMERA = Manifest.permission.CAMERA

/**
 * The pairing scanner: the camera fills the screen and every frame is read for a QR code, so a code
 * is caught the moment it is in view, at any angle and from across a desk (the camera zooms itself
 * in on a small or distant code). [accept] takes the text of a code and says whether it was the
 * kind this screen wants; anything else keeps the scanner open with a hint.
 */
@Composable
fun Scanner(accept: (String) -> Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { if (!granted) permission.launch(CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraFeed(
                onCamera = { camera = it },
                onCode = { text -> accept(text).also { wrong = !it } },
            )
            Viewfinder()
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(onClose) { Ico(Icons.Close, 22.dp, Color.White, desc = "Close scanner") }
                if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                    RoundButton(
                        onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                        on = torch,
                    ) { Ico(Icons.Torch, 22.dp, if (torch) Color.Black else Color.White, desc = if (torch) "Turn the light off" else "Turn the light on") }
                }
            }
            T(
                "Scan pairing code", Modifier.padding(top = 20.dp),
                size = 22.sp, weight = W6, color = Color.White, spacing = (-0.02).em, align = TextAlign.Center,
            )
            T(
                when {
                    !granted -> "Coma needs the camera to read the code."
                    wrong -> "That isn't a Coma pairing code. Point at the one Hermes shows."
                    else -> "Point at the code in Hermes Desktop, or the one hermes coma prints."
                },
                Modifier.padding(top = 8.dp), size = 14.sp, color = if (wrong) C.Amber else Color.White.copy(alpha = .78f),
                align = TextAlign.Center,
            )
            if (!granted && asked) {
                Box(
                    Modifier.padding(top = 24.dp).height(48.dp).press { permission.launch(CAMERA) }
                        .clip(RoundedCornerShape(24.dp)).background(Color.White).padding(horizontal = 24.dp),
                    Alignment.Center,
                ) { T("Allow the camera", weight = W5, color = Color.Black) }
            }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, on: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).press(onClick = onClick).clip(CircleShape)
            .background(if (on) Color.White else Color.Black.copy(alpha = .45f)),
        Alignment.Center,
    ) { content() }
}

/** The camera preview with a reader on every frame. [onCode] returns true once a code was accepted. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraFeed(onCamera: (Camera) -> Unit, onCode: (String) -> Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val deliver by rememberUpdatedState(onCode)
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(owner) {
        val main = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        var bound: Camera? = null
        var done = false
        var lastRejected = ""
        val reader = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE) // one format reads far faster than all of them
                .setZoomSuggestionOptions(
                    // A code too small to read asks for a closer look, and the camera gives it one.
                    ZoomSuggestionOptions.Builder { ratio -> bound?.cameraControl?.setZoomRatio(ratio); true }
                        .setMaxSupportedZoomRatio(4f).build(),
                )
                .build(),
        )
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // never queue stale frames
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    ).build(),
                )
                .build()
            analysis.setAnalyzer(worker) { frame ->
                val image = frame.image
                if (image == null || done) return@setAnalyzer frame.close()
                reader.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                    .addOnSuccessListener(main) { codes ->
                        val text = codes.firstNotNullOfOrNull { it.rawValue }
                        if (text != null && !done && text != lastRejected) {
                            if (deliver(text)) {
                                done = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                lastRejected = text // say so once, not sixty times a second
                            }
                        }
                    }
                    .addOnCompleteListener { frame.close() }
            }
            runCatching {
                provider.unbindAll()
                bound = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                bound?.let(onCamera)
            }
        }, main)

        onDispose {
            done = true
            runCatching { future.get().unbindAll() }
            reader.close()
            worker.shutdown()
        }
    }

    AndroidView({ view }, Modifier.fillMaxSize())
}

/** Dims everything but a square window, marks its corners, and sweeps a line down it. */
@Composable
private fun Viewfinder() {
    val clock = LocalClock.current
    val reduce = LocalReduceMotion.current
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = min(size.width * .72f, 300.dp.toPx())
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val radius = 28.dp.toPx()
        val corner = CornerRadius(radius)
        val origin = Offset(left, top)
        val window = androidx.compose.ui.geometry.Size(side, side)

        drawRect(Color.Black.copy(alpha = .62f))
        drawRoundRect(Color.Black, origin, window, corner, blendMode = BlendMode.Clear)

        // Corner brackets: the window's own outline, kept only near its four corners.
        val arm = 34.dp.toPx()
        val keep = Path().apply {
            for (x in listOf(left - 8, left + side - arm)) for (y in listOf(top - 8, top + side - arm)) {
                addRect(androidx.compose.ui.geometry.Rect(x, y, x + arm + 8, y + arm + 8))
            }
        }
        clipPath(keep) {
            drawRoundRect(Color.White, origin, window, corner, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }

        if (!reduce) {
            // Down and back up, easing at both ends.
            val phase = frac(clock.floatValue / 2.4f)
            val travel = 1f - abs(1f - 2f * phase)
            val eased = travel * travel * (3f - 2f * travel)
            val y = top + radius * .6f + (side - radius * 1.2f) * eased
            val inset = 18.dp.toPx()
            drawLine(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White, Color.White, Color.Transparent), left + inset, left + side - inset,
                ),
                Offset(left + inset, y), Offset(left + side - inset, y), 2.dp.toPx(), StrokeCap.Round,
            )
        }
    }
}
