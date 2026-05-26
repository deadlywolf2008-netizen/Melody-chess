package com.example.chess.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.AppDatabase
import com.example.chess.data.MatchRecord
import com.example.chess.data.MatchRepository
import com.example.chess.model.*
import com.example.chess.music.ProceduralMusicPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Random
import java.util.UUID

enum class ActiveScreen {
    PLAY, STATS, SETTINGS
}

enum class GameMode {
    ONLINE_SIM, VS_COMPUTER, LOCAL_PASS
}

class ChessViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = MatchRepository(db.matchRecordDao())
    
    private val prefs: SharedPreferences = application.getSharedPreferences("zen_chess_prefs", Application.MODE_PRIVATE)

    // UI Active screens
    private val _activeScreen = MutableStateFlow(ActiveScreen.PLAY)
    val activeScreen: StateFlow<ActiveScreen> = _activeScreen.asStateFlow()

    // Player metadata
    private val _playerName = MutableStateFlow("Challenger")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    private val _playerRating = MutableStateFlow(1200)
    val playerRating: StateFlow<Int> = _playerRating.asStateFlow()

    // Chess Core Board State
    private val _board = MutableStateFlow(ChessBoard.createInitialBoard())
    val board: StateFlow<ChessBoard> = _board.asStateFlow()

    private val _selectedSquare = MutableStateFlow<BoardPosition?>(null)
    val selectedSquare: StateFlow<BoardPosition?> = _selectedSquare.asStateFlow()

    private val _legalMoves = MutableStateFlow<List<BoardPosition>>(emptyList())
    val legalMoves: StateFlow<List<BoardPosition>> = _legalMoves.asStateFlow()

    private val _isGameInProgress = MutableStateFlow(true)
    val isGameInProgress: StateFlow<Boolean> = _isGameInProgress.asStateFlow()

    private val _gameStatusText = MutableStateFlow("White to move. Good luck!")
    val gameStatusText: StateFlow<String> = _gameStatusText.asStateFlow()

    private val _gameMode = MutableStateFlow(GameMode.ONLINE_SIM)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    // Opponent Details
    private val _currentOpponent = MutableStateFlow(SimulatedOpponent.ALL_OPPONENTS[0])
    val currentOpponent: StateFlow<SimulatedOpponent> = _currentOpponent.asStateFlow()

    // Sound Synthesizer instance
    val musicPlayer = ProceduralMusicPlayer()

    // Conversational Chat Elements
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatTheme = MutableStateFlow(CustomizableChatTheme())
    val chatTheme: StateFlow<CustomizableChatTheme> = _chatTheme.asStateFlow()

    // Game stats history flowing from Room
    val matchRecords: StateFlow<List<MatchRecord>> = repository.allMatches.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Analytical ELO rating history generator
    val calculatedRatingProgression: StateFlow<List<Int>> = matchRecords.map { records ->
        val list = mutableListOf<Int>()
        list.add(1200) // Starting seed
        records.reversed().forEach {
            list.add(it.playerRatingAfter)
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf(1200)
    )

    private var opponentJob: Job? = null

    init {
        _playerName.value = prefs.getString("player_name", "Challenger") ?: "Challenger"
        _playerRating.value = prefs.getInt("player_rating", 1200)
        resetGame(GameMode.ONLINE_SIM)
    }

    fun navigateTo(screen: ActiveScreen) {
        _activeScreen.value = screen
    }

    fun changePlayerName(name: String) {
        val cleanName = name.trim().takeIf { it.isNotEmpty() } ?: "Challenger"
        _playerName.value = cleanName
        prefs.edit().putString("player_name", cleanName).apply()
    }

    fun updateChatTheme(updater: (CustomizableChatTheme) -> CustomizableChatTheme) {
        _chatTheme.value = updater(_chatTheme.value)
    }

    fun resetGame(mode: GameMode) {
        opponentJob?.cancel()
        _gameMode.value = mode
        _board.value = ChessBoard.createInitialBoard()
        _selectedSquare.value = null
        _legalMoves.value = emptyList()
        _isGameInProgress.value = true
        _chatMessages.value = emptyList()

        if (mode == GameMode.ONLINE_SIM) {
            // Select random simulated opponent
            val randOpponent = SimulatedOpponent.ALL_OPPONENTS.random()
            _currentOpponent.value = randOpponent
            _gameStatusText.value = "Connected to live simulation vs. ${randOpponent.name} (${randOpponent.rating} ELO)"
            
            // System and online greeting message
            _chatMessages.value = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = SenderType.SYSTEM,
                    senderName = "System",
                    text = "Connected in Arena vs ${randOpponent.name}."
                ),
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = SenderType.OPPONENT,
                    senderName = randOpponent.name,
                    text = randOpponent.greetingPhrase
                )
            )
        } else if (mode == GameMode.VS_COMPUTER) {
            _gameStatusText.value = "Single-player vs. Mellow AI is active. Make your move!"
        } else {
            _gameStatusText.value = "Local Pass & Play active. Control both sides on the same screen."
        }
    }

    fun selectSquare(pos: BoardPosition) {
        if (!_isGameInProgress.value) return
        
        val currentBoard = _board.value
        val currentSelected = _selectedSquare.value

        // If turn is opponent's computer AI, prevent choosing move manually
        if (currentBoard.turn == PieceColor.BLACK && _gameMode.value != GameMode.LOCAL_PASS) {
            return
        }

        if (currentSelected == null) {
            val piece = currentBoard.pieces[pos]
            if (piece != null && piece.color == currentBoard.turn) {
                _selectedSquare.value = pos
                _legalMoves.value = currentBoard.getLegalMoves(pos)
            }
        } else {
            val legalSelected = _legalMoves.value
            if (legalSelected.contains(pos)) {
                // Execute movement
                val newBoard = currentBoard.makeMove(currentSelected, pos)
                _board.value = newBoard
                _selectedSquare.value = null
                _legalMoves.value = emptyList()

                // Check board state
                checkGameStatus(newBoard)

                // Trigger AI response is necessary
                if (_isGameInProgress.value && _gameMode.value != GameMode.LOCAL_PASS) {
                    triggerOpponentMoveIfNeeded()
                }
            } else {
                // Change selection
                val piece = currentBoard.pieces[pos]
                if (piece != null && piece.color == currentBoard.turn) {
                    _selectedSquare.value = pos
                    _legalMoves.value = currentBoard.getLegalMoves(pos)
                } else {
                    _selectedSquare.value = null
                    _legalMoves.value = emptyList()
                }
            }
        }
    }

    private fun triggerOpponentMoveIfNeeded() {
        val currentBoard = _board.value
        if (!_isGameInProgress.value) return

        if (currentBoard.turn == PieceColor.BLACK) {
            opponentJob?.cancel()
            opponentJob = viewModelScope.launch {
                val rangeStart = _currentOpponent.value.thinkingDelayMsRange.first.toInt()
                val rangeEnd = _currentOpponent.value.thinkingDelayMsRange.last.toInt()
                val delayMs = if (_gameMode.value == GameMode.VS_COMPUTER) 1000L 
                              else (rangeStart..rangeEnd).random().toLong()
                delay(delayMs)
                executeOpponentMove()
            }
        }
    }

    private suspend fun executeOpponentMove() {
        val currentBoard = _board.value
        val blackPositions = currentBoard.pieces.filter { it.value.color == PieceColor.BLACK }.keys
        val allLegal = mutableListOf<Pair<BoardPosition, BoardPosition>>()

        for (from in blackPositions) {
            val targets = currentBoard.getLegalMoves(from)
            for (to in targets) {
                allLegal.add(Pair(from, to))
            }
        }

        if (allLegal.isEmpty()) {
            checkGameStatus(currentBoard)
            return
        }

        // Prefer moves that capture, otherwise random selection
        val capturesOnly = allLegal.filter { currentBoard.pieces.containsKey(it.second) }
        val chosenMove = if (capturesOnly.isNotEmpty() && Random().nextDouble() < 0.75) {
            capturesOnly.random()
        } else {
            allLegal.random()
        }

        val targetPosition = chosenMove.second
        val triggerChat = currentBoard.pieces.containsKey(targetPosition) || currentBoard.isCheckState

        val newBoard = currentBoard.makeMove(chosenMove.first, targetPosition)
        _board.value = newBoard
        _selectedSquare.value = null
        _legalMoves.value = emptyList()

        if (_gameMode.value == GameMode.ONLINE_SIM && triggerChat) {
            maybeTriggerOpponentChatRemark(newBoard)
        }

        checkGameStatus(newBoard)
    }

    private fun maybeTriggerOpponentChatRemark(newBoard: ChessBoard) {
        val opp = _currentOpponent.value
        if (Random().nextDouble() < 0.65) {
            val phrase = if (newBoard.isCheckState) {
                opp.winningPhrases.random()
            } else {
                opp.generalPhrases.random()
            }
            addOpponentChatMessage(phrase)
        }
    }

    private fun addOpponentChatMessage(text: String) {
        val updated = _chatMessages.value.toMutableList()
        updated.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.OPPONENT,
                senderName = _currentOpponent.value.name,
                text = text
            )
        )
        _chatMessages.value = updated
    }

    fun sendPlayerMessage(text: String) {
        if (text.isBlank()) return
        val updated = _chatMessages.value.toMutableList()
        updated.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.PLAYER,
                senderName = _playerName.value,
                text = text
            )
        )
        _chatMessages.value = updated

        // Trigger spontaneous opponent responses with normal chat rhythm
        if (_isGameInProgress.value && _gameMode.value == GameMode.ONLINE_SIM) {
            viewModelScope.launch {
                val randomDelay = (1200..2500).random().toLong()
                delay(randomDelay)
                val reply = _currentOpponent.value.generalPhrases.random()
                addOpponentChatMessage(reply)
            }
        }
    }

    private fun checkGameStatus(board: ChessBoard) {
        when {
            board.isCheckmate -> {
                _isGameInProgress.value = false
                // If it is White's turn and Checkmate is active, White lost. Else White won!
                if (board.turn == PieceColor.WHITE) {
                    _gameStatusText.value = "Checkmate! Defeat, Black wins!"
                    adjustElo(isWin = false, isDraw = false)
                    logMatchToDatabase("LOSS", board)
                } else {
                    _gameStatusText.value = "Checkmate! Victory, White wins!"
                    adjustElo(isWin = true, isDraw = false)
                    logMatchToDatabase("WIN", board)
                }
            }
            board.isStalemate -> {
                _isGameInProgress.value = false
                _gameStatusText.value = "Draw! Stalemate reached on board."
                adjustElo(isWin = false, isDraw = true)
                logMatchToDatabase("DRAW", board)
            }
            board.isCheckState -> {
                val colorString = if (board.turn == PieceColor.WHITE) "White" else "Black"
                _gameStatusText.value = "Check! $colorString King is threatened!"
            }
            else -> {
                val colorString = if (board.turn == PieceColor.WHITE) "White" else "Black"
                _gameStatusText.value = "$colorString Turn is active. Breathe and contemplate."
            }
        }
    }

    private fun adjustElo(isWin: Boolean, isDraw: Boolean) {
        if (_gameMode.value != GameMode.ONLINE_SIM) return // Elo only updates in simulated online matches
        
        var currentElo = _playerRating.value
        if (isDraw) {
            // Drawn matches don't affect much
        } else if (isWin) {
            currentElo += 15
        } else {
            currentElo = (currentElo - 12).coerceAtLeast(100)
        }
        _playerRating.value = currentElo
        prefs.edit().putInt("player_rating", currentElo).apply()
    }

    private fun logMatchToDatabase(resultStr: String, board: ChessBoard) {
        viewModelScope.launch {
            val opponentName = if (_gameMode.value == GameMode.ONLINE_SIM) _currentOpponent.value.name else "AI Tactical Unit"
            val opponentRating = if (_gameMode.value == GameMode.ONLINE_SIM) _currentOpponent.value.rating else 1350
            val moveCount = board.lastMoveFrom?.let { 24 } ?: 12 // Simple dynamic evaluation for list summary

            repository.insertMatch(
                MatchRecord(
                    opponentName = opponentName,
                    opponentRating = opponentRating,
                    result = resultStr,
                    gameMode = _gameMode.value.name,
                    finalMovesCount = moveCount,
                    playerRatingAfter = _playerRating.value
                )
            )
        }
    }

    fun clearStatsHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun offerDraw() {
        if (!_isGameInProgress.value) return
        _isGameInProgress.value = false
        _gameStatusText.value = "Match ended in a mutual agreed draw."
        adjustElo(isWin = false, isDraw = true)
        logMatchToDatabase("DRAW", _board.value)
    }

    fun resignGame() {
        if (!_isGameInProgress.value) return
        _isGameInProgress.value = false
        _gameStatusText.value = "Defeat by resignation."
        adjustElo(isWin = false, isDraw = false)
        logMatchToDatabase("LOSS", _board.value)

        if (_gameMode.value == GameMode.ONLINE_SIM) {
            addOpponentChatMessage("Good game! Thanks for the combat.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }
}
