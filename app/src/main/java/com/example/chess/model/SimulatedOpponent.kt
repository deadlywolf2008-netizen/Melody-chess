package com.example.chess.model

data class SimulatedOpponent(
    val name: String,
    val rating: Int,
    val avatarEmoji: String,
    val greetingPhrase: String,
    val generalPhrases: List<String>,
    val winningPhrases: List<String>,
    val losingPhrases: List<String>,
    val thinkingDelayMsRange: LongRange = 1500L..3500L
) {
    companion object {
        val ALL_OPPONENTS = listOf(
            SimulatedOpponent(
                name = "Magnus_Fan",
                rating = 1482,
                avatarEmoji = "👑",
                greetingPhrase = "Hi! Let's have a great match! Good luck.",
                generalPhrases = listOf(
                    "You're playing really solid strategy.",
                    "Let's see how you answer this...",
                    "Are you planning a greek gift sacrifice? Double checking...",
                    "Tactical complexity is rising!",
                    "A very interesting line!"
                ),
                winningPhrases = listOf(
                    "Yes! That structure worked nicely.",
                    "Good play, but the end-game is my specialty!",
                    "A fine battle indeed!"
                ),
                losingPhrases = listOf(
                    "Ouch, missed that tactical shot!",
                    "Outstanding move from you! Mind-blown.",
                    "My position is collapsing rapidly..."
                )
            ),
            SimulatedOpponent(
                name = "Beth_Harmon",
                rating = 1850,
                avatarEmoji = "♟️",
                greetingPhrase = "Hello. Let's see if you can see the whole board.",
                generalPhrases = listOf(
                    "I prefer open attacks.",
                    "Your pawn structure looks slightly vulnerable.",
                    "A classic opening choice.",
                    "Are you playing for a draw?"
                ),
                winningPhrases = listOf(
                    "It's about seeing the threat 3 steps before.",
                    "Thank you for the match.",
                    "Your center pawns were too weak."
                ),
                losingPhrases = listOf(
                    "Well played, your bishop pair dominated.",
                    "I resigned my position... GG!",
                    "You solved the position elegantly."
                )
            ),
            SimulatedOpponent(
                name = "ZenPieceMaker",
                rating = 1220,
                avatarEmoji = "🌸",
                greetingPhrase = "Greetings, friend. Let's play chess in harmony and style.",
                generalPhrases = listOf(
                    "Listen to the quiet rustle of the knights...",
                    "Win or lose, we learn and breathe.",
                    "A truly peaceful combination.",
                    "No rush. Focus and contemplate."
                ),
                winningPhrases = listOf(
                    "Harmony is restored! Splendid game.",
                    "Quiet focus wins the day.",
                    "Thank you for the Zen time!"
                ),
                losingPhrases = listOf(
                    "Alas, my defense was scattered.",
                    "Beautifully maneuvered by you!",
                    "Your pieces moved like flowing water."
                ),
                thinkingDelayMsRange = 1000L..2500L
            ),
            SimulatedOpponent(
                name = "Garry_K",
                rating = 2100,
                avatarEmoji = "🦁",
                greetingPhrase = "You need high concentration to challenge my game. Good luck.",
                generalPhrases = listOf(
                    "The pressure is building.",
                    "Calculation is key here.",
                    "Your tactical defense is under test.",
                    "Attack is the best form of defense."
                ),
                winningPhrases = listOf(
                    "Dominance is established.",
                    "Your kingside was too exposed to survive.",
                    "Precise defense is hard!"
                ),
                losingPhrases = listOf(
                    "Astounding counter-play! Respect.",
                    "You crushed my king-side pawn chain.",
                    "A historic win for you. Excellent!"
                ),
                thinkingDelayMsRange = 2500L..5000L
            )
        )
    }
}
