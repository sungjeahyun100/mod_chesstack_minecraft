package com.chesstack;

import com.chesstack.engine.core.*;
import com.chesstack.engine.core.Move.*;
import com.chesstack.engine.core.Piece.*;
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.minecraft.api.TestMode;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EngineTests — 엔진 코어 기능 테스트.
 */
class EngineTests {

    // ── 초기화 테스트 ─────────────────────────────────────────

    @Test
    void testInitialSetup() {
        GameState state = GameState.newDefault();

        // 백 킹 (e1)
        PieceData wk = state.getPieceAt(new Square(4, 0));
        assertNotNull(wk);
        assertEquals(PieceKind.KING, wk.kind);
        assertTrue(wk.isRoyal);

        // 흑 킹 (e8)
        PieceData bk = state.getPieceAt(new Square(4, 7));
        assertNotNull(bk);
        assertEquals(PieceKind.KING, bk.kind);
        assertTrue(bk.isRoyal);
    }

    @Test
    void testPieceScores() {
        assertEquals(1, PieceKind.PAWN.score());
        assertEquals(4, PieceKind.KING.score());
        assertEquals(9, PieceKind.QUEEN.score());
        assertEquals(5, PieceKind.ROOK.score());
        assertEquals(3, PieceKind.KNIGHT.score());
        assertEquals(3, PieceKind.BISHOP.score());
        assertEquals(13, PieceKind.AMAZON.score());
    }

    // ── 포켓 테스트 ─────────────────────────────────────────

    @Test
    void testPocketScoreLimit() {
        GameState state = GameState.newDefault();

        state.setupInitialPosition();
        assertFalse(state.getPocket(0).isEmpty());

        GameState state2 = GameState.newDefault();
        List<PieceSpec> overflow = List.of(
                new PieceSpec(PieceKind.AMAZON),
                new PieceSpec(PieceKind.QUEEN),
                new PieceSpec(PieceKind.QUEEN),
                new PieceSpec(PieceKind.QUEEN)
        );
        assertThrows(IllegalArgumentException.class,
                () -> state2.setupPocket(1, new java.util.ArrayList<>(overflow)));
    }

    // ── 이동 테스트 ─────────────────────────────────────────

    @Test
    void testKingLegalMoves() {
        GameState state = GameState.newDefault();

        List<LegalMove> moves = state.getLegalMovesAt(new Square(4, 0));
        assertFalse(moves.isEmpty());

        assertTrue(moves.stream().anyMatch(m -> m.to.equals(new Square(3, 1))));
        assertTrue(moves.stream().anyMatch(m -> m.to.equals(new Square(4, 1))));
        assertTrue(moves.stream().anyMatch(m -> m.to.equals(new Square(5, 1))));

        assertFalse(moves.stream().anyMatch(m -> m.to.equals(new Square(4, 2))));
    }

    @Test
    void testKingMoveExecution() {
        GameState state = GameState.newDefault();

        List<LegalMove> moves = state.getLegalMovesAt(new Square(4, 0));
        LegalMove e2move = moves.stream()
                .filter(m -> m.to.equals(new Square(4, 1)))
                .findFirst().orElseThrow();

        String captured = state.movePieceByLegalMove(e2move);
        assertNull(captured);

        PieceData king = state.getPieceAt(new Square(4, 1));
        assertNotNull(king);
        assertEquals(PieceKind.KING, king.kind);

        assertNull(state.getPieceAt(new Square(4, 0)));
    }

    // ── 턴 테스트 ───────────────────────────────────────────

    @Test
    void testEndTurn() {
        GameState state = GameState.newDefault();
        assertEquals(0, state.getTurn());

        state.endTurn();
        assertEquals(1, state.getTurn());

        state.endTurn();
        assertEquals(0, state.getTurn());
    }

    // ── 승리 조건 테스트 ──────────────────────────────────────

    @Test
    void testVictoryCondition() {
        GameState state = GameState.newDefault();
        assertEquals(GameResult.ONGOING, state.checkVictory());
    }

    // ── 착수 테스트 ─────────────────────────────────────────

    @Test
    void testPlacement() {
        GameState state = GameState.newDefault();
        state.setupInitialPosition();

        String pawnId = state.placePiece(0, PieceKind.PAWN, new Square(0, 1));
        assertNotNull(pawnId);

        PieceData pawn = state.getPieceAt(new Square(0, 1));
        assertNotNull(pawn);
        assertEquals(PieceKind.PAWN, pawn.kind);
    }

