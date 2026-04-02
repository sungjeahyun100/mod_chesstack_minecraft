package com.chesstack.engine.core;

/**
 * RuleSet — 게임 규칙 상수 및 설정.
 */
public final class RuleSet {

    private RuleSet() {}

    /** 보드 크기 */
    public static final int BOARD_WIDTH = 8;
    public static final int BOARD_HEIGHT = 8;

    /** 포켓 점수 제한 */
    public static final int MAX_POCKET_SCORE = 39;

    /** 백 플레이어 ID */
    public static final int WHITE = 0;
    /** 흑 플레이어 ID */
    public static final int BLACK = 1;
    /** 중립 ID */
    public static final int NEUTRAL = 2;
}
