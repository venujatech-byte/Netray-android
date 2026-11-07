package com.venujatech.netray.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onScanResult: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraDenied by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var bound by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraUseCases by remember { mutableStateOf<List<UseCase>?>(null) }
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
    val barcodeScanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val previewView = remember { PreviewView(context) }

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
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cameraProviderFuture, scanning, cameraDenied, cameraError, previewView) {
        if (!scanning || cameraDenied || cameraError != null || bound) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        try {
            val provider = withContext(Dispatchers.IO) { cameraProviderFuture.get() }
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null || !scanning) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val task = barcodeScanner.process(inputImage)
                task.addOnSuccessListener { barcodes ->
                    val hit = barcodes.firstOrNull { it.rawValue?.isNotBlank() == true && it.rawValue!!.contains("://") }
                    if (hit != null && scanning) {
                        scanning = false
                        val raw = hit.rawValue ?: return@addOnSuccessListener
                        onScanResult(raw)
                    }
                    imageProxy.close()
                }
                task.addOnFailureListener { imageProxy.close() }
            }
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            cameraProvider = provider
            cameraUseCases = listOf(preview, analysis)
            bound = true
        } catch (e: Exception) {
            cameraError = e.message ?: "Camera unavailable"
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
}
