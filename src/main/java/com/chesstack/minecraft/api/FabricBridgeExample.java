package com.chesstack.minecraft.api;

import com.chesstack.engine.core.*;

import java.util.List;

/**
 * FabricBridgeExample — Minecraft Fabric 모드에서 ChessStackEngine을 호출하는 예시.
 *
 * 실제 Fabric 모드에서는:
 * - ServerPlayerEntity, BlockPos, World 등 MC 클래스와 연결
 * - 이 브릿지가 MC 이벤트 → 엔진 API 호출 → MC 렌더링 을 중개
 *
 * 이 클래스는 MC 의존성 없이 "이렇게 사용한다"는 패턴을 보여주는 예시.
 */
public final class FabricBridgeExample {

    private final ChessStackEngine engine;
    private String activeGameId;

    public FabricBridgeExample() {
        this.engine = new ChessStackEngine();
    }

    // ── Fabric 이벤트 핸들러 예시 ─────────────────────

    public void onChessBoardInteract() {
        activeGameId = engine.createGame();
        System.out.println("[ChessStack] 새 게임 시작: " + activeGameId);
        printBoard();
    }

    public void onPieceSelect(int x, int y) {
        if (activeGameId == null) return;

        Piece.PieceData piece = engine.getPieceAt(activeGameId, x, y);
        if (piece == null) {
            System.out.println("[ChessStack] 해당 위치에 기물이 없습니다.");
            return;
        }

        System.out.printf("[ChessStack] 기물 선택: %s @ (%d, %d)%n",
                piece.kind.scriptName(), x, y);

        List<Move.LegalMove> moves = engine.getLegalMoves(activeGameId, x, y);
        System.out.println("[ChessStack] 이동 가능한 칸 (" + moves.size() + "개):");
        for (Move.LegalMove m : moves) {
            System.out.printf("  → %s (%s)%s%n",
                    m.to.toNotation(), m.moveType, m.isCapture ? " [잡기]" : "");
        }
    }

    public void onMoveConfirm(int fromX, int fromY, int toX, int toY) {
        if (activeGameId == null) return;

        try {
            String captured = engine.makeMove(activeGameId, fromX, fromY, toX, toY);
            System.out.printf("[ChessStack] 이동: (%d,%d) → (%d,%d)%n",
                    fromX, fromY, toX, toY);
            if (captured != null) {
                System.out.println("[ChessStack] 잡힌 기물: " + captured);
            }

            Move.GameResult result = engine.getGameResult(activeGameId);
            if (result != Move.GameResult.ONGOING) {
                System.out.println("[ChessStack] 게임 종료: " + result);
            }

            printBoard();
        } catch (Exception e) {
            System.out.println("[ChessStack] 이동 실패: " + e.getMessage());
        }
    }

    public void onEndTurn() {
        if (activeGameId == null) return;
        engine.endTurn(activeGameId);
        System.out.println("[ChessStack] 턴 종료. 현재 턴: "
                + (engine.getCurrentPlayer(activeGameId) == 0 ? "백" : "흑"));
    }

    // ── 디버그 유틸 ───────────────────────────────────

    private void printBoard() {
        if (activeGameId == null) return;

        System.out.println("  a b c d e f g h");
        for (int y = 7; y >= 0; y--) {
            StringBuilder sb = new StringBuilder();
            sb.append(y + 1).append(' ');
            for (int x = 0; x < 8; x++) {
                Piece.PieceData p = engine.getPieceAt(activeGameId, x, y);
                if (p == null) {
                    sb.append(". ");
                } else {
                    char c;
                    switch (p.kind) {
                        case KING:   c = 'K'; break;
                        case QUEEN:  c = 'Q'; break;
                        case ROOK:   c = 'R'; break;
                        case BISHOP: c = 'B'; break;
                        case KNIGHT: c = 'N'; break;
                        case PAWN:   c = 'P'; break;
                        default:     c = p.kind.scriptName().charAt(0); break;
                    }
                    sb.append(p.isWhite() ? c : Character.toLowerCase(c)).append(' ');
                }
            }
            System.out.println(sb);
        }
    }

    // ── main (데모) ───────────────────────────────────

    public static void main(String[] args) {
        FabricBridgeExample bridge = new FabricBridgeExample();
        bridge.onChessBoardInteract();
        bridge.onPieceSelect(4, 0);
        bridge.onMoveConfirm(4, 0, 4, 1);
        bridge.onEndTurn();
        bridge.onPieceSelect(4, 7);

        // ── 테스트 모드 데모 ──
        System.out.println("\n[ChessStack] === 테스트 모드 ===");
        TestMode tm = bridge.engine.createTestMode(bridge.activeGameId);
        tm.setTarget("rook", true, 3, 3);
        tm.addPiece("pawn", false, 4, 5);
        List<Move.LegalMove> testMoves = tm.execute();
        System.out.println("[ChessStack] 테스트 결과: " + testMoves.size() + "개 이동 가능");
        for (Move.LegalMove m : testMoves) {
            System.out.printf("  → %s (%s)%n", m.to.toNotation(), m.moveType);
        }
    }
}
