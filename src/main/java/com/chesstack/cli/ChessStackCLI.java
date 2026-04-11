package com.chesstack.cli;

import com.chesstack.engine.core.*;
import com.chesstack.engine.dsl.chessembly.BuiltinOps;
import com.chesstack.engine.dsl.chessembly.DebugSession;
import com.chesstack.engine.dsl.chessembly.AST;
import com.chesstack.minecraft.api.ChessStackEngine;

import java.util.*;

/**
 * ChessStackCLI — 터미널에서 ChessStack 엔진을 테스트할 수 있는 CLI.
 *
 * 실행: gradle runCli
 *
 * 명령어:
 *   place <기물> <칸>   — 포켓에서 기물 착수 (예: place pawn e4)
 *   move <from> <to>   — 기물 이동 (예: move e2 e4)
 *   end                — 턴 종료
 *   legal [<칸>]       — 특정 칸의 합법 수 조회 (칸 생략 시 모든 기물)
 *   pocket             — 현재 포켓 표시
 *   board              — 보드 다시 표시
 *   help               — 도움말
 *   quit / exit        — 종료
 */
public class ChessStackCLI {

    // ── Unicode 기물 심볼 ─────────────────────────────

    private static final Map<String, String[]> SYMBOLS = new LinkedHashMap<>();

    static {
        // [0]=백, [1]=흑
        SYMBOLS.put("pawn",           new String[]{"P", "p"});
        SYMBOLS.put("king",           new String[]{"K", "k"});
        SYMBOLS.put("queen",          new String[]{"Q", "q"});
        SYMBOLS.put("rook",           new String[]{"R", "r"});
        SYMBOLS.put("knight",         new String[]{"N", "n"});
        SYMBOLS.put("bishop",         new String[]{"B", "b"});
        SYMBOLS.put("amazon",         new String[]{"A", "a"});
        SYMBOLS.put("grasshopper",    new String[]{"G", "g"});
        SYMBOLS.put("knightrider",    new String[]{"R", "r"});
        SYMBOLS.put("archbishop",     new String[]{"C", "c"});
        SYMBOLS.put("dabbaba",        new String[]{"D", "d"});
        SYMBOLS.put("alfil",          new String[]{"F", "f"});
        SYMBOLS.put("ferz",           new String[]{"Z", "z"});
        SYMBOLS.put("centaur",        new String[]{"T", "t"});
        SYMBOLS.put("camel",          new String[]{"M", "m"});
        SYMBOLS.put("tempestrook",    new String[]{"W", "w"});
        SYMBOLS.put("cannon",         new String[]{"O", "o"});
        SYMBOLS.put("bouncingbishop", new String[]{"B", "b"});
        SYMBOLS.put("experiment",     new String[]{"X", "x"});
        SYMBOLS.put("dsltesting",     new String[]{"?", "?"});
    }

    private static String pieceSymbol(Piece.PieceData p) {
        String[] syms = SYMBOLS.get(p.kind.scriptName());
        if (syms == null) return p.isNeutral() ? "?" : p.isWhite() ? "W" : "B";
        if (p.isNeutral()) return "N";
        return syms[p.isWhite() ? 0 : 1];
    }

    // ── 보드 출력 ─────────────────────────────────────

    private static void printBoard(ChessStackEngine engine, String gameId,
                                   Set<Move.Square> highlights) {
        List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);

        // 좌표 -> 기물 맵
        Map<String, Piece.PieceData> grid = new HashMap<>();
        for (Piece.PieceData p : pieces) {
            if (p.pos != null) {
                grid.put(p.pos.x + "," + p.pos.y, p);
            }
        }

        System.out.println();
        System.out.println("    a   b   c   d   e   f   g   h");
        System.out.println("  +---+---+---+---+---+---+---+---+");

