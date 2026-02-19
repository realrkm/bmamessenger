package com.example.bmamessenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bmamessenger.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Represents the two main screens of the application.
 */
enum class AppScreen { MAIN, SETTINGS }

/**
 * A custom modifier that adds a bounce effect to a composable when clicked.
 */
@Composable
fun Modifier.bounceClick(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, animationSpec = tween(150), label = "bounce")
    return this.scale(scale).clickable(interactionSource = interactionSource, indication = null) {}
}

/**
 * The main activity of the application. This is the entry point for the UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the settings manager, which will be used by the view model.
        val settingsManager = SettingsManager(applicationContext)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Create the view model, providing it with the settings manager.
                    val viewModel: SmsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return SmsViewModel(settingsManager) as T
                        }
                    })

                    // State to manage the current screen being displayed.
                    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }

                    // Navigate between the main and settings screens.
                    if (currentScreen == AppScreen.MAIN) {
                        SmsGatewayScreen(viewModel, onOpenSettings = { currentScreen = AppScreen.SETTINGS })
                    } else {
                        SettingsScreen(viewModel, onBack = { currentScreen = AppScreen.MAIN })
                    }
                }
            }
        }
    }
}

/**
 * The main screen of the application, which displays the list of pending SMS messages.
 *
 * @param viewModel The view model that provides the data for this screen.
 * @param onOpenSettings A callback to navigate to the settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SmsGatewayScreen(viewModel: SmsViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Check for and request SMS permission.
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.SEND_SMS) }

    var showShareOptions by remember { mutableStateOf(false) }

    // Dialog to ask the user whether to send a PDF with the WhatsApp message.
    if (showShareOptions) {
        AlertDialog(
            onDismissRequest = { showShareOptions = false },
            title = { Text("Share via WhatsApp", fontWeight = FontWeight.Medium) },
            text = { Text("Do you want to send only the text or attach a PDF?", fontWeight = FontWeight.Medium) },
            confirmButton = {
                TextButton(onClick = {
                    showShareOptions = false
                    viewModel.whatsAppRecipient?.let { msg ->
                        viewModel.generateAndSendPdf(context, msg)
                    }
                }) {
                    Text("Send with PDF", fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareOptions = false
                    viewModel.whatsAppRecipient?.let { msg ->
                        viewModel.sendToWhatsApp(context, msg.phone, msg.message)
                    }
                }) {
                    Text("Send Text Only", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("BMA Messenger", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.bounceClick()) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {

            // Show the "Send All" and "Clear All" buttons if there are messages.
            if (viewModel.messages.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.sendAllSms(context) },
                        modifier = Modifier.weight(1f).height(56.dp).bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send All", fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = { viewModel.cancelAllSms() },
                        modifier = Modifier.weight(1f).height(56.dp).bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The main content area, with pull-to-refresh functionality.
            PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.fetchMessages() },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Show a message when there are no pending SMS messages.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = viewModel.messages.isEmpty() && !viewModel.isRefreshing,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            Spacer(Modifier.height(16.dp))
                            Text("No Pending SMS", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                        }
                    }

                    // Display the list of SMS messages.
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(items = viewModel.messages, key = { it.id }) { msg ->
                            SmsCard(
                                msg = msg,
                                onSend = { viewModel.sendSingleSms(context, msg) },
                                onCancel = {
                                    viewModel.removeMessageOptimistically(msg)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Message removed",
                                            actionLabel = "UNDO",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoveMessage(msg)
                                        else viewModel.confirmCancelMessage(msg)
                                    }
                                },
                                onShareWhatsApp = {
                                    viewModel.whatsAppRecipient = msg
                                    showShareOptions = true
                                },
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A card that displays the details of a single SMS message.
 *
 * @param msg The SMS message to display.
 * @param onSend A callback to send the SMS message.
 * @param onCancel A callback to cancel the SMS message.
 * @param onShareWhatsApp A callback to share the message on WhatsApp.
 * @param modifier A modifier for this composable.
 */
@Composable
fun SmsCard(
    msg: SmsMessage,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onShareWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = msg.fullname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    Text(text = msg.phone, style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onShareWhatsApp, modifier = Modifier.size(48.dp).bounceClick()) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share on WhatsApp", tint = Color.White)
                }

                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
                    Text(text = "Pending", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text(text = msg.message, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onSend, modifier = Modifier.height(48.dp).bounceClick(), shape = RoundedCornerShape(12.dp)) {
                    Text("Send SMS", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(48.dp).bounceClick(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel SMS", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * The settings screen, where the user can configure the application.
 *
 * @param viewModel The view model that provides the data for this screen.
 * @param onBack A callback to navigate back to the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SmsViewModel, onBack: () -> Unit) {
    var tempUrl by remember { mutableStateOf(viewModel.baseUrl) }
    var tempInterval by remember { mutableStateOf(viewModel.refreshIntervalSeconds.toString()) }
    val focusManager = LocalFocusManager.current

    // Action to save the settings and navigate back to the main screen.
    val saveAction = {
        val interval = tempInterval.toLongOrNull() ?: 30L
        viewModel.saveAndApplySettings(tempUrl, interval)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            OutlinedTextField(
                value = tempUrl,
                onValueChange = { tempUrl = it },
                label = { Text("Anvil Base URL") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = tempInterval,
                onValueChange = { tempInterval = it },
                label = { Text("Refresh Interval (Seconds)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveAction() })
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = saveAction,
                modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Medium)
            }
        }
    }
}
