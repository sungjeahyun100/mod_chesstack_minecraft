package com.chesstack.engine.dsl.chessembly;

import java.util.*;

/**
 * DebugSession — Chessembly 인터프리터의 단계별 실행 상태.
 *
 * 사용 흐름:
 *   DebugSession session = new DebugSession(board);
 *   session.appendChain("take-move(1,0) repeat(1);");  // 식 연쇄 추가
 *   while (session.hasNext()) {
 *       DebugSession.StepResult r = session.step();    // 한 식씩 실행
 *       // r 출력 ...
 *   }
 *   session.appendChain("take-move(-1,0) repeat(1);"); // 다음 체인 추가
 */
public final class DebugSession {

    // ── 단계 결과 ─────────────────────────────────────

    public enum StepKind {
        EXECUTED,   // 토큰 실행됨
        CHAIN_END,  // SEMICOLON (체인 경계)
        SKIPPED,    // lastValue=false여서 스킵됨
        FINISHED    // 더 이상 토큰 없음
    }

    public static final class StepResult {
        public final AST.Token token;             // 처리된 토큰 (FINISHED이면 null)
        public final int startPc;                 // 실행 전 PC
        public final int endPc;                   // 실행 후 PC
        public final StepKind kind;
        public final int chainIndex;              // 실행 후 체인 인덱스
        public final int anchorX, anchorY;        // 실행 후 앵커
        public final boolean lastValue;           // 실행 후 lastValue
        public final List<AST.Activation> activations;  // 스냅샷 (불변)
        public final AST.Activation addedActivation;    // 이번 스텝에서 새로 추가된 활성화 (없으면 null)

        StepResult(AST.Token token, int startPc, int endPc, StepKind kind,
                   int chainIndex, int anchorX, int anchorY, boolean lastValue,
                   List<AST.Activation> activations, AST.Activation addedActivation) {
            this.token = token;
            this.startPc = startPc;
            this.endPc = endPc;
            this.kind = kind;
            this.chainIndex = chainIndex;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.lastValue = lastValue;
            this.activations = Collections.unmodifiableList(new ArrayList<>(activations));
            this.addedActivation = addedActivation;
        }
    }

    // ── 토큰 & 보드 ───────────────────────────────────

    private final List<AST.Token> tokens = new ArrayList<>();
    final BuiltinOps.BoardState board;

    // ── 실행 상태 (execute() 루프 변수 → 필드) ────────

    private int pc = 0;
    private int chainIndex = 0;
    private int numOpenBrace = 0;
    private int anchorX = 0, anchorY = 0;
    private boolean lastValue = true;
    private final List<AST.ActionTag> pendingTags = new ArrayList<>();
    private int doIndex = -1;
    private final Deque<int[]> scopeStack = new ArrayDeque<>();
    private int[] lastTakePos = null;
    private final List<AST.Activation> activations = new ArrayList<>();
    private Map<Integer, Map<String, Integer>> labels = new HashMap<>();

    // ── 생성자 ────────────────────────────────────────

    public DebugSession(BuiltinOps.BoardState board) {
        this.board = board;
    }

    // ── 체인 추가 ─────────────────────────────────────

    /**
     * 식 연쇄 문자열을 파싱해 토큰 목록에 추가한다.
     *
     * @param chainText 세미콜론으로 끝나는 식 연쇄 문자열 (없으면 자동 추가)
     * @return 추가된 토큰 수
     * @throws RuntimeException 파싱 실패 시
     */
    public int appendChain(String chainText) {
        String text = chainText.trim();
        if (!text.endsWith(";")) text += ";";
        List<AST.Token> newTokens = Parser.parse(text);
        tokens.addAll(newTokens);
        rebuildLabels();
        return newTokens.size();
    }

    private void rebuildLabels() {
        labels.clear();
        int chainIdx = 0;
        for (int i = 0; i < tokens.size(); i++) {
            AST.Token t = tokens.get(i);
            if (t.type == AST.TokenType.LABEL) {
                labels.computeIfAbsent(chainIdx, k -> new HashMap<>())
                      .put(t.strArg, i + 1);
            } else if (t.type == AST.TokenType.SEMICOLON) {
                chainIdx++;
            }
        }
    }

    // ── 실행 제어 ─────────────────────────────────────

    /** 처리할 토큰이 남아 있으면 true */
    public boolean hasNext() {
        return pc < tokens.size();
    }

    /** 다음 실행될 토큰 (없으면 null) */
    public AST.Token peekToken() {
        return pc < tokens.size() ? tokens.get(pc) : null;
    }

