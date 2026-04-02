# ChessStack Java API - Quick Start Guide

ChessStack Java API를 빠르게 시작하는 가이드입니다.

## 목차
- [5분 시작하기](#5분-시작하기)
- [기본 개념](#기본-개념)
- [사용 시나리오별 예제](#사용-시나리오별-예제)
- [문제 해결](#문제-해결)
- [다음 단계](#다음-단계)

---

## 5분 시작하기

### 1. 의존성 추가

```gradle
// build.gradle
dependencies {
    implementation project(':engine')
}
```

### 2. 첫 게임 실행

```java
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;

public class QuickStart {
    public static void main(String[] args) {
        // 1. 엔진 생성
        ChessStackEngine engine = new ChessStackEngine();
        
        // 2. 게임 시작
        String gameId = engine.createGame();
        
        // 3. 백 플레이어: 나이트 착수
        engine.placePiece(gameId, "knight", 3, 3); // d4
        engine.endTurn(gameId);
        
        // 4. 흑 플레이어: 폰 착수
        engine.placePiece(gameId, "pawn", 4, 4); // e5
        engine.endTurn(gameId);
        
        // 5. 백 플레이어: 나이트 이동
        engine.makeMove(gameId, 3, 3, 4, 5); // d4 → e6 (나이트 점프)
        
        // 6. 결과 확인
        System.out.println("게임 상태: " + engine.getGameResult(gameId));
        
        // 7. 보드 출력
        engine.getBoardPieces(gameId).forEach(p -> 
            System.out.println(p.kind + " at " + p.pos)
        );
    }
}
```

---

## 기본 개념

### 좌표 시스템

ChessStack은 0-based 좌표를 사용합니다:

```
  a b c d e f g h
8 7 7 7 7 7 7 7 7
7 6 6 6 6 6 6 6 6
6 5 5 5 5 5 5 5 5
5 4 4 4 4 4 4 4 4
4 3 3 3 3 3 3 3 3
3 2 2 2 2 2 2 2 2
2 1 1 1 1 1 1 1 1
1 0 0 0 0 0 0 0 0
  0 1 2 3 4 5 6 7
```

**좌표 변환:**
```java
// 문자열 → 좌표
Move.Square e4 = Move.Square.fromNotation("e4"); // (4, 3)

// 좌표 → 문자열
String notation = e4.toNotation(); // "e4"
```

### 게임 흐름

```
1. createGame() - 게임 생성
2. 플레이어 턴 시작
3. placePiece() / makeMove() / crownPiece() - 행동 (택 1)
4. endTurn() - 턴 종료 (자동 이동 처리 포함)
5. 2-4 반복
6. checkVictory() - 승리 확인
```

### 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `ChessStackEngine` | 고수준 API (추천) |
| `GameState` | 게임 상태 관리 |
| `TestMode` | 행마법 격리 테스트 |
| `Board` | 체스판 |
| `Piece` | 기물 정의 (PieceKind, PieceData, AutoMove) |
| `Move` | 좌표 및 이동 |
| `MoveGenerator` | 합법 수 생성 |
| `Interpreter` | Chessembly 실행 |

### 소유자 (Owner)

| 값 | 의미 |
|----|------|
| 0 | 백 (White) |
| 1 | 흑 (Black) |
| 2 | 중립 (Neutral) — 양측 모두에게 아군 |

---

## 사용 시나리오별 예제

### 시나리오 1: 간단한 2인 게임

```java
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;
import java.util.*;

public class TwoPlayerGame {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        Scanner scanner = new Scanner(System.in);
        
        while (engine.getGameResult(gameId) == Move.GameResult.ONGOING) {
            int player = engine.getCurrentPlayer(gameId);
            System.out.println("\n" + (player == 0 ? "백" : "흑") + " 턴");
            
            // 포켓 표시
            List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
            System.out.println("포켓: " + pocket.size() + "개 기물");
            
            // 착수 또는 이동
            System.out.print("p=착수, m=이동, c=계승: ");
            String action = scanner.next();
            
            try {
                if (action.equals("p")) {
                    System.out.print("기물 (knight, pawn, etc.): ");
                    String kind = scanner.next();
                    System.out.print("좌표 (e4): ");
                    Move.Square sq = Move.Square.fromNotation(scanner.next());
                    engine.placePiece(gameId, kind, sq.x, sq.y);
                    System.out.println("착수 완료");
                    
                } else if (action.equals("m")) {
                    System.out.print("출발 (e2): ");
                    Move.Square from = Move.Square.fromNotation(scanner.next());
                    System.out.print("도착 (e4): ");
                    Move.Square to = Move.Square.fromNotation(scanner.next());
                    String captured = engine.makeMove(gameId, from.x, from.y, to.x, to.y);
                    System.out.println("이동 완료" + (captured != null ? " (캡처!)" : ""));
                    
                } else if (action.equals("c")) {
                    System.out.print("기물 ID: ");
                    String pid = scanner.next();
                    engine.getGame(gameId).crownPiece(player, pid);
                    System.out.println("계승 완료");
                }
                
                engine.endTurn(gameId);
            } catch (Exception e) {
                System.out.println("오류: " + e.getMessage());
            }
        }
        
        System.out.println("\n게임 종료: " + engine.getGameResult(gameId));
    }
}
```

### 시나리오 2: 합법 수 표시 기능

```java
public class MoveHintGame {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        
        // 나이트 배치
        engine.placePiece(gameId, "knight", 3, 3); // d4
        engine.endTurn(gameId);
        engine.endTurn(gameId); // 흑 턴 스킵 (시연용)
        
        // 합법 수 조회
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 3, 3);
        
        System.out.println("d4 나이트 가능한 이동:");
        for (int i = 0; i < moves.size(); i++) {
            Move.LegalMove move = moves.get(i);
            System.out.printf("%d. %s (타입: %s, 캡처: %b)%n",
                i + 1, move.to.toNotation(), move.moveType, move.isCapture);
        }
        
        // 시각화
        visualizeMoves(moves, new Move.Square(3, 3));
    }
    
    static void visualizeMoves(List<Move.LegalMove> moves, Move.Square start) {
        char[][] board = new char[8][8];
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                board[y][x] = '.';
        
        board[start.y][start.x] = 'N';
        for (Move.LegalMove move : moves)
            board[move.to.y][move.to.x] = '*';
        
        System.out.println("\n  a b c d e f g h");
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

### 시나리오 3: TestMode로 행마법 테스트

```java
public class TestModeExample {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        
        // 1. 테스트 모드 생성
        TestMode testMode = engine.createTestMode(gameId);
        
        // 2. 그라스호퍼 테스트 (기물 뛰어넘기)
        testMode.setTarget("grasshopper", true, 0, 0); // a1
        testMode.addPiece("pawn", true, 0, 3);         // a4 (발판)
        
        List<Move.LegalMove> ghMoves = testMode.execute();
        System.out.println("그라스호퍼 합법 수: " + ghMoves.size());
        ghMoves.forEach(m -> System.out.println("  → " + m.to.toNotation()));
        
        // 3. 커스텀 스크립트 테스트
        testMode.reset();
        testMode.setTarget("experiment", true, 3, 3); // d4
        
        // 커스텀: 나이트 이동을 2회 연속
        String customScript = "take-move(1, 2) repeat(2); take-move(2, 1) repeat(2);";
        List<Move.LegalMove> customMoves = testMode.execute(customScript);
        System.out.println("\n커스텀 합법 수: " + customMoves.size());
        customMoves.forEach(m -> System.out.println("  → " + m.to.toNotation()));
        
        // 4. 중립 기물 테스트
        testMode.reset();
        testMode.setTarget("rook", true, 3, 3);       // d4 백 룩
        testMode.addNeutralPiece("bishop", 3, 5);      // d6 중립 비숍
        testMode.addPiece("pawn", false, 3, 6);        // d7 흑 폰
        
        List<Move.LegalMove> rookMoves = testMode.execute();
        System.out.println("\n룩 합법 수 (중립 기물 있는 보드): " + rookMoves.size());
        // 중립 비숍은 아군이므로 그 칸으로 이동 불가, 흑 폰은 잡기 가능
        
        // 5. 정리
        engine.removeTestMode(gameId);
    }
}
```

### 시나리오 4: AI 대전 (간단한 예)

```java
public class SimpleAI {
    
    private final ChessStackEngine engine;
    
    public SimpleAI(ChessStackEngine engine) {
        this.engine = engine;
    }
    
    public void makeRandomMove(String gameId) {
        int player = engine.getCurrentPlayer(gameId);
        List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);
        
        List<Move.LegalMove> allMoves = new ArrayList<>();
        for (Piece.PieceData piece : pieces) {
            if ((piece.owner == player || piece.isNeutral()) && piece.canMove()) {
                allMoves.addAll(engine.getLegalMoves(gameId, piece.pos.x, piece.pos.y));
            }
        }
        
        if (allMoves.isEmpty()) {
            placeRandomPiece(gameId, player);
        } else {
            Move.LegalMove move = allMoves.get(new Random().nextInt(allMoves.size()));
            engine.makeMove(gameId, move.from.x, move.from.y, move.to.x, move.to.y);
        }
    }
    
    private void placeRandomPiece(String gameId, int player) {
        List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
        if (pocket.isEmpty()) return;
        
        Piece.PieceSpec spec = pocket.get(0);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                try {
                    engine.placePiece(gameId, spec.kind.scriptName(), x, y);
                    return;
                } catch (Exception ignored) {}
            }
        }
    }
}
```

### 시나리오 5: 중립 기물과 자동 이동

```java
public class NeutralAndAutoMoveExample {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        GameState state = engine.getGame(gameId);
        
        // 중립 기물 배치 (포켓 불필요, 직접 배치)
        String neutralId = state.placeNeutralPiece(
            Piece.PieceKind.ROOK, new Move.Square(3, 3)); // d4
        
        // 중립 기물은 양쪽 모두 이동 가능
        int player = engine.getCurrentPlayer(gameId); // 0 (백)
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 3, 3);
        System.out.println("백 턴 - 중립 룩 합법 수: " + moves.size());
        
        engine.endTurn(gameId); // 흑 턴
        moves = engine.getLegalMoves(gameId, 3, 3);
        System.out.println("흑 턴 - 중립 룩 합법 수: " + moves.size());
        
        // 자동 이동 확인
        Piece.PieceData np = state.getPiece(neutralId);
        if (np.autoMove != null) {
            System.out.printf("자동 이동 설정: dx=%d, dy=%d, mode=%s%n",
                np.autoMove.dx, np.autoMove.dy, np.autoMove.mode);
        }
        
        // 이동 히스토리 확인
        for (GameState.MoveRecord r : state.getMoveHistory()) {
            System.out.printf("히스토리: %s %s → %s (턴 %d)%n",
                r.pieceKind, r.from, r.to, r.turnNumber);
        }
    }
}
```

---

## 문제 해결

### Q1: "자신의 턴이 아닙니다" 오류

```java
// 문제: 턴 순서를 지키지 않음
engine.placePiece(gameId, "pawn", 0, 0);
engine.placePiece(gameId, "pawn", 1, 1); // 오류!

