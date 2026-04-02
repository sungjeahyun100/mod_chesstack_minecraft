package com.chesstack.minecraft.api;

import com.chesstack.engine.core.*;
import com.chesstack.engine.dsl.chessembly.*;
import com.chesstack.engine.movegen.*;

import java.util.*;

/**
 * TestMode — Chessembly 테스트 모드.
 * 격리된 보드에서 특정 기물의 행마법을 실시간으로 테스트한다.
 *
 * 사용 흐름:
 * 1. setTarget(kind, isWhite, x, y) — 대상 기물 설정
 * 2. addPiece(kind, isWhite, x, y) — 보드에 배치할 기물 추가
 * 3. execute() — 행마법 실행 → 합법 수 반환
 * 4. execute(script) — 커스텀 스크립트로 실행
 * 5. reset() — 보드 초기화
 */
public final class TestMode {

    private Piece.PieceKind targetKind;
    private boolean targetIsWhite;
    private int targetX;
    private int targetY;
    private final List<PlacedPiece> additionalPieces = new ArrayList<>();

    private static final class PlacedPiece {
        final Piece.PieceKind kind;
        final boolean isWhite;
        final int x, y;
        final boolean neutral;

        PlacedPiece(Piece.PieceKind kind, boolean isWhite, int x, int y, boolean neutral) {
            this.kind = kind;
            this.isWhite = isWhite;
            this.x = x;
            this.y = y;
            this.neutral = neutral;
        }
    }

    /** 대상 기물 설정 */
    public void setTarget(String kindName, boolean isWhite, int x, int y) {
        this.targetKind = Piece.PieceKind.fromString(kindName);
        this.targetIsWhite = isWhite;
        this.targetX = x;
        this.targetY = y;
    }

    /** 추가 기물 배치 */
    public void addPiece(String kindName, boolean isWhite, int x, int y) {
        additionalPieces.add(new PlacedPiece(
                Piece.PieceKind.fromString(kindName), isWhite, x, y, false));
    }

    /** 중립 기물 배치 */
    public void addNeutralPiece(String kindName, int x, int y) {
        additionalPieces.add(new PlacedPiece(
                Piece.PieceKind.fromString(kindName), true, x, y, true));
    }

    /** 내장 스크립트로 행마법 실행 */
    public List<Move.LegalMove> execute() {
        if (targetKind == null) throw new IllegalStateException("대상 기물이 설정되지 않았습니다.");
        String script = targetKind.chessemblyScript(targetIsWhite);
        return executeWithScript(script);
    }

    /** 커스텀 스크립트로 행마법 실행 */
    public List<Move.LegalMove> execute(String script) {
        if (targetKind == null) throw new IllegalStateException("대상 기물이 설정되지 않았습니다.");
        return executeWithScript(script);
    }

    /** 보드 초기화 (대상 기물 포함 전체 리셋) */
    public void reset() {
        targetKind = null;
        targetIsWhite = true;
        targetX = 0;
        targetY = 0;
        additionalPieces.clear();
    }

    // ── 내부 ──────────────────────────────────────────

    private List<Move.LegalMove> executeWithScript(String script) {
        BuiltinOps.BoardState board = new BuiltinOps.BoardState(
                RuleSet.BOARD_WIDTH, RuleSet.BOARD_HEIGHT,
                targetX, targetY,
                targetKind.scriptName(), targetIsWhite);

        // 추가 기물 배치
        for (PlacedPiece pp : additionalPieces) {
            if (pp.neutral) {
                board.putPiece(pp.x, pp.y, pp.kind.scriptName(), true, RuleSet.NEUTRAL);
            } else {
                board.putPiece(pp.x, pp.y, pp.kind.scriptName(), pp.isWhite);
            }
        }

        // 인터프리터 실행
        Interpreter interpreter = new Interpreter();
        interpreter.parse(script);
        List<AST.Activation> activations = interpreter.execute(board);

        // Activation → LegalMove 변환
        List<Move.LegalMove> results = new ArrayList<>();
        Move.Square pos = new Move.Square(targetX, targetY);

        for (AST.Activation act : activations) {
            Move.Square target = new Move.Square(pos.x + act.dx, pos.y + act.dy);
            if (!target.isValid()) continue;

            Move.Square catchTo = new Move.Square(0, 0);
            if (act.catchTo != null) {
                catchTo = new Move.Square(pos.x + act.catchTo[0], pos.y + act.catchTo[1]);
            }

            boolean isCapture = board.pieces.containsKey(
                    BuiltinOps.BoardState.key(target.x, target.y));

            results.add(new Move.LegalMove(
                    pos, target, act.moveType, isCapture, act.tags, catchTo, act.strArg));
        }

        return results;
    }
}
