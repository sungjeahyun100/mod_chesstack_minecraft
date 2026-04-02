package com.chesstack.engine.core;

import java.util.*;

/**
 * Piece — 기물 종류(PieceKind), 기물 스펙(PieceSpec), 기물(PieceData)을 정의한다.
 * Rust의 PieceKind enum + Piece struct 1:1 포팅.
 */
public final class Piece {

    private Piece() {}

    // ── PieceKind ─────────────────────────────────────

    public enum PieceKind {
        PAWN("pawn", 1),
        KING("king", 4),
        QUEEN("queen", 9),
        ROOK("rook", 5),
        KNIGHT("knight", 3),
        BISHOP("bishop", 3),
        AMAZON("amazon", 13),
        GRASSHOPPER("grasshopper", 4),
        KNIGHTRIDER("knightrider", 7),
        ARCHBISHOP("archbishop", 6),
        DABBABA("dabbaba", 2),
        ALFIL("alfil", 2),
        FERZ("ferz", 1),
        CENTAUR("centaur", 5),
        CAMEL("camel", 3),
        TEMPEST_ROOK("tempestrook", 7),
        CANNON("cannon", 5),
        BOUNCING_BISHOP("bouncingbishop", 7),
        EXPERIMENT("experiment", 1);

        private final String scriptName;
        private final int score;

        PieceKind(String scriptName, int score) {
            this.scriptName = scriptName;
            this.score = score;
        }

        public int score() { return score; }
        public String scriptName() { return scriptName; }

        public boolean canPromote() {
            return this == PAWN;
        }

        public List<PieceKind> promotionTargets() {
            if (this == PAWN) {
                return Arrays.asList(QUEEN, ROOK, BISHOP, KNIGHT);
            }
            return Collections.emptyList();
        }

        public boolean isPromotionSquare(Move.Square sq, boolean isWhite) {
            if (!canPromote()) return false;
            return isWhite ? sq.y == 7 : sq.y == 0;
        }