    @Test
    void testPawnCannotPlaceOnPromotionRank() {
        GameState state = GameState.newDefault();
        state.setupInitialPosition();

        assertThrows(IllegalStateException.class,
                () -> state.placePiece(0, PieceKind.PAWN, new Square(0, 7)));
    }

    // ── 중립 기물 테스트 ──────────────────────────────────────

    @Test
    void testNeutralPiecePlacement() {
        GameState state = GameState.newDefault();
        String id = state.placeNeutralPiece(PieceKind.PAWN, new Square(3, 3));
        assertNotNull(id);

        PieceData p = state.getPieceAt(new Square(3, 3));
        assertNotNull(p);
        assertEquals(RuleSet.NEUTRAL, p.owner);
        assertTrue(p.isNeutral());
    }

    @Test
    void testNeutralPieceFriendlyToAll() {
        com.chesstack.engine.dsl.chessembly.BuiltinOps.BoardState board =
                new com.chesstack.engine.dsl.chessembly.BuiltinOps.BoardState(8, 8, 4, 4, "test", true);
        board.putPiece(5, 4, "pawn", true, RuleSet.NEUTRAL);

        assertTrue(board.hasFriendly(5, 4));
        assertFalse(board.hasEnemy(5, 4));

        board.isWhite = false;
        assertTrue(board.hasFriendly(5, 4));
        assertFalse(board.hasEnemy(5, 4));
    }

    // ── 이동 기록 테스트 ─────────────────────────────────────

    @Test
    void testMoveHistory() {
        GameState state = GameState.newDefault();
        assertTrue(state.getMoveHistory().isEmpty());

        List<LegalMove> moves = state.getLegalMovesAt(new Square(4, 0));
        LegalMove e2 = moves.stream()
                .filter(m -> m.to.equals(new Square(4, 1)))
                .findFirst().orElseThrow();
        state.movePieceByLegalMove(e2);

        assertEquals(1, state.getMoveHistory().size());
        assertEquals(PieceKind.KING, state.getMoveHistory().get(0).pieceKind);
    }

    // ── ChessStackEngine API 테스트 ──────────────────

    @Test
    void testEngineAPIFlow() {
        ChessStackEngine engine = new ChessStackEngine();

        String gameId = engine.createGame();
        assertNotNull(gameId);

        assertEquals(0, engine.getCurrentPlayer(gameId));

        PieceData king = engine.getPieceAt(gameId, 4, 0);
        assertNotNull(king);
        assertEquals(PieceKind.KING, king.kind);

        List<LegalMove> moves = engine.getLegalMoves(gameId, 4, 0);
        assertFalse(moves.isEmpty());

        String captured = engine.makeMove(gameId, 4, 0, 4, 1);
        assertNull(captured);

        engine.endTurn(gameId);
        assertEquals(1, engine.getCurrentPlayer(gameId));

        assertEquals(GameResult.ONGOING, engine.getGameResult(gameId));
    }

    // ── Square 표기법 테스트 ──────────────────────────────────

    @Test
    void testSquareNotation() {
        Square e4 = Square.fromNotation("e4");
        assertNotNull(e4);
        assertEquals(4, e4.x);
        assertEquals(3, e4.y);
        assertEquals("e4", e4.toNotation());

        Square a1 = Square.fromNotation("a1");
        assertNotNull(a1);
        assertEquals(0, a1.x);
        assertEquals(0, a1.y);

        Square h8 = Square.fromNotation("h8");
        assertNotNull(h8);
        assertEquals(7, h8.x);
        assertEquals(7, h8.y);
    }

    // ── 테스트 모드 테스트 ──────────────────────────────────────

    @Test
    void testTestModeBasic() {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();

        TestMode tm = engine.createTestMode(gameId);
        tm.setTarget("rook", true, 3, 3);

        List<LegalMove> moves = tm.execute();
        assertFalse(moves.isEmpty());
    }

    @Test
    void testTestModeWithBlocker() {
        TestMode tm = new TestMode();
        tm.setTarget("rook", true, 3, 3);
        tm.addPiece("pawn", true, 5, 3);

        List<LegalMove> movesWithBlocker = tm.execute();

        tm.reset();
        tm.setTarget("rook", true, 3, 3);
        List<LegalMove> movesWithout = tm.execute();

        assertTrue(movesWithBlocker.size() < movesWithout.size());
    }
}
