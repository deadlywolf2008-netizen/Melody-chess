package com.example.chess.model

data class BoardPosition(val row: Int, val col: Int) {
    fun isValid(): Boolean = row in 0..7 && col in 0..7
}

data class ChessBoard(
    val pieces: Map<BoardPosition, ChessPiece> = emptyMap(),
    val turn: PieceColor = PieceColor.WHITE,
    val lastMoveFrom: BoardPosition? = null,
    val lastMoveTo: BoardPosition? = null,
    val whiteCastled: Boolean = false,
    val blackCastled: Boolean = false,
    val isKingMovedWhite: Boolean = false,
    val isKingMovedBlack: Boolean = false,
    val isRookKMovedWhite: Boolean = false,
    val isRookQMovedWhite: Boolean = false,
    val isRookKMovedBlack: Boolean = false,
    val isRookQMovedBlack: Boolean = false
) {
    companion object {
        fun createInitialBoard(): ChessBoard {
            val pieces = mutableMapOf<BoardPosition, ChessPiece>()
            
            // Black back rank
            val blackBack = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
            )
            for (col in 0..7) {
                pieces[BoardPosition(0, col)] = ChessPiece(blackBack[col], PieceColor.BLACK)
                pieces[BoardPosition(1, col)] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
            }

            // White back rank
            val whiteBack = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
            )
            for (col in 0..7) {
                pieces[BoardPosition(7, col)] = ChessPiece(whiteBack[col], PieceColor.WHITE)
                pieces[BoardPosition(6, col)] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
            }

            return ChessBoard(pieces = pieces, turn = PieceColor.WHITE)
        }
    }

    // Generate pseudo-legal moves for a piece at index, ignoring whether they put King in check.
    fun getPseudoLegalMoves(pos: BoardPosition): List<BoardPosition> {
        val piece = pieces[pos] ?: return emptyList()
        val moves = mutableListOf<BoardPosition>()

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) -1 else 1
                val startRow = if (piece.color == PieceColor.WHITE) 6 else 1

                // 1 step forward
                val oneStep = BoardPosition(pos.row + dir, pos.col)
                if (oneStep.isValid() && !pieces.containsKey(oneStep)) {
                    moves.add(oneStep)
                    // 2 steps forward
                    val twoSteps = BoardPosition(pos.row + (2 * dir), pos.col)
                    if (pos.row == startRow && !pieces.containsKey(twoSteps)) {
                        moves.add(twoSteps)
                    }
                }

                // Captures
                val captures = listOf(
                    BoardPosition(pos.row + dir, pos.col - 1),
                    BoardPosition(pos.row + dir, pos.col + 1)
                )
                for (cap in captures) {
                    if (cap.isValid()) {
                        val capPiece = pieces[cap]
                        if (capPiece != null && capPiece.color != piece.color) {
                            moves.add(cap)
                        }
                    }
                }
            }

            PieceType.KNIGHT -> {
                val offsets = listOf(
                    Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
                    Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
                )
                for (offset in offsets) {
                    val next = BoardPosition(pos.row + offset.first, pos.col + offset.second)
                    if (next.isValid()) {
                        val occupier = pieces[next]
                        if (occupier == null || occupier.color != piece.color) {
                            moves.add(next)
                        }
                    }
                }
            }

            PieceType.BISHOP -> {
                addRays(pos, piece.color, listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)), moves)
            }

            PieceType.ROOK -> {
                addRays(pos, piece.color, listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)), moves)
            }

            PieceType.QUEEN -> {
                addRays(pos, piece.color, listOf(
                    Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1),
                    Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
                ), moves)
            }

            PieceType.KING -> {
                val offsets = listOf(
                    -1 to -1, -1 to 0, -1 to 1,
                    0 to -1,           0 to 1,
                    1 to -1,  1 to 0,  1 to 1
                )
                for (offset in offsets) {
                    val next = BoardPosition(pos.row + offset.first, pos.col + offset.second)
                    if (next.isValid()) {
                        val occupier = pieces[next]
                        if (occupier == null || occupier.color != piece.color) {
                            moves.add(next)
                        }
                    }
                }

                // Castling details (pseudo-legal addition)
                addCastlingMoves(pos, piece.color, moves)
            }
        }
        return moves
    }

    private fun addRays(pos: BoardPosition, color: PieceColor, dirs: List<Pair<Int, Int>>, moves: MutableList<BoardPosition>) {
        for (dir in dirs) {
            var curr = BoardPosition(pos.row + dir.first, pos.col + dir.second)
            while (curr.isValid()) {
                val occupier = pieces[curr]
                if (occupier == null) {
                    moves.add(curr)
                } else {
                    if (occupier.color != color) {
                        moves.add(curr)
                    }
                    break // Blocked
                }
                curr = BoardPosition(curr.row + dir.first, curr.col + dir.second)
            }
        }
    }

    private fun addCastlingMoves(pos: BoardPosition, color: PieceColor, moves: MutableList<BoardPosition>) {
        if (color == PieceColor.WHITE) {
            if (!isKingMovedWhite && !isKingInCheck(PieceColor.WHITE, pieces)) {
                // Kingside castling
                if (!isRookKMovedWhite) {
                    val pathEmpty = pieces[BoardPosition(7, 5)] == null && pieces[BoardPosition(7, 6)] == null
                    if (pathEmpty) {
                        moves.add(BoardPosition(7, 6))
                    }
                }
                // Queenside castling
                if (!isRookQMovedWhite) {
                    val pathEmpty = pieces[BoardPosition(7, 1)] == null && 
                                    pieces[BoardPosition(7, 2)] == null && 
                                    pieces[BoardPosition(7, 3)] == null
                    if (pathEmpty) {
                        moves.add(BoardPosition(7, 2))
                    }
                }
            }
        } else {
            if (!isKingMovedBlack && !isKingInCheck(PieceColor.BLACK, pieces)) {
                // Kingside castling
                if (!isRookKMovedBlack) {
                    val pathEmpty = pieces[BoardPosition(0, 5)] == null && pieces[BoardPosition(0, 6)] == null
                    if (pathEmpty) {
                        moves.add(BoardPosition(0, 6))
                    }
                }
                // Queenside castling
                if (!isRookQMovedBlack) {
                    val pathEmpty = pieces[BoardPosition(0, 1)] == null && 
                                    pieces[BoardPosition(0, 2)] == null && 
                                    pieces[BoardPosition(0, 3)] == null
                    if (pathEmpty) {
                        moves.add(BoardPosition(0, 2))
                    }
                }
            }
        }
    }

    // Get strictly legal moves that don't trigger or leave your King in check
    fun getLegalMoves(pos: BoardPosition): List<BoardPosition> {
        val piece = pieces[pos] ?: return emptyList()
        if (piece.color != turn) return emptyList()

        val pseudo = getPseudoLegalMoves(pos)
        val legal = mutableListOf<BoardPosition>()

        for (target in pseudo) {
            // Simulate move
            val nextPieces = simulateMovePieces(pos, target)
            if (!isKingInCheck(piece.color, nextPieces)) {
                // For castling, also verify the king doesn't pass through a square attacked by opponent!
                if (piece.type == PieceType.KING && Math.abs(pos.col - target.col) == 2) {
                    val passCol = if (target.col == 6) 5 else 3
                    val passPos = BoardPosition(pos.row, passCol)
                    val simPassPieces = simulateMovePieces(pos, passPos)
                    if (!isKingInCheck(piece.color, simPassPieces)) {
                        legal.add(target)
                    }
                } else {
                    legal.add(target)
                }
            }
        }
        return legal
    }

    // Direct helper to compute pieces map after moving
    private fun simulateMovePieces(from: BoardPosition, to: BoardPosition): Map<BoardPosition, ChessPiece> {
        val nextMap = pieces.toMutableMap()
        val piece = nextMap[from] ?: return pieces
        
        // Remove old
        nextMap.remove(from)

        // Handle Castling moves
        if (piece.type == PieceType.KING && Math.abs(from.col - to.col) == 2) {
            // Kingside
            if (to.col == 6) {
                val rookFrom = BoardPosition(from.row, 7)
                val rookTo = BoardPosition(from.row, 5)
                val rook = nextMap.remove(rookFrom)
                if (rook != null) nextMap[rookTo] = rook
            }
            // Queenside
            if (to.col == 2) {
                val rookFrom = BoardPosition(from.row, 0)
                val rookTo = BoardPosition(from.row, 3)
                val rook = nextMap.remove(rookFrom)
                if (rook != null) nextMap[rookTo] = rook
            }
        }

        // Apply promotion automatically to Queen if pawn hits the last rank
        val isPromotion = piece.type == PieceType.PAWN && (to.row == 0 || to.row == 7)
        if (isPromotion) {
            nextMap[to] = ChessPiece(PieceType.QUEEN, piece.color)
        } else {
            nextMap[to] = piece
        }

        return nextMap
    }

    // Helper to check if King of a specific color is under attack
    fun isKingInCheck(color: PieceColor, boardPieces: Map<BoardPosition, ChessPiece>): Boolean {
        // 1. Locate King
        var kingPos: BoardPosition? = null
        for ((p, pc) in boardPieces) {
            if (pc.type == PieceType.KING && pc.color == color) {
                kingPos = p
                break
            }
        }
        // If King has been eaten or does not exist for some reason, return false (or true, safety first)
        val kPos = kingPos ?: return false

        // 2. See if any opponent piece attacks kPos
        for ((p, pc) in boardPieces) {
            if (pc.color != color) {
                val attackersPseudo = getPseudoLegalMovesForCheck(p, boardPieces)
                if (attackersPseudo.contains(kPos)) {
                    return true
                }
            }
        }
        return false
    }

    // Pseudo moves ignoring turn, strictly for check analysis (prevents infinite recursion)
    private fun getPseudoLegalMovesForCheck(pos: BoardPosition, boardPieces: Map<BoardPosition, ChessPiece>): List<BoardPosition> {
        val piece = boardPieces[pos] ?: return emptyList()
        val moves = mutableListOf<BoardPosition>()

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) -1 else 1
                val captures = listOf(
                    BoardPosition(pos.row + dir, pos.col - 1),
                    BoardPosition(pos.row + dir, pos.col + 1)
                )
                for (cap in captures) {
                    if (cap.isValid()) {
                        moves.add(cap) // Add any diagonal position as a threatened square
                    }
                }
            }

            PieceType.KNIGHT -> {
                val offsets = listOf(
                    Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
                    Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
                )
                for (offset in offsets) {
                    val next = BoardPosition(pos.row + offset.first, pos.col + offset.second)
                    if (next.isValid()) {
                        moves.add(next)
                    }
                }
            }

            PieceType.BISHOP -> {
                addRaysForCheck(pos, listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)), boardPieces, moves)
            }

            PieceType.ROOK -> {
                addRaysForCheck(pos, listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)), boardPieces, moves)
            }

            PieceType.QUEEN -> {
                addRaysForCheck(pos, listOf(
                    Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1),
                    Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
                ), boardPieces, moves)
            }

            PieceType.KING -> {
                val offsets = listOf(
                    -1 to -1, -1 to 0, -1 to 1,
                    0 to -1,           0 to 1,
                    1 to -1,  1 to 0,  1 to 1
                )
                for (offset in offsets) {
                    val next = BoardPosition(pos.row + offset.first, pos.col + offset.second)
                    if (next.isValid()) {
                        moves.add(next)
                    }
                }
            }
        }
        return moves
    }

    private fun addRaysForCheck(pos: BoardPosition, dirs: List<Pair<Int, Int>>, boardPieces: Map<BoardPosition, ChessPiece>, moves: MutableList<BoardPosition>) {
        for (dir in dirs) {
            var curr = BoardPosition(pos.row + dir.first, pos.col + dir.second)
            while (curr.isValid()) {
                val occupier = boardPieces[curr]
                moves.add(curr)
                if (occupier != null) {
                    break // Ray ends because of block
                }
                curr = BoardPosition(curr.row + dir.first, curr.col + dir.second)
            }
        }
    }

    // Make an actual turn move, returning the new board state
    fun makeMove(from: BoardPosition, to: BoardPosition): ChessBoard {
        val piece = pieces[from] ?: return this
        val nextPieces = simulateMovePieces(from, to).toMutableMap()

        // Handle flags
        var newWhiteCastled = whiteCastled
        var newBlackCastled = blackCastled
        var newKingMovedWhite = isKingMovedWhite
        var newKingMovedBlack = isKingMovedBlack
        var newRookKMovedWhite = isRookKMovedWhite
        var newRookQMovedWhite = isRookQMovedWhite
        var newRookKMovedBlack = isRookKMovedBlack
        var newRookQMovedBlack = isRookQMovedBlack

        if (piece.type == PieceType.KING) {
            if (piece.color == PieceColor.WHITE) {
                newKingMovedWhite = true
                if (Math.abs(from.col - to.col) == 2) newWhiteCastled = true
            } else {
                newKingMovedBlack = true
                if (Math.abs(from.col - to.col) == 2) newBlackCastled = true
            }
        }

        if (piece.type == PieceType.ROOK) {
            if (piece.color == PieceColor.WHITE) {
                if (from == BoardPosition(7, 7)) newRookKMovedWhite = true
                if (from == BoardPosition(7, 0)) newRookQMovedWhite = true
            } else {
                if (from == BoardPosition(0, 7)) newRookKMovedBlack = true
                if (from == BoardPosition(0, 0)) newRookQMovedBlack = true
            }
        }

        return ChessBoard(
            pieces = nextPieces,
            turn = turn.opponent(),
            lastMoveFrom = from,
            lastMoveTo = to,
            whiteCastled = newWhiteCastled,
            blackCastled = newBlackCastled,
            isKingMovedWhite = newKingMovedWhite,
            isKingMovedBlack = newKingMovedBlack,
            isRookKMovedWhite = newRookKMovedWhite,
            isRookQMovedWhite = newRookQMovedWhite,
            isRookKMovedBlack = newRookKMovedBlack,
            isRookQMovedBlack = newRookQMovedBlack
        )
    }

    // Has current player any legal moves?
    fun hasAnyLegalMoves(): Boolean {
        for ((pos, piece) in pieces) {
            if (piece.color == turn) {
                if (getLegalMoves(pos).isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    // Derived states
    val isCheckState: Boolean
        get() = isKingInCheck(turn, pieces)

    val isCheckmate: Boolean
        get() = isCheckState && !hasAnyLegalMoves()

    val isStalemate: Boolean
        get() = !isCheckState && !hasAnyLegalMoves()
}
