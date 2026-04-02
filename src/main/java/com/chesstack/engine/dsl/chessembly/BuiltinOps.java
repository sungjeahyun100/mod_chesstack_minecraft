package com.chesstack.engine.dsl.chessembly;

import com.chesstack.engine.core.GameState;

import java.util.*;

/**
 * BuiltinOps — Chessembly 실행에 필요한 보드 상태(BoardState) 및 유틸리티.
 */
public final class BuiltinOps {

    private BuiltinOps() {}

    /** 보드 위 기물 정보 */
    public static final class PieceInfo {
        public final String name;
        public final boolean isWhite;
        public final int owner; // 0=백, 1=흑, 2=중립

        public PieceInfo(String name, boolean isWhite, int owner) {
            this.name = name;
            this.isWhite = isWhite;
            this.owner = owner;
        }

        public boolean isNeutral() {
            return owner == 2;
        }
    }

    /** 보드 상태 — 인터프리터가 행마법을 계산할 때 참조하는 외부 상태 */
    public static final class BoardState {
        public int boardWidth;
        public int boardHeight;
        public int pieceX;
        public int pieceY;
        public String pieceName;
        public boolean isWhite;
        /** (x,y) → PieceInfo */
        public final Map<Long, PieceInfo> pieces = new HashMap<>();
        /** 전역 상태 */
        public final Map<String, Integer> state = new HashMap<>();
        /** 위협 칸 */
        public final Set<Long> dangerSquares = new HashSet<>();
        /** 체크 상태 */
        public boolean inCheck;
        /** 이동 히스토리 */
        public final List<GameState.MoveRecord> moveHistory = new ArrayList<>();

        public BoardState(int boardWidth, int boardHeight, int pieceX, int pieceY,
                          String pieceName, boolean isWhite) {
            this.boardWidth = boardWidth;
            this.boardHeight = boardHeight;
            this.pieceX = pieceX;
            this.pieceY = pieceY;
            this.pieceName = pieceName;
            this.isWhite = isWhite;
        }

        public static long key(int x, int y) {
            return ((long) x << 32) | (y & 0xFFFFFFFFL);
        }

        public void putPiece(int x, int y, String name, boolean white) {
            pieces.put(key(x, y), new PieceInfo(name, white, white ? 0 : 1));
        }

        public void putPiece(int x, int y, String name, boolean white, int owner) {
            pieces.put(key(x, y), new PieceInfo(name, white, owner));
        }

        public boolean inBounds(int x, int y) {
            return x >= 0 && x < boardWidth && y >= 0 && y < boardHeight;
        }

        public boolean isEmpty(int x, int y) {
            return inBounds(x, y) && !pieces.containsKey(key(x, y));
        }

        /** 적 판정: 중립 기물은 아군이므로 적이 아님 */
        public boolean hasEnemy(int x, int y) {
            PieceInfo info = pieces.get(key(x, y));
            if (info == null) return false;
            if (info.isNeutral()) return false; // 중립은 적이 아님
            return info.isWhite != this.isWhite;
        }

        /** 아군 판정: 중립 기물은 모두에게 아군 */
        public boolean hasFriendly(int x, int y) {
            PieceInfo info = pieces.get(key(x, y));
            if (info == null) return false;
            if (info.isNeutral()) return true; // 중립은 아군
            return info.isWhite == this.isWhite;
        }

        public boolean hasPiece(int x, int y, String pieceName) {
            PieceInfo info = pieces.get(key(x, y));
            return info != null && info.name.equals(pieceName);
        }

        public int getState(String k) {
            return state.getOrDefault(k, 0);
        }

        public boolean isDanger(int x, int y) {
            return dangerSquares.contains(key(x, y));
        }

        /** 히스토리: 특정 종류의 기물이 이동한 적이 있는지 */
        public boolean hasKindMoved(String kindName) {
            for (GameState.MoveRecord r : moveHistory) {
                if (r.pieceKind.scriptName().equals(kindName)) return true;
            }
            return false;
        }

        /** 히스토리: 특정 좌표 간 이동이 존재하는지 */
        public boolean hasMoveExists(int fromX, int fromY, int toX, int toY) {
            for (GameState.MoveRecord r : moveHistory) {
                if (r.from.x == fromX && r.from.y == fromY
                        && r.to.x == toX && r.to.y == toY) return true;
            }
            return false;
        }
    }
}
