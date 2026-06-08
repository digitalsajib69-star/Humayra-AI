package com.example

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.BusinessPage
import com.example.database.ChatMessage
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewState
import com.example.viewmodel.AssistantViewModel
import com.example.ui.DigitalOfflineMapView
import com.example.ui.PhoneLookupCard
import com.example.ui.HumayraAnimatedAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import android.webkit.WebView
import android.webkit.WebViewClient
import android.annotation.SuppressLint
import androidx.compose.ui.graphics.graphicsLayer

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private val viewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Android TextToSpeech engine with designated AppOps attribution tag context
        val ttsCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            createAttributionContext("text_to_speech")
        } else {
            this
        }
        tts = TextToSpeech(ttsCtx, this)

        setContent {
            MyApplicationTheme {
                // Collect stateflows
                val viewState by viewModel.viewState.collectAsStateWithLifecycle()
                val apiLoading by viewModel.apiLoading.collectAsStateWithLifecycle()
                val isCreatingPage by viewModel.isCreatingPage.collectAsStateWithLifecycle()
                val pageCreationStep by viewModel.pageCreationStep.collectAsStateWithLifecycle()
                val currentExpression by viewModel.currentExpression.collectAsStateWithLifecycle()
                val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
                val ttsPromptToSpeak by viewModel.ttsPromptToSpeak.collectAsStateWithLifecycle()

                // Trigger TTS speak out loud if a prompt is received
                LaunchedEffect(ttsPromptToSpeak) {
                    ttsPromptToSpeak?.let { text ->
                        speakBengali(text)
                        viewModel.consumeTtsPrompt()
                    }
                }

                 Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // High fidelity moving neon/studio wave video loop background
                        com.example.MagicalBackgroundVideoEffect()

                        var isSplashActive by remember { mutableStateOf(true) }
                        LaunchedEffect(Unit) {
                            delay(3500) // 3.5 seconds
                            isSplashActive = false
                        }

                        if (isSplashActive) {
                            com.example.SajibStudioSplashScreen()
                        } else {
                            Crossfade(targetState = viewState, label = "AppScreens") { screen ->
                                when (screen) {
                                    is AppViewState.Welcome -> {
                                        WelcomeScreen(
                                            onContinue = {
                                                viewModel.navigateTo(AppViewState.Login)
                                            }
                                        )
                                    }
                                    is AppViewState.Login -> {
                                        LoginScreen(
                                            viewModel = viewModel,
                                            onLoginSuccess = {
                                                viewModel.navigateTo(AppViewState.Dashboard)
                                                viewModel.playWelcomeGreeting()
                                            }
                                        )
                                    }
                                    is AppViewState.Dashboard -> {
                                        DashboardWorkspace(
                                            viewModel = viewModel,
                                            apiLoading = apiLoading,
                                            isCreatingPage = isCreatingPage,
                                            pageCreationStep = pageCreationStep,
                                            currentExpression = currentExpression,
                                            isSpeaking = isSpeaking
                                        )
                                    }
                                }
                            }

                            // Virtual assistant Humayra floats and runs around the entire app!
                            FloatingHumayraAssistant(
                                viewModel = viewModel,
                                onSpeakRequested = { text -> speakBengali(text) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Setup Bengali (BD or India fallback)
            val result = tts.setLanguage(Locale("bn", "BD"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val altResult = tts.setLanguage(Locale("bn", "IN"))
                if (altResult == TextToSpeech.LANG_MISSING_DATA || altResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Bengali language pack is not verified/supported on this device.")
                }
            }

            // Set progress listener to coordinate mouth soundwave lip sync
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { viewModel.setSpeaking(true) }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread { viewModel.setSpeaking(false) }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread { viewModel.setSpeaking(false) }
                }
            })
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    private fun playCustomChime() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 120) // sweet short beep
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun speakBengali(text: String) {
        playCustomChime()
        if (::tts.isInitialized) {
            // Filter emojis out to prevent TTS from reading out code names
            val cleanText = text.replace(Regex("[\\p{So}\\p{Cn}]"), "")
            tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "HUMAYRA_SPEECH")
        } else {
            // fallback UI speaking status simulation if TTS fails
            viewModel.setSpeaking(true)
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    viewModel.setSpeaking(false)
                }, 3000)
            } catch (e: Exception) {
                viewModel.setSpeaking(false)
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}

