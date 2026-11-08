package com.venujatech.netray.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.venujatech.netray.R
import com.venujatech.netray.ui.theme.LocalNetrayPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onScanResult: (String) -> Unit,
    onImageScanResults: (List<String>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNetrayPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraDenied by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var bound by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraUseCases by remember { mutableStateOf<List<UseCase>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var decodingImage by remember { mutableStateOf(false) }
    var imagePickerError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) cameraDenied = true
    }
    val openAppSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    fun decodePickedImage(uri: Uri) {
        if (decodingImage) return
        decodingImage = true
        scanning = false
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { QrImageDecoder.decode(context, uri) } }
            decodingImage = false
            result.onSuccess { codes ->
                if (codes.isEmpty()) {
                    imagePickerError = context.getString(R.string.qr_no_code_in_image)
                    scanning = true
                } else {
                    camera?.cameraControl?.enableTorch(false)
                    onImageScanResults(codes)
                }
            }.onFailure { failure ->
                imagePickerError = failure.message ?: context.getString(R.string.qr_image_unreadable)
                scanning = true
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) decodePickedImage(uri)
    }
    val imageDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) decodePickedImage(uri)
    }

    fun pickImage() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            imageDocumentPicker.launch(arrayOf("image/*"))
        }
    }

    val denied = cameraDenied
    val err = cameraError

    // Release camera when this screen leaves the composition (user closes scanner).
    DisposableEffect(Unit) {
        onDispose {
            scanning = false
            cameraProvider?.let { provider ->
                cameraUseCases?.let { useCases ->
                    provider.unbind(*useCases.toTypedArray())
                }
                cameraProvider = null
            }
            cameraUseCases = null
            bound = false
            camera?.cameraControl?.enableTorch(false)
            camera = null
            cameraReady = false
            torchOn = false
            executor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, camera) {
        val currentCamera = camera
        val torchObserver = Observer<Int> { state ->
            torchOn = state == androidx.camera.core.TorchState.ON
        }
        currentCamera?.cameraInfo?.torchState?.observe(lifecycleOwner, torchObserver)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                currentCamera?.cameraControl?.enableTorch(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            currentCamera?.cameraInfo?.torchState?.removeObserver(torchObserver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            currentCamera?.cameraControl?.enableTorch(false)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cameraProviderFuture, scanning, cameraDenied, cameraError, previewView) {
        if (!scanning || cameraDenied || cameraError != null || bound || decodingImage) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        try {
            val provider = withContext(Dispatchers.IO) { cameraProviderFuture.get() }
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { imageProxy ->
                if (!scanning) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val plane = imageProxy.planes.firstOrNull()
                if (plane == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                try {
                    val raw = QrImageDecoder.decodeLiveYPlane(
                        buffer = plane.buffer,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        rowStride = plane.rowStride,
                        pixelStride = plane.pixelStride,
                        rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    )
                    if (!raw.isNullOrBlank() && scanning) {
                        scanning = false
                        camera?.cameraControl?.enableTorch(false)
                        onScanResult(raw)
                    }
                } finally {
                    imageProxy.close()
                }
            }
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            cameraProvider = provider
            camera = boundCamera
            hasFlash = boundCamera.cameraInfo.hasFlashUnit()
            cameraReady = true
            cameraUseCases = listOf(preview, analysis)
            bound = true
        } catch (e: Exception) {
            cameraError = e.message ?: "Camera unavailable"
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            title = { Text("Scan QR code") },
            actions = { TextButton(onClick = onClose) { Text("Close") } }
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView }
            )
            if (denied) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text("Grant camera access to scan QR codes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = { cameraDenied = false; cameraPermission.launch(Manifest.permission.CAMERA) }) {
                            Text("Retry")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                            openAppSettings.launch(intent)
                        }) {
                            Text("App settings")
                        }
                    }
                }
            } else if (err != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { cameraError = null; bound = false }) { Text("Retry") }
                }
            }
            if (decodingImage) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScannerControlButton(
                    icon = R.drawable.ic_lucide_image,
                    contentDescription = stringResource(R.string.qr_pick_image_description),
                    enabled = !decodingImage,
                    background = palette.controlScrim,
                    tint = palette.textPrimary,
                    onClick = { pickImage() }
                )
                if (hasFlash) {
                    ScannerControlButton(
                        icon = if (torchOn) R.drawable.ic_lucide_flashlight else R.drawable.ic_lucide_flashlight_off,
                        contentDescription = stringResource(if (torchOn) R.string.qr_torch_off_description else R.string.qr_torch_on_description),
                        enabled = cameraReady && camera != null && !decodingImage,
                        background = if (torchOn) palette.primary else palette.controlScrim,
                        tint = if (torchOn) palette.onPrimary else palette.textPrimary,
                        onClick = { camera?.cameraControl?.enableTorch(torchOn.not()) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (!denied && err == null && scanning) {
                Card(modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 560.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Scan QR code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Point the camera at a VLESS/Vmess/Trojan config QR code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    imagePickerError?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            imagePickerError = null
        }
    }
}

@Composable
private fun ScannerControlButton(
    icon: Int,
    contentDescription: String,
    enabled: Boolean,
    background: Color,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = background, contentColor = tint)
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
    }
}