        /** Chessembly 행마법 스크립트 반환 */
        public String chessemblyScript(boolean isWhite) {
            switch (this) {
                case PAWN:
                    return isWhite
                        ? "move(0, 1); take(1, 1); take(-1, 1);"
                        : "move(0, -1); take(1, -1); take(-1, -1);";

                case KING:
                    return "take-move(1, 0); take-move(-1, 0); take-move(0, 1); take-move(0, -1);"
                         + " take-move(1, 1); take-move(1, -1); take-move(-1, 1); take-move(-1, -1);";

                case QUEEN:
                    return "take-move(1, 0) repeat(1); take-move(-1, 0) repeat(1);"
                         + " take-move(0, 1) repeat(1); take-move(0, -1) repeat(1);"
                         + " take-move(1, 1) repeat(1); take-move(1, -1) repeat(1);"
                         + " take-move(-1, 1) repeat(1); take-move(-1, -1) repeat(1);";

                case ROOK:
                    return "take-move(1, 0) repeat(1); take-move(-1, 0) repeat(1);"
                         + " take-move(0, 1) repeat(1); take-move(0, -1) repeat(1);";

                case KNIGHT:
                    return "take-move(1, 2); take-move(2, 1); take-move(2, -1); take-move(1, -2);"
                         + " take-move(-1, 2); take-move(-2, 1); take-move(-2, -1); take-move(-1, -2);";

                case BISHOP:
                    return "take-move(1, 1) repeat(1); take-move(1, -1) repeat(1);"
                         + " take-move(-1, 1) repeat(1); take-move(-1, -1) repeat(1);";

                case AMAZON:
                    return "take-move(1, 0) repeat(1); take-move(-1, 0) repeat(1);"
                         + " take-move(0, 1) repeat(1); take-move(0, -1) repeat(1);"
                         + " take-move(1, 1) repeat(1); take-move(1, -1) repeat(1);"
                         + " take-move(-1, 1) repeat(1); take-move(-1, -1) repeat(1);"
                         + " take-move(1, 2); take-move(2, 1); take-move(2, -1); take-move(1, -2);"
                         + " take-move(-1, 2); take-move(-2, 1); take-move(-2, -1); take-move(-1, -2);";

                case GRASSHOPPER:
                    return "do peek(1, 0) while take-move(1, 0);"
                         + " do peek(-1, 0) while take-move(-1, 0);"
                         + " do peek(0, 1) while take-move(0, 1);"
                         + " do peek(0, -1) while take-move(0, -1);"
                         + " do peek(1, 1) while take-move(1, 1);"
                         + " do peek(1, -1) while take-move(1, -1);"
                         + " do peek(-1, 1) while take-move(-1, 1);"
                         + " do peek(-1, -1) while take-move(-1, -1);";

                case KNIGHTRIDER:
                    return "take-move(1, 2) repeat(1); take-move(2, 1) repeat(1);"
                         + " take-move(2, -1) repeat(1); take-move(1, -2) repeat(1);"
                         + " take-move(-1, 2) repeat(1); take-move(-2, 1) repeat(1);"
                         + " take-move(-2, -1) repeat(1); take-move(-1, -2) repeat(1);";

                case ARCHBISHOP:
                    return "take-move(1, 1) repeat(1); take-move(1, -1) repeat(1);"
                         + " take-move(-1, 1) repeat(1); take-move(-1, -1) repeat(1);"
                         + " take-move(1, 2); take-move(2, 1); take-move(2, -1); take-move(1, -2);"
                         + " take-move(-1, 2); take-move(-2, 1); take-move(-2, -1); take-move(-1, -2);";

                case DABBABA:
                    return "take-move(2, 0); take-move(-2, 0); take-move(0, 2); take-move(0, -2);";

                case ALFIL:
                    return "take-move(2, 2); take-move(2, -2); take-move(-2, 2); take-move(-2, -2);";

                case FERZ:
                    return "take-move(1, 1); take-move(1, -1); take-move(-1, 1); take-move(-1, -1);";

                case CENTAUR:
                    return "take-move(1, 0); take-move(-1, 0); take-move(0, 1); take-move(0, -1);"
                         + " take-move(1, 1); take-move(1, -1); take-move(-1, 1); take-move(-1, -1);"
                         + " take-move(1, 2); take-move(2, 1); take-move(2, -1); take-move(1, -2);"
                         + " take-move(-1, 2); take-move(-2, 1); take-move(-2, -1); take-move(-1, -2);";

                case CAMEL:
                    return "take-move(3, 1); take-move(3, -1); take-move(-3, 1); take-move(-3, -1);"
                         + " take-move(1, 3); take-move(1, -3); take-move(-1, 3); take-move(-1, -3);";

                case TEMPEST_ROOK:
                    return "take-move(1, 1) { take-move(1, 0) repeat(1) } { take-move(0, 1) repeat(1) };"
                         + " take-move(-1, 1) { take-move(-1, 0) repeat(1) } { take-move(0, 1) repeat(1) };"
                         + " take-move(1, -1) { take-move(1, 0) repeat(1) } { take-move(0, -1) repeat(1) };"
                         + " take-move(-1, -1) { take-move(-1, 0) repeat(1) } { take-move(0, -1) repeat(1) };";

                case CANNON:
                    return "do take(1, 0) enemy(0, 0) not while jump(1, 0) repeat(1);"
                         + " do take(-1, 0) enemy(0, 0) not while jump(-1, 0) repeat(1);"
                         + " do take(0, 1) enemy(0, 0) not while jump(0, 1) repeat(1);"
                         + " do take(0, -1) enemy(0, 0) not while jump(0, -1) repeat(1);"
                         + " do peek(1, 0) while friendly(0, 0) move(1, 0) repeat(1);"
                         + " do peek(-1, 0) while friendly(0, 0) move(-1, 0) repeat(1);"
                         + " do peek(0, 1) while friendly(0, 0) move(0, 1) repeat(1);"
                         + " do peek(0, -1) while friendly(0, 0) move(0, -1) repeat(1);";

                case BOUNCING_BISHOP:
                    return "do take-move(1, 1) while peek(0, 0) edge-right(1, 1) jne(0) take-move(-1, 1) repeat(1) label(0) edge-top(1, 1) jne(1) take-move(1, -1) repeat(1) label(1);"
                         + " do take-move(-1, 1) while peek(0, 0) edge-left(-1, 1) jne(0) take-move(1, 1) repeat(1) label(0) edge-top(-1, 1) jne(1) take-move(-1, -1) repeat(1) label(1);"
                         + " do take-move(1, -1) while peek(0, 0) edge-right(1, -1) jne(0) take-move(-1, -1) repeat(1) label(0) edge-bottom(1, -1) jne(1) take-move(1, 1) repeat(1) label(1);"
                         + " do take-move(-1, -1) while peek(0, 0) edge-left(-1, -1) jne(0) take-move(1, -1) repeat(1) label(0) edge-bottom(-1, -1) jne(1) take-move(-1, 1) repeat(1) label(1);";

                case EXPERIMENT:
                    return "move(0, 1); move(1, 0); move(-1, 0); move(0, -1);"
                         + " move(1, 1); move(-1, 1); move(-1, -1); move(1, -1);"
                         + " catch(2, 2);";

                default:
                    return "";
            }
        }

