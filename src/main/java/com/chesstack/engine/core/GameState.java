package com.chesstack.engine.core;

import com.chesstack.engine.dsl.chessembly.*;
import com.chesstack.engine.movegen.MoveGenerator;

import java.util.*;

/**
 * GameState — 전체 게임 상태 관리.
 *
 * 포함: 보드, 포켓, 기물 맵, 턴 관리, 이동/착수/캡처/계승,
 *       행마법 계산(chessembly 연동), 승리 조건 확인, 중립 기물,
 *       자동 이동, 이동 히스토리.
 */
public final class GameState {

    // ── 이동 기록 ─────────────────────────────────────

    public static final class MoveRecord {
        public final String pieceId;
        public final Piece.PieceKind pieceKind;
        public final Move.Square from;
        public final Move.Square to;
        public final int turnNumber;

        public MoveRecord(String pieceId, Piece.PieceKind pieceKind,
                          Move.Square from, Move.Square to, int turnNumber) {
            this.pieceId = pieceId;
            this.pieceKind = pieceKind;
            this.from = from;
            this.to = to;
            this.turnNumber = turnNumber;
        }
    }

    // ── 필드 ──────────────────────────────────────────

    private final Board board = new Board();
    private final Map<Integer, List<Piece.PieceSpec>> pockets = new HashMap<>();
    private final Map<String, Piece.PieceData> pieces = new HashMap<>();
    private int turn;
    private int turnNumber;
    private final Map<String, Integer> globalState = new HashMap<>();
    private String activePiece;   // 현재 턴에 이동 중인 기물 ID
    private boolean actionTaken;  // 이번 턴에 행동 여부
    private boolean debugMode;
    private int nextPieceId;
    private final List<MoveRecord> moveHistory = new ArrayList<>();

    // ── 생성자 ────────────────────────────────────────

    public GameState(int startingPlayer) {
        this.turn = startingPlayer;
        this.turnNumber = 0;
        setupInitialKings();
    }

    public static GameState newDefault() {
        return new GameState(0);
    }

    // ── 초기화 ────────────────────────────────────────

    private void setupInitialKings() {
        // 백 킹 (e1)
        Piece.PieceData wk = createPiece(Piece.PieceKind.KING, 0);
        pieces.put(wk.id, wk);
        placeKing(wk.id, new Move.Square(4, 0));

        // 흑 킹 (e8)
        Piece.PieceData bk = createPiece(Piece.PieceKind.KING, 1);
        pieces.put(bk.id, bk);
        placeKing(bk.id, new Move.Square(4, 7));
    }

