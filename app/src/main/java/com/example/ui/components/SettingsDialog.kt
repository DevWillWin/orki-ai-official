package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.VoiceOptions
import com.example.ui.theme.AmberPro
import com.example.ui.theme.CharcoalTextPrimary
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.PaleSage
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarmBorderSubtle

@Composable
fun SettingsDialog(
    initialScript: String,
    initialUiLang: String,
    initialName: String,
    initialPersona: String,
    initialVoice: String,
    currentPlan: String = "Free",
    activePlayingText: String? = null,
    onPreviewVoice: (String) -> Unit,
    onDismiss: () -> Unit,
    onUpgradeClick: (() -> Unit)? = null,
    onSave: (script: String, uiLang: String, name: String, persona: String, voice: String) -> Unit
) {
    var script by remember { mutableStateOf(initialScript) }
    var uiLang by remember { mutableStateOf(initialUiLang) }
    var name by remember { mutableStateOf(initialName) }
    var persona by remember { mutableStateOf(initialPersona) }
    var voice by remember { mutableStateOf(initialVoice) }
    var lastPreviewedVoiceId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    var imageEngine by remember { mutableStateOf(prefs.imageEnginePreference) }
    var dalleDeployment by remember { mutableStateOf(prefs.azureDalleDeployment) }

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IvoryBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Fixed Header: Refined Title, Glyphs, and Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PureWhite)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PaleSage)
                                .border(1.dp, SageGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = com.example.R.drawable.ic_orki_inapp_logo_circle,
                                contentDescription = "Orki AI Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Settings",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = CharcoalTextPrimary
                            )
                            Text(
                                text = "Preferences, speech voice & personalization",
                                fontSize = 12.sp,
                                color = SlateTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PaleSage)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close settings",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Header Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WarmBorder)
                )

                // Independently Scrollable Settings Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: LANGUAGE & SCRIPT
                    SettingsSection(title = "LANGUAGE & SCRIPT") {
                        // Interface Language
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "App Interface Language",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = CharcoalTextPrimary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PaleSage)
                                    .border(1.dp, WarmBorder, RoundedCornerShape(10.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SegmentedPill(
                                    label = "ब Devanagari",
                                    selected = uiLang == "deva",
                                    modifier = Modifier.weight(1f)
                                ) { uiLang = "deva" }

                                SegmentedPill(
                                    label = "R Roman",
                                    selected = uiLang == "roman",
                                    modifier = Modifier.weight(1f)
                                ) { uiLang = "roman" }

                                SegmentedPill(
                                    label = "EN English",
                                    selected = uiLang == "en",
                                    modifier = Modifier.weight(1f)
                                ) { uiLang = "en" }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // AI Output Response Script
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI Response Script",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CharcoalTextPrimary
                                )
                                Text(
                                    text = if (script == "deva") "बर' फरायनो" else "Roman script",
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PaleSage)
                                    .border(1.dp, WarmBorder, RoundedCornerShape(10.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SegmentedPill(
                                    label = "ब Devanagari",
                                    selected = script == "deva",
                                    modifier = Modifier.weight(1f)
                                ) { script = "deva" }

                                SegmentedPill(
                                    label = "R Roman Bodo",
                                    selected = script == "roman",
                                    modifier = Modifier.weight(1f)
                                ) { script = "roman" }
                            }
                        }
                    }

                    // SECTION 2: VOICE & SPEECH (Premium Redesigned Cards)
                    SettingsSection(title = "VOICE & SPEECH") {
                        Text(
                            text = "Select the voice model used for audio playback & Live Talk.",
                            fontSize = 12.sp,
                            color = SlateTextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceOptions.ALL.forEach { profile ->
                                val isSelected = profile.id == voice
                                val isCurrentlyPlaying = (lastPreviewedVoiceId == profile.id && activePlayingText != null)

                                // Model Identifier tag (e.g. BRX_F, BRX_M)
                                val modelTag = when (profile.id) {
                                    "female_mainao" -> "BRX_F"
                                    "male_birphung" -> "BRX_M"
                                    else -> profile.id.take(5).uppercase()
                                }

                                VoiceOptionCard(
                                    profileName = profile.name,
                                    modelId = modelTag,
                                    profileDesc = profile.description,
                                    samplePhrase = profile.samplePhrase,
                                    isSelected = isSelected,
                                    isPlaying = isCurrentlyPlaying,
                                    onSelect = { voice = profile.id },
                                    onPlayPreview = {
                                        lastPreviewedVoiceId = profile.id
                                        onPreviewVoice(profile.id)
                                    }
                                )
                            }
                        }
                    }

                    // SECTION 3: PERSONALIZATION
                    SettingsSection(title = "PERSONALIZATION") {
                        // User Name
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "What should Orki call you?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = CharcoalTextPrimary
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = {
                                    Text("e.g., Rahul, Priya, Bodo Friend…", fontSize = 13.sp, color = SlateTextSecondary)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = SlateTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ForestGreenPrimary,
                                    unfocusedBorderColor = WarmBorder,
                                    focusedTextColor = CharcoalTextPrimary,
                                    unfocusedTextColor = CharcoalTextPrimary,
                                    cursorColor = ForestGreenPrimary,
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom Instructions / Persona
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Custom Instructions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = CharcoalTextPrimary
                            )
                            Text(
                                text = "Provide background or guidelines Orki should follow when answering.",
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                            OutlinedTextField(
                                value = persona,
                                onValueChange = { persona = it },
                                placeholder = {
                                    Text(
                                        "e.g., Speak like a friendly tutor, keep answers short and simple…",
                                        fontSize = 12.sp,
                                        color = SlateTextSecondary
                                    )
                                },
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ForestGreenPrimary,
                                    unfocusedBorderColor = WarmBorder,
                                    focusedTextColor = CharcoalTextPrimary,
                                    unfocusedTextColor = CharcoalTextPrimary,
                                    cursorColor = ForestGreenPrimary,
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // SECTION 4: AI IMAGE GENERATION ENGINE
                    SettingsSection(title = "AI IMAGE GENERATION ENGINE") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Option 1: Perchance AI (Primary Default)
                            val isPerchanceSelected = imageEngine == "auto" || imageEngine == "perchance"
                            EngineOptionRow(
                                title = "Perchance AI",
                                badgeText = "PRIMARY • UNLIMITED FREE",
                                description = "Runs in background with automatic warm-up & zero quota limits",
                                isSelected = isPerchanceSelected,
                                onClick = { imageEngine = "auto" }
                            )

                            // Option 2: Cloudflare Workers AI
                            val isCfSelected = imageEngine == "cloudflare"
                            EngineOptionRow(
                                title = "Cloudflare Workers AI",
                                badgeText = "2ND FALLBACK",
                                description = "Fast SDXL/Flux via orki-img-gen worker",
                                isSelected = isCfSelected,
                                onClick = { imageEngine = "cloudflare" }
                            )

                            // Option 3: Azure OpenAI DALL-E 3
                            val isDalleSelected = imageEngine == "azure_dalle"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDalleSelected) PaleSage else PureWhite)
                                    .border(
                                        width = if (isDalleSelected) 1.5.dp else 1.dp,
                                        color = if (isDalleSelected) ForestGreenPrimary else WarmBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { imageEngine = "azure_dalle" }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isDalleSelected) 5.dp else 1.5.dp,
                                                color = if (isDalleSelected) ForestGreenPrimary else SlateTextSecondary,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Azure OpenAI DALL-E 3", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = CharcoalTextPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(PaleSage)
                                                    .border(1.dp, SageGreen, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text("CUSTOM • $0.04/img", fontSize = 9.sp, color = ForestGreenPrimary, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Text("Ultra-photorealistic 1024x1024 synthesis via Azure key", fontSize = 11.sp, color = SlateTextSecondary)
                                    }
                                    if (isDalleSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = ForestGreenPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (isDalleSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = dalleDeployment,
                                        onValueChange = { dalleDeployment = it },
                                        label = { Text("Azure DALL-E Deployment Name", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ForestGreenPrimary,
                                            unfocusedBorderColor = WarmBorder,
                                            focusedTextColor = CharcoalTextPrimary,
                                            unfocusedTextColor = CharcoalTextPrimary,
                                            cursorColor = ForestGreenPrimary,
                                            focusedLabelColor = ForestGreenPrimary,
                                            focusedContainerColor = PureWhite,
                                            unfocusedContainerColor = PureWhite
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            // Emergency Backup Status Pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PaleSage)
                                    .border(1.dp, SageGreen, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = ForestGreenPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Emergency Backup: Pollinations.AI", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = CharcoalTextPrimary)
                                    Text("Active Fallback • Ensures 100% uptime if all other engines fail", fontSize = 10.sp, color = SlateTextSecondary)
                                }
                            }
                        }
                    }

                    // SECTION 5: ACCOUNT & SUBSCRIPTION
                    SettingsSection(title = "ACCOUNT & SUBSCRIPTION") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PureWhite)
                                .border(1.dp, WarmBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Current Plan:",
                                        fontSize = 12.sp,
                                        color = SlateTextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (currentPlan == "Pro") AmberPro.copy(alpha = 0.15f)
                                                else PaleSage
                                            )
                                            .border(
                                                1.dp,
                                                if (currentPlan == "Pro") AmberPro.copy(alpha = 0.4f) else SageGreen,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = currentPlan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentPlan == "Pro") AmberPro else ForestGreenPrimary
                                        )
                                    }
                                }
                                Text(
                                    text = if (currentPlan == "Pro") "Okafwr 2.1 Deep Reasoning Active"
                                    else if (currentPlan == "Plus") "Extended Context & Voice Access"
                                    else "Standard daily conversation quota",
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            if (currentPlan != "Pro" && onUpgradeClick != null) {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onUpgradeClick()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreenPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Upgrade",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Fixed Bottom Action Bar: Cancel & Save Changes
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PureWhite)
                        .border(
                            width = 1.dp,
                            color = WarmBorder,
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextSecondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text(text = "Cancel", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                prefs.imageEnginePreference = imageEngine
                                prefs.azureDalleDeployment = dalleDeployment.trim().ifEmpty { "dall-e-3" }
                                onSave(script, uiLang, name.trim(), persona.trim(), voice)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForestGreenPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(42.dp)
                        ) {
                            Text(
                                text = "Save Changes",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = ForestGreenPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

/**
 * Premium Voice Selection Card:
 * Clear visual hierarchy, voice name, short description, model ID tag,
 * properly aligned radio indicator, subtle pale-sage background with thin forest-green border when selected,
 * and compact Preview button with speaker icon / wave animation.
 */
@Composable
private fun VoiceOptionCard(
    profileName: String,
    modelId: String,
    profileDesc: String,
    samplePhrase: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onPlayPreview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PaleSage else PureWhite)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) ForestGreenPrimary else WarmBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio Indicator
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 5.dp else 1.5.dp,
                        color = if (isSelected) ForestGreenPrimary else SlateTextSecondary,
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) SageGreen.copy(alpha = 0.5f) else PaleSage)
                            .border(
                                1.dp,
                                if (isSelected) ForestGreenPrimary.copy(alpha = 0.3f) else WarmBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = modelId,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestGreenPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•  $samplePhrase",
                        fontSize = 10.sp,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }
                Text(
                    text = profileDesc,
                    fontSize = 11.sp,
                    color = SlateTextSecondary,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        // Compact Preview Button (Speaker Icon & Accessible Touch Target)
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isPlaying) ForestGreenPrimary.copy(alpha = 0.12f) else PaleSage)
                .border(
                    1.dp,
                    if (isPlaying) ForestGreenPrimary else SageGreen,
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onPlayPreview)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isPlaying) {
                    AudioWaveAnimation()
                } else {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Preview voice",
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = if (isPlaying) "Playing" else "Preview",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestGreenPrimary
                )
            }
        }
    }
}

@Composable
private fun EngineOptionRow(
    title: String,
    badgeText: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) PaleSage else PureWhite)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) ForestGreenPrimary else WarmBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 5.dp else 1.5.dp,
                    color = if (isSelected) ForestGreenPrimary else SlateTextSecondary,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = CharcoalTextPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(PaleSage)
                        .border(1.dp, SageGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(badgeText, fontSize = 9.sp, color = ForestGreenPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(description, fontSize = 11.sp, color = SlateTextSecondary)
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = ForestGreenPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AudioWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(h1.dp)
                .clip(CircleShape)
                .background(ForestGreenPrimary)
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(h2.dp)
                .clip(CircleShape)
                .background(ForestGreenPrimary)
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(h3.dp)
                .clip(CircleShape)
                .background(ForestGreenPrimary)
        )
    }
}

@Composable
private fun SegmentedPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PureWhite else Color.Transparent)
            .border(
                1.dp,
                if (selected) SageGreen else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) ForestGreenPrimary else SlateTextSecondary
        )
    }
}
