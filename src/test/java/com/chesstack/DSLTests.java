package com.chesstack;

import com.chesstack.engine.dsl.chessembly.*;
import com.chesstack.engine.dsl.chessembly.AST.*;
import com.chesstack.engine.movegen.StandardGenerators;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSLTests — Chessembly DSL 인터프리터 테스트.
 * Rust의 chessembly 테스트를 1:1 이식.
 */
class DSLTests {

    private static BuiltinOps.BoardState makeEmptyBoard() {
        return new BuiltinOps.BoardState(8, 8, 4, 4, "test", true);
    }

    // ── 기본 행마식 테스트 ────────────────────────────

    @Test
    void testWazir() {
        // 와지르: 상하좌우 1칸
        Interpreter interp = new Interpreter();
        interp.parse("take-move(1, 0); take-move(0, 1); take-move(-1, 0); take-move(0, -1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        assertEquals(4, acts.size());
        assertTrue(acts.stream().anyMatch(a -> a.dx == 1 && a.dy == 0));
        assertTrue(acts.stream().anyMatch(a -> a.dx == 0 && a.dy == 1));
        assertTrue(acts.stream().anyMatch(a -> a.dx == -1 && a.dy == 0));
        assertTrue(acts.stream().anyMatch(a -> a.dx == 0 && a.dy == -1));
    }

    @Test
    void testRookSlide() {
        Interpreter interp = new Interpreter();
        interp.parse("take-move(1, 0) repeat(1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        // (4,4)에서 오른쪽 → (5,4),(6,4),(7,4) = 3칸
        assertEquals(3, acts.size());
        assertTrue(acts.stream().anyMatch(a -> a.dx == 1 && a.dy == 0));
        assertTrue(acts.stream().anyMatch(a -> a.dx == 2 && a.dy == 0));
        assertTrue(acts.stream().anyMatch(a -> a.dx == 3 && a.dy == 0));
    }

    @Test
    void testRookBlockedByFriendly() {
        Interpreter interp = new Interpreter();
        interp.parse("take-move(1, 0) repeat(1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        board.putPiece(6, 4, "pawn", true); // 아군 배치
        List<Activation> acts = interp.execute(board);

        // (5,4)까지만 가능 (dx=1)
        assertEquals(1, acts.size());
        assertEquals(1, acts.get(0).dx);
    }

    @Test
    void testRookCaptureEnemy() {
        Interpreter interp = new Interpreter();
        interp.parse("take-move(1, 0) repeat(1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        board.putPiece(6, 4, "pawn", false); // 적 배치
        List<Activation> acts = interp.execute(board);

        // (5,4)와 (6,4) 모두 활성화
        assertEquals(2, acts.size());
    }

    @Test
    void testMoveOnly() {
        Interpreter interp = new Interpreter();
        interp.parse("move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        board.putPiece(5, 4, "enemy", false);
        List<Activation> acts = interp.execute(board);

        // 적이 있으면 move는 활성화 안됨
        assertEquals(0, acts.size());
    }

    @Test
    void testTakeOnly() {
        Interpreter interp = new Interpreter();
        interp.parse("take(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        // 빈 칸이면 take는 활성화 안됨
        assertEquals(0, acts.size());
    }

    // ── 스코프 테스트 ─────────────────────────────────

    @Test
    void testScopeAnchorRestore() {
        // Y자 행마: move(0,1) { move(1,1) } move(-1,1);
        Interpreter interp = new Interpreter();
        interp.parse("move(0, 1) { move(1, 1) } move(-1, 1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        assertEquals(3, acts.size());
        assertTrue(acts.stream().anyMatch(a -> a.dx == 0 && a.dy == 1));
        assertTrue(acts.stream().anyMatch(a -> a.dx == 1 && a.dy == 2));
        assertTrue(acts.stream().anyMatch(a -> a.dx == -1 && a.dy == 2));
    }

    // ── 조건식 테스트 ─────────────────────────────────

    @Test
    void testObserveBlockedKnight() {
        // 장기 마: 막히면 못 감
        Interpreter interp = new Interpreter();
        interp.parse("observe(1, 0) take-move(2, 1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        board.putPiece(5, 4, "blocker", true); // 아군 장애물
        List<Activation> acts = interp.execute(board);

        assertEquals(0, acts.size());
    }

    @Test
    void testObserveOpenKnight() {
        // 장기 마: 비어있으면 이동 가능
        Interpreter interp = new Interpreter();
        interp.parse("observe(1, 0) take-move(2, 1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        assertEquals(1, acts.size());
        assertEquals(2, acts.get(0).dx);
        assertEquals(1, acts.get(0).dy);
    }

    // ── 제어식 테스트 ─────────────────────────────────

    @Test
    void testDoWhilePattern() {
        Interpreter interp = new Interpreter();
        interp.parse("do move(1, 0) while;");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        // (4,4)에서 오른쪽 끝까지: 3칸
        assertEquals(3, acts.size());
    }

    @Test
    void testIfState() {
        Interpreter interp = new Interpreter();
        interp.parse("if-state(mode, 0) move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        // mode 기본값 0 → 조건 만족
        List<Activation> acts = interp.execute(board);

        assertEquals(1, acts.size());
    }

    @Test
    void testIfStateFalse() {
        Interpreter interp = new Interpreter();
        interp.parse("if-state(mode, 1) move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        // mode=0, 조건 mode==1 불만족
        List<Activation> acts = interp.execute(board);

        assertEquals(0, acts.size());
    }

    @Test
    void testPieceCondition() {
        Interpreter interp = new Interpreter();
        interp.parse("piece(rook) move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        board.pieceName = "rook";
        List<Activation> acts = interp.execute(board);

        assertEquals(1, acts.size());
    }

    @Test
    void testTransitionTag() {
        Interpreter interp = new Interpreter();
        interp.parse("transition(queen) move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        assertEquals(1, acts.size());
        assertEquals(1, acts.get(0).tags.size());
        assertEquals(ActionTagType.TRANSITION, acts.get(0).tags.get(0).tagType);
        assertEquals("queen", acts.get(0).tags.get(0).pieceName);
    }

    @Test
    void testNot() {
        Interpreter interp = new Interpreter();
        // observe false → not true → move 실행
        interp.parse("observe(1, 0) not move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        board.putPiece(5, 4, "blocker", true);
        // observe(1,0)=false(아군), not=true, 하지만 move(1,0)는 빈칸이 아니라 false
        List<Activation> acts = interp.execute(board);

        // (5,4)에 아군 → move 실행되지만 빈칸 아니므로 활성화 안됨
        assertEquals(0, acts.size());
    }

    @Test
    void testSkipChainOverBraces() {
        Interpreter interp = new Interpreter();
        interp.parse("if-state(mode, 1) set-state(mode, 0) { take-move(1, 0) repeat(1) } { take-move(-1, 0) repeat(1) };");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        // mode=0이므로 if-state(mode,1) false → 전체 스킵
        assertEquals(0, acts.size());
    }

    @Test
    void testJmp() {
        Interpreter interp = new Interpreter();
        interp.parse("piece(test) jmp(0) move(0, 1) label(0) piece(test) jmp(1) move(1, 0) move(1, 0) label(1);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        // piece(test)=true → jmp(0) → label(0)으로 점프 → move(0,1) 스킵
        // label(0) → piece(test)=true → jmp(1) → label(1)으로 점프 → move 두개 스킵
        assertEquals(0, acts.size());
    }

    @Test
    void testJne() {
        Interpreter interp = new Interpreter();
        interp.parse("piece(queen) jne(0) move(0, 1) label(0) move(1, 0) move(1, 0);");
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = interp.execute(board);

        // piece(queen)=false → jne(0)은 false일때 점프 → label(0)으로
        // label(0) → move(1,0) → move(1,0) 실행
        assertEquals(2, acts.size());
    }

    // ── VM 편의 래퍼 테스트 ───────────────────────────

    @Test
    void testVMWrapper() {
        VM vm = new VM();
        BuiltinOps.BoardState board = makeEmptyBoard();

        List<Activation> acts = vm.run(
                "take-move(1, 0); take-move(-1, 0); take-move(0, 1); take-move(0, -1);",
                board);

        assertEquals(4, acts.size());
    }

    // ── 커스텀 기물 등록 테스트 ───────────────────────

    @Test
    void testCustomPieceRegistration() {
        StandardGenerators.registerScript("testpiece",
                "take-move(2, 0); take-move(-2, 0);");

        String script = StandardGenerators.getScript("testpiece", true);
        assertEquals("take-move(2, 0); take-move(-2, 0);", script);

        VM vm = new VM();
        BuiltinOps.BoardState board = makeEmptyBoard();
        List<Activation> acts = vm.run(script, board);

        assertEquals(2, acts.size());
        assertTrue(acts.stream().anyMatch(a -> a.dx == 2 && a.dy == 0));
        assertTrue(acts.stream().anyMatch(a -> a.dx == -2 && a.dy == 0));

        StandardGenerators.unregisterScript("testpiece");
    }
}