        /** 문자열에서 PieceKind 파싱 */
        public static PieceKind fromString(String s) {
            if (s == null) throw new IllegalArgumentException("기물 이름이 null입니다");
            switch (s.toLowerCase()) {
                case "pawn": return PAWN;
                case "king": return KING;
                case "queen": return QUEEN;
                case "rook": return ROOK;
                case "knight": return KNIGHT;
                case "bishop": return BISHOP;
                case "amazon": return AMAZON;
                case "grasshopper": return GRASSHOPPER;
                case "knightrider": return KNIGHTRIDER;
                case "archbishop": return ARCHBISHOP;
                case "dabbaba": return DABBABA;
                case "alfil": return ALFIL;
                case "ferz": return FERZ;
                case "centaur": return CENTAUR;
                case "camel": return CAMEL;
                case "tempestrook": return TEMPEST_ROOK;
                case "cannon": return CANNON;
                case "bouncingbishop": return BOUNCING_BISHOP;
                case "experiment": return EXPERIMENT;
                default: throw new IllegalArgumentException("알 수 없는 기물: " + s);
            }
        }
    }

    // ── PieceSpec (포켓용 기물 스펙) ──────────────────

    public static final class PieceSpec {
        public final PieceKind kind;

        public PieceSpec(PieceKind kind) {
            this.kind = kind;
        }

        public int score() { return kind.score(); }

        @Override
        public String toString() { return kind.scriptName(); }
    }

    // ── PieceData (보드 위 기물) ──────────────────────

    public static final class PieceData {
        public final String id;
        public PieceKind kind;
        public final int owner; // 0=백, 1=흑, 2=중립
        public Move.Square pos; // null이면 포켓
        public boolean isRoyal;
        public AutoMove autoMove; // nullable — 자동 이동 설정

        public PieceData(String id, PieceKind kind, int owner) {
            this.id = id;
            this.kind = kind;
            this.owner = owner;
            this.pos = null;
            this.isRoyal = false;
            this.autoMove = null;
        }

        public int score() { return kind.score(); }

        public boolean canMove() {
            return pos != null;
        }

        public boolean isWhite() {
            return owner == 0;
        }

        public boolean isNeutral() {
            return owner == 2;
        }

        /** 깊은 복사 */
        public PieceData copy() {
            PieceData c = new PieceData(id, kind, owner);
            c.pos = pos;
            c.isRoyal = isRoyal;
            c.autoMove = autoMove;
            return c;
        }

        @Override
        public String toString() {
            return kind.scriptName() + "(" + id + ") @" + pos
                    + (isRoyal ? " ROYAL" : "")
                    + (isNeutral() ? " NEUTRAL" : "");
        }
    }

    // ── AutoMove (자동 이동 설정) ─────────────────────

    public enum AutoMoveMode {
        TAKE_MOVE, // 적 기물 잡기 + 빈칸 이동, 그 외 멈춤
        SHIFT      // 기물과 위치 교환 + 빈칸 이동, 그 외 멈춤
    }

    public static final class AutoMove {
        public final int dx;
        public final int dy;
        public final AutoMoveMode mode;

        public AutoMove(int dx, int dy, AutoMoveMode mode) {
            this.dx = dx;
            this.dy = dy;
            this.mode = mode;
        }

        @Override
        public String toString() {
            return "AutoMove{" + dx + "," + dy + " " + mode + "}";
        }
    }
}