    /**
     * 한 토큰 실행.
     * - 일반 토큰: 실행 후 EXECUTED 반환
     * - SEMICOLON: 체인 종료 처리 후 CHAIN_END 반환
     * - lastValue=false 상태의 비면제 토큰: 체인/스코프 끝까지 스킵 후 SKIPPED 반환
     * - 토큰 없음: FINISHED 반환
     */
    public StepResult step() {
        if (pc >= tokens.size()) {
            return new StepResult(null, pc, pc, StepKind.FINISHED,
                    chainIndex, anchorX, anchorY, lastValue,
                    activations, null);
        }

        AST.Token token = tokens.get(pc);
        int startPc = pc;
        pc++;

        boolean isExempt = isExemptType(token.type);

        if (!lastValue && !isExempt) {
            runSkip();
            return new StepResult(token, startPc, pc, StepKind.SKIPPED,
                    chainIndex, anchorX, anchorY, lastValue,
                    activations, null);
        }

        int prevSize = activations.size();
        executeToken(token);
        AST.Activation newAct = activations.size() > prevSize
                ? activations.get(activations.size() - 1) : null;

        StepKind kind = token.type == AST.TokenType.SEMICOLON
                ? StepKind.CHAIN_END : StepKind.EXECUTED;
        return new StepResult(token, startPc, pc, kind,
                chainIndex, anchorX, anchorY, lastValue,
                activations, newAct);
    }

    /**
     * 현재 체인이 끝날 때(CHAIN_END)까지 실행한다.
     */
    public List<StepResult> runChain() {
        List<StepResult> results = new ArrayList<>();
        int startChain = chainIndex;
        while (hasNext()) {
            StepResult r = step();
            results.add(r);
            if (r.kind == StepKind.CHAIN_END || r.kind == StepKind.FINISHED) break;
            // 스킵으로 체인 인덱스가 바뀐 경우도 종료
            if (chainIndex != startChain) break;
        }
        return results;
    }

    /**
     * 남은 모든 토큰을 실행한다.
     */
    public List<StepResult> runAll() {
        List<StepResult> results = new ArrayList<>();
        while (hasNext()) {
            results.add(step());
        }
        return results;
    }

    // ── 리셋 ──────────────────────────────────────────

    /** 실행 상태만 초기화 (토큰 목록 유지) */
    public void resetExecution() {
        pc = 0;
        chainIndex = 0;
        numOpenBrace = 0;
        anchorX = 0;
        anchorY = 0;
        lastValue = true;
        pendingTags.clear();
        doIndex = -1;
        scopeStack.clear();
        lastTakePos = null;
        activations.clear();
    }

    /** 토큰 목록과 실행 상태 전체 초기화 */
    public void clearAll() {
        tokens.clear();
        labels.clear();
        resetExecution();
    }

    // ── 상태 게터 ─────────────────────────────────────

    public int getPc()                            { return pc; }
    public int getTokenCount()                    { return tokens.size(); }
    public int getChainIndex()                    { return chainIndex; }
    public int getAnchorX()                       { return anchorX; }
    public int getAnchorY()                       { return anchorY; }
    public boolean getLastValue()                 { return lastValue; }
    public List<AST.Activation> getActivations()  { return Collections.unmodifiableList(activations); }
    public List<AST.Token> getTokens()            { return Collections.unmodifiableList(tokens); }
    public BuiltinOps.BoardState getBoard()       { return board; }

    // ── 내부 헬퍼 ─────────────────────────────────────

    private static boolean isExemptType(AST.TokenType type) {
        switch (type) {
            case WHILE: case JMP: case JNE: case NOT:
            case LABEL: case SEMICOLON: case CLOSE_BRACE:
                return true;
            default:
                return false;
        }
    }

    /** false 체인 스킵: 세미콜론 또는 스코프 닫기까지 전진 */
    private void runSkip() {
        while (pc < tokens.size()) {
            AST.TokenType tt = tokens.get(pc).type;
            if (tt == AST.TokenType.SEMICOLON) {
                anchorX = 0; anchorY = 0;
                pendingTags.clear();
                doIndex = -1;
                lastTakePos = null;
                pc++;
                chainIndex++;
                break;
            } else if (tt == AST.TokenType.CLOSE_BRACE) {
                if (numOpenBrace > 0) { numOpenBrace--; pc++; continue; }
                if (!scopeStack.isEmpty()) {
                    int[] saved = scopeStack.pop();
                    anchorX = saved[0]; anchorY = saved[1];
                }
                pc++;
                break;
            } else if (tt == AST.TokenType.OPEN_BRACE) {
                numOpenBrace++;
                pc++;
            } else {
                pc++;
            }
        }
        lastValue = true;
    }