// 해결: endTurn() 호출
engine.placePiece(gameId, "pawn", 0, 0);
engine.endTurn(gameId);
engine.placePiece(gameId, "pawn", 1, 1); // OK
```

### Q2: "이번 턴에 이미 행동했습니다" 오류

```java
// 문제: 한 턴에 여러 행동
engine.placePiece(gameId, "pawn", 0, 0);
engine.makeMove(gameId, 0, 0, 0, 1); // 오류!

// 해결: 턴당 하나의 행동만
engine.placePiece(gameId, "pawn", 0, 0);
engine.endTurn(gameId);
// 다음 턴에 이동
```

### Q3: "포켓에 해당 기물이 없습니다" 오류

```java
// 해결: 포켓 확인
List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
System.out.println("사용 가능한 기물: " + pocket);
```

### Q4: 합법 수가 비어있음

```java
// 원인 1: 해당 위치에 기물이 없음
Piece.PieceData piece = engine.getPieceAt(gameId, x, y);
if (piece == null) {
    System.out.println("해당 위치에 기물이 없습니다");
}

// 원인 2: 기물이 보드 위에 없음 (포켓 상태)
if (piece != null && !piece.canMove()) {
    System.out.println("기물이 보드 위에 있지 않습니다");
}

// 원인 3: 상대 기물임 (중립이 아닌 경우)
if (piece != null && piece.owner != engine.getCurrentPlayer(gameId) 
    && !piece.isNeutral()) {
    System.out.println("자신의 기물이 아닙니다");
}

