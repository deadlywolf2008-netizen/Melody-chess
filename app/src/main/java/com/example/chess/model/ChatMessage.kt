package com.example.chess.model

enum class ChatBubbleStyle {
    ROUNDED_BOUNCY,     // Bouncy pill styled arcs
    NEO_BRUTALIST,      // Flat bold box with sharp corners and heavy borders
    MINIMAL_INTEGRATED, // Borderless, light subtle background
    CLASSIC_SHARP       // Standard conversational edge-aligned bubbles
}

enum class ChatThemeColor(val displayName: String, val primaryColorHex: String, val containerColorHex: String) {
    ZEN_PURPLE("Zen Purple", "#6750A4", "#E8DEF8"),
    EMERALD_MINT("Emerald Mint", "#0F7D52", "#D2EBD9"),
    RETRO_GOLD("Retro Amber", "#8F6B00", "#FFECB3"),
    CRIMSON_FLAME("Crimson Rush", "#B3261E", "#F9DEDC"),
    COSMIC_DARK("Cosmic Slate", "#21005D", "#EADDFF")
}

data class CustomizableChatTheme(
    val bubbleStyle: ChatBubbleStyle = ChatBubbleStyle.ROUNDED_BOUNCY,
    val themeColor: ChatThemeColor = ChatThemeColor.ZEN_PURPLE,
    val textScaleSp: Int = 14,
    val showOpponentAvatar: Boolean = true,
    val playSoundOnMessage: Boolean = true
)

enum class SenderType {
    PLAYER,
    OPPONENT,
    SYSTEM
}

data class ChatMessage(
    val id: String,
    val sender: SenderType,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object QuickChatTemplates {
    val templates = listOf(
        "Hello! Good luck, have fun!",
        "Thanks!",
        "Wow, beautiful move! 👏",
        "Oops, that was a mistake...",
        "Interesting position here.",
        "Good game!",
        "Rematch after this?",
        "My king is sweating! 😅",
        "Nicely played."
    )
}
