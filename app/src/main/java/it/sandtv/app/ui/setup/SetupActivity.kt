package it.sandtv.app.ui.setup

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalFocusManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.data.repository.PlaylistRepository
import it.sandtv.app.ui.loading.LoadingActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.util.QRCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// Setup tab modes
private enum class SetupTab { M3U, XTREAM, QR_CODE }

// Fun loading phrases - cinema themed
private val setupLoadingPhrases = listOf(
    "Stiamo preparando i popcorn... 🍿",
    "Accendiamo il proiettore... 🎬",
    "Controlliamo le frequenze... 📡",
    "Sintonizzazione canali... 📺",
    "Prepariamo il tuo cinema... 🎥",
    "Un momento, quasi pronti... ⏳",
    "Connessione in corso... 🔗",
    "Verifica credenziali... 🔐",
    "Caricamento contenuti... 📦",
    "Accendiamo le luci... 💡"
)

/**
 * Setup Wizard - Modern Design with Glassmorphism
 * Premium streaming app style with QR Code remote setup
 */
@AndroidEntryPoint
class SetupActivity : ComponentActivity() {
    
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var playlistRepository: PlaylistRepository
    
    // Firebase Database reference
    private val database by lazy { FirebaseDatabase.getInstance() }
    private var firebaseListener: ValueEventListener? = null
    private var currentSessionCode: String? = null
    
    // URL base per la pagina web di setup
    private val webSetupBaseUrl = "https://sandtv-581c1.web.app/setup/"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SandTVTheme {
                SetupScreenContent()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup Firebase listener and session
        cleanupFirebaseSession()
    }
    
    private fun cleanupFirebaseSession() {
        currentSessionCode?.let { code ->
            firebaseListener?.let { listener ->
                database.getReference("sessions").child(code).removeEventListener(listener)
            }
            // Delete the session
            database.getReference("sessions").child(code).removeValue()
        }
        firebaseListener = null
        currentSessionCode = null
    }
    