    private void placeKing(String pieceId, Move.Square square) {
        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) return;
        p.pos = square;
        p.isRoyal = true;
        board.put(square, pieceId);
    }

    private Piece.PieceData createPiece(Piece.PieceKind kind, int owner) {
        String id = "piece_" + nextPieceId++;
        return new Piece.PieceData(id, kind, owner);
    }

    // ── 포켓 ──────────────────────────────────────────

    /** 초기 포지션 설정 (킹 + 기본 포켓) */
    public void setupInitialPosition() {
        List<Piece.PieceSpec> wPocket = Arrays.asList(
            new Piece.PieceSpec(Piece.PieceKind.QUEEN),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN)
        );
        setupPocket(0, new ArrayList<>(wPocket));

        List<Piece.PieceSpec> bPocket = Arrays.asList(
            new Piece.PieceSpec(Piece.PieceKind.QUEEN),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN)
        );
        setupPocket(1, new ArrayList<>(bPocket));
    }

    /** 포켓 설정 (점수 합계 검증) */
    public void setupPocket(int player, List<Piece.PieceSpec> specs) {
        int total = specs.stream().mapToInt(Piece.PieceSpec::score).sum();
        if (total > RuleSet.MAX_POCKET_SCORE) {
            throw new IllegalArgumentException(
                "포켓 점수 " + total + "점이 제한 " + RuleSet.MAX_POCKET_SCORE + "점을 초과합니다");
        }
        pockets.put(player, new ArrayList<>(specs));
    }

    /** 포켓 설정 (점수 제한 없음, 실험용) */
    public void setupPocketUnchecked(int player, List<Piece.PieceSpec> specs) {
        pockets.put(player, new ArrayList<>(specs));
    }

    /** 실험용 포켓 설정 */
    public void setupExperimentalPocket() {
        List<Piece.PieceSpec> pocket = Arrays.asList(
            new Piece.PieceSpec(Piece.PieceKind.AMAZON),
            new Piece.PieceSpec(Piece.PieceKind.GRASSHOPPER),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHTRIDER),
            new Piece.PieceSpec(Piece.PieceKind.ARCHBISHOP),
            new Piece.PieceSpec(Piece.PieceKind.DABBABA),
            new Piece.PieceSpec(Piece.PieceKind.ALFIL),
            new Piece.PieceSpec(Piece.PieceKind.FERZ),
            new Piece.PieceSpec(Piece.PieceKind.CENTAUR),
            new Piece.PieceSpec(Piece.PieceKind.CAMEL),
            new Piece.PieceSpec(Piece.PieceKind.TEMPEST_ROOK),
            new Piece.PieceSpec(Piece.PieceKind.CANNON),
            new Piece.PieceSpec(Piece.PieceKind.BOUNCING_BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.EXPERIMENT)
        );
        setupPocketUnchecked(0, new ArrayList<>(pocket));
        setupPocketUnchecked(1, new ArrayList<>(pocket));
    }

    // ── 착수 ──────────────────────────────────────────

    /** 착수 가능 여부 확인 */
    public void canPlace(int player, Piece.PieceKind kind, Move.Square target) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken) throw new IllegalStateException("이번 턴에 이미 행동했습니다");
        if (activePiece != null) throw new IllegalStateException("이동 중인 기물이 있습니다");
        if (board.contains(target)) throw new IllegalStateException("해당 칸에 이미 기물이 있습니다");

        boolean isWhite = player == 0;
        if (kind.isPromotionSquare(target, isWhite)) {
            throw new IllegalStateException("프로모션 기물은 프로모션 칸에 착수할 수 없습니다");
        }

        List<Piece.PieceSpec> pocket = pockets.get(player);
        if (pocket == null || pocket.stream().noneMatch(s -> s.kind == kind)) {
            throw new IllegalStateException("포켓에 해당 기물이 없습니다");
        }
    }

    /** 착수 실행 */
    public String placePiece(int player, Piece.PieceKind kind, Move.Square target) {
        canPlace(player, kind, target);

        // 포켓에서 제거
        List<Piece.PieceSpec> pocket = pockets.get(player);
        if (pocket != null) {
            for (int i = 0; i < pocket.size(); i++) {
                if (pocket.get(i).kind == kind) {
                    pocket.remove(i);
                    break;
                }
            }
        }

        // 기물 생성 및 배치
        Piece.PieceData piece = createPiece(kind, player);
        piece.pos = target;

        pieces.put(piece.id, piece);
        board.put(target, piece.id);
        actionTaken = true;

        return piece.id;
    }

    /** 중립 기물 착수 (보드에 직접 배치, 포켓 불필요) */
    public String placeNeutralPiece(Piece.PieceKind kind, Move.Square target) {
        if (board.contains(target)) throw new IllegalStateException("해당 칸에 이미 기물이 있습니다");

        Piece.PieceData piece = createPiece(kind, RuleSet.NEUTRAL);
        piece.pos = target;

        pieces.put(piece.id, piece);
        board.put(target, piece.id);

        return piece.id;
    }

    // ── 이동 검증 ─────────────────────────────────────

    /** 이동 가능 여부 확인 */
    public void canMovePiece(int player, String pieceId, Move.Square from, Move.Square to,
                              AST.MoveType moveType) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken) throw new IllegalStateException("이번 턴에 이미 다른 행동을 했습니다");

        if (activePiece != null && !activePiece.equals(pieceId)) {
            throw new IllegalStateException("다른 기물이 이동 중입니다");
        }

        Piece.PieceData piece = pieces.get(pieceId);
        if (piece == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        // 자신의 기물 또는 중립 기물만 이동 가능
        if (piece.owner != player && !piece.isNeutral()) {
            throw new IllegalStateException("자신의 기물이 아닙니다");
        }

        boolean targetEmpty = !board.contains(to);
        boolean hasEnemy = false, hasFriendly = false;
        String targetPid = board.get(to);
        if (targetPid != null) {
            Piece.PieceData tp = pieces.get(targetPid);
            if (tp != null) {
                hasEnemy = tp.owner != player && !tp.isNeutral();
                hasFriendly = tp.owner == player || tp.isNeutral();
            }
        }

        switch (moveType) {
            case MOVE:
                if (!targetEmpty) throw new IllegalStateException("Move는 빈 칸으로만 이동할 수 있습니다");
                break;
            case TAKE:
                if (!hasEnemy) throw new IllegalStateException("Take는 적이 있는 칸으로만 이동할 수 있습니다");
                break;
            case CATCH:
                if (!hasEnemy) throw new IllegalStateException("Catch는 적이 있는 칸만 선택할 수 있습니다");
                break;
            case SHIFT:
                if (targetEmpty) throw new IllegalStateException("Shift는 다른 기물이 있는 칸만 선택할 수 있습니다");
                break;
            case TAKE_MOVE:
                if (hasFriendly) throw new IllegalStateException("아군 기물이 있는 칸으로 이동할 수 없습니다");
                break;
            case JUMP:
                if (!targetEmpty) throw new IllegalStateException("Jump는 빈 칸으로만 이동할 수 있습니다");
                break;
        }
    }

    // ── 이동 실행 ─────────────────────────────────────

    /** LegalMove 기반 이동 실행 */
    public String movePieceByLegalMove(Move.LegalMove mv) {
        Move.Square from = mv.from;
        Move.Square to = mv.to;

        String pieceId = board.get(from);
        if (pieceId == null) throw new IllegalStateException("출발 위치에 기물이 없습니다");

        Piece.PieceData piece = pieces.get(pieceId);
        if (piece == null) throw new IllegalStateException("기물을 찾을 수 없습니다");

        int player = piece.isNeutral() ? turn : piece.owner;
        canMovePiece(player, pieceId, from, to, mv.moveType);

        String capturedId = null;

        switch (mv.moveType) {
            case MOVE: {
                board.remove(from);
                board.put(to, pieceId);
                piece.pos = to;
                break;
            }
            case TAKE:
            case TAKE_MOVE: {
                String victimId = board.get(to);
                if (victimId != null) {
                    capturedId = victimId;
                    capture(pieceId, victimId);
                }
                board.remove(from);
                board.put(to, pieceId);
                piece.pos = to;
                break;
            }
            case CATCH: {
                String victimId = board.get(to);
                if (victimId == null) throw new IllegalStateException("Catch 대상이 없습니다");
                capturedId = victimId;
                capture(pieceId, victimId);
                break;
            }
            case SHIFT: {
                String targetPid = board.get(to);
                if (targetPid == null) throw new IllegalStateException("Shift 대상이 없습니다");
                board.remove(from);
                board.remove(to);
                board.put(from, targetPid);
                board.put(to, pieceId);
                piece.pos = to;
                Piece.PieceData tp = pieces.get(targetPid);
                if (tp != null) tp.pos = from;
                break;
            }
            case JUMP: {
                board.remove(from);
                board.put(to, pieceId);
                piece.pos = to;

                if (mv.catchTo != null && mv.catchTo.isValid()) {
                    String victimId = board.get(mv.catchTo);
                    if (victimId != null) {
                        capturedId = victimId;
                        capture(pieceId, victimId);
                    }
                }
                break;
            }
        }

        // 이동 히스토리 기록
        moveHistory.add(new MoveRecord(pieceId, piece.kind, from, to, turnNumber));

        activePiece = pieceId;
        applyActionTags(pieceId, mv.tags);

        return capturedId;
    }

    /** 캡처 처리 */
    public void capture(String attackerId, String victimId) {
        Piece.PieceData victim = pieces.get(victimId);
        if (victim == null) throw new IllegalStateException("피해자를 찾을 수 없습니다");

        if (victim.pos != null) board.remove(victim.pos);
        pieces.remove(victimId);
    }

    /** 액션 태그 적용 */
    private void applyActionTags(String pieceId, List<AST.ActionTag> tags) {
        if (tags == null) return;
        for (AST.ActionTag tag : tags) {
            switch (tag.tagType) {
                case TRANSITION: {
                    if (tag.pieceName != null) {
                        Piece.PieceData p = pieces.get(pieceId);
                        if (p != null) {
                            Piece.PieceKind newKind = Piece.PieceKind.fromString(tag.pieceName);
                            p.kind = newKind;
                        }
                    }
                    break;
                }
                case SET_STATE:
                    globalState.put(tag.key, tag.value);
                    break;
                case SUMMON: {
                    if (tag.pieceName != null) {
                        Piece.PieceData acting = pieces.get(pieceId);
                        if (acting != null) {
                            int summonX = acting.pos.x + tag.value;
                            int summonY = acting.pos.y + (tag.key != null ? Integer.parseInt(tag.key) : 0);
                            Move.Square summonPos = new Move.Square(summonX, summonY);
                            if (summonPos.isValid() && !board.contains(summonPos)) {
                                Piece.PieceKind summonKind = Piece.PieceKind.fromString(tag.pieceName);
                                Piece.PieceData summoned = createPiece(summonKind, turn);
                                summoned.pos = summonPos;
                                pieces.put(summoned.id, summoned);
                                board.put(summonPos, summoned.id);
                            }
                        }
                    }
                    break;
                }
                case AUTO_MOVE: {
                    Piece.PieceData p = pieces.get(pieceId);
                    if (p != null && tag.pieceName != null) {
                        Piece.AutoMoveMode mode = "shift".equals(tag.pieceName)
                                ? Piece.AutoMoveMode.SHIFT
                                : Piece.AutoMoveMode.TAKE_MOVE;
                        p.autoMove = new Piece.AutoMove(tag.value,
                                tag.key != null ? Integer.parseInt(tag.key) : 0, mode);
                    }
                    break;
                }
            }
        }
    }

    // ── 계승 ──────────────────────────────────────────

    public void crownPiece(int player, String pieceId) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken || activePiece != null) throw new IllegalStateException("이번 턴에 이미 행동했습니다");

        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        if (p.owner != player) throw new IllegalStateException("자신의 기물이 아닙니다");
        if (p.pos == null) throw new IllegalStateException("보드 위의 기물만 계승할 수 있습니다");
        if (p.isNeutral()) throw new IllegalStateException("중립 기물은 계승할 수 없습니다");

        p.isRoyal = true;
        actionTaken = true;
    }

    // ── 프로모션 ──────────────────────────────────────

    public void promote(String pieceId, Piece.PieceKind toKind) {
        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        if (!p.kind.canPromote()) throw new IllegalStateException("프로모션할 수 없는 기물입니다");
        if (!p.kind.promotionTargets().contains(toKind))
            throw new IllegalStateException("유효하지 않은 프로모션 대상입니다");
        if (p.pos == null) throw new IllegalStateException("보드 위에 없는 기물입니다");
        if (!p.kind.isPromotionSquare(p.pos, p.isWhite()))
            throw new IllegalStateException("프로모션 칸에 있지 않습니다");

        p.kind = toKind;
    }

    // ── 턴 ────────────────────────────────────────────

    public void endTurn() {
        // 자동 이동 처리 (현재 턴 플레이어의 기물)
        processAutoMoves();

        // 다음 플레이어
        turn = 1 - turn;
        turnNumber++;

        activePiece = null;
        actionTaken = false;
    }

    /** 자동 이동 처리 — autoMove가 설정된 기물들 처리 */
    private void processAutoMoves() {
        // 기물 ID 순서로 처리 (결정론적 순서)
        List<Piece.PieceData> autoMovePieces = new ArrayList<>();
        for (Piece.PieceData p : pieces.values()) {
            if (p.autoMove != null && p.pos != null) {
                autoMovePieces.add(p);
            }
        }
        autoMovePieces.sort(Comparator.comparing(p -> p.id));

        for (Piece.PieceData p : autoMovePieces) {
            if (p.autoMove == null || p.pos == null) continue; // 이전 auto-move로 상태 변경됨
            if (!pieces.containsKey(p.id)) continue; // 캡처됨

            Piece.AutoMove am = p.autoMove;
            Move.Square target = new Move.Square(p.pos.x + am.dx, p.pos.y + am.dy);

            if (!target.isValid()) {
                p.autoMove = null; // 보드 밖 → 멈춤
                continue;
            }

            String targetPid = board.get(target);

            if (am.mode == Piece.AutoMoveMode.TAKE_MOVE) {
                if (targetPid == null) {
                    // 빈 칸 → 이동
                    board.remove(p.pos);
                    board.put(target, p.id);
                    moveHistory.add(new MoveRecord(p.id, p.kind, p.pos, target, turnNumber));
                    p.pos = target;
                } else {
                    Piece.PieceData tp = pieces.get(targetPid);
                    if (tp != null && tp.owner != p.owner && !tp.isNeutral()) {
                        // 적 → 잡기 + 이동
                        capture(p.id, targetPid);
                        board.remove(p.pos);
                        board.put(target, p.id);
                        moveHistory.add(new MoveRecord(p.id, p.kind, p.pos, target, turnNumber));
                        p.pos = target;
                    } else {
                        p.autoMove = null; // 아군/중립 → 멈춤
                    }
                }
            } else { // SHIFT
                if (targetPid == null) {
                    // 빈 칸 → 이동
                    board.remove(p.pos);
                    board.put(target, p.id);
                    moveHistory.add(new MoveRecord(p.id, p.kind, p.pos, target, turnNumber));
                    p.pos = target;
                } else {
                    Piece.PieceData tp = pieces.get(targetPid);
                    if (tp != null) {
                        // 기물 있음 → 위치 교환
                        Move.Square oldPos = p.pos;
                        board.remove(p.pos);
                        board.remove(target);
                        board.put(oldPos, targetPid);
                        board.put(target, p.id);
                        moveHistory.add(new MoveRecord(p.id, p.kind, oldPos, target, turnNumber));
                        tp.pos = oldPos;
                        p.pos = target;
                    } else {
                        p.autoMove = null; // 멈춤
                    }
                }
            }
        }
    }

    // ── 승리 조건 ─────────────────────────────────────

    public Move.GameResult checkVictory() {
        boolean whiteHasRoyal = false, blackHasRoyal = false;
        for (Piece.PieceData p : pieces.values()) {
            if (p.isRoyal) {
                if (p.owner == 0) whiteHasRoyal = true;
                else if (p.owner == 1) blackHasRoyal = true;
            }
        }
        if (!whiteHasRoyal) return Move.GameResult.BLACK_WINS;
        if (!blackHasRoyal) return Move.GameResult.WHITE_WINS;
        return Move.GameResult.ONGOING;
    }

    // ── 행마법 계산 ───────────────────────────────────

    /** Chessembly 보드 상태 생성 */
    public BuiltinOps.BoardState toChessemblyBoard(String pieceId) {
        Piece.PieceData piece = pieces.get(pieceId);
        if (piece == null || piece.pos == null) return null;

        // 중립 기물 이동 시: 현재 턴 플레이어의 관점으로 처리
        boolean isWhitePerspective = piece.isNeutral() ? (turn == 0) : piece.isWhite();

        BuiltinOps.BoardState bs = new BuiltinOps.BoardState(
                RuleSet.BOARD_WIDTH, RuleSet.BOARD_HEIGHT,
                piece.pos.x, piece.pos.y,
                piece.kind.scriptName(),
                isWhitePerspective
        );

        // 보드 위 모든 기물 등록
        for (Map.Entry<Move.Square, String> entry : board.entries()) {
            Move.Square sq = entry.getKey();
            Piece.PieceData p = pieces.get(entry.getValue());
            if (p != null) {
                boolean pIsWhite = p.isNeutral() ? isWhitePerspective : p.isWhite();
                bs.putPiece(sq.x, sq.y, p.kind.scriptName(), pIsWhite,
                            p.isNeutral() ? RuleSet.NEUTRAL : p.owner);
            }
        }

        // 전역 상태 복사
        bs.state.putAll(globalState);

        // 이동 히스토리 복사
        bs.moveHistory.addAll(moveHistory);

        return bs;
    }

    /** 특정 기물의 합법 수 목록 계산 */
    public List<Move.LegalMove> getLegalMoves(String pieceId) {
        return MoveGenerator.generateLegalMoves(this, pieceId);
    }

    /** 특정 위치의 기물 합법 수 */
    public List<Move.LegalMove> getLegalMovesAt(Move.Square square) {
        String pid = board.get(square);
        if (pid == null) return Collections.emptyList();
        return getLegalMoves(pid);
    }

    /** 이동 유효성 확인 */
    public boolean isValidMove(String pieceId, Move.Square from, Move.Square to) {
        return getLegalMoves(pieceId).stream()
                .anyMatch(m -> m.from.equals(from) && m.to.equals(to));
    }

    public boolean isValidMoveAt(Move.Square from, Move.Square to) {
        String pid = board.get(from);
        if (pid == null) return false;
        return isValidMove(pid, from, to);
    }

    // ── 액션 적용 ─────────────────────────────────────

    public void applyAction(Move.Action action) {
        switch (action.type) {
            case PLACE: {
                Piece.PieceData p = pieces.get(action.pieceId);
                if (p != null) {
                    placePiece(turn, p.kind, action.to);
                }
                break;
            }
            case MOVE: {
                List<Move.LegalMove> moves = getLegalMovesAt(action.from);
                for (Move.LegalMove lm : moves) {
                    if (lm.to.equals(action.to)) {
                        movePieceByLegalMove(lm);
                        break;
                    }
                }
                break;
            }
            case CROWN: {
                Piece.PieceData p = pieces.get(action.pieceId);
                if (p != null) crownPiece(turn, action.pieceId);
                break;
            }
        }
    }

    // ── 접근자 ────────────────────────────────────────

    public Board getBoard()             { return board; }
    public int getTurn()                { return turn; }
    public int getTurnNumber()          { return turnNumber; }
    public String getActivePiece()      { return activePiece; }
    public boolean isActionTaken()      { return actionTaken; }
    public boolean isDebugMode()        { return debugMode; }
    public void setDebugMode(boolean d) { debugMode = d; }

    public Piece.PieceData getPieceAt(Move.Square sq) {
        String id = board.get(sq);
        return id != null ? pieces.get(id) : null;
    }

    public Piece.PieceData getPiece(String id) {
        return pieces.get(id);
    }

    public Map<String, Piece.PieceData> getAllPieces() {
        return Collections.unmodifiableMap(pieces);
    }

    public List<Piece.PieceSpec> getPocket(int player) {
        return pockets.getOrDefault(player, Collections.emptyList());
    }

    public Map<String, Integer> getGlobalState() {
        return Collections.unmodifiableMap(globalState);
    }

    public List<MoveRecord> getMoveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }

    /** 보드 위 모든 기물 정보 반환 */
    public List<Piece.PieceData> getBoardPieces() {
        List<Piece.PieceData> result = new ArrayList<>();
        for (Piece.PieceData p : pieces.values()) {
            if (p.pos != null) result.add(p);
        }
        return result;
    }
}