// 디버그 모드로 상세 확인
engine.setDebugMode(gameId, true);
List<Move.LegalMove> moves = engine.getLegalMoves(gameId, x, y);
engine.setDebugMode(gameId, false);
```

### Q5: 좌표 변환 오류

```java
// 문제: 좌표 혼동
Move.Square wrong = new Move.Square(1, 4); // ???

// 해결: fromNotation 사용
Move.Square e4 = Move.Square.fromNotation("e4"); // (4, 3)

// 또는 직접 계산
int x = 'e' - 'a'; // 4
int y = '4' - '1'; // 3
```

---

## 성능 팁

### 1. 합법 수 캐싱

```java
Map<String, List<Move.LegalMove>> moveCache = new HashMap<>();
String key = gameId + "_" + x + "_" + y;
List<Move.LegalMove> moves = moveCache.computeIfAbsent(key,
    k -> engine.getLegalMoves(gameId, x, y));

// 보드 변경 시 캐시 무효화
moveCache.clear();
```

### 2. 디버그 모드 비활성화

```java
// 프로덕션에서는 디버그 모드 끄기
engine.setDebugMode(gameId, false);
```

---

## 다음 단계

### 더 배우기

1. **[Core API](01-core-api.md)** - Board, GameState, Move, Piece, RuleSet
2. **[Move Generation API](02-move-generation-api.md)** - MoveGenerator, StandardGenerators
3. **[Chessembly DSL API](03-chessembly-dsl-api.md)** - Interpreter, Parser, AST, BuiltinOps
4. **[Minecraft Integration API](04-minecraft-integration-api.md)** - ChessStackEngine, TestMode

### 게임 규칙 문서

- `docs/chesstack/rule.md` — 게임 규칙
- `docs/chesstack/move.md` — 턴 행동 종류
- `docs/chesstack/stack.md` — 기물 점수
- `docs/chesstack/promotion.md` — 프로모션

### Chessembly DSL 문서

- `docs/chessembly/TUTORIAL.md` — 튜토리얼
- `docs/chessembly/CONCEPT.md` — 핵심 개념
- `docs/chessembly/CONTROL.md` — 제어 흐름
- `docs/chessembly/DEBUGGER.md` — 디버거