    private fun generateSessionCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No confusing chars like 0/O, 1/I
        return (1..6).map { chars.random() }.joinToString("")
    }
    
    @Composable
    private fun SetupScreenContent() {
        var currentTab by remember { mutableStateOf(SetupTab.M3U) }
        var isLoading by remember { mutableStateOf(false) }
        var loadingProgress by remember { mutableFloatStateOf(0f) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }
        
        // M3U fields
        var m3uName by remember { mutableStateOf("") }
        var m3uUrl by remember { mutableStateOf("") }
        
        // Xtream fields
        var xtreamName by remember { mutableStateOf("") }
        var xtreamServer by remember { mutableStateOf("") }
        var xtreamUsername by remember { mutableStateOf("") }
        var xtreamPassword by remember { mutableStateOf("") }
        
        // QR Code state
        var sessionCode by remember { mutableStateOf("") }
        var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isWaitingForData by remember { mutableStateOf(false) }
        
        // Generate session code and QR when switching to QR tab
        LaunchedEffect(currentTab) {
            if (currentTab == SetupTab.QR_CODE && sessionCode.isEmpty()) {
                sessionCode = generateSessionCode()
                currentSessionCode = sessionCode
                
                // Generate QR code
                val url = "$webSetupBaseUrl?session=$sessionCode"
                qrCodeBitmap = QRCodeGenerator.generate(url, 400)
                
                // Start listening for data from Firebase
                isWaitingForData = true
                startFirebaseListener(
                    sessionCode = sessionCode,
                    onDataReceived = { type, name, data ->
                        isWaitingForData = false
                        successMessage = "Dati ricevuti! Compilo il form..."
                        
                        when (type) {
                            "m3u" -> {
                                m3uName = name
                                m3uUrl = data["url"] ?: ""
                                currentTab = SetupTab.M3U
                            }
                            "xtream" -> {
                                xtreamName = name
                                xtreamServer = data["server"] ?: ""
                                xtreamUsername = data["username"] ?: ""
                                xtreamPassword = data["password"] ?: ""
                                currentTab = SetupTab.XTREAM
                            }
                        }
                        
                        // Clear success message after delay
                        lifecycleScope.launch {
                            delay(2000)
                            successMessage = null
                        }
                    },
                    onError = { error ->
                        errorMessage = error
                        isWaitingForData = false
                    }
                )
            } else if (currentTab != SetupTab.QR_CODE) {
                // Cleanup when leaving QR tab
                cleanupFirebaseSession()
                sessionCode = ""
                qrCodeBitmap = null
                isWaitingForData = false
            }
        }
        
        // Simulate progress while loading
        LaunchedEffect(isLoading) {
            if (isLoading) {
                loadingProgress = 0f
                while (loadingProgress < 0.9f) {
                    delay(100)
                    loadingProgress += (0.9f - loadingProgress) * 0.1f
                }
            } else {
                loadingProgress = 0f
            }
        }
        
        SetupScreen(
            currentTab = currentTab,
            onTabChange = { currentTab = it },
            m3uName = m3uName,
            onM3uNameChange = { m3uName = it },
            m3uUrl = m3uUrl,
            onM3uUrlChange = { m3uUrl = it },
            xtreamName = xtreamName,
            onXtreamNameChange = { xtreamName = it },
            xtreamServer = xtreamServer,
            onXtreamServerChange = { xtreamServer = it },
            xtreamUsername = xtreamUsername,
            onXtreamUsernameChange = { xtreamUsername = it },
            xtreamPassword = xtreamPassword,
            onXtreamPasswordChange = { xtreamPassword = it },
            isLoading = isLoading,
            loadingProgress = loadingProgress,
            errorMessage = errorMessage,
            successMessage = successMessage,
            sessionCode = sessionCode,
            qrCodeBitmap = qrCodeBitmap,
            isWaitingForData = isWaitingForData,
            onContinue = {
                errorMessage = null
                
                when (currentTab) {
                    SetupTab.M3U -> {
                        if (m3uName.isBlank() || m3uUrl.isBlank()) {
                            errorMessage = "Compila tutti i campi"
                            return@SetupScreen
                        }
                        addM3UPlaylist(m3uName, m3uUrl,
                            onLoading = { isLoading = it },
                            onError = { errorMessage = it }
                        )
                    }
                    SetupTab.XTREAM -> {
                        if (xtreamName.isBlank() || xtreamServer.isBlank() || 
                            xtreamUsername.isBlank() || xtreamPassword.isBlank()) {
                            errorMessage = "Compila tutti i campi"
                            return@SetupScreen
                        }
                        addXtreamPlaylist(xtreamName, xtreamServer, xtreamUsername, xtreamPassword,
                            onLoading = { isLoading = it },
                            onError = { errorMessage = it }
                        )
                    }
                    SetupTab.QR_CODE -> {
                        // QR tab doesn't have a continue action
                    }
                }
            }
        )
    }
    
    private fun startFirebaseListener(
        sessionCode: String,
        onDataReceived: (type: String, name: String, data: Map<String, String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val sessionRef = database.getReference("sessions").child(sessionCode)
        
        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    try {
                        val type = snapshot.child("type").getValue(String::class.java) ?: return
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        
                        val data = mutableMapOf<String, String>()
                        when (type) {
                            "m3u" -> {
                                data["url"] = snapshot.child("url").getValue(String::class.java) ?: ""
                            }
                            "xtream" -> {
                                data["server"] = snapshot.child("server").getValue(String::class.java) ?: ""
                                data["username"] = snapshot.child("username").getValue(String::class.java) ?: ""
                                data["password"] = snapshot.child("password").getValue(String::class.java) ?: ""
                            }
                        }
                        
                        // Delete the session data after receiving
                        sessionRef.removeValue()
                        
                        onDataReceived(type, name, data)
                        
                    } catch (e: Exception) {
                        Log.e("SetupActivity", "Error parsing Firebase data", e)
                        onError("Errore nella lettura dei dati")
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("SetupActivity", "Firebase listener cancelled", error.toException())
                onError("Connessione interrotta")
            }
        }
        
        sessionRef.addValueEventListener(firebaseListener!!)
    }
    
    private fun addM3UPlaylist(
        name: String, 
        url: String,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        onLoading(true)
        
        lifecycleScope.launch {
            try {
                playlistRepository.addM3UPlaylist(name, url)
                completeSetup()
            } catch (e: Exception) {
                onLoading(false)
                onError(e.message ?: "Errore durante il caricamento della playlist")
            }
        }
    }
    
    private fun addXtreamPlaylist(
        name: String,
        server: String,
        username: String,
        password: String,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        onLoading(true)
        
        lifecycleScope.launch {
            try {
                playlistRepository.addXtreamPlaylist(name, server, username, password)
                completeSetup()
            } catch (e: Exception) {
                onLoading(false)
                onError(e.message ?: "Errore durante il caricamento della playlist")
            }
        }
    }
    
    private suspend fun completeSetup() {
        userPreferences.setSetupComplete(true)
        
        val intent = Intent(this@SetupActivity, LoadingActivity::class.java)
        startActivity(intent)
        finish()
    }
}

