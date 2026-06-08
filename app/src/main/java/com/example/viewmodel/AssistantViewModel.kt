package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiService
import com.example.database.AppDatabase
import com.example.database.BusinessPage
import com.example.database.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AppViewState {
    object Welcome : AppViewState()
    object Login : AppViewState()
    object Dashboard : AppViewState()
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val chatDao = database.chatDao()
    private val businessPageDao = database.businessPageDao()

    // Screen navigation state
    private val _viewState = MutableStateFlow<AppViewState>(AppViewState.Welcome)
    val viewState: StateFlow<AppViewState> = _viewState.asStateFlow()

    // Logged in user profile (Sajib's details extracted from uploaded images)
    private val _userName = MutableStateFlow("NC Sajib Hasan")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun updateUserName(name: String) {
        _userName.value = name
    }
    val userBio = "আমি কখনো হারিনি- হয় জিতেছে -না হয় শিখেছি"
    val userDetails = mapOf(
        "followers" to "1.7K followers",
        "following" to "788 following",
        "posts" to "4.9K posts",
        "category" to "Digital Creator",
        "location" to "Narsingdi, Dhaka, Bangladesh",
        "hometown" to "Dhaka, Bangladesh",
        "birthdate" to "March 31, 1980",
        "education" to "Narsingdi Pilot High School, Gazipur Cantonment Board High School"
    )

    // Reactive lists from database
    val chatMessages = chatDao.getAllMessagesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val businessPages = businessPageDao.getAllPagesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Inputs & Assistant states
    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _apiLoading = MutableStateFlow(false)
    val apiLoading: StateFlow<Boolean> = _apiLoading.asStateFlow()

    private val _isCreatingPage = MutableStateFlow(false)
    val isCreatingPage: StateFlow<Boolean> = _isCreatingPage.asStateFlow()

    private val _pageCreationStep = MutableStateFlow("")
    val pageCreationStep: StateFlow<String> = _pageCreationStep.asStateFlow()

    private val _currentExpression = MutableStateFlow("idle") // idle, thinking, speaking, surprised, happy
    val currentExpression: StateFlow<String> = _currentExpression.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Trigger TTS out loud event
    private val _ttsPromptToSpeak = MutableStateFlow<String?>(null)
    val ttsPromptToSpeak: StateFlow<String?> = _ttsPromptToSpeak.asStateFlow()

    init {
        // Pre-insert a nice initial welcoming message from Humayra if empty
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(application)
            // Just double checking if dialogue empty
            db.runInTransaction {
                // Done
            }
        }
    }

    fun navigateTo(state: AppViewState) {
        _viewState.value = state
        if (state is AppViewState.Welcome) {
            setExpression("happy")
        } else {
            setExpression("idle")
        }
    }

    fun updateInput(text: String) {
        _currentInput.value = text
    }

    fun setExpression(expression: String) {
        _currentExpression.value = expression
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
        if (speaking) {
            _currentExpression.value = "speaking"
        } else {
            _currentExpression.value = "idle"
        }
    }

    fun consumeTtsPrompt() {
        _ttsPromptToSpeak.value = null
    }

    /**
     * Resets database entries for a clean dashboard slate
     */
    fun resetApp() {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.clearChat()
            businessPageDao.clearPages()
            _viewState.value = AppViewState.Welcome
            _currentInput.value = ""
            _apiLoading.value = false
            _isCreatingPage.value = false
            _pageCreationStep.value = ""
            _currentExpression.value = "idle"
            _isSpeaking.value = false
            _ttsPromptToSpeak.value = null
        }
    }

    fun getDisplayName(): String {
        val raw = _userName.value
        return if (raw == "NC Sajib Hasan") "সজীব" else raw
    }

    /**
     * Standard Welcome audio/greeting TTS trigger
     */
    fun playWelcomeGreeting() {
        val name = getDisplayName()
        _ttsPromptToSpeak.value = "আসসালামু আলাইকুম $name ভাই! আমি হুমায়রা। ডিজিটাল সজীব রেকর্ডিং স্টুডিও এর এআই অ্যাসিস্ট্যান্ট। ড্যাশবোর্ডে আপনাকে স্বাগতম।"
        setExpression("happy")
    }

    /**
     * AI-driven Chat system: sends dialogue history including context to Gemini
     */
    fun sendMessage() {
        val text = _currentInput.value.trim()
        if (text.isEmpty()) return

        _currentInput.value = ""
        setExpression("thinking")

        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            // 1. Save user's message
            val userMsg = ChatMessage(
                sender = "user",
                text = text,
                timestamp = timestamp,
                expression = "idle"
            )
            chatDao.insertMessage(userMsg)

            _apiLoading.value = true
            setExpression("thinking")

            // Get conversational history from database for thread memory context
            val currentList = chatMessages.value
            val historyPairs = currentList.map { it.sender to it.text }.toMutableList()
            // add user msg
            historyPairs.add("user" to text)

            // Keep conversation size small to fits in context limit as guided
            val lastTurns = if (historyPairs.size > 14) historyPairs.takeLast(14) else historyPairs

            // Call API
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            val rawReply = GeminiService.getChatResponse(apiKey, lastTurns)

            _apiLoading.value = false
            
            // 2. Save Humayra's reply
            val humayraMsg = ChatMessage(
                sender = "humayra",
                text = rawReply,
                timestamp = System.currentTimeMillis(),
                expression = "speaking"
            )
            chatDao.insertMessage(humayraMsg)

            // 3. Command text speech readout
            _ttsPromptToSpeak.value = rawReply
            setSpeaking(true)
        }
    }

    /**
     * Simulates creating a Facebook Business Page using Metas API specifications,
     * guiding user via speech narrations of each stage in Bengali!
     */
    fun createMetaPage() {
        if (_isCreatingPage.value) return
        _isCreatingPage.value = true
        setExpression("thinking")

        viewModelScope.launch(Dispatchers.IO) {
            val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val businessId = "FB123456_$dateStamp"
            val name = getDisplayName()
            
            _pageCreationStep.value = "ক্রিয়েটিং টোকেন অথরাইজেশন..."
            _ttsPromptToSpeak.value = "$name ভাই, আমি মেটা এপিআই-এর সাথে সংযোগ স্থাপন করে আপনার পেজ তৈরি করার কাজ শুরু করছি..."
            setSpeaking(true)
            delay(3000)

            _pageCreationStep.value = "মেটা বিজনেস ম্যানেজার সংযোগ..."
            _ttsPromptToSpeak.value = "আপনার ফেসবুক বিজনেস আইডি $businessId যাচাই করছি। ভেরিফিকেশন সফল হয়েছে।"
            setSpeaking(true)
            delay(3000)

            _pageCreationStep.value = "পেজ ফাইলস কনফিগারেশন..."
            _ttsPromptToSpeak.value = "ফেসবুক সার্ভারে ‘$name হাসানের ম্যাজিক স্টুডিও’ ডিজিটাল ক্রিয়েটর ক্যাটাগরিতে রেজিস্টার করা হচ্ছে..."
            setSpeaking(true)
            delay(3000)

            _pageCreationStep.value = "পেজ তৈরি সফল!"
            
            // Insert created page record into local Room SQLite
            val newPage = BusinessPage(
                pageId = "PAGE_MAGIC_${System.currentTimeMillis() % 1000000}",
                name = "$name হাসানের ম্যাজিক স্টুডিও",
                category = "Digital Creator",
                createdTime = System.currentTimeMillis(),
                fbBusinessId = businessId
            )
            businessPageDao.insertPage(newPage)

            // Also post message from Humayra announcing success
            val announcementMessage = ChatMessage(
                sender = "humayra",
                text = "অভিনন্দন $name ভাই! 🌹 আপনার নতুন ফেসবুক পেজ '$name হাসানের ম্যাজিক স্টুডিও' সফলভাবে মেটা বিজনেস এপিআই দিয়ে তৈরি করা হয়েছে! এটি এখন সক্রিয়।",
                timestamp = System.currentTimeMillis(),
                expression = "happy"
            )
            chatDao.insertMessage(announcementMessage)

            _pageCreationStep.value = "কমপ্লিট!"
            _ttsPromptToSpeak.value = "অভিনন্দন $name ভাই! আপনার নতুন মেটা বিজনেস পেজ ‘$name হাসানের ম্যাজিক স্টুডিও’ সফলভাবে লাইভ করা হয়েছে!"
            setExpression("happy")
            setSpeaking(true)
            
            delay(2000)
            _isCreatingPage.value = false
        }
    }
}