        for (int y = 7; y >= 0; y--) {
            System.out.print((y + 1) + " ");
            for (int x = 0; x < 8; x++) {
                Move.Square sq = new Move.Square(x, y);
                boolean isHighlight = highlights.contains(sq);
                Piece.PieceData p = grid.get(x + "," + y);

                String cell;
                if (p != null) {
                    String sym = pieceSymbol(p);
                    cell = isHighlight ? "[" + sym + "]" : " " + sym + " ";
                } else {
                    cell = isHighlight ? "[·]" : " · ";
                }
                System.out.print("|" + cell);
            }
            System.out.println("| " + (y + 1));
        }

        System.out.println("  +---+---+---+---+---+---+---+---+");
        System.out.println("    a   b   c   d   e   f   g   h");
        System.out.println();
    }

    // ── 포켓 출력 ─────────────────────────────────────

    private static void printPocket(ChessStackEngine engine, String gameId, int player) {
        List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
        String label = player == 0 ? "백(White)" : "흑(Black)";
        if (pocket == null || pocket.isEmpty()) {
            System.out.println("  " + label + " 포켓: (비어 있음)");
            return;
        }

        // 기물 종류별 카운트
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Piece.PieceSpec spec : pocket) {
            String name = spec.kind.scriptName();
            counts.merge(name, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder("  ").append(label).append(" 포켓: ");
        counts.forEach((name, cnt) -> sb.append(name).append(cnt > 1 ? "×" + cnt : "").append("  "));
        System.out.println(sb);
    }

    // ── 상태 헤더 출력 ────────────────────────────────

    private static void printStatus(ChessStackEngine engine, String gameId) {
        int turn = engine.getCurrentPlayer(gameId);
        System.out.println("══════════════════════════════════════════");
        System.out.println("  현재 턴: " + (turn == 0 ? "백(White) ●" : "흑(Black) ●"));
        printPocket(engine, gameId, 0);
        printPocket(engine, gameId, 1);
        System.out.println("══════════════════════════════════════════");
    }

    // ── 합법 수 출력 ──────────────────────────────────

    private static Set<Move.Square> legalTargets(ChessStackEngine engine,
                                                  String gameId, int x, int y) {
        try {
            List<Move.LegalMove> moves = engine.getLegalMoves(gameId, x, y);
            Set<Move.Square> targets = new HashSet<>();
            for (Move.LegalMove lm : moves) targets.add(lm.to);
            return targets;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    // ── 화면 지우기 ───────────────────────────────────

    private static void clearScreen() {
        // ANSI 이스케이프: 화면 지우기 + 커서 홈
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // ── 도움말 ────────────────────────────────────────

    private static void printHelp() {
        System.out.println();
        System.out.println("  명령어 목록:");
        System.out.println("  place <기물> <칸>           — 포켓에서 기물 착수  (예: place pawn e4)");
        System.out.println("  move <from> <to>           — 기물 이동           (예: move e1 e2)");
        System.out.println("  end                        — 턴 종료");
        System.out.println("  legal [<칸>]               — 합법 수 조회        (예: legal e1)");
        System.out.println("  pocket                     — 포켓 표시");
        System.out.println("  board                      — 보드 다시 표시");
        System.out.println("  clear                      — 화면 지우기");
        System.out.println("  new                        — 새 게임 시작");
        System.out.println("  exp                        — 실험용 게임 시작");
        System.out.println("  dsl [<기물> <칸> [white|black]] — 체섬블리 디버거  (예: dsl rook d4)");
        System.out.println("  help                       — 이 도움말");
        System.out.println("  quit / exit                — 종료");
        System.out.println();
        System.out.println("  기물 이름: pawn king queen rook knight bishop");
        System.out.println("             amazon grasshopper knightrider archbishop");
        System.out.println("             dabbaba alfil ferz centaur camel");
        System.out.println("             tempestrook cannon bouncingbishop experiment");
        System.out.println();
    }

    // ── 메인 루프 ─────────────────────────────────────

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       ChessStack CLI  (v1.0)             ║");
        System.out.println("║  'help' 로 명령어 목록을 확인하세요.      ║");
        System.out.println("╚══════════════════════════════════════════╝");

        printStatus(engine, gameId);
        printBoard(engine, gameId, Collections.emptySet());

        while (true) {
            // 승리 확인
            Move.GameResult result = engine.getGameResult(gameId);
            if (result != Move.GameResult.ONGOING) {
                System.out.println();
                if (result == Move.GameResult.WHITE_WINS) {
                    System.out.println("  ★ 게임 종료: 백(White) 승리!");
                } else {
                    System.out.println("  ★ 게임 종료: 흑(Black) 승리!");
                }
                System.out.println("  새 게임: 'new'  |  종료: 'quit'");
            }

            System.out.print("> ");
            String line;
            try {
                line = scanner.nextLine();
            } catch (NoSuchElementException e) {
                break;
            }
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+");
            String cmd = tokens[0].toLowerCase();

            try {
                switch (cmd) {

                    case "quit":
                    case "exit":
                        System.out.println("  종료합니다.");
                        scanner.close();
                        return;

                    case "help":
                        printHelp();
                        break;

                    case "new": {
                        gameId = engine.createGame();
                        System.out.println("  새 게임이 시작되었습니다 (표준 포켓).");
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.emptySet());
                        break;
                    }

                    case "exp": {
                        gameId = engine.createExperimentalGame();
                        System.out.println("  새 게임이 시작되었습니다 (실험용 포켓).");
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.emptySet());
                        break;
                    }

                    case "board":
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.emptySet());
                        break;

                    case "clear":
                        clearScreen();
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.emptySet());
                        break;

                    case "pocket":
                        printPocket(engine, gameId, 0);
                        printPocket(engine, gameId, 1);
                        break;

                    case "end": {
                        engine.endTurn(gameId);
                        System.out.println("  턴 종료.");
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.emptySet());
                        break;
                    }

                    case "legal": {
                        if (tokens.length < 2) {
                            // 현재 플레이어의 모든 기물 합법 수 조회
                            int currentTurn = engine.getCurrentPlayer(gameId);
                            Set<Move.Square> allTargets = new HashSet<>();
                            for (Piece.PieceData p : engine.getBoardPieces(gameId)) {
                                if (p.pos != null && (p.owner == currentTurn || p.isNeutral())) {
                                    allTargets.addAll(legalTargets(engine, gameId, p.pos.x, p.pos.y));
                                }
                            }
                            System.out.println("  현재 플레이어 이동 가능 칸: " + formatSquares(allTargets));
                            printBoard(engine, gameId, allTargets);
                        } else {
                            Move.Square sq = Move.Square.fromNotation(tokens[1]);
                            if (sq == null) { System.out.println("  올바르지 않은 칸: " + tokens[1]); break; }
                            List<Move.LegalMove> moves = engine.getLegalMoves(gameId, sq.x, sq.y);
                            if (moves.isEmpty()) {
                                System.out.println("  " + tokens[1] + " : 합법 수가 없습니다.");
                            } else {
                                System.out.println("  " + tokens[1] + " 합법 수 (" + moves.size() + "개):");
                                Set<Move.Square> targets = new HashSet<>();
                                for (Move.LegalMove lm : moves) {
                                    System.out.println("    " + lm);
                                    targets.add(lm.to);
                                }
                                printBoard(engine, gameId, targets);
                            }
                        }
                        break;
                    }

                    case "place": {
                        if (tokens.length < 3) { System.out.println("  사용법: place <기물> <칸>"); break; }
                        String kindName = tokens[1].toLowerCase();
                        Move.Square sq = Move.Square.fromNotation(tokens[2]);
                        if (sq == null) { System.out.println("  올바르지 않은 칸: " + tokens[2]); break; }

                        String placedId = engine.placePiece(gameId, kindName, sq.x, sq.y);
                        System.out.println("  [" + kindName + "] -> " + tokens[2] + " (ID: " + placedId + ")");
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.singleton(sq));
                        break;
                    }

                    case "move": {
                        if (tokens.length < 3) { System.out.println("  사용법: move <from> <to>"); break; }
                        Move.Square from = Move.Square.fromNotation(tokens[1]);
                        Move.Square to   = Move.Square.fromNotation(tokens[2]);
                        if (from == null) { System.out.println("  올바르지 않은 칸: " + tokens[1]); break; }
                        if (to   == null) { System.out.println("  올바르지 않은 칸: " + tokens[2]); break; }

                        String captured = engine.makeMove(gameId, from.x, from.y, to.x, to.y);
                        System.out.print("  이동: " + tokens[1] + " -> " + tokens[2]);
                        if (captured != null) System.out.print("  (캡처: " + captured + ")");
                        System.out.println();
                        printStatus(engine, gameId);
                        printBoard(engine, gameId, Collections.singleton(to));
                        break;
                    }

                    case "dsl":
                        runDslDebugger(scanner, tokens);
                        break;

                    default:
                        System.out.println("  알 수 없는 명령어: '" + cmd + "'  ('help' 로 도움말)");
                }

            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("  오류: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static String formatSquares(Set<Move.Square> squares) {
        if (squares.isEmpty()) return "(없음)";
        List<String> list = new ArrayList<>();
        for (Move.Square sq : squares) list.add(sq.toNotation());
        Collections.sort(list);
        return String.join(" ", list);
    }

    // ══════════════════════════════════════════════════
    //  체섬블리 디버거
    // ══════════════════════════════════════════════════

    /**
     * 체섬블리 식을 한 연쇄씩 입력하고 한 식 단위로 단계 실행하는 대화형 디버거.
     *
     * 사용법:  dsl [<기물> <칸> [white|black]]
     *   예) dsl rook d4
     *       dsl pawn e2 black
     *
     * 디버거 내부 명령:
     *   <식 연쇄>;          — 식 연쇄를 추가 (세미콜론으로 끝내기)
     *   s / step            — 한 식 실행
     *   r / run             — 현재 체인 끝까지 실행
     *   a / all             — 남은 모든 토큰 실행
     *   l / list            — 토큰 목록 + PC 위치 표시
     *   i / info / state    — 현재 실행 상태 표시
     *   b / board           — 보드 + 활성화 표시
     *   place <기물> <칸> [w|b]  — 보드에 기물 추가
     *   remove <칸>         — 보드에서 기물 제거
     *   reset               — 실행 상태 초기화 (토큰 유지)
     *   clear               — 토큰 및 실행 상태 전체 초기화
     *   exit / quit         — 디버거 종료
     */
    private static void runDslDebugger(Scanner scanner, String[] args) {

        // ── 보드 컨텍스트 초기화 ──────────────────────
        int pieceX = 3, pieceY = 3;   // 기본: d4
        String pieceName = "pawn";
        boolean isWhite = true;

        if (args.length >= 3) {
            pieceName = args[1].toLowerCase();
            Move.Square sq = Move.Square.fromNotation(args[2]);
            if (sq != null) { pieceX = sq.x; pieceY = sq.y; }
            else System.out.println("  경고: 올바르지 않은 칸 '" + args[2] + "', d4 사용.");
        }
        if (args.length >= 4) {
            isWhite = !args[3].toLowerCase().startsWith("b");
        }

        BuiltinOps.BoardState board =
                new BuiltinOps.BoardState(8, 8, pieceX, pieceY, pieceName, isWhite);
        DebugSession session = new DebugSession(board);

        System.out.println();
        System.out.println("  ╔══ 체섬블리 디버거 ══════════════════════════════════╗");
        System.out.printf ("  ║  기물: %-8s @ %-3s  (%s)%n",
                pieceName,
                new Move.Square(pieceX, pieceY).toNotation(),
                isWhite ? "백" : "흑");
        System.out.println("  ╚════════════════════════════════════════════════════╝");
        printDslHelp();
        dslPrintBoard(board, Collections.emptyList(), 0, 0);

        while (true) {
            // 프롬프트: [체인=N  PC=P/Total]
            System.out.printf("  dsl[C=%d PC=%d/%d]> ",
                    session.getChainIndex(), session.getPc(), session.getTokenCount());

            String line;
            try { line = scanner.nextLine(); }
            catch (NoSuchElementException e) { break; }
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            // 세미콜론으로 끝나면 식 연쇄 추가
            if (line.endsWith(";")) {
                try {
                    int added = session.appendChain(line);
                    System.out.printf("  [+] 체인 %d: %d개 토큰 추가%n",
                            session.getChainIndex(), added);
                    dslPrintTokenList(session);
                    AST.Token next = session.peekToken();
                    if (next != null) {
                        System.out.printf("  ▶ 다음: PC=%d  %s%n",
                                session.getPc(), dslTokenStr(next));
                    }
                } catch (Exception e) {
                    System.out.println("  파싱 오류: " + e.getMessage());
                }
                continue;
            }

            String cmd = line.split("\\s+")[0].toLowerCase();

            switch (cmd) {

                case "exit": case "quit":
                    System.out.println("  디버거를 종료합니다.");
                    return;

                case "s": case "step": {
                    if (!session.hasNext()) {
                        System.out.println("  (실행할 토큰 없음 — 식 연쇄를 입력하세요)");
                        break;
                    }
                    DebugSession.StepResult r = session.step();
                    dslPrintStepResult(r, board);
                    if (!session.hasNext()) {
                        System.out.println("  ── 모든 토큰 실행 완료 ──");
                        dslPrintActivations(session.getActivations(), board);
                    }
                    break;
                }

                case "r": case "run": {
                    if (!session.hasNext()) {
                        System.out.println("  (실행할 토큰 없음 — 식 연쇄를 입력하세요)");
                        break;
                    }
                    List<DebugSession.StepResult> results = session.runChain();
                    for (DebugSession.StepResult r : results) dslPrintStepResult(r, board);
                    System.out.println("  ── 체인 실행 완료 ──");
                    break;
                }

                case "a": case "all": {
                    if (!session.hasNext()) {
                        System.out.println("  (실행할 토큰 없음)");
                        break;
                    }
                    List<DebugSession.StepResult> results = session.runAll();
                    for (DebugSession.StepResult r : results) dslPrintStepResult(r, board);
                    System.out.println("  ── 전체 실행 완료 ──");
                    dslPrintActivations(session.getActivations(), board);
                    break;
                }

                case "l": case "list":
                    dslPrintTokenList(session);
                    break;

                case "i": case "info": case "state":
                    dslPrintState(session);
                    break;

                case "b": case "board":
                    dslPrintBoard(board, session.getActivations(),
                            session.getAnchorX(), session.getAnchorY());
                    dslPrintActivations(session.getActivations(), board);
                    break;

                case "place": {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 3) {
                        System.out.println("  사용법: place <기물> <칸> [w|b]");
                        break;
                    }
                    Move.Square sq = Move.Square.fromNotation(parts[2]);
                    if (sq == null) { System.out.println("  올바르지 않은 칸: " + parts[2]); break; }
                    boolean white = parts.length < 4 || !parts[3].toLowerCase().startsWith("b");
                    board.putPiece(sq.x, sq.y, parts[1].toLowerCase(), white);
                    System.out.printf("  보드에 추가: %s @ %s (%s)%n",
                            parts[1], parts[2], white ? "백" : "흑");
                    dslPrintBoard(board, session.getActivations(),
                            session.getAnchorX(), session.getAnchorY());
                    break;
                }

                case "remove": {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 2) { System.out.println("  사용법: remove <칸>"); break; }
                    Move.Square sq = Move.Square.fromNotation(parts[1]);
                    if (sq == null) { System.out.println("  올바르지 않은 칸: " + parts[1]); break; }
                    board.pieces.remove(BuiltinOps.BoardState.key(sq.x, sq.y));
                    System.out.println("  제거: " + parts[1]);
                    dslPrintBoard(board, session.getActivations(),
                            session.getAnchorX(), session.getAnchorY());
                    break;
                }

                case "reset":
                    session.resetExecution();
                    System.out.println("  실행 상태 초기화 (토큰 유지).");
                    dslPrintTokenList(session);
                    break;

                case "wipe":
                    session.clearAll();
                    System.out.println("  토큰 및 실행 상태 전체 초기화.");
                    break;

                case "clear":
                    clearScreen();
                    dslPrintBoard(board, session.getActivations(),
                            session.getAnchorX(), session.getAnchorY());
                    break;

                case "help": case "?":
                    printDslHelp();
                    break;

                default:
                    System.out.println("  알 수 없는 명령: '" + cmd + "'  (help 로 도움말)");
            }
        }
    }

    // ── DSL 디버거 출력 헬퍼 ──────────────────────────

    private static void printDslHelp() {
        System.out.println();
        System.out.println("  명령어 (식 연쇄: 세미콜론으로 끝내서 입력)");
        System.out.println("  <식 연쇄>;     — 식 연쇄 추가  (예: take-move(1,0) repeat(1);)");
        System.out.println("  s / step       — 한 식 실행");
        System.out.println("  r / run        — 현재 체인 끝까지 실행");
        System.out.println("  a / all        — 남은 전체 실행");
        System.out.println("  l / list       — 토큰 목록 + PC 표시");
        System.out.println("  i / info       — 실행 상태 표시");
        System.out.println("  b / board      — 보드 + 활성화 표시");
        System.out.println("  clear          — 화면 지우기");
        System.out.println("  place <기물> <칸> [w|b]  — 보드에 기물 추가");
        System.out.println("  remove <칸>    — 보드에서 기물 제거");
        System.out.println("  reset          — 실행 상태 초기화 (토큰 유지)");
        System.out.println("  wipe           — 토큰 및 실행 상태 전체 초기화");
        System.out.println("  clear          — 화면 지우기");
        System.out.println("  exit / quit    — 디버거 종료");
        System.out.println();
    }

    /** 토큰 하나를 읽기 좋은 문자열로 변환 */
    private static String dslTokenStr(AST.Token t) {
        switch (t.type) {
            case TAKE_MOVE: return String.format("take-move(%d,%d)", t.dx, t.dy);
            case MOVE:      return String.format("move(%d,%d)", t.dx, t.dy);
            case TAKE:      return String.format("take(%d,%d)", t.dx, t.dy);
            case CATCH:     return String.format("catch(%d,%d)", t.dx, t.dy);
            case SHIFT:     return String.format("shift(%d,%d)", t.dx, t.dy);
            case JUMP:      return String.format("jump(%d,%d)", t.dx, t.dy);
            case ANCHOR:    return String.format("anchor(%d,%d)", t.dx, t.dy);
            case AUTO_MOVE: return String.format("auto(%d,%d)", t.dx, t.dy);
            case AUTO_SHIFT:return String.format("auto-shift(%d,%d)", t.dx, t.dy);
            case SUMMON:    return String.format("summon(%s,%d,%d)", t.strArg, t.dx, t.dy);
            case REPEAT:    return String.format("repeat(%d)", t.intArg);
            case LABEL:     return String.format("label(%s)", t.strArg);
            case JMP:       return String.format("jmp(%s)", t.strArg);
            case JNE:       return String.format("jne(%s)", t.strArg);
            case SET_STATE: return String.format("set-state(%s,%d)", t.strArg, t.intArg);
            case IF_STATE:  return String.format("if-state(%s,%d)", t.strArg, t.intArg);
            case TRANSITION:return String.format("transition(%s)", t.strArg);
            case PIECE:     return String.format("piece(%s)", t.strArg);
            case PIECE_ON:  return String.format("piece-on(%s,%d,%d)", t.strArg, t.dx, t.dy);
            case OBSERVE:   return String.format("empty(%d,%d)", t.dx, t.dy);
            case PEEK:      return String.format("peek(%d,%d)", t.dx, t.dy);
            case ENEMY:     return String.format("enemy(%d,%d)", t.dx, t.dy);
            case FRIENDLY:  return String.format("friendly(%d,%d)", t.dx, t.dy);
            case DANGER:    return String.format("danger(%d,%d)", t.dx, t.dy);
            case BOUND:     return String.format("bound(%d,%d)", t.dx, t.dy);
            case HISTORY_MOVED:   return String.format("history-moved(%s)", t.strArg);
            case HISTORY_EXISTS:  return String.format("history-exists(%s)", t.strArg);
            case SEMICOLON: return ";";
            case OPEN_BRACE:  return "{";
            case CLOSE_BRACE: return "}";
            default:        return t.type.name().toLowerCase();
        }
    }

    /** 토큰 목록을 PC 커서와 함께 출력 */
    private static void dslPrintTokenList(DebugSession session) {
        List<AST.Token> toks = session.getTokens();
        int pc = session.getPc();
        System.out.println();
        System.out.printf("  [토큰 목록] 총 %d개%n", toks.size());
        for (int i = 0; i < toks.size(); i++) {
            String cursor = (i == pc) ? "▶" : " ";
            System.out.printf("  %s %3d: %s%n", cursor, i, dslTokenStr(toks.get(i)));
        }
        if (pc >= toks.size()) {
            System.out.printf("  ▶ %3d: (끝 — 새 체인을 입력하세요)%n", pc);
        }
        System.out.println();
    }

    /** 현재 실행 상태 출력 */
    private static void dslPrintState(DebugSession session) {
        System.out.println();
        System.out.printf("  PC=%d / %d  │  체인=%d  │  Anchor=(%d,%d)  │  last=%b%n",
                session.getPc(), session.getTokenCount(),
                session.getChainIndex(),
                session.getAnchorX(), session.getAnchorY(),
                session.getLastValue());
        System.out.printf("  기물 위치: (%d,%d)=%s  │  활성화: %d개%n",
                session.getBoard().pieceX, session.getBoard().pieceY,
                new Move.Square(session.getBoard().pieceX, session.getBoard().pieceY).toNotation(),
                session.getActivations().size());
        System.out.println();
    }

    /** 한 스텝 결과 출력 */
    private static void dslPrintStepResult(DebugSession.StepResult r,
                                            BuiltinOps.BoardState board) {
        String kindMark;
        switch (r.kind) {
            case CHAIN_END: kindMark = "──"; break;
            case SKIPPED:   kindMark = "✗ "; break;
            case FINISHED:  kindMark = "▣ "; break;
            default:        kindMark = "  "; break;
        }

        if (r.kind == DebugSession.StepKind.FINISHED) {
            System.out.println("  ▣ [FINISHED]");
            return;
        }

        String tokenStr = r.token != null ? dslTokenStr(r.token) : "?";

        if (r.kind == DebugSession.StepKind.SKIPPED) {
            System.out.printf("  ✗ [PC=%d] %s  -> SKIP (last=false)  │  PC->%d %n",
                    r.startPc, tokenStr, r.endPc);
            return;
        }

        // EXECUTED or CHAIN_END
        String lastMark = r.lastValue ? "✓" : "✗";
        String actInfo = r.addedActivation != null
                ? String.format("  +Activation{dx=%d,dy=%d,%s}",
                        r.addedActivation.dx, r.addedActivation.dy,
                        r.addedActivation.moveType)
                : "";
        String pcJump = r.endPc != r.startPc + 1
                ? String.format("  PC->%d", r.endPc)
                : "";

        if (r.kind == DebugSession.StepKind.CHAIN_END) {
            System.out.printf("  ── [PC=%d] %s  (체인 %d 종료 -> 체인 %d)%n",
                    r.startPc, tokenStr, r.chainIndex - 1, r.chainIndex);
        } else {
            System.out.printf("  %s [PC=%d] %-30s  Anchor=(%d,%d)  last=%s%s%s%n",
                    kindMark, r.startPc, tokenStr,
                    r.anchorX, r.anchorY, lastMark,
                    actInfo, pcJump);
        }

        // 다음 토큰 예고
        if (r.kind != DebugSession.StepKind.CHAIN_END) {
            // 다음 토큰 정보는 state 명령으로 확인
        }
    }

    /** 활성화 목록 요약 출력 */
    private static void dslPrintActivations(List<AST.Activation> acts,
                                             BuiltinOps.BoardState board) {
        if (acts.isEmpty()) {
            System.out.println("  [활성화] 없음");
            return;
        }
        System.out.printf("  [활성화] %d개:%n", acts.size());
        for (int i = 0; i < acts.size(); i++) {
            AST.Activation a = acts.get(i);
            int tx = board.pieceX + a.dx;
            int ty = board.pieceY + a.dy;
            System.out.printf("    [%d] %-12s  오프셋=(%+d,%+d)  칸=%s%n",
                    i, a.moveType,
                    a.dx, a.dy,
                    new Move.Square(tx, ty).toNotation());
        }
        System.out.println();
    }

    /** DSL 디버거용 미니 보드 (기물 위치 + 활성화 표시) */
    private static void dslPrintBoard(BuiltinOps.BoardState board,
                                       List<AST.Activation> activations,
                                       int anchorX, int anchorY) {
        // 활성화 -> 대상 칸 셋
        Set<String> actKeys = new HashSet<>();
        for (AST.Activation a : activations) {
            int tx = board.pieceX + a.dx;
            int ty = board.pieceY + a.dy;
            actKeys.add(tx + "," + ty);
        }
        String pieceKey  = board.pieceX + "," + board.pieceY;
        int anchorAbsX   = board.pieceX + anchorX;
        int anchorAbsY   = board.pieceY + anchorY;
        String anchorKey = anchorAbsX + "," + anchorAbsY;
        boolean anchorOnPiece = anchorKey.equals(pieceKey);

        System.out.println();
        System.out.println("    a   b   c   d   e   f   g   h");
        System.out.println("  +---+---+---+---+---+---+---+---+");
        for (int y = 7; y >= 0; y--) {
            System.out.print((y + 1) + " ");
            for (int x = 0; x < 8; x++) {
                String key = x + "," + y;
                String cell;
                if (key.equals(pieceKey) && !anchorOnPiece) {
                    cell = "[◎]";
                } else if (key.equals(anchorKey) && !anchorOnPiece) {
                    cell = "[★]";
                } else if (key.equals(pieceKey)) {
                    // 앵커와 기물이 같은 칸
                    cell = "[✦]";
                } else if (actKeys.contains(key)) {
                    cell = "[●]";
                } else {
                    BuiltinOps.PieceInfo pi = board.pieces.get(BuiltinOps.BoardState.key(x, y));
                    if (pi != null) {
                        cell = " " + (pi.isWhite ? "W" : "B") + " ";
                    } else {
                        cell = " · ";
                    }
                }
                System.out.print("|" + cell);
            }
            System.out.println("| " + (y + 1));
        }
        System.out.println("  +---+---+---+---+---+---+---+---+");
        System.out.println("    a   b   c   d   e   f   g   h");
        if (anchorOnPiece) {
            System.out.printf("  ✦=기물·앵커(%s)  ●=활성화(%d개)%n",
                    new Move.Square(board.pieceX, board.pieceY).toNotation(), activations.size());
        } else {
            System.out.printf("  ◎=기물(%s)  ★=앵커(%s)  ●=활성화(%d개)%n",
                    new Move.Square(board.pieceX, board.pieceY).toNotation(),
                    new Move.Square(anchorAbsX, anchorAbsY).toNotation(),
                    activations.size());
        }
        System.out.println();
    }
}