/**
 * Setup Screen with Modern Glassmorphism Design
 */
@Composable
private fun SetupScreen(
    currentTab: SetupTab,
    onTabChange: (SetupTab) -> Unit,
    m3uName: String,
    onM3uNameChange: (String) -> Unit,
    m3uUrl: String,
    onM3uUrlChange: (String) -> Unit,
    xtreamName: String,
    onXtreamNameChange: (String) -> Unit,
    xtreamServer: String,
    onXtreamServerChange: (String) -> Unit,
    xtreamUsername: String,
    onXtreamUsernameChange: (String) -> Unit,
    xtreamPassword: String,
    onXtreamPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    loadingProgress: Float,
    errorMessage: String?,
    successMessage: String?,
    sessionCode: String,
    qrCodeBitmap: Bitmap?,
    isWaitingForData: Boolean,
    onContinue: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    // Request focus on first field (only for form tabs)
    LaunchedEffect(currentTab) {
        if (currentTab != SetupTab.QR_CODE) {
            delay(100)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        // Animated background
        AnimatedGradientBackground()
        
        // Main card with glassmorphism
        GlassCard(
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 720.dp)  // Increased to fit all content
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),  // Reduced from 20dp
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon
                Box(
                    modifier = Modifier
                        .size(42.dp) // Reduced from 52dp
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    SandTVColors.Accent,
                                    SandTVColors.AccentDark
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LiveTv,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Title
                Text(
                    text = "Configurazione",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Aggiungi la tua playlist IPTV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(10.dp))  // Reduced from 16dp
                
                // Modern Tab Selector with 3 tabs
                ThreeTabSelector(
                    currentTab = currentTab,
                    onTabChange = onTabChange,
                    enabled = !isLoading
                )
                
                Spacer(modifier = Modifier.height(10.dp))  // Reduced from 16dp
                
                // Success message
                AnimatedVisibility(
                    visible = successMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    successMessage?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                color = Color(0xFF22C55E),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Animated Form Content
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
                    },
                    label = "formContent"
                ) { tab ->
                    Column {
                        when (tab) {
                            SetupTab.M3U -> {
                                // M3U Form
                                ModernTextField(
                                    value = m3uName,
                                    onValueChange = onM3uNameChange,
                                    label = "Nome playlist",
                                    icon = Icons.AutoMirrored.Outlined.Label,
                                    focusRequester = focusRequester,
                                    enabled = !isLoading
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                ModernTextField(
                                    value = m3uUrl,
                                    onValueChange = onM3uUrlChange,
                                    label = "URL M3U",
                                    icon = Icons.Outlined.Link,
                                    enabled = !isLoading
                                )
                            }
                            SetupTab.XTREAM -> {
                                // Xtream Form
                                ModernTextField(
                                    value = xtreamName,
                                    onValueChange = onXtreamNameChange,
                                    label = "Nome playlist",
                                    icon = Icons.AutoMirrored.Outlined.Label,
                                    focusRequester = focusRequester,
                                    enabled = !isLoading
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                ModernTextField(
                                    value = xtreamServer,
                                    onValueChange = onXtreamServerChange,
                                    label = "Server URL",
                                    icon = Icons.Outlined.Dns,
                                    enabled = !isLoading
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                ModernTextField(
                                    value = xtreamUsername,
                                    onValueChange = onXtreamUsernameChange,
                                    label = "Username",
                                    icon = Icons.Outlined.Person,
                                    enabled = !isLoading
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                ModernTextField(
                                    value = xtreamPassword,
                                    onValueChange = onXtreamPasswordChange,
                                    label = "Password",
                                    icon = Icons.Outlined.Lock,
                                    isPassword = true,
                                    enabled = !isLoading
                                )
                            }
                            SetupTab.QR_CODE -> {
                                // QR Code Tab
                                QRCodeSetupContent(
                                    sessionCode = sessionCode,
                                    qrCodeBitmap = qrCodeBitmap,
                                    isWaitingForData = isWaitingForData
                                )
                            }
                        }
                    }
                }
                
                // Error message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SandTVColors.Error.copy(alpha = 0.15f))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Error,
                                contentDescription = null,
                                tint = SandTVColors.Error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = SandTVColors.Error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))  // Reduced from 16dp
                
                // Add Playlist Button (hide on QR tab)
                if (currentTab != SetupTab.QR_CODE) {
                    AddPlaylistButton(
                        isLoading = isLoading,
                        loadingProgress = loadingProgress,
                        onClick = onContinue
                    )
                }
            }
        }
    }
}

