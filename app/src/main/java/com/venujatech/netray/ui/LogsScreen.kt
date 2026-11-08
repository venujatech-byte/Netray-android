package com.venujatech.netray.ui

import android.content.Intent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.venujatech.netray.ConnectionLogs
import com.venujatech.netray.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logLines by remember { mutableStateOf(listOf("Loading connection logs…")) }
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                logLines = withContext(Dispatchers.IO) { ConnectionLogs.read() }
                delay(3000)
            }
        }
    }

    var totalDrag by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> totalDrag += delta },
                onDragStopped = { velocity ->
                    if (totalDrag > 60f || totalDrag < -60f || velocity > 300f || velocity < -300f) {
                        onBack()
                    }
                    totalDrag = 0f
                }
            ),
        topBar = {
            TopAppBar(
                title = { Text("Connection Logs") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_lucide_menu), contentDescription = "Open drawer")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            logLines = withContext(Dispatchers.IO) { ConnectionLogs.read() }
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_lucide_refresh_cw), contentDescription = "Refresh logs")
                    }
                    IconButton(onClick = {
                        val text = logLines.joinToString("\n")
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Share Connection Logs"))
                    }) {
                        Icon(painterResource(R.drawable.ic_lucide_share_2), contentDescription = "Share logs")
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(logLines) { line ->
                    Text(
                        text = line,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

