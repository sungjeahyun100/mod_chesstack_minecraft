# ChessStack Chessembly DSL API 사용법

Chessembly는 체스 기물의 행마법(이동 패턴)을 정의하는 도메인 특화 언어(DSL)입니다. 이 문서는 Chessembly를 직접 사용하는 방법을 설명합니다.

## 목차
- [개요](#개요)
- [기본 문법](#기본-문법)
- [Interpreter](#interpreter) - 스크립트 실행
- [Parser](#parser) - 스크립트 파싱
- [Lexer](#lexer) - 토큰화
- [AST](#ast) - 추상 구문 트리
- [VM](#vm) - 가상 머신
- [BuiltinOps](#builtinops) - 내장 연산
- [고급 기법](#고급-기법)
- [실전 예제](#실전-예제)

---

## 개요

Chessembly는 다음과 같은 특징을 갖습니다:

- **선언적 문법**: 이동 방향과 거리를 간단하게 표현
- **스택 기반**: 앵커(Anchor) 개념으로 연속 이동 지원
- **조건부 실행**: 보드 상태에 따른 동적 행마법
- **제어 흐름**: 반복, 점프, 조건문
- **소환·자동이동**: 이동 시 추가 기물 소환, 자동 이동 설정
- **히스토리 조건**: 이동 히스토리를 기반으로 한 조건부 실행

### Chessembly 파이프라인

```
스크립트 문자열
    ↓
Lexer: 토큰화
    ↓
Parser: AST 생성
    ↓
Interpreter: 실행
    ↓
Activation 목록 (합법 수 + 액션 태그)
```

---

## 기본 문법

### 이동 명령어

```chessembly
move(dx, dy)       # 빈 칸으로만 이동
take(dx, dy)       # 적 기물이 있는 칸으로만 이동
take-move(dx, dy)  # 빈 칸 또는 적 기물 칸으로 이동
shift(dx, dy)      # 다른 기물이 있는 칸으로 이동 (위치 교환)
catch(dx, dy)      # 캡처만 (이동하지 않음, 원거리 잡기)
jump(dx, dy)       # 뛰어넘기 (마지막 take 위치에서 캡처)
```

### 조건 명령어

```chessembly
peek(dx, dy)         # 기물이 있으면 true
observe(dx, dy)      # (peek과 동일)
enemy(dx, dy)        # 적 기물이 있으면 true (중립은 적이 아님)
friendly(dx, dy)     # 아군 기물이 있으면 true (중립은 아군)
piece-on(name, dx, dy) # 특정 종류의 기물이 있으면 true
danger(dx, dy)       # 위협받는 칸이면 true
check                # 체크 상태이면 true
bound(dx, dy)        # 보드 범위 안이면 true
edge(dx, dy)         # 보드 가장자리이면 true
edge-top(dx, dy)     # 상단 가장자리면 true
edge-bottom(dx, dy)  # 하단 가장자리면 true
edge-left(dx, dy)    # 좌측 가장자리면 true
edge-right(dx, dy)   # 우측 가장자리면 true
corner(dx, dy)       # 코너이면 true
corner-top-left(dx, dy)     # 좌상단 코너이면 true
corner-top-right(dx, dy)    # 우상단 코너이면 true
corner-bottom-left(dx, dy)  # 좌하단 코너이면 true
corner-bottom-right(dx, dy) # 우하단 코너이면 true
```

### 상태·소환·자동이동 명령어

```chessembly
# 상태 관련
piece(name)              # 현재 기물이 name인지 확인
if-state(key, value)     # 전역 상태 key == value인지 확인
set-state(key, value)    # 전역 상태 key를 value으로 설정 (액션 태그)
set-state-reset(key)     # 전역 상태 key를 0으로 리셋 (액션 태그)
transition(name)         # 기물 종류를 name으로 변환 (액션 태그)

# 소환 (이동 실행 시 추가 기물을 소환하는 액션 태그)
summon(kindName, dx, dy) # 현재 위치 기준 (dx, dy) 오프셋에 kindName 기물 소환

# 자동 이동 (이동 실행 시 기물에 자동 이동을 설정하는 액션 태그)
auto(dx, dy)             # take-move 모드의 자동 이동 설정
auto-shift(dx, dy)       # shift 모드의 자동 이동 설정
```

### 히스토리 조건 명령어

```chessembly
# 이동 히스토리를 기반으로 한 조건
history-moved(kindName)                # kindName 종류의 기물이 이동한 적이 있으면 true
history-exists(fromX, fromY, toX, toY) # 특정 좌표 간 이동이 존재하면 true
```

### 제어 흐름

```chessembly
repeat(n)          # 이전 명령어를 n번 반복 (0=무한, 1=무한 반복)
do ... while       # do-while 루프
{ ... }            # 스코프 (앵커 격리)
label(n)           # 라벨 정의
jmp(n)             # 라벨로 점프
jne(n)             # 마지막 값이 false가 아니면 점프
not                # 마지막 값 반전
;                  # 체인 구분자 (앵커 초기화)
```

### 예제: 기본 기물들

```chessembly
# 폰 (백)
move(0, 1); take(1, 1); take(-1, 1);

# 룩
take-move(1, 0) repeat(1);
take-move(-1, 0) repeat(1);
take-move(0, 1) repeat(1);
take-move(0, -1) repeat(1);

# 나이트
take-move(1, 2); take-move(2, 1);
take-move(2, -1); take-move(1, -2);
take-move(-1, 2); take-move(-2, 1);
take-move(-2, -1); take-move(-1, -2);

# 비숍
take-move(1, 1) repeat(1);
take-move(1, -1) repeat(1);
take-move(-1, 1) repeat(1);
take-move(-1, -1) repeat(1);
```

---

## Interpreter

`Interpreter`는 Chessembly 스크립트를 실행하여 `Activation` 목록을 생성합니다.

### 기본 사용법

```java
import com.chesstack.engine.dsl.chessembly.*;
import java.util.*;

// 1. Interpreter 생성
Interpreter interpreter = new Interpreter();

// 2. 디버그 모드 (선택)
interpreter.setDebug(true);

// 3. 스크립트 파싱
String script = "take-move(1, 0); take-move(0, 1);";
interpreter.parse(script);

// 4. 보드 상태 생성
BuiltinOps.BoardState board = new BuiltinOps.BoardState(
    8, 8,    // boardWidth, boardHeight
    3, 3,    // pieceX, pieceY (기물 위치)
    "knight", // 기물 이름
    true      // isWhite
);

// 5. 실행
List<AST.Activation> activations = interpreter.execute(board);

// 6. 결과 확인
for (AST.Activation act : activations) {
    System.out.printf("이동: (%d, %d) - %s, 태그: %s%n", 
        act.dx, act.dy, act.moveType, act.tags);
}
```

### setDebug()

디버그 로그를 활성화합니다.

```java
interpreter.setDebug(true);

// 실행 시 상세 로그 출력:
// [PC:0] TAKE_MOVE(1,0) | Anchor(0,0) | last=true
// [PC:1] SEMICOLON | Anchor(1,0) | last=true
// ...
```

### parse()

스크립트를 토큰 리스트로 파싱합니다.

```java
String script = "move(0, 1) repeat(2); take(1, 1);";

interpreter.parse(script);
// 내부적으로 Parser.parse()를 호출하여 Token 리스트 생성
```

### execute()

파싱된 스크립트를 실행합니다.

```java
/**
 * 행마법 계산 실행
 *
 * @param board Chessembly 보드 상태
 * @return Activation 목록 (합법 수)
 */
public List<AST.Activation> execute(BuiltinOps.BoardState board)
```

**동작 원리:**

1. **앵커 초기화**: (0, 0)에서 시작
2. **토큰 순회**: PC(Program Counter)로 순차 실행
3. **종료 규칙**: 일반 식이 false면 현재 체인 스킵
4. **제어식 면제**: `while`, `jmp`, `jne`, `not`, `label`은 종료하지 않음
5. **스코프 관리**: `{ }`로 앵커 저장/복원
6. **Activation 수집**: 이동 명령어 성공 시 추가
7. **액션 태그 수집**: `transition`, `summon`, `auto`, `auto-shift`, `set-state` 등

---

## Parser

`Parser`는 스크립트를 토큰 리스트로 변환합니다.

### parse()

```java
import com.chesstack.engine.dsl.chessembly.Parser;

String script = "move(1, 0); take(1, 1);";
List<AST.Token> tokens = Parser.parse(script);

for (AST.Token token : tokens) {
    System.out.println(token);
}
```

---

## Lexer

`Lexer`는 문자열을 원시 토큰으로 분해합니다 (내부 사용).

```java
import com.chesstack.engine.dsl.chessembly.Lexer;

Lexer lexer = new Lexer("move(1, 0); take(2, 1);");
// Parser가 내부적으로 사용
```

---

## AST

`AST`는 추상 구문 트리 관련 클래스를 포함합니다.

### TokenType (전체 목록)

```java
// 행마식
AST.TokenType.TAKE_MOVE   // take-move(dx, dy)
AST.TokenType.MOVE        // move(dx, dy)
AST.TokenType.TAKE        // take(dx, dy)
AST.TokenType.CATCH       // catch(dx, dy)
AST.TokenType.SHIFT       // shift(dx, dy)
AST.TokenType.JUMP        // jump(dx, dy)
AST.TokenType.ANCHOR      // anchor

// 조건식
AST.TokenType.OBSERVE     // observe(dx, dy)
AST.TokenType.PEEK        // peek(dx, dy)
AST.TokenType.ENEMY       // enemy(dx, dy)
AST.TokenType.FRIENDLY    // friendly(dx, dy)
AST.TokenType.PIECE_ON    // piece-on(name, dx, dy)
AST.TokenType.DANGER      // danger(dx, dy)
AST.TokenType.CHECK       // check
AST.TokenType.BOUND       // bound(dx, dy)
AST.TokenType.EDGE        // edge(dx, dy)
AST.TokenType.EDGE_TOP    // edge-top(dx, dy)
AST.TokenType.EDGE_BOTTOM // edge-bottom(dx, dy)
AST.TokenType.EDGE_LEFT   // edge-left(dx, dy)
AST.TokenType.EDGE_RIGHT  // edge-right(dx, dy)
AST.TokenType.CORNER      // corner(dx, dy)
AST.TokenType.CORNER_TOP_LEFT     // corner-top-left(dx, dy)
AST.TokenType.CORNER_TOP_RIGHT    // corner-top-right(dx, dy)
AST.TokenType.CORNER_BOTTOM_LEFT  // corner-bottom-left(dx, dy)
AST.TokenType.CORNER_BOTTOM_RIGHT // corner-bottom-right(dx, dy)

// 상태
AST.TokenType.PIECE        // piece(name)
AST.TokenType.IF_STATE     // if-state(key, value)
AST.TokenType.SET_STATE    // set-state(key, value)
AST.TokenType.SET_STATE_RESET // set-state-reset(key)
AST.TokenType.TRANSITION   // transition(name)

// 소환·자동이동
AST.TokenType.SUMMON       // summon(kindName, dx, dy)
AST.TokenType.AUTO_MOVE    // auto(dx, dy)
AST.TokenType.AUTO_SHIFT   // auto-shift(dx, dy)

// 히스토리 조건
AST.TokenType.HISTORY_MOVED  // history-moved(kindName)
AST.TokenType.HISTORY_EXISTS // history-exists(fromX, fromY, toX, toY)

// 제어
AST.TokenType.REPEAT       // repeat(n)
AST.TokenType.DO           // do
AST.TokenType.WHILE        // while
AST.TokenType.JMP          // jmp(n)
AST.TokenType.JNE          // jne(n)
AST.TokenType.LABEL        // label(n)
AST.TokenType.NOT          // not
AST.TokenType.END          // end

// 구조
AST.TokenType.OPEN_BRACE   // {
AST.TokenType.CLOSE_BRACE  // }
AST.TokenType.SEMICOLON    // ;
```

### Token

```java
public static final class Token {
    public final TokenType type;
    public final int dx;           // X 오프셋
    public final int dy;           // Y 오프셋
    public final String strArg;    // 기물 이름, 라벨, 상태 키 등
    public final int intArg;       // repeat 횟수, 상태 값 등
}
```

### MoveType

```java
public enum MoveType {
    MOVE,      // 빈 칸으로만
    TAKE,      // 적 칸으로만
    TAKE_MOVE, // 빈 칸 또는 적
    CATCH,     // 캡처만 (이동 안함)
    SHIFT,     // 위치 교환
    JUMP       // 뛰어넘기
}
```

### ActionTagType

```java
public enum ActionTagType {
    TRANSITION, // 기물 변환 (transition(name))
    SET_STATE,  // 전역 상태 설정 (set-state(key, value))
    SUMMON,     // 기물 소환 (summon(kindName, dx, dy))
    AUTO_MOVE   // 자동 이동 설정 (auto(dx, dy) / auto-shift(dx, dy))
}
```

### ActionTag

```java
public static final class ActionTag {
    public final ActionTagType tagType; // 태그 종류
    public final String key;            // 상태 키 또는 dy(문자열)
    public final int value;             // 상태 값 또는 dx
    public final String pieceName;      // 기물 이름 (nullable)
}

// 예: summon(knight, 1, 2)
// → ActionTag(SUMMON, key="2", value=1, pieceName="knight")
// GameState에서: summonX = pieceX + value, summonY = pieceY + parseInt(key)

// 예: auto(1, 0)
// → ActionTag(AUTO_MOVE, key="0", value=1, pieceName="take-move")

// 예: auto-shift(0, -1)
// → ActionTag(AUTO_MOVE, key="-1", value=0, pieceName="shift")
```

### Activation

```java
public static final class Activation {
    public final int dx;               // 이동 오프셋 X
    public final int dy;               // 이동 오프셋 Y
    public final MoveType moveType;    // 이동 타입
    public final List<ActionTag> tags; // 수집된 액션 태그
    public final int[] catchTo;        // jump용 캡처 위치 [dx, dy] (nullable)
}
```

---

## VM

`VM`은 Chessembly 가상 머신으로, Interpreter의 실행 로직을 담당합니다 (내부 사용).

---

## BuiltinOps

`BuiltinOps`는 Chessembly 내장 연산과 보드 상태를 정의합니다.

### PieceInfo

보드 위 기물 정보입니다.

```java
public static final class PieceInfo {
    public final String name;      // 기물 이름 (scriptName)
    public final boolean isWhite;  // 백 여부
    public final int owner;        // 소유자 (0=백, 1=흑, 2=중립)
    
    public boolean isNeutral() { return owner == 2; }
}
```

### BoardState

인터프리터가 행마법을 계산할 때 참조하는 외부 상태입니다.

```java
public static final class BoardState {
    public int boardWidth;          // 보드 너비 (8)
    public int boardHeight;         // 보드 높이 (8)
    public int pieceX;              // 현재 기물 X
    public int pieceY;              // 현재 기물 Y
    public String pieceName;        // 현재 기물 이름
    public boolean isWhite;         // 현재 기물이 백인지
    
    // (x,y) → PieceInfo
    public final Map<Long, PieceInfo> pieces;
    // 전역 상태
    public final Map<String, Integer> state;
    // 위협 칸
    public final Set<Long> dangerSquares;
    // 체크 상태
    public boolean inCheck;
    // 이동 히스토리
    public final List<GameState.MoveRecord> moveHistory;
}
```

### BoardState 주요 메서드

```java
// 좌표 → 키 변환
static long key(int x, int y)

// 기물 배치 (소유자 자동 결정: white → 0, !white → 1)
void putPiece(int x, int y, String name, boolean white)

// 기물 배치 (소유자 직접 지정, 중립 가능)
void putPiece(int x, int y, String name, boolean white, int owner)

// 범위 확인
boolean inBounds(int x, int y)

// 빈 칸 확인
boolean isEmpty(int x, int y)

// 적 판정 (중립 기물은 적이 아님)
boolean hasEnemy(int x, int y)

// 아군 판정 (중립 기물은 모두에게 아군)
boolean hasFriendly(int x, int y)

// 특정 기물 확인
boolean hasPiece(int x, int y, String pieceName)

// 전역 상태 조회
int getState(String key)

// 위협 확인
boolean isDanger(int x, int y)

// 히스토리: 특정 종류의 기물이 이동한 적이 있는지
boolean hasKindMoved(String kindName)

// 히스토리: 특정 좌표 간 이동이 존재하는지
boolean hasMoveExists(int fromX, int fromY, int toX, int toY)
```

### 보드 상태 생성 예제

```java
// 보드 상태 생성
BuiltinOps.BoardState board = new BuiltinOps.BoardState(
    8, 8,       // boardWidth, boardHeight
    3, 3,       // pieceX, pieceY (d4)
    "knight",   // pieceName
    true        // isWhite
);

// 기물 배치
board.putPiece(4, 5, "pawn", false);  // 흑 폰 at e6
board.putPiece(2, 4, "rook", true);   // 백 룩 at c5

// 중립 기물 배치
board.putPiece(5, 3, "bishop", true, 2); // 중립 비숍 at f4

// 인터프리터 실행
Interpreter interpreter = new Interpreter();
interpreter.parse("take-move(1, 2); take-move(2, 1);");
List<AST.Activation> acts = interpreter.execute(board);
```

---

## 고급 기법

### 1. 앵커(Anchor) 활용

앵커는 연속 이동의 기준점입니다.

```chessembly
# 나이트라이더: 나이트 점프를 연속으로
take-move(1, 2) repeat(1);
# (1,2) → (2,4) → (3,6) ...

# 앵커 초기화 (세미콜론)
take-move(1, 0);   # 앵커: (1,0)
take-move(1, 0);   # 앵커: (2,0)
;                  # 앵커 초기화 → (0,0)
take-move(0, 1);   # 앵커: (0,1)
```

### 2. 스코프 { } 활용

스코프는 앵커를 격리합니다.

```chessembly
# Tempest Rook: 대각선 1칸 후 직선 분기
take-move(1, 1) {
    take-move(1, 0) repeat(1)
} {
    take-move(0, 1) repeat(1)
};

# 동작:
# 1. (1,1)로 이동 성공 시
# 2. 첫 번째 { }: 앵커 저장 → (1,0) 방향 무한 반복 → 앵커 복원
# 3. 두 번째 { }: 앵커 저장 → (0,1) 방향 무한 반복 → 앵커 복원
```

### 3. do-while 루프

```chessembly
# Grasshopper: 기물을 만날 때까지 이동 후 뛰어넘기
do peek(1, 0) while take-move(1, 0);

# 동작:
# 1. peek(1,0) - 다음 칸에 기물이 있는지 확인
# 2. true면 take-move(1,0) 실행 → 반복
# 3. false면 종료 (기물 뛰어넘기 완료)
```

### 4. 조건부 점프

```chessembly
# Bouncing Bishop: 벽에 부딪히면 반사
do take-move(1, 1) while
    peek(0, 0)
    edge-right(1, 1) jne(0)    # 오른쪽 벽 아니면 label 0으로
    take-move(-1, 1) repeat(1) # 반사: 왼쪽으로
    label(0)
    edge-top(1, 1) jne(1)      # 위쪽 벽 아니면 label 1로
    take-move(1, -1) repeat(1) # 반사: 아래로
    label(1);
```

### 5. 조건 반전

```chessembly
# Cannon: 기물 뛰어넘어 캡처
do take(1, 0) enemy(0, 0) not while jump(1, 0) repeat(1);

# enemy(0, 0) not: 적이 아니면 (빈 칸 또는 아군)
```

### 6. 소환 활용

```chessembly
# 이동 후 뒤에 폰을 소환
move(0, 1) summon(pawn, 0, -1);
# (0,1)로 이동 성공 시 현재 위치 기준 (0,-1)에 폰 소환

# 전방에 나이트 소환
take-move(1, 0) summon(knight, 2, 0);
```

### 7. 자동 이동 설정

```chessembly
# 이동 후 오른쪽으로 자동 이동 (take-move 모드)
move(0, 1) auto(1, 0);
# 매 턴 종료 시 자동으로 (1,0) 방향 이동. 실패하면 해제.

# 이동 후 위로 자동 이동 (shift 모드)
take-move(1, 0) auto-shift(0, 1);
# 매 턴 종료 시 자동으로 (0,1) 방향 이동. 기물이 있으면 교환.
```

### 8. 히스토리 조건 활용

```chessembly
# 나이트가 이동한 적이 없을 때만 실행
history-moved(knight) not take-move(2, 0);
# 나이트가 아직 이동하지 않았으면 (2,0)으로 이동 가능

# 특정 좌표 간 이동이 있었을 때만 실행
history-exists(4, 0, 4, 2) take-move(0, 1);
# (4,0)→(4,2) 이동이 기록에 있으면 (0,1) 이동 가능
# 캐슬링 등 특수 규칙 구현에 활용
```

---

## 실전 예제

### 예제 1: 커스텀 기물 "텔레포터"

특정 위치로만 순간이동하는 기물.

```java
String teleporterScript = 
    "take-move(3, 0); take-move(-3, 0); " +
    "take-move(0, 3); take-move(0, -3); " +
    "take-move(3, 3); take-move(3, -3); " +
    "take-move(-3, 3); take-move(-3, -3);";

Interpreter interpreter = new Interpreter();
interpreter.parse(teleporterScript);

BuiltinOps.BoardState board = new BuiltinOps.BoardState(
    8, 8, 3, 3, "teleporter", true);

List<AST.Activation> acts = interpreter.execute(board);
System.out.println("텔레포터 이동 가능: " + acts.size() + "칸");
```

### 예제 2: 소환 기물 "네크로맨서"

이동 시 뒤에 폰을 소환하는 기물.

```java
String necromancerScript = 
    // 1칸 직선 이동 후 원래 자리에 폰 소환
    "move(1, 0) summon(pawn, -1, 0); " +
    "move(-1, 0) summon(pawn, 1, 0); " +
    "move(0, 1) summon(pawn, 0, -1); " +
    "move(0, -1) summon(pawn, 0, 1); " +
    // 적 잡기는 소환 없음
    "take(1, 0); take(-1, 0); take(0, 1); take(0, -1);";

Interpreter interpreter = new Interpreter();
interpreter.parse(necromancerScript);
```

### 예제 3: 자동이동 기물 "미사일"

이동 후 특정 방향으로 자동 이동하는 기물.

```java
String missileScript = 
    // 전방 이동 시 계속 전진
    "move(0, 1) auto(0, 1); " +
    // 대각 이동 시 대각 자동이동
    "move(1, 1) auto(1, 1); " +
    "move(-1, 1) auto(-1, 1);";

Interpreter interpreter = new Interpreter();
interpreter.parse(missileScript);
```

### 예제 4: 스크립트 동적 생성

```java
public class DynamicScriptGenerator {
    
    public static String generateScript(List<int[]> movePatterns, boolean canRepeat) {
        StringBuilder script = new StringBuilder();
        
        for (int[] pattern : movePatterns) {
            int dx = pattern[0];
            int dy = pattern[1];
            
            script.append("take-move(").append(dx).append(", ").append(dy).append(")");
            
            if (canRepeat) {
                script.append(" repeat(1)");
            }
            
            script.append("; ");
        }
        
        return script.toString();
    }
    
    public static void main(String[] args) {
        // 사용자 정의: 십자가 패턴
        List<int[]> patterns = Arrays.asList(
            new int[]{1, 0}, new int[]{-1, 0},
            new int[]{0, 1}, new int[]{0, -1}
        );
        
        String script = generateScript(patterns, true);
        System.out.println("생성된 스크립트: " + script);
        
        Interpreter interpreter = new Interpreter();
        interpreter.parse(script);
    }
}
```

### 예제 5: 시각화 도구

```java
public class ChessemblyVisualizer {
    
    public static void visualize(
        Move.Square startPos,
        List<AST.Activation> activations
    ) {
        char[][] board = new char[8][8];
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                board[y][x] = '.';
        
        board[startPos.y][startPos.x] = 'S';
        
        for (AST.Activation act : activations) {
            int tx = startPos.x + act.dx;
            int ty = startPos.y + act.dy;
            
            if (tx >= 0 && tx < 8 && ty >= 0 && ty < 8) {
                char marker;
                switch (act.moveType) {
                    case MOVE:      marker = 'M'; break;
                    case TAKE:      marker = 'T'; break;
                    case TAKE_MOVE: marker = 'X'; break;
                    case JUMP:      marker = 'J'; break;
                    case CATCH:     marker = 'C'; break;
                    case SHIFT:     marker = 'H'; break;
                    default:        marker = '?';
                }
                board[ty][tx] = marker;
            }
        }
        
        System.out.println("  a b c d e f g h");
        for (int y = 7; y >= 0; y--) {
            System.out.print((y + 1) + " ");
            for (int x = 0; x < 8; x++)
                System.out.print(board[y][x] + " ");
            System.out.println((y + 1));
        }
        System.out.println("  a b c d e f g h");
    }
}
```

---

## 디버깅 팁

### 1. 디버그 모드 활용

```java
Interpreter interpreter = new Interpreter();
interpreter.setDebug(true);
interpreter.parse(script);
List<AST.Activation> acts = interpreter.execute(board);
```

### 2. 토큰 확인

```java
List<AST.Token> tokens = Parser.parse(script);
System.out.println("토큰 목록:");
for (int i = 0; i < tokens.size(); i++) {
    System.out.printf("%d: %s%n", i, tokens.get(i));
}
```

---

## 성능 최적화

### 스크립트 사전 컴파일

```java
// 게임 시작 시 모든 기물 스크립트 파싱
Map<Piece.PieceKind, Interpreter> precompiled = new HashMap<>();

for (Piece.PieceKind kind : Piece.PieceKind.values()) {
    Interpreter interp = new Interpreter();
    interp.parse(kind.chessemblyScript(true));
    precompiled.put(kind, interp);
}
```

---

## 다음 단계

- [Minecraft Integration API](04-minecraft-integration-api.md) - 고수준 API
- [Core API](01-core-api.md) - 기본 구조 이해
- [Chessembly Tutorial](../../chessembly/TUTORIAL.md) - 심화 학습
