package com.chesstack.engine.movegen;

import com.chesstack.engine.core.Piece;

import java.util.*;

/**
 * StandardGenerators — 기본 기물들의 Chessembly 스크립트를 관리한다.
 */
public final class StandardGenerators {

    private StandardGenerators() {}

    /**
     * 기물 이름으로 PieceKind 내장 스크립트를 조회한다.
     */
    public static String getScript(String pieceName, boolean isWhite) {
        Piece.PieceKind kind = Piece.PieceKind.fromString(pieceName);
        return kind.chessemblyScript(isWhite);
    }

    /** 모든 내장 기물의 스크립트 맵 반환 */
    public static Map<String, String> getAllBuiltinScripts() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Piece.PieceKind kind : Piece.PieceKind.values()) {
            map.put(kind.scriptName(), kind.chessemblyScript(true));
        }
        return map;
    }
}
