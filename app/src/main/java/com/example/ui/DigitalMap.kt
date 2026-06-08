package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class MapFriend(
    val id: String,
    val name: String,
    val nickName: String,
    val profileResId: Int,
    val initialX: Float, // coordinates inside the virtual map
    val initialY: Float,
    val lastSeenLocation: String,
    val status: String, // "Active Live", "Idle", "Offline"
    val bearing: Float
)

@Composable
fun DigitalOfflineMapView() {
    var scale by remember { mutableStateOf(1.0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFriendId by remember { mutableStateOf<String?>(null) }
    var isMapDownloading by remember { mutableStateOf(false) }
    var isMapDownloaded by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableStateOf(1f) }
    var isScanningRadar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val friends = remember {
        mutableStateListOf(
            MapFriend(
                id = "sajib",
                name = "NC Sajib Hasan (আপনি)",
                nickName = "সজীব ভাই",
                profileResId = R.drawable.img_sajib,
                initialX = 350f,
                initialY = 250f,
                lastSeenLocation = "Bilasdi Narsingdi Studio (বিলাশদী রেকর্ড স্টুডিও)",
                status = "Active Live",
                bearing = 45f
            ),
            MapFriend(
                id = "humayra",
                name = "হুমায়রা এআই এসিস্ট্যান্ট",
                nickName = "হুমায়রা এআই",
                profileResId = R.drawable.img_humayra,
                initialX = 480f,
                initialY = 320f,
                lastSeenLocation = "Main Studio Control Room (মেটা ক্লাউড সার্ভার)",
                status = "Active Live",
                bearing = 120f
            ),
            MapFriend(
                id = "mahi",
                name = "Mahi Zaman (মাহি)",
                nickName = "মাহি জামান",
                profileResId = R.drawable.img_sajib, // using existing drawables
                initialX = 180f,
                initialY = 460f,
                lastSeenLocation = "Velanagar Overbridge, Narsingdi (ভেলানগর মোড়)",
                status = "Active Live",
                bearing = 270f
            ),
            MapFriend(
                id = "hasan",
                name = "Hasan Chowdhury (হাসান)",
                nickName = "হাসান চৌধুরী",
                profileResId = R.drawable.img_sajib,
                initialX = 580f,
                initialY = 150f,
                lastSeenLocation = "Narsingdi Sadar Bazar (সদর বাজার)",
                status = "Active Live",
                bearing = 10f
            )
        )
    }

    // Filtered friends
    val filteredFriends = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            friends
        } else {
            friends.filter { it.name.contains(searchQuery, ignoreCase = true) || it.nickName.contains(searchQuery) }
        }
    }

    // Auto walking movement generator to make map look dynamic and fully LIVE!
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            // Shift friends location subtly to prove it is a real functional tracker!
            for (i in 0 until friends.size) {
                val friend = friends[i]
                if (friend.id != "sajib" && friend.id != "humayra") {
                    val dx = (-15..15).random().toFloat()
                    val dy = (-15..15).random().toFloat()
                    friends[i] = friend.copy(
                        initialX = (friend.initialX + dx).coerceIn(100f, 900f),
                        initialY = (friend.initialY + dy).coerceIn(100f, 900f),
                        bearing = (friend.bearing + (-30..30).random()).absoluteValue % 360f
                    )
                }
            }
        }
    }

    // Infinite radar pulse rotation
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "radarAngle"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F091F)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header panel of the downloaded map
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(containerColor = Color.Green, modifier = Modifier.size(8.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "লাইভ ডিজিটাল ফ্রেন্ড ম্যাপ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = if (isMapDownloaded) "ডাউনলোড করা ম্যাপ (অফলাইন ক্যাশেড সক্রিয়)" else "ম্যাপ ডাউনলোড করা হয়নি",
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF)
                    )
                }

                // Download/Reset Map button
                OutlinedButton(
                    onClick = {
                        if (!isMapDownloading) {
                            scope.launch {
                                isMapDownloading = true
                                isMapDownloaded = false
                                downloadProgress = 0f
                                while (downloadProgress < 1.0f) {
                                    delay(200)
                                    downloadProgress += 0.1f
                                }
                                isMapDownloaded = true
                                isMapDownloading = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isMapDownloaded) Icons.Default.CheckCircle else Icons.Default.ArrowDropDown,
                        contentDescription = "Download map icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMapDownloading) "ডাউনলোডিং ${(downloadProgress * 100).toInt()}%" else if (isMapDownloaded) "রিলোড ম্যাপ" else "ডাউনলোড ম্যাপ",
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Map interaction, search, and details
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("নাম অথবা ডাকনাম সার্চ করুন...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f)) },
                leadingIcon = { Icon(Icons.Default.Search, "search icon", tint = Color.White.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = Color(0xFF00E5FF),
                    unfocusedContainerColor = Color(0xFF161026),
                    focusedContainerColor = Color(0xFF161026),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedBorderColor = Color(0xFF00E5FF)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Map canvas renderer viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080410))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Update offsets to let user pan smoothly!
                            offsetX = (offsetX + dragAmount.x).coerceIn(-400f, 400f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(-400f, 400f)
                        }
                    }
            ) {
                // Vector grid lines render
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // Draw grid lines
                    val step = 40f
                    for (x in 0..(width.toInt() / step.toInt())) {
                        val finalX = x * step + (offsetX / 2f) % step
                        drawLine(
                            color = Color(0xFF1F1735).copy(alpha = 0.4f),
                            start = Offset(finalX, 0f),
                            end = Offset(finalX, height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..(height.toInt() / step.toInt())) {
                        val finalY = y * step + (offsetY / 2f) % step
                        drawLine(
                            color = Color(0xFF1F1735).copy(alpha = 0.4f),
                            start = Offset(0f, finalY),
                            end = Offset(width, finalY),
                            strokeWidth = 1f
                        )
                    }

                    // Vector concentric map radar background lines representing telemetry rings
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.05f),
                        radius = 180f,
                        center = Offset(width / 2 + offsetX, height / 2 + offsetY),
                        style = Stroke(width = 1f)
                    )
                    drawCircle(
                        color = Color(0xFFE040FB).copy(alpha = 0.04f),
                        radius = 320f,
                        center = Offset(width / 2 + offsetX, height / 2 + offsetY),
                        style = Stroke(width = 1f)
                    )

                    // Draw stylized blueprints highways and junctions representing Narsingdi town bypass roads!
                    val roadColor = Color(0xFF2E1B4E)
                    // Bypass Dhaka Narsingdi High road
                    drawLine(
                        color = roadColor,
                        start = Offset(0f, height / 2 + offsetY + 50f),
                        end = Offset(width, height / 2 + offsetY - 80f),
                        strokeWidth = 14f
                    )
                    // Narsingdi Town Bridge highway crossing bypass
                    drawLine(
                        color = roadColor,
                        start = Offset(width / 2 + offsetX - 100f, 0f),
                        end = Offset(width / 2 + offsetX + 80f, height),
                        strokeWidth = 12f
                    )

                    // Center radar sweeping line during scan mode
                    if (isScanningRadar) {
                        val radius = size.minDimension
                        val sweepRad = Math.toRadians(radarAngle.toDouble())
                        val endRadarX = (width / 2 + offsetX) + radius * kotlin.math.cos(sweepRad).toFloat()
                        val endRadarY = (height / 2 + offsetY) + radius * kotlin.math.sin(sweepRad).toFloat()

                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                            start = Offset(width / 2 + offsetX, height / 2 + offsetY),
                            end = Offset(endRadarX, endRadarY),
                            strokeWidth = 3f
                        )
                    }
                }

                // Friends floating pin anchors
                for (friend in filteredFriends) {
                    val animatedPingGlow by rememberInfiniteTransition(label = "").animateFloat(
                        initialValue = 10f,
                        targetValue = 28f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ping"
                    )

                    val virtualX = (friend.initialX * scale) + offsetX
                    val virtualY = (friend.initialY * scale) + offsetY

                    // Frame coordinate translation to respect layout bounds
                    val viewWidth = 400f
                    val viewHeight = 260f

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (virtualX / 1.8f).dp,
                                y = (virtualY / 2.3f).dp
                            )
                            .size(54.dp)
                            .clickable { selectedFriendId = friend.id },
                        contentAlignment = Alignment.Center
                    ) {
                        // Floating pulse halo
                        Box(
                            modifier = Modifier
                                .size(animatedPingGlow.dp)
                                .clip(CircleShape)
                                .background(
                                    if (friend.id == "sajib") Color(0xFF1877F2).copy(alpha = 0.25f)
                                    else if (friend.id == "humayra") Color(0xFFEC407A).copy(alpha = 0.25f)
                                    else Color(0xFF00FF87).copy(alpha = 0.25f)
                                )
                        )

                        // Anchor Node content
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D0221))
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = if (friend.id == "sajib") listOf(Color(0xFF1877F2), Color(0xFF00E5FF))
                                        else if (friend.id == "humayra") listOf(Color(0xFFEC407A), Color(0xFFFFD54F))
                                        else listOf(Color(0xFF00FF87), Color(0xFF1877F2))
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = friend.profileResId),
                                contentDescription = "Friend Profile photo on map",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }

                        // Mini name banner above pin
                        Box(
                            modifier = Modifier
                                .padding(bottom = 34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = friend.nickName,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Map HUD Controls (Zoom In/Out + Radar search toggle button)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF170F2B))
                            .clickable {
                                scale = (scale + 0.15f).coerceAtMost(2.0f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF170F2B))
                            .clickable {
                                scale = (scale - 0.15f).coerceAtLeast(0.6f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isScanningRadar) Color(0xFF00E5FF) else Color(0xFF170F2B))
                            .clickable {
                                isScanningRadar = !isScanningRadar
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Radar Scan Toggle",
                            tint = if (isScanningRadar) Color.Black else Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(if (isScanningRadar) radarAngle else 0f)
                        )
                    }
                }

                // Reset positions
                IconButton(
                    onClick = {
                        offsetX = 0f
                        offsetY = 0f
                        scale = 1.0f
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.LocationOn, "Center Map", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            // Map instruction banner and zoom tracker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "* ম্যাপে ড্র্যাগ বা প্যান করে শহরের বিভিন্ন স্থান স্পর্শ করে বন্ধুদের সন্ধান করুন",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = "জুম: ${(scale * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
            }

            // Detailed Interactive Node Info Panel
            AnimatedVisibility(
                visible = selectedFriendId != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                selectedFriendId?.let { id ->
                    val friend = friends.firstOrNull { it.id == id }
                    if (friend != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF17112E)),
                            border = BorderStroke(1.dp, Color(0xFFEC407A).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(id = friend.profileResId),
                                            contentDescription = "Avatar portrait popup",
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, Color(0xFF00E5FF), CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = friend.name,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Green)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = friend.status,
                                                    fontSize = 10.sp,
                                                    color = Color.Green,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { selectedFriendId = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Dismiss Popup", tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = Color.White.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Pin coordinates",
                                        tint = Color(0xFFEC407A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "বর্তমান লাইভ লোকেশন (ফেসবুক ট্র্যাকার):",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = friend.lastSeenLocation,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("ফেসবুক প্রোফাইল", fontSize = 11.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = { },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("ডিরেকশন রিং করুন", fontSize = 11.sp, color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