/**
 * QR Code Setup Content
 */
@Composable
private fun QRCodeSetupContent(
    sessionCode: String,
    qrCodeBitmap: Bitmap?,
    isWaitingForData: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Instructions
        Text(
            text = "Scansiona con il cellulare",
            style = MaterialTheme.typography.titleMedium,
            color = SandTVColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Compila i dati comodamente dal telefono",
            style = MaterialTheme.typography.bodySmall,
            color = SandTVColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp)) // Reduced from 24dp
        
        // QR Code
        qrCodeBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .size(180.dp) // Reduced from 220dp
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
        } ?: Box(
            modifier = Modifier
                .size(180.dp) // Reduced from 220dp
                .clip(RoundedCornerShape(16.dp))
                .background(SandTVColors.BackgroundTertiary),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = SandTVColors.Accent,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp)) // Reduced from 20dp
        
        // Session code display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SandTVColors.BackgroundTertiary.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Codice: ",
                color = SandTVColors.TextSecondary,
                fontSize = 14.sp
            )
            Text(
                text = sessionCode.ifEmpty { "------" },
                color = SandTVColors.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp)) // Reduced from 20dp
        
        // Waiting indicator
        if (isWaitingForData) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(alpha)
            ) {
                CircularProgressIndicator(
                    color = SandTVColors.Accent,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "In attesa di dati dal cellulare...",
                    color = SandTVColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Three Tab Selector with sliding indicator
 */
@Composable
private fun ThreeTabSelector(
    currentTab: SetupTab,
    onTabChange: (SetupTab) -> Unit,
    enabled: Boolean
) {
    val indicatorPosition by animateFloatAsState(
        targetValue = when (currentTab) {
            SetupTab.M3U -> 0f
            SetupTab.XTREAM -> 1f
            SetupTab.QR_CODE -> 2f
        },
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "tabIndicator"
    )
    
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SandTVColors.BackgroundDark.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        // Calculate tab width based on available space (minus padding)
        val tabWidth = (maxWidth - 8.dp) / 3
        val indicatorOffset = tabWidth * indicatorPosition
        
        // Sliding indicator - properly clipped
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(tabWidth)
                .offset(x = indicatorOffset)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            SandTVColors.Accent,
                            SandTVColors.AccentDark
                        )
                    )
                )
        )
        
        // Tab buttons
        Row(modifier = Modifier.fillMaxSize()) {
            TabButton(
                text = "M3U",
                icon = Icons.Outlined.Link,
                isSelected = currentTab == SetupTab.M3U,
                onClick = { if (enabled) onTabChange(SetupTab.M3U) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "Xtream",
                icon = Icons.Outlined.Cloud,
                isSelected = currentTab == SetupTab.XTREAM,
                onClick = { if (enabled) onTabChange(SetupTab.XTREAM) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "QR",
                icon = Icons.Outlined.QrCode,
                isSelected = currentTab == SetupTab.QR_CODE,
                onClick = { if (enabled) onTabChange(SetupTab.QR_CODE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}


/**
 * Animated gradient background with floating orbs
 */
@Composable
private fun AnimatedGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bgAnim")
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Primary glow orb
        Box(
            modifier = Modifier
                .size(800.dp)
                .offset(x = offsetX.dp, y = offsetY.dp)
                .align(Alignment.Center)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SandTVColors.Accent.copy(alpha = 0.12f),
                            SandTVColors.Accent.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Secondary orb
        Box(
            modifier = Modifier
                .size(500.dp)
                .offset(x = (-offsetX * 0.6f).dp, y = (-offsetY * 0.8f).dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

/**
 * Glassmorphism Card
 */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        SandTVColors.BackgroundSecondary.copy(alpha = 0.85f),
                        SandTVColors.BackgroundSecondary.copy(alpha = 0.75f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        content()
    }
}

/**
 * Modern Tab Selector with sliding indicator
 */
@Composable
private fun ModernTabSelector(
    isM3UMode: Boolean,
    onTabChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    val indicatorOffset by animateFloatAsState(
        targetValue = if (isM3UMode) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "tabIndicator"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SandTVColors.BackgroundDark.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        // Sliding indicator
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .offset(x = (indicatorOffset * 220).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            SandTVColors.Accent,
                            SandTVColors.AccentDark
                        )
                    )
                )
        )
        
        // Tab buttons
        Row(modifier = Modifier.fillMaxSize()) {
            TabButton(
                text = "M3U / URL",
                icon = Icons.Outlined.Link,
                isSelected = isM3UMode,
                onClick = { if (enabled) onTabChange(true) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "Xtream",
                icon = Icons.Outlined.Cloud,
                isSelected = !isM3UMode,
                onClick = { if (enabled) onTabChange(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "tabScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    SandTVColors.Accent.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else SandTVColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (isSelected) Color.White else SandTVColors.TextSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Modern Text Field with icon and glow
 */
@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val localFocusManager = LocalFocusManager.current
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> SandTVColors.Accent
            value.isNotEmpty() -> SandTVColors.BackgroundTertiary.copy(alpha = 0.8f)
            else -> SandTVColors.BackgroundTertiary.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextTertiary,
        animationSpec = tween(200),
        label = "iconColor"
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
                )
                .focusable(interactionSource = interactionSource, enabled = enabled)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    if (isFocused) SandTVColors.BackgroundTertiary.copy(alpha = 0.4f)
                    else SandTVColors.BackgroundDark.copy(alpha = 0.6f)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = TextStyle(
                    color = SandTVColors.TextPrimary,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(SandTVColors.Accent),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { localFocusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = SandTVColors.TextHint,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

/**
 * Add Playlist Button with integrated progress bar
 */
@Composable
private fun AddPlaylistButton(
    isLoading: Boolean,
    loadingProgress: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Rotating loading phrase
    var currentPhraseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (true) {
                delay(2500)
                currentPhraseIndex = (currentPhraseIndex + 1) % setupLoadingPhrases.size
            }
        } else {
            currentPhraseIndex = 0
        }
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = loadingProgress,
        animationSpec = tween(200),
        label = "progressAnim"
    )
    
    val scale by animateFloatAsState(
        targetValue = when {
            isFocused && !isLoading -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.7f),
        label = "buttonScale"
    )
    
    val buttonHeight by animateDpAsState(
        targetValue = if (isLoading) 100.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "buttonHeight"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isLoading) {
                    Modifier.background(SandTVColors.BackgroundTertiary.copy(alpha = 0.6f))
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                SandTVColors.Accent,
                                SandTVColors.AccentDark
                            )
                        )
                    )
                }
            )
            .then(
                if (isFocused && !isLoading) Modifier.border(
                    2.dp,
                    SandTVColors.AccentLight,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .focusable(interactionSource = interactionSource, enabled = !isLoading)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            label = "buttonContent"
        ) { loading ->
            if (loading) {
                // Loading state with progress
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Fun phrase
                    Text(
                        text = setupLoadingPhrases[currentPhraseIndex],
                        color = SandTVColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SandTVColors.BackgroundDark.copy(alpha = 0.5f))
                    ) {
                        // Shimmer effect
                        val shimmer = rememberInfiniteTransition(label = "shimmer")
                        val shimmerOffset by shimmer.animateFloat(
                            initialValue = -1f,
                            targetValue = 2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "shimmerOffset"
                        )
                        
                        // Progress fill
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress.coerceIn(0.05f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            SandTVColors.Accent,
                                            SandTVColors.AccentLight,
                                            SandTVColors.Accent
                                        ),
                                        startX = shimmerOffset * 300f,
                                        endX = (shimmerOffset + 1f) * 300f
                                    ),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Percentage
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = SandTVColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Normal button state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Aggiungi Playlist",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
