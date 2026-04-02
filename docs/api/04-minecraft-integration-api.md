# ChessStack Minecraft Integration API 사용법

Minecraft Fabric 모드와의 통합을 위한 고수준 API 문서입니다. `ChessStackEngine`은 게임 로직을 간편하게 사용할 수 있는 인터페이스를 제공합니다.

## 목차
- [개요](#개요)
- [ChessStackEngine](#chessstackengine) - 메인 API
- [TestMode](#testmode) - 행마법 테스트
- [게임 생명주기](#게임-생명주기)
- [Minecraft 통합 예제](#minecraft-통합-예제)
- [멀티플레이어 지원](#멀티플레이어-지원)
- [GUI 통합](#gui-통합)

---

## 개요

`ChessStackEngine`은 다음과 같은 특징을 갖습니다:

- **Pure Java**: Minecraft 코드 의존성 없음
- **간단한 API**: 복잡한 내부 로직 숨김
- **멀티 게임**: 여러 게임 동시 관리
- **TestMode**: 격리된 환경에서 행마법 테스트

### 사용 흐름

```
1. ChessStackEngine 인스턴스 생성
2. createGame() → 게임 ID 받기
3. getLegalMoves() / makeMove() / placePiece()
4. endTurn() - 턴 종료
5. getGameResult() - 승리 확인
```

---

## ChessStackEngine

### 생성자

```java
import com.chesstack.minecraft.api.ChessStackEngine;

ChessStackEngine engine = new ChessStackEngine();
```

---

## 게임 생성

### createGame()

표준 포켓으로 새 게임을 생성합니다.

```java
String gameId = engine.createGame();
System.out.println("게임 생성: " + gameId); // "game_1"
```

**반환값**: 게임 ID (예: `"game_1"`, `"game_2"`, ...)

**초기 설정**:
- 백/흑 킹 자동 배치 (e1, e8)
- 표준 포켓 설정 (퀸, 룩x2, 비숍x2, 나이트x2, 폰x8)
- 총 39점 제한

### createExperimentalGame()

실험용 포켓으로 게임을 생성합니다.

```java
String gameId = engine.createExperimentalGame();
```

**실험용 포켓 기물**:
- Amazon, Grasshopper, Knightrider, Archbishop
- Dabbaba, Alfil, Ferz, Centaur, Camel
- Tempest Rook, Cannon, Bouncing Bishop, Experiment

### registerGame()

기존 `GameState`를 등록합니다.

```java
GameState customState = GameState.newDefault();
// ... 커스텀 설정

String gameId = engine.registerGame(customState);
```

---

## 게임 플레이

### getLegalMoves()

특정 위치의 합법 수를 조회합니다.

```java
List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 4, 3); // e4

System.out.println("합법 수: " + moves.size() + "개");
for (Move.LegalMove move : moves) {
    System.out.printf("%s → %s (%s)%n", 
        move.from.toNotation(), 
        move.to.toNotation(),
        move.moveType);
}
```

### makeMove()

이동을 실행합니다.

```java
// e2 → e4
String captured = engine.makeMove(gameId, 4, 1, 4, 3);

if (captured != null) {
    System.out.println("캡처: " + captured);
}
```

**예외**: `IllegalArgumentException` - 유효하지 않은 이동

### placePiece()

포켓에서 기물을 착수합니다.

```java
String pieceId = engine.placePiece(gameId, "knight", 3, 3); // d4
System.out.println("배치된 기물: " + pieceId);
```

**예외**: `IllegalStateException` - 착수 규칙 위반

### endTurn()

턴을 종료합니다.

```java
engine.endTurn(gameId);
```

**동작**:
- 자동 이동(autoMove) 처리
- 다음 플레이어로 전환
- turnNumber 증가
- activePiece, actionTaken 초기화

---

## 게임 조회

### getGameResult()

게임 결과를 확인합니다.

```java
Move.GameResult result = engine.getGameResult(gameId);

switch (result) {
    case ONGOING:    System.out.println("게임 진행 중"); break;
    case WHITE_WINS: System.out.println("백 승리!");     break;
    case BLACK_WINS: System.out.println("흑 승리!");     break;
}
```

### getCurrentPlayer()

현재 턴 플레이어를 조회합니다.

```java
int player = engine.getCurrentPlayer(gameId);
// 0 = 백, 1 = 흑
```

### getPieceAt()

특정 위치의 기물 정보를 조회합니다.

```java
Piece.PieceData piece = engine.getPieceAt(gameId, 4, 3); // e4

if (piece != null) {
    String ownerStr = piece.isNeutral() ? "중립" : (piece.isWhite() ? "백" : "흑");
    System.out.printf("기물: %s (%s)%n", piece.kind, ownerStr);
    
    if (piece.autoMove != null) {
        System.out.printf("자동이동: dx=%d, dy=%d, mode=%s%n",
            piece.autoMove.dx, piece.autoMove.dy, piece.autoMove.mode);
    }
}
```

### getBoardPieces()

보드 위 모든 기물을 조회합니다.

```java
List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);

for (Piece.PieceData piece : pieces) {
    System.out.printf("%s at %s (owner=%d)%n", piece.kind, piece.pos, piece.owner);
}
```

### getPocket()

포켓을 조회합니다.

```java
List<Piece.PieceSpec> pocket = engine.getPocket(gameId, 0); // 백 포켓

System.out.println("백 포켓:");
for (Piece.PieceSpec spec : pocket) {
    System.out.println("  " + spec.kind + " (" + spec.score() + "점)");
}
```

---

## 게임 관리

### getGame()

`GameState` 직접 접근.

```java
GameState state = engine.getGame(gameId);

// 저수준 API 사용 가능
state.setDebugMode(true);
state.crownPiece(0, "piece_1");

// 중립 기물 배치
state.placeNeutralPiece(Piece.PieceKind.ROOK, new Move.Square(3, 3));

// 이동 히스토리 조회
List<GameState.MoveRecord> history = state.getMoveHistory();
```

### removeGame()

게임을 삭제합니다.

```java
engine.removeGame(gameId);
```

### setDebugMode()

디버그 모드를 설정합니다.

```java
engine.setDebugMode(gameId, true);
// Chessembly 실행 로그 출력
```

---

## TestMode

`TestMode`는 격리된 보드에서 특정 기물의 행마법을 실시간으로 테스트하는 도구입니다.

### 사용 흐름

```
1. createTestMode(gameId) → TestMode 인스턴스
2. setTarget(kind, isWhite, x, y) — 대상 기물 설정
3. addPiece(kind, isWhite, x, y) — 주변 기물 배치
4. addNeutralPiece(kind, x, y) — 중립 기물 배치 (선택)
5. execute() — 내장 행마법 실행 → 합법 수 반환
6. execute(script) — 커스텀 스크립트로 실행 (선택)
7. reset() — 보드 초기화
```

### TestMode 생성/조회/제거

```java
// 테스트 모드 생성 (게임 ID와 연결)
TestMode testMode = engine.createTestMode(gameId);

// 테스트 모드 조회
TestMode tm = engine.getTestMode(gameId);

// 테스트 모드 제거
engine.removeTestMode(gameId);
```

### 대상 기물 설정

```java
// 백 나이트를 d4에 배치
testMode.setTarget("knight", true, 3, 3);
```

### 주변 기물 배치

```java
// 흑 폰을 e6에 배치
testMode.addPiece("pawn", false, 4, 5);

// 백 룩을 c5에 배치
testMode.addPiece("rook", true, 2, 4);

// 중립 비숍을 f4에 배치
testMode.addNeutralPiece("bishop", 5, 3);
```

### 행마법 실행

```java
// 내장 스크립트로 실행
List<Move.LegalMove> moves = testMode.execute();

System.out.println("합법 수: " + moves.size() + "개");
for (Move.LegalMove move : moves) {
    System.out.printf("  %s → %s (%s, 캡처=%b)%n",
        move.from.toNotation(), move.to.toNotation(),
        move.moveType, move.isCapture);
}

// 커스텀 스크립트로 실행
String customScript = "take-move(1, 2) repeat(2); take-move(2, 1) repeat(2);";
List<Move.LegalMove> customMoves = testMode.execute(customScript);
```

### 보드 초기화

```java
testMode.reset();
// 대상 기물 포함 전체 리셋
```

### TestMode 전체 예제

```java
ChessStackEngine engine = new ChessStackEngine();
String gameId = engine.createGame();

// 테스트 모드 생성
TestMode testMode = engine.createTestMode(gameId);

// 1. 나이트 기본 행마법 테스트
testMode.setTarget("knight", true, 3, 3); // d4
List<Move.LegalMove> knightMoves = testMode.execute();
System.out.println("나이트 합법 수 (빈 보드): " + knightMoves.size());

// 2. 주변에 기물 배치 후 재테스트
testMode.addPiece("pawn", false, 4, 5); // 흑 폰 at e6 (잡기 가능)
testMode.addPiece("rook", true, 2, 4);  // 백 룩 at c5 (아군, 이동 불가)
testMode.addNeutralPiece("bishop", 5, 3); // 중립 비숍 (아군, 이동 불가)

List<Move.LegalMove> movesWithPieces = testMode.execute();
System.out.println("나이트 합법 수 (기물 있는 보드): " + movesWithPieces.size());

// 3. 커스텀 스크립트 테스트
String customScript = "take-move(1, 0) repeat(1); take-move(0, 1) repeat(1);";
List<Move.LegalMove> customMoves = testMode.execute(customScript);
System.out.println("커스텀 행마법 합법 수: " + customMoves.size());

// 4. 리셋 후 다른 기물 테스트
testMode.reset();
testMode.setTarget("grasshopper", true, 3, 3);
testMode.addPiece("pawn", true, 3, 5); // 아군 폰 at d6 (그라스호퍼 발판)
List<Move.LegalMove> ghMoves = testMode.execute();
System.out.println("그라스호퍼 합법 수: " + ghMoves.size());

// 정리
engine.removeTestMode(gameId);
```

---

## 게임 생명주기

### 전체 워크플로우

```java
import com.chesstack.minecraft.api.*;
import com.chesstack.engine.core.*;
import java.util.*;

public class GameLifecycle {
    
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        
        // 1. 게임 생성
        String gameId = engine.createGame();
        
        // 2. 게임 루프
        while (engine.getGameResult(gameId) == Move.GameResult.ONGOING) {
            int player = engine.getCurrentPlayer(gameId);
            String color = (player == 0) ? "백" : "흑";
            
            System.out.println("\n" + color + " 턴");
            displayBoard(engine, gameId);
            
            performPlayerAction(engine, gameId, player);
            engine.endTurn(gameId);
        }
        
        // 3. 게임 종료
        System.out.println("\n게임 종료: " + engine.getGameResult(gameId));
        engine.removeGame(gameId);
    }
    
    static void performPlayerAction(ChessStackEngine engine, String gameId, int player) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("1=착수, 2=이동, 3=계승: ");
        int choice = scanner.nextInt();
        
        try {
            if (choice == 1) {
                System.out.print("기물 종류: ");
                String kind = scanner.next();
                System.out.print("좌표 (x y): ");
                int x = scanner.nextInt(), y = scanner.nextInt();
                
                String pieceId = engine.placePiece(gameId, kind, x, y);
                System.out.println("배치 완료: " + pieceId);
                
            } else if (choice == 2) {
                System.out.print("출발 (x y): ");
                int fromX = scanner.nextInt(), fromY = scanner.nextInt();
                
                List<Move.LegalMove> moves = engine.getLegalMoves(gameId, fromX, fromY);
                for (int i = 0; i < moves.size(); i++) {
                    System.out.printf("%d: %s%n", i, moves.get(i));
                }
                
                System.out.print("선택: ");
                int idx = scanner.nextInt();
                Move.LegalMove selected = moves.get(idx);
                
                String captured = engine.makeMove(gameId,
                    selected.from.x, selected.from.y,
                    selected.to.x, selected.to.y);
                if (captured != null) System.out.println("캡처: " + captured);
                
            } else if (choice == 3) {
                System.out.print("기물 ID: ");
                String pid = scanner.next();
                engine.getGame(gameId).crownPiece(player, pid);
                System.out.println("계승 완료");
            }
        } catch (Exception e) {
            System.out.println("오류: " + e.getMessage());
        }
    }
    
    static void displayBoard(ChessStackEngine engine, String gameId) {
        System.out.println("현재 보드:");
        for (Piece.PieceData p : engine.getBoardPieces(gameId)) {
            String ownerStr = p.isNeutral() ? "중립" : (p.isWhite() ? "백" : "흑");
            System.out.printf("  %s %s at %s%s%n", ownerStr, p.kind, p.pos,
                p.isRoyal ? " [ROYAL]" : "");
        }
    }
}
```

---

## Minecraft 통합 예제

### 예제: Fabric 모드 통합

```java
package com.example.chessmod;

import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;
import java.util.*;

public class ChessGameManager {
    
    private final ChessStackEngine engine;
    private final Map<UUID, String> playerGames;
    
    public ChessGameManager() {
        this.engine = new ChessStackEngine();
        this.playerGames = new HashMap<>();
    }
    
    /** 새 게임 시작 */
    public String startGame(UUID player1, UUID player2) {
        String gameId = engine.createGame();
        playerGames.put(player1, gameId);
        playerGames.put(player2, gameId);
        return gameId;
    }
    
    /** 기물 착수 */
    public boolean placePiece(UUID playerId, String kind, int x, int z) {
        String gameId = playerGames.get(playerId);
        if (gameId == null) return false;
        
        try {
            engine.placePiece(gameId, kind, x, z);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /** 기물 이동 */
    public boolean movePiece(UUID playerId, int fromX, int fromZ, int toX, int toZ) {
        String gameId = playerGames.get(playerId);
        if (gameId == null) return false;
        
        try {
            String captured = engine.makeMove(gameId, fromX, fromZ, toX, toZ);
            if (captured != null) playCaptureFX(captured);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /** 턴 종료 */
    public void endTurn(UUID playerId) {
        String gameId = playerGames.get(playerId);
        if (gameId != null) engine.endTurn(gameId);
    }
    
    /** 승리 확인 */
    public Move.GameResult checkWinner(UUID playerId) {
        String gameId = playerGames.get(playerId);
        if (gameId == null) return Move.GameResult.ONGOING;
        return engine.getGameResult(gameId);
    }
    
    /** 게임 종료 및 정리 */
    public void endGame(UUID player1, UUID player2) {
        String gameId = playerGames.get(player1);
        if (gameId != null) {
            engine.removeTestMode(gameId); // 테스트 모드도 정리
            engine.removeGame(gameId);
            playerGames.remove(player1);
            playerGames.remove(player2);
        }
    }
    
    private void playCaptureFX(String capturedId) {
        // Minecraft 파티클/사운드 효과
    }
}
```

---

## 멀티플레이어 지원

### 게임 세션 관리

```java
public class MultiplayerChessServer {
    
    private final ChessStackEngine engine;
    private final Map<String, GameSession> sessions;
    
    public MultiplayerChessServer() {
        this.engine = new ChessStackEngine();
        this.sessions = new HashMap<>();
    }
    
    static class GameSession {
        String gameId;
        UUID whitePlayer;
        UUID blackPlayer;
        long startTime;
    }
    
    /** 매치메이킹 */
    public GameSession createMatch(UUID player1, UUID player2) {
        String gameId = engine.createGame();
        
        GameSession session = new GameSession();
        session.gameId = gameId;
        session.whitePlayer = player1;
        session.blackPlayer = player2;
        session.startTime = System.currentTimeMillis();
        
        sessions.put(gameId, session);
        return session;
    }
    
    /** 플레이어 검증 */
    public boolean validatePlayer(String gameId, UUID playerId) {
        GameSession session = sessions.get(gameId);
        if (session == null) return false;
        
        int currentPlayer = engine.getCurrentPlayer(gameId);
        return currentPlayer == 0
            ? playerId.equals(session.whitePlayer)
            : playerId.equals(session.blackPlayer);
    }
}
```

---

## GUI 통합

### 보드 렌더링

```java
public class ChessBoardRenderer {
    
    /** 3D 체스 보드 렌더링 (Minecraft) */
    public void renderBoard(ChessStackEngine engine, String gameId, 
                           World world, BlockPos origin) {
        // 보드 기초
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                BlockPos pos = origin.add(x, 0, z);
                boolean isWhite = (x + z) % 2 == 0;
                BlockState state = isWhite
                    ? Blocks.QUARTZ_BLOCK.getDefaultState()
                    : Blocks.OBSIDIAN.getDefaultState();
                world.setBlockState(pos, state);
            }
        }
        
        // 기물 배치
        for (Piece.PieceData piece : engine.getBoardPieces(gameId)) {
            BlockPos piecePos = origin.add(piece.pos.x, 1, piece.pos.y);
            renderPiece(world, piecePos, piece);
        }
    }
    
    private void renderPiece(World world, BlockPos pos, Piece.PieceData piece) {
        // 기물을 Minecraft 엔티티로 표현
        // 중립 기물(piece.isNeutral())은 별도 색상으로 표시
    }
    
    /** 합법 수 하이라이트 */
    public void highlightLegalMoves(ChessStackEngine engine, String gameId,
                                   World world, BlockPos origin, int x, int z) {
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, x, z);
        
        for (Move.LegalMove move : moves) {
            BlockPos highlightPos = origin.add(move.to.x, 0, move.to.y);
            spawnParticles(world, highlightPos, move.isCapture);
        }
    }
    
    private void spawnParticles(World world, BlockPos pos, boolean isCapture) {
        // 캡처 가능 → 빨간 파티클, 이동 → 녹색 파티클
    }
}
```

---

## 성능 최적화

### 비동기 처리

```java
import java.util.concurrent.*;

public class AsyncChessEngine {
    private final ChessStackEngine engine;
    private final ExecutorService executor;
    
    public AsyncChessEngine() {
        this.engine = new ChessStackEngine();
        this.executor = Executors.newCachedThreadPool();
    }
    
    public CompletableFuture<List<Move.LegalMove>> getLegalMovesAsync(
        String gameId, int x, int y
    ) {
        return CompletableFuture.supplyAsync(
            () -> engine.getLegalMoves(gameId, x, y), executor);
    }
    
    public CompletableFuture<String> makeMoveAsync(
        String gameId, int fromX, int fromY, int toX, int toY
    ) {
        return CompletableFuture.supplyAsync(
            () -> engine.makeMove(gameId, fromX, fromY, toX, toY), executor);
    }
}
```

---

## 다음 단계

- [Core API](01-core-api.md) - 저수준 API 이해
- [Move Generation API](02-move-generation-api.md) - 합법 수 생성 커스터마이징
- [Chessembly DSL API](03-chessembly-dsl-api.md) - Chessembly DSL 직접 사용