// ----------------------------------------------------------------------------
// WELCOME / ONBOARDING SCREEN
// ----------------------------------------------------------------------------
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F081D), Color(0xFF1E0E38), Color(0xFF07040C))
                )
            )
            .padding(24.dp)
            .safeDrawingPadding()
    ) {
        // Aesthetic sparkling grid canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(0xFF4C1087).copy(alpha = 0.15f), radius = 250f, center = center)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Humayra Title with stardust accent
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "HUMAYRA AI",
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    color = Color(0xFFD0BCFF),
                    letterSpacing = 4.sp
                )
                Text(
                    text = "হুমায়রা এআই অ্যাসিস্ট্যান্ট",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color(0xFFCCC2DC).copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Humayra Avatar Portrait Card
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Pulsing outer pink glow halo
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .drawBehind {
                            drawCircle(
                                color = Color(0xFFEC407A).copy(alpha = haloAlpha),
                                radius = (size.minDimension / 2f) * haloScale,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                )

                // Main Avatar Portrait
                Image(
                    painter = painterResource(id = R.drawable.img_humayra),
                    contentDescription = "Humayra AI Assistant",
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .border(4.dp, Brush.linearGradient(listOf(Color(0xFF80DEEA), Color(0xFFEC407A))), CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Welcoming speech bubble styled container
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C132E)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "হাই, আসসালামু আলাইকুম! 💖",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFD0BCFF),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "আমি ডিজিটাল সজীব রেকর্ডিং স্টুডিও এর বিজ্ঞাপন এবং অ্যাপস তৈরির জাদুকর সজীব হাসান (Sajib Hasan) এর তৈরি এআই ভয়েস অ্যাসিস্ট্যান্ট ‘হুমায়রা’ বলছি! আমি আপনাকে বাংলায় সাহায্য করব।",
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50.dp)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "অ্যাসিস্ট্যান্ট কনফিগার করুন",
                        color = Color(0xFF07040C),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Arrow Icon",
                        tint = Color(0xFF07040C)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ----------------------------------------------------------------------------
// LOGIN / ATHENTICATION SCREEN
// ----------------------------------------------------------------------------
@Composable
fun LoginScreen(viewModel: AssistantViewModel, onLoginSuccess: () -> Unit) {
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(userName) }
    var isEditing by remember { mutableStateOf(false) }

    // Tab Selection: 0 = Quick Login (NC Sajib Hasan), 1 = Any Facebook login
    var selectedLoginType by remember { mutableStateOf(0) }

    // Manual inputs
    var mobileOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var manualProfileName by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF090B1A), Color(0xFF091F44), Color(0xFF01040A))
                )
            )
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Facebook logo representation using circular styled design
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1877F2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "f",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    modifier = Modifier.offset(y = (-4).dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "মেটা অথরাইজেশন পোর্টাল",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
            Text(
                text = "যেকোনো নম্বর ও পাসওয়ার্ড দিয়ে সরাসরি লগইন করুন",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Switch Tab selector (Facebook blue styled)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedLoginType == 0) Color(0xFF1877F2) else Color.Transparent)
                        .clickable { selectedLoginType = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ডিফল্ট আইডি",
                        color = if (selectedLoginType == 0) Color.White else Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedLoginType == 1) Color(0xFF1877F2) else Color.Transparent)
                        .clickable { selectedLoginType = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "অন্য অ্যাকাউন্ট",
                        color = if (selectedLoginType == 1) Color.White else Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedLoginType == 0) {
                // Quick Login Card (Sajib Hasan profile)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                    border = BorderStroke(1.5.dp, Color(0xFF1877F2).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.size(110.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_sajib),
                                contentDescription = "Sajib Hasan profile picture",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, Color(0xFF1877F2), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            // Verified badge mockup
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1877F2))
                                    .border(1.5.dp, Color(0xFF0D1B2A), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified badge",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isEditing) {
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = {
                                    editingName = it
                                    viewModel.updateUserName(it)
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                label = { Text("আপনার নাম লিখুন", color = Color(0xFF1877F2)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1877F2),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = Color(0xFF1877F2)
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                            Button(
                                onClick = { isEditing = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text("নাম সংরক্ষণ করুন", color = Color.White)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable { isEditing = true }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = userName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit name",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "ডিজিটাল ক্রিয়েটর · স্টুডেন্ট আইডি",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Bio icon",
                                    tint = Color(0xFF1877F2),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "আমি কখনো হারিনি- হয় জিতেছে -না হয় শিখেছি",
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Location icon",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Narsingdi, Bangladesh",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Default entry log button
                Button(
                    onClick = onLoginSuccess,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Login Key",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$userName হিসেবে প্রবেশ করুন",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Manual Facebook login panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                    border = BorderStroke(1.5.dp, Color(0xFF1877F2).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "ফেসবুক প্রোফাইল সংযোগ করুন",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        // Mobile or Email field
                        OutlinedTextField(
                            value = mobileOrEmail,
                            onValueChange = { 
                                mobileOrEmail = it
                                validationError = ""
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            label = { Text("মোবাইল নম্বর অথবা ইমেল", color = Color.White.copy(alpha = 0.6f)) },
                            placeholder = { Text("যেমন: ০১৭০০০০০০০০", color = Color.White.copy(alpha = 0.3f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1877F2),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color(0xFF1877F2)
                            ),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Phone Icon",
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { 
                                password = it
                                validationError = ""
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            label = { Text("পাসওয়ার্ড", color = Color.White.copy(alpha = 0.6f)) },
                            placeholder = { Text("ফেসবুক পাসওয়ার্ড লিখুন", color = Color.White.copy(alpha = 0.3f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1877F2),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color(0xFF1877F2)
                            ),
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password Icon",
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .clickable { isPasswordVisible = !isPasswordVisible }
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (isPasswordVisible) "লুকান" else "দেখুন",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Custom Profile Name for layout personalization
                        OutlinedTextField(
                            value = manualProfileName,
                            onValueChange = { 
                                manualProfileName = it
                                validationError = ""
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            label = { Text("ফেসবুক প্রোফাইল নাম লিখুন", color = Color.White.copy(alpha = 0.6f)) },
                            placeholder = { Text("যেমন: সজীব হাসান", color = Color.White.copy(alpha = 0.3f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1877F2),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color(0xFF1877F2)
                            ),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Account Icon",
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Error Output
                        if (validationError.isNotEmpty()) {
                            Text(
                                text = validationError,
                                color = Color(0xFFEC407A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                if (mobileOrEmail.isBlank()) {
                                    validationError = "মোবাইল নম্বর বা ইমেইল প্রদান করুন!"
                                } else if (password.length < 4) {
                                    validationError = "পাসওয়ার্ড অন্তত ৪ অক্ষরের হতে হবে!"
                                } else {
                                    val finalName = if (manualProfileName.isNotBlank()) manualProfileName else "ফেসবুক ব্যবহারকারী"
                                    viewModel.updateUserName(finalName)
                                    onLoginSuccess()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "লগইন করুন",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Extra Options Decorators
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "পাসওয়ার্ড ভুলে গেছেন?",
                        color = Color(0xFF1877F2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { }
                            .padding(vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "অ্যাসিস্ট্যান্ট পোর্টাল ডাটা সিঙ্ক হচ্ছে",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ----------------------------------------------------------------------------
// MAIN DASHBOARD & WORKSPACE CONTAINER
// ----------------------------------------------------------------------------
@Composable
fun DashboardWorkspace(
    viewModel: AssistantViewModel,
    apiLoading: Boolean,
    isCreatingPage: Boolean,
    pageCreationStep: String,
    currentExpression: String,
    isSpeaking: Boolean
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = ড্যাশবোর্ড, 1 = হুমায়রা চ্যাট, 2 = টিউটোরিয়াল

    val currentDateStrMap = remember {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        "FB123456_${sdf.format(Date())}"
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F081D),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, "ড্যাশবোর্ড") },
                    label = { Text("ড্যাশবোর্ড") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF381E72),
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Person, "হুমায়রা চ্যাট") },
                    label = { Text("হুমায়রা চ্যাট") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF381E72),
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.PlayArrow, "টিউটোরিয়াল") },
                    label = { Text("টিউটোরিয়াল") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF381E72),
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            }
        },
        containerColor = Color(0xFF050309)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF050309))
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "workspace-swapper"
            ) { tab ->
                when (tab) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        currentBusinessId = currentDateStrMap,
                        isCreatingPage = isCreatingPage,
                        pageCreationStep = pageCreationStep
                    )
                    1 -> ChatTab(
                        viewModel = viewModel,
                        apiLoading = apiLoading,
                        currentExpression = currentExpression,
                        isSpeaking = isSpeaking
                    )
                    2 -> TutorialTab()
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 1: USER DASHBOARD TAB
// ----------------------------------------------------------------------------
@Composable
fun DashboardTab(
    viewModel: AssistantViewModel,
    currentBusinessId: String,
    isCreatingPage: Boolean,
    pageCreationStep: String
) {
    val businessPages by viewModel.businessPages.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val displayName = remember(userName) {
        val clean = userName.replace("NC ", "").replace(" Hasan", "")
        if (clean.isBlank()) "সজীব" else clean
    }
    var activeSubTab by remember { mutableStateOf(0) } // 0 = মেটা বিজনেস পেজ, 1 = ফ্রেন্ড ট্র্যাকার ম্যাপ, 2 = আইডি ও ওয়ালেট গেটওয়ে, 3 = ফেসবুক লগইন

    val greetingHeaderContent = @Composable {
        // Upper Greeting card with Sajib's image
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D24).copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_sajib),
                    contentDescription = "Sajib Hasan icon",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "স্বাগতম $displayName ভাই! 🌹",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "ডিজিটাল সজীব রেকর্ডিং স্টুডিও",
                        fontSize = 12.sp,
                        color = Color(0xFF80DEEA),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "বিজনেস পোর্টাল: सक्रिय",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Persistent Segmented switcher row for Sajib's sub-tools
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF130E26).copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    0 to "মেটা পেজ",
                    1 to "ফ্রেন্ড ম্যাপ",
                    2 to "ওয়ালেট স্ক্যানার",
                    3 to "ফেসবুক লগইন"
                ).forEach { (idx, label) ->
                    val isSelected = activeSubTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { activeSubTab = idx }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (activeSubTab == 1 || activeSubTab == 3) {
        // NON-SCROLL viewport, perfect for Digital Live Map Tracker gestures and WebView!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            greetingHeaderContent()

            if (activeSubTab == 1) {
                // Immersive Zoomable / Multitouch Draggable Map
                DigitalOfflineMapView()
            } else {
                com.example.FacebookWebView()
            }
        }
    } else {
        // SCROLL layout for page creation and heavy list of tables
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            greetingHeaderContent()

            when (activeSubTab) {
                0 -> {
                    // --------------------------------------------------------
                    // DIGITAL SAJIB STUDIO ADVERTISEMENT PROMOTION CARD
                    // --------------------------------------------------------
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1145).copy(alpha = 0.8f)),
                        border = BorderStroke(1.5.dp, Color(0xFFE040FB)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_sajib),
                                    contentDescription = "NC Sajib Hasan Studio Owner",
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF00E5FF), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "ডিজিটাল সজীব রেকর্ডিং স্টুডিও 🎵",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "প্রোপ্রাইটর: জাদুকর সজীব হাসান",
                                        fontSize = 12.sp,
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "আমাদের বিশেষ জাদুকরী সেবাসমূহ:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE040FB)
                            )

                            // Services list
                            val services = listOf(
                                "🎬 যেকোনো ম্যাজিক ও অ্যাডভার্টাইজিং ভিডিও তৈরি",
                                "✍️ জাদুকরী লেখা এবং ডিজাইন ক্যাটাগরি",
                                "🌐 অরিজিনাল ফেসবুকে অটো-কানেক্ট ও রিয়েল পোস্ট সাপোর্ট",
                                "📍 রিয়েল-টাইম ফ্রেন্ড লোকেশন ট্র্যাকিং ডিজিটাল ম্যাপ"
                            )
                            services.forEach { svc ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = svc,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "যোগাযোগ (WhatsApp / IMO):",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.Green)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "জিপি: 017 14410 528",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = Color.Green
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.Green)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "রবি: 01897 238 395",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = Color.Green
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ঠিকানা: বিলাশদী, নরসিংদী, ঢাকা।",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Previous Meta Business Creator tools
                    // Meta Business ID Info Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0B16)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ফেইসবুক মেটা বিজনেস লিংক",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "লাইভ কানেক্ট",
                                        fontSize = 10.sp,
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "অটো বিজনেস আইডি",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = currentBusinessId,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "সার্ভার টোকেন",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "META_LIVE_V22.0",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Create My Page Trigger
                    Button(
                        onClick = { viewModel.createMetaPage() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isCreatingPage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add page icon",
                                tint = Color(0xFF381E72)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "মেটা পেজ তৈরি করুন (Create My Page)",
                                color = Color(0xFF381E72),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Meta Page API Loader Dialogue overlay
                    if (isCreatingPage) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF261942)),
                            border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00E5FF),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        text = "মেটা সার্ভারে কমান্ড প্রেরণ করা হচ্ছে...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "স্ট্যাটাস: $pageCreationStep",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFF80DEEA),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Created pages result section
                    Text(
                        text = "তৈরি কৃত মেটা পেজ সমূহ (${businessPages.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (businessPages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Empty state icon",
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "এখনও কোনো পেজ ক্রিয়েট করা হয়নি।",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "‘Create My Page’ বাটনে চাপ দিয়ে পেজ তৈরি করুন।",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (page in businessPages) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13111C)),
                                    border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = page.name,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "ক্যাটাগরি: ${page.category}",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "পেজ আইডি: ${page.pageId}",
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Green.copy(alpha = 0.1f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "সাকসেস",
                                                fontSize = 11.sp,
                                                color = Color.Green,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // ID & Wallet Verification Scanner segment card
                    PhoneLookupCard(viewModel = viewModel)
                }
            }

            // Reset App utility button
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { viewModel.resetApp() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart simulation",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "অ্যাপ রিসেট করুন (Reset Simulation)",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 2: ACTIVE AI CHATBOT ROOM (HUMAYRA INTERACTIVE)
// ----------------------------------------------------------------------------
@Composable
fun ChatTab(
    viewModel: AssistantViewModel,
    apiLoading: Boolean,
    currentExpression: String,
    isSpeaking: Boolean
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentInput.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()

    var showHistorySearch by remember { mutableStateOf(false) }
    var historySearchQuery by remember { mutableStateOf("") }
    var historySenderFilter by remember { mutableStateOf("all") } // "all", "user", "humayra"

    // Auto scroll down to the bottom of the chat list on new messages
    LaunchedEffect(chatMessages.size, apiLoading) {
        if (chatMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatMessages.size)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // TOP AVATAR PANEL WITH GLOWING HALO & VOICE EXPRESSION WAVE
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF110B22)),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            border = BorderStroke(1.dp, Color(0xFFEC407A).copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // History search top utility bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "হুমায়রা এআই এসিস্ট্যান্ট",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showHistorySearch = !showHistorySearch },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showHistorySearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search history",
                            tint = if (showHistorySearch) Color(0xFFEC407A) else Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Interactive Face Avatar Frame
                HumayraAnimatedAvatar(
                    currentExpression = currentExpression,
                    isSpeaking = isSpeaking
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Expression Info text
                val expressionBnLabel = when (currentExpression) {
                    "speaking" -> "🗣️ Humayra (হুমায়রা) কথা বলছে..."
                    "thinking" -> "🤔 Humayra গভীর মনোযোগে চিন্তা করছে..."
                    "happy" -> "💖 Humayra হাস্যোজ্জ্বল মুখে কথা বলছে..."
                    else -> "😊 Humayra শান্তভাবে শুনছে..."
                }

                Text(
                    text = expressionBnLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = when (currentExpression) {
                        "speaking" -> Color(0xFFEC407A)
                        "thinking" -> Color(0xFF00E5FF)
                        "happy" -> Color(0xFFFFD54F)
                        else -> Color(0xFFD0BCFF)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // LIP SYNC SOUNDWAVE VISUALIZER OVERLAY COMPOSABLE
                SoundWaveVisualizer(isSpeaking = isSpeaking)
            }
        }

        // Floating history search results panel
        AnimatedVisibility(
            visible = showHistorySearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF140D24)),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "কথোপকথনের ব্যাকলগ ও অনুসন্ধান 🔍",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Search Input
                    OutlinedTextField(
                        value = historySearchQuery,
                        onValueChange = { historySearchQuery = it },
                        placeholder = { Text("কি খুঁজতে চান লিখুন (যেমন: মেটা)...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f)) },
                        leadingIcon = { Icon(Icons.Default.Search, "history search icon", modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color(0xFF0E081A),
                            unfocusedContainerColor = Color(0xFF0E081A)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Sender Filter Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("all" to "সব বার্তা", "user" to "সজীব হাসান", "humayra" to "হুমায়রা এআই").forEach { (filterKey, label) ->
                            val isSelected = historySenderFilter == filterKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .clickable { historySenderFilter = filterKey }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Filter logic
                    val filteredHistory = remember(chatMessages, historySearchQuery, historySenderFilter) {
                        chatMessages.filter { msg ->
                            val matchQuery = if (historySearchQuery.isBlank()) true else msg.text.contains(historySearchQuery, ignoreCase = true)
                            val matchSender = if (historySenderFilter == "all") true else msg.sender == historySenderFilter
                            matchQuery && matchSender
                        }
                    }
                    
                    if (filteredHistory.isEmpty()) {
                        Text(
                            text = "কোনো বার্তা ম্যাচিং পাওয়া যায়নি!",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "ম্যাচিং ফলাফল (${filteredHistory.size}):",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 140.dp)
                        ) {
                            items(filteredHistory) { msg ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            // Fill input for quick retry
                                            viewModel.updateInput(msg.text)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (msg.sender == "user") "NC Sajib Hasan" else "Humayra AI",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (msg.sender == "user") Color(0xFF00E5FF) else Color(0xFFEC407A)
                                            )
                                            val timeStr = SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
                                            Text(
                                                text = timeStr,
                                                fontSize = 9.sp,
                                                color = Color.White.copy(alpha = 0.3f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msg.text,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DIALOG CHAT BUBBLE STREAM
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isEmpty() && !apiLoading) {
                // Friendly AI onboarding layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Empty chat",
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(68.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "হুমায়রা এআই চ্যাট রুম",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "নিচের টেক্সট ফিল্ডে বাংলায় যেকোনো প্রশ্ন লিখুন সজীব ভাই, হুমায়রা কৃত্রিম বুদ্ধিমত্তার শক্তিতে সেকেন্ডে উত্তর দেবে।",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Spark prompt suggestions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13111C)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.updateInput("আসসালামু আলাইকুম হুমায়রা, তুমি কেমন আছ?")
                                }
                        ) {
                            Text(
                                text = "“কেমন আছ?”",
                                fontSize = 12.sp,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13111C)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.updateInput("আমার ফেসবুক মেটা পেজ কিভাবে তৈরি করব?")
                                }
                        ) {
                            Text(
                                text = "“পেজ তৈরি করব?”",
                                fontSize = 12.sp,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { message ->
                        ChatBubbleItem(message = message)
                    }

                    if (apiLoading) {
                        // Humayra thinking response indicator
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C112C)),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF00E5FF),
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "হুমায়রা উত্তরটি ভাবছে...",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FIELD CHAT INPUT BAR
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0717)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = currentInput,
                    onValueChange = { viewModel.updateInput(it) },
                    placeholder = { Text("হুমায়রাকে কিছু বলুন...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1C132E),
                        unfocusedContainerColor = Color(0xFF150D24),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF00E5FF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                IconButton(
                    onClick = { viewModel.sendMessage() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                        .size(48.dp),
                    enabled = currentInput.isNotBlank() && !apiLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send message",
                        tint = Color(0xFF050309)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// AUDIO SPEECH ANIMATING SOUNDWAVE VISUALIZER
// ----------------------------------------------------------------------------
@Composable
fun SoundWaveVisualizer(isSpeaking: Boolean) {
    val barCount = 12
    val heightsRatio = remember { listOf(0.15f, 0.4f, 0.7f, 0.85f, 0.6f, 0.95f, 0.5f, 0.9f, 0.45f, 0.65f, 0.35f, 0.1f) }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val infiniteTransition = rememberInfiniteTransition(label = "wavebar")
            val animatedHeightScale by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = heightsRatio[i],
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + (i * 45), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale-$i"
            )

            val currentHeight = if (isSpeaking) animatedHeightScale else 0.1f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(currentHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFFEC407A))
                        )
                    )
            )
        }
    }
}

// ----------------------------------------------------------------------------
// CHAT BUBBLE ITEM LIST BUILDER
// ----------------------------------------------------------------------------
@Composable
fun ChatBubbleItem(message: ChatMessage) {
    val isUser = message.sender == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Humayra little rounded avatar beside her messages
            Image(
                painter = painterResource(id = R.drawable.img_humayra),
                contentDescription = "Humayra profile",
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF00E5FF), CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF176B87) else Color(0xFF140E24)
            ),
            shape = if (isUser) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
            },
            border = if (isUser) null else BorderStroke(1.dp, Color(0xFFEC407A).copy(alpha = 0.2f)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 21.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                // Stamp formatted readable hours
                val timeFormatted = remember(message.timestamp) {
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
                }

                Text(
                    text = timeFormatted,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isUser) TextAlign.End else TextAlign.Start
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(id = R.drawable.img_sajib),
                contentDescription = "Sajib profile",
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF00E5FF), CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 3: HELP TUTORIAL SCREEN (BENGALI USER GUIDES)
// ----------------------------------------------------------------------------
@Composable
fun TutorialTab() {
    var isPlaying by remember { mutableStateOf(false) }
    var mockTimeMs by remember { mutableStateOf(0L) }
    val totalTimeMs = 300000L // 5 minutes

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mockTimeMs < totalTimeMs) {
                delay(1000)
                mockTimeMs += 1000
            }
            if (mockTimeMs >= totalTimeMs) {
                isPlaying = false
                mockTimeMs = 0L
            }
        }
    }

    val minutes = (mockTimeMs / 1000) / 60
    val seconds = (mockTimeMs / 1000) % 60
    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper text instructions
        Text(
            text = "সহায়িকা ও টিউটোরিয়াল ফাইল",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        Text(
            text = "কিভাবে কোনো খরচ ছাড়াই হুমায়রা এআই ব্যবহার করে ফেসবুক মেটা পেজ ও স্টুডিও অটোমেশন সাজাবেন বিস্তারিত দেখুন।",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f),
            lineHeight = 20.sp
        )

        // HIGH FIDELITY MOCK MULTIMEDIA TUTORIAL PLAYER BOX
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF120E21)),
            border = BorderStroke(1.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Video visualizer container frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        // Real actual media streaming video!
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val mediaCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    ctx.createAttributionContext("webview")
                                } else {
                                    ctx
                                }
                                VideoView(mediaCtx).apply {
                                    val videoUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                                    setVideoURI(videoUri)
                                    val controller = MediaController(mediaCtx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            update = { view ->
                                view.start()
                            }
                        )

                        // Floating Stop/Pause button
                        IconButton(
                            onClick = { isPlaying = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = Color.White
                            )
                        }
                    } else {
                        // Humayra face as mock video preview backgroud
                        Image(
                            painter = painterResource(id = R.drawable.img_humayra),
                            contentDescription = "Preview cover image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.35f
                        )

                        // Big visual glass play icon
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isPlaying) 0.15f else 0.25f))
                                .border(1.5.dp, Color.White, CircleShape)
                                .clickable { isPlaying = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "State icon",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tutorial subtitle/telepresence guidelines in Bengali
                Text(
                    text = "হুমায়রা এআই দিয়ে মেটা পেজ অটোমেশন গাইড",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val subtitleStr = when {
                    mockTimeMs < 10000 -> "১. সজীব হাসান এর স্টুডিও স্পেশাল মেটা অটোমেশন অ্যাপে স্বাগতম..."
                    mockTimeMs < 20000 -> "২. ‘Create My Page’ বাটন দিয়ে আপনার পেজ স্বয়ংক্রিয়ভাবে তৈরি হবে..."
                    mockTimeMs < 30000 -> "৩. সম্পূর্ণ প্রোটোকলটি ওয়াইফাই বা ইন্টারনেটের মাধ্যমে একদম ফ্রিতে কাজ করবে..."
                    else -> "৪. চ্যাটবটের মাধ্যমে মেটা বিজনেস পেজের সেটিংস হুমায়রা এআই দিয়ে পরিচালনা করুন..."
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = subtitleStr,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF80DEEA),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }

        // List of helpful tips cards
        Text(
            text = "অতিরীক্ত টিপস ও গাইডলাইন সমূহ",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0913)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Check, "bullet", tint = Color.Green, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "অ্যাপটি চালাতে ওয়াইফাই অথবা ইন্টারনেট কানেকশন চালু রাখুন, কোনো ক্রেডিট মিনিট কাটার ভয় নেই।",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Check, "bullet", tint = Color.Green, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "হুমায়রার সাথে কথা বলার সময় আপনার স্পিকার অন রাখুন, সে আপনার যেকোনো কথা শুনে উত্তর দিতে এবং ডিরেকশন বলতে সক্ষম।",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Check, "bullet", tint = Color.Green, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ফ্রি এপিআই কি দিয়ে নিরাপদে কথা বলুন এআই স্টুডিওর সাহায্যে, কোনো সার্ভার চার্জ বা মাসিক সাবস্ক্রিপশন ফি লাগবে না।",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// FLOATING DYNAMIC VIRTUAL ASSISTANT OVERLAY
// ----------------------------------------------------------------------------
@Composable
fun FloatingHumayraAssistant(
    viewModel: AssistantViewModel,
    onSpeakRequested: (String) -> Unit
) {
    // Collect states
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    
    val displayName = remember(userName) {
        val clean = userName.replace("NC ", "").replace(" Hasan", "")
        if (clean.isBlank()) "সজীব" else clean
    }

    val messages = remember(displayName) {
        listOf(
            "আসসালামু আলাইকুম $displayName ভাই! আশা করি আপনার দিনটি খুব সুন্দর কাটছে! 💖",
            "$displayName ভাই, আমি কিন্তু আপনার ফেস বুক মেটা পেইজ তৈরি করার সব প্রোটোকল রেডি করে রেখেছি! ✨",
            "বাহ! $displayName ভাই, অ্যাপটির ইন্টারফেস সম্পূর্ণ সচল ও অনেক আকর্ষনীয় করা হয়েছে! 🎉",
            "যদি আপনার কোনো টেকনিক্যাল প্রশ্ন থাকে, তবে ড্যাশবোর্ডের হুমায়রা এআই চ্যাট রুমে চলে আসুন! 💬",
            "$displayName ভাই, স্পিকারের অডিও ভলিউম বাড়িয়ে নিন, আমি আপনাকে সুন্দর কণ্ঠে দিকনির্দেশনা দেব! 🔊",
            "আমি হুমায়রা, আপনার বুদ্ধিমান এআই কণ্ঠ সহকারী। আমি সবসময় আপনার পুরো অ্যাপে ঘুরে বেড়াচ্ছি! 🌸",
            "মেটা পেজ তৈরি করতে উপরের টিউটোরিয়াল ভিডিওটি অবশ্যই প্লে করবেন $displayName ভাই! 🎞️",
            "$displayName ভাই, আপনার বিজনেস ও রেকর্ডিং স্টুডিওর কাজের গতি বাড়াতে আমি প্রস্তুত! 🚀",
            "আপনি খুব চমৎকারভাবে অ্যাপটি পরিচালনা করছেন, $displayName ভাই! ধন্যবাদ আপনাকে! 🙏"
        )
    }

    // Wandering offset coordinates
    var targetX by remember { mutableStateOf(50f) }
    var targetY by remember { mutableStateOf(350f) }

    // Automatic wandering effect
    LaunchedEffect(Unit) {
        while (true) {
            // Select random target positions within reasonable screen boundaries
            delay(5000) // move every 5 seconds
            targetX = (20..240).random().toFloat()
            targetY = (120..500).random().toFloat()
        }
    }

    val animatedX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "xPath"
    )
    val animatedY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "yPath"
    )

    // Manual drag support to pick up and place her anywhere!
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalContext.current.resources.displayMetrics.density

    // Speech bubble state
    var speechText by remember { mutableStateOf("") }
    var showBubble by remember { mutableStateOf(false) }

    // Auto fade out speech bubble after 5 seconds
    LaunchedEffect(speechText) {
        if (speechText.isNotEmpty()) {
            showBubble = true
            delay(5000)
            showBubble = false
        }
    }

    val finalX = animatedX + dragOffsetX
    val finalY = animatedY + dragOffsetY

    Box(
        modifier = Modifier
            .offset(x = finalX.dp, y = finalY.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x / density
                    dragOffsetY += dragAmount.y / density
                }
            }
            .size(115.dp)
    ) {
        // Floating Humayra image bubble with glow
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Speech Balloon overlayed above
            AnimatedVisibility(
                visible = showBubble && speechText.isNotEmpty(),
                enter = fadeIn() + expandIn(),
                exit = fadeOut() + shrinkOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1035)),
                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(170.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = speechText,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(
                            2.5.dp,
                            Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFEC407A)))
                        ),
                        CircleShape
                    )
                    .background(Color(0xFF0F081D))
                    .clickable {
                        // Choose random sweet message
                        val msg = messages.random()
                        speechText = msg
                        onSpeakRequested(msg)
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_humayra),
                    contentDescription = "Humayra floating virtual assistant",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // If speaking, show audio wave overlay tint
                if (isSpeaking) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEC407A).copy(alpha = 0.25f))
                    )
                }
            }
            
            // Subtitle tag
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEC407A)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "হুমায়রা এআই",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// HIGH FIDELITY GEOMETRIC LOOP SIMULATOR (MAGICAL BACKDROP VIDEO EFFECT)
// ----------------------------------------------------------------------------
@Composable
fun MagicalBackgroundVideoEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "videoLoop")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Fill with space slate dark
        drawRect(color = Color(0xFF040209))

        // Draw animated wave lines that emulate futuristic studio loop visuals
        val path1 = Path()
        val path2 = Path()

        path1.moveTo(0f, height * 0.4f)
        path2.moveTo(0f, height * 0.6f)

        for (x in 0..width.toInt() step 20) {
            val angle1 = (x / width) * 2 * Math.PI + (time / 100f)
            val angle2 = (x / width) * 3 * Math.PI - (time / 80f)
            
            val y1 = height * 0.4f + Math.sin(angle1).toFloat() * 120f
            val y2 = height * 0.6f + Math.cos(angle2).toFloat() * 150f

            path1.lineTo(x.toFloat(), y1)
            path2.lineTo(x.toFloat(), y2)
        }

        path1.lineTo(width, height)
        path1.lineTo(0f, height)
        path1.close()

        // Gradient colors for magic video vibes
        drawPath(
            path = path1,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF26123D).copy(alpha = 0.35f),
                    Color(0xFF05010C).copy(alpha = 0.6f)
                )
            )
        )

        drawPath(
            path = path2,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.08f),
                    Color(0xFFE040FB).copy(alpha = 0.12f)
                )
            )
        )

        // Draw some floating glowing stardust orbits
        for (i in 1..8) {
            val offsetAngle = i * (Math.PI / 4) + (time / 120f)
            val cx = width / 2f + Math.cos(offsetAngle).toFloat() * (220f + i * 40f)
            val cy = height / 3f + Math.sin(offsetAngle).toFloat() * (180f + i * 30f)
            val radius = 12f + (i % 3) * 6f
            
            drawCircle(
                color = if (i % 2 == 0) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFFE040FB).copy(alpha = 0.15f),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// HOME ENTRY DELAY SPLASH SCREEN (NC SAJIB HASAN STUDIO PROMO)
// ----------------------------------------------------------------------------
@Composable
fun SajibStudioSplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05030C)),
        contentAlignment = Alignment.Center
    ) {
        // Glowing animated space loops background
        MagicalBackgroundVideoEffect()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "WELCOME TO",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFF00E5FF),
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "DIGITAL SAJIB RECORDING STUDIO",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            Text(
                text = "ডিজিটাল সজীব রেকর্ডিং স্টুডিও",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFFE040FB),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sajib Portrait Frame with Pulsing Magic Lights
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
            ) {
                // outer glow ring
                Box(
                    modifier = Modifier
                        .size(165.dp)
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFFE040FB), Color(0xFF00E5FF))
                            ),
                            shape = CircleShape
                        )
                )
                // photo
                Image(
                    painter = painterResource(id = R.drawable.img_sajib),
                    contentDescription = "NC Sajib Hasan Splash",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NC SAJIB HASAN",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                text = "জাদুকর সজীব হাসান",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF00E5FF)
            )
            Text(
                text = "বিলাশদী, নরসিংদী, ঢাকা, বাংলাদেশ",
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Special promotional badge recreated from flyer!
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF140D24).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = glowAlpha)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "হোয়াটসঅ্যাপ ও ইমো যোগাযোগ (PHONE WHATSAPP IMO) 📞",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color(0xFF00E5FF),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "জিপি নাম্বার (PRO)",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "017 14410 528",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color.Green
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "রবি নাম্বার (HOT)",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "01897 238 395",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color.Green
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ডিজিটাল প্রচার ও জাদুকরী কন্টেন্ট লোড হচ্ছে...",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// IMMERSIVE ACTUAL WEBVIEW LOAD OF ORIGINAL FACEBOOK PORTAL
// ----------------------------------------------------------------------------
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FacebookWebView() {
    var webView: WebView? by remember { mutableStateOf(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F081D).copy(alpha = 0.85f)),
        border = BorderStroke(1.5.dp, Color(0xFF1877F2).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Control and status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1877F2))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Secure FB Icon",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ফেসবুক অফিশিয়াল ওয়েব লাইভ",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Back button
                    Text(
                        text = "পিছনে",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                if (webView?.canGoBack() == true) {
                                    webView?.goBack()
                                }
                            }
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    // Refresh button
                    Text(
                        text = "রিফ্রেশ",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { webView?.reload() }
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        val webViewCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            context.createAttributionContext("webview")
                        } else {
                            context
                        }
                        WebView(webViewCtx).apply {
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    return false
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                            }
                            loadUrl("https://m.facebook.com")
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