    private void addAct(int dx, int dy, AST.MoveType mt, int[] catchTo) {
        addAct(dx, dy, mt, catchTo, null);
    }

    private void addAct(int dx, int dy, AST.MoveType mt, int[] catchTo, String strArg) {
        activations.add(new AST.Activation(dx, dy, mt, pendingTags, catchTo, strArg));
    }

    // ── 토큰 실행 (Interpreter.execute() 루프 본체와 동일) ──────────

    private void executeToken(AST.Token t) {
        switch (t.type) {

            // ── 구조 ──────────────────────────────────────────
            case SEMICOLON: {
                anchorX = 0; anchorY = 0;
                lastValue = true;
                pendingTags.clear();
                doIndex = -1;
                lastTakePos = null;
                chainIndex++;
                break;
            }
            case OPEN_BRACE: {
                scopeStack.push(new int[]{ anchorX, anchorY });
                lastValue = true;
                break;
            }
            case CLOSE_BRACE: {
                if (!scopeStack.isEmpty()) {
                    int[] saved = scopeStack.pop();
                    anchorX = saved[0]; anchorY = saved[1];
                }
                lastValue = true;
                break;
            }

            // ── 행마식 ────────────────────────────────────────
            case TAKE_MOVE: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (!board.inBounds(tx, ty) || board.hasFriendly(tx, ty)) {
                    lastValue = false;
                } else if (board.hasEnemy(tx, ty)) {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.TAKE_MOVE, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = false;
                } else {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.TAKE_MOVE, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = true;
                }
                break;
            }
            case MOVE: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (board.isEmpty(tx, ty)) {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.MOVE, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = true;
                } else {
                    lastValue = false;
                }
                break;
            }
            case TAKE: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (board.hasEnemy(tx, ty)) {
                    lastTakePos = new int[]{ anchorX + t.dx, anchorY + t.dy };
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.TAKE, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = true;
                } else {
                    if (board.inBounds(tx, ty) && !board.hasFriendly(tx, ty)) {
                        anchorX += t.dx; anchorY += t.dy;
                        lastValue = true;
                    } else {
                        lastValue = false;
                    }
                }
                break;
            }
            case JUMP: {
                if (!activations.isEmpty()
                        && activations.get(activations.size() - 1).moveType == AST.MoveType.TAKE) {
                    activations.remove(activations.size() - 1);
                }
                if (lastTakePos != null) {
                    int tx = board.pieceX + anchorX + t.dx;
                    int ty = board.pieceY + anchorY + t.dy;
                    if (board.isEmpty(tx, ty)) {
                        addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.JUMP, lastTakePos);
                        anchorX += t.dx; anchorY += t.dy;
                        lastValue = true;
                    } else {
                        lastValue = false;
                    }
                } else {
                    lastValue = false;
                }
                break;
            }
            case CATCH: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (board.hasEnemy(tx, ty)) {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.CATCH, null);
                    lastValue = true;
                } else {
                    lastValue = false;
                }
                break;
            }
            case SHIFT: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (board.inBounds(tx, ty) && !board.isEmpty(tx, ty)) {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.SHIFT, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = true;
                } else {
                    lastValue = false;
                }
                break;
            }
            case ANCHOR: {
                anchorX += t.dx;
                anchorY += t.dy;
                lastValue = true;
                break;
            }

            // ── 조건식 ────────────────────────────────────────
            case OBSERVE: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = board.isEmpty(tx, ty);
                break;
            }
            case PEEK: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (!board.inBounds(tx, ty)) {
                    lastValue = false;
                } else {
                    anchorX += t.dx;
                    anchorY += t.dy;
                    lastValue = board.isEmpty(tx, ty);
                }
                break;
            }
            case ENEMY: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = board.hasEnemy(tx, ty);
                break;
            }
            case FRIENDLY: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = board.hasFriendly(tx, ty);
                break;
            }
            case PIECE_ON: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = board.hasPiece(tx, ty, t.strArg);
                break;
            }
            case DANGER: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = board.isDanger(tx, ty);
                break;
            }
            case CHECK:
                lastValue = board.inCheck;
                break;
            case BOUND: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = !board.inBounds(tx, ty);
                break;
            }
            case EDGE: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = tx < 0 || tx >= board.boardWidth || ty < 0 || ty >= board.boardHeight;
                break;
            }
            case EDGE_TOP: {
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = ty >= board.boardHeight;
                break;
            }
            case EDGE_BOTTOM: {
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = ty < 0;
                break;
            }
            case EDGE_LEFT: {
                int tx = board.pieceX + anchorX + t.dx;
                lastValue = tx < 0;
                break;
            }
            case EDGE_RIGHT: {
                int tx = board.pieceX + anchorX + t.dx;
                lastValue = tx >= board.boardWidth;
                break;
            }
            case CORNER: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = (tx < 0 || tx >= board.boardWidth)
                         && (ty < 0 || ty >= board.boardHeight);
                break;
            }
            case CORNER_TOP_LEFT: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = tx < 0 && ty >= board.boardHeight;
                break;
            }
            case CORNER_TOP_RIGHT: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = tx >= board.boardWidth && ty >= board.boardHeight;
                break;
            }
            case CORNER_BOTTOM_LEFT: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = tx < 0 && ty < 0;
                break;
            }
            case CORNER_BOTTOM_RIGHT: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                lastValue = tx >= board.boardWidth && ty < 0;
                break;
            }

            // ── 상태 ──────────────────────────────────────────
            case PIECE:
                lastValue = board.pieceName.equals(t.strArg);
                break;
            case IF_STATE: {
                int actual = board.getState(t.strArg);
                lastValue = actual == t.intArg;
                break;
            }
            case SET_STATE: {
                pendingTags.add(new AST.ActionTag(
                        AST.ActionTagType.SET_STATE, t.strArg, t.intArg, null));
                lastValue = true;
                break;
            }
            case SET_STATE_RESET: {
                if (!pendingTags.isEmpty()) pendingTags.remove(pendingTags.size() - 1);
                lastValue = true;
                break;
            }
            case TRANSITION: {
                pendingTags.add(new AST.ActionTag(
                        AST.ActionTagType.TRANSITION, "", 0, t.strArg));
                lastValue = true;
                break;
            }

            // ── 제어 ──────────────────────────────────────────
            case REPEAT: {
                int n = t.intArg;
                if (lastValue && n > 0) {
                    int target = pc > n ? pc - n - 1 : 0;
                    pc = target;
                }
                break;
            }
            case DO: {
                if (lastValue) {
                    doIndex = pc;
                }
                break;
            }
            case WHILE: {
                if (lastValue && doIndex >= 0) {
                    pc = doIndex;
                }
                lastValue = true;
                break;
            }
            case JMP: {
                if (lastValue) {
                    Map<String, Integer> chainLabels = labels.get(chainIndex);
                    if (chainLabels != null && chainLabels.containsKey(t.strArg)) {
                        pc = chainLabels.get(t.strArg);
                    }
                }
                lastValue = true;
                break;
            }
            case JNE: {
                if (!lastValue) {
                    Map<String, Integer> chainLabels = labels.get(chainIndex);
                    if (chainLabels != null && chainLabels.containsKey(t.strArg)) {
                        pc = chainLabels.get(t.strArg);
                    }
                }
                lastValue = true;
                break;
            }
            case LABEL:
                /* 투명 — lastValue 유지 */
                break;
            case NOT:
                lastValue = !lastValue;
                break;
            case END:
                lastValue = false;
                break;

            // ── 소환·자동이동 ──────────────────────────────────
            case SUMMON: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (board.isEmpty(tx, ty)) {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.SUMMON, null, t.strArg);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = true;
                } else {
                    lastValue = false;
                }
                break;
            }
            case AUTO_MOVE: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (!board.inBounds(tx, ty) || board.hasFriendly(tx, ty)) {
                    lastValue = false;
                } else {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.AUTO_MOVE, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = !board.hasEnemy(tx, ty);
                }
                break;
            }
            case AUTO_SHIFT: {
                int tx = board.pieceX + anchorX + t.dx;
                int ty = board.pieceY + anchorY + t.dy;
                if (board.inBounds(tx, ty)) {
                    addAct(anchorX + t.dx, anchorY + t.dy, AST.MoveType.AUTO_SHIFT, null);
                    anchorX += t.dx; anchorY += t.dy;
                    lastValue = true;
                } else {
                    lastValue = false;
                }
                break;
            }

            // ── 히스토리 조건 ──────────────────────────────────
            case HISTORY_MOVED:
                lastValue = board.hasKindMoved(t.strArg);
                break;
            case HISTORY_EXISTS: {
                String[] parts = t.strArg.split(",");
                if (parts.length == 4) {
                    lastValue = board.hasMoveExists(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()),
                            Integer.parseInt(parts[3].trim()));
                } else {
                    lastValue = false;
                }
                break;
            }

            default:
                break;
        }
    }
}
