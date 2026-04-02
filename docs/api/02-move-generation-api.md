# ChessStack Move Generation API 사용법

Chessembly 인터프리터를 사용한 합법 수 생성 API 문서입니다.

## 목차
- [MoveGenerator](#movegenerator) - 합법 수 생성
- [StandardGenerators](#standardgenerators) - 기본 행마법 관리
- [고급 사용법](#고급-사용법)

---

## MoveGenerator

`MoveGenerator`는 Chessembly 인터프리터를 사용하여 특정 기물의 합법 수를 계산하는 클래스입니다.

### 기본 사용법

```java
import com.chesstack.engine.movegen.MoveGenerator;
import com.chesstack.engine.core.*;
import java.util.*;

// GameState와 기물 ID로 합법 수 생성
List<Move.LegalMove> legalMoves = MoveGenerator.generateLegalMoves(state, pieceId);

for (Move.LegalMove move : legalMoves) {
    System.out.printf("%s → %s (%s)%n", 
        move.from, move.to, move.moveType);
}
```

### generateLegalMoves()

기물의 모든 합법 수를 계산합니다.

```java
/**
 * 특정 기물의 모든 합법 수를 계산한다.
 *
 * @param state   현재 게임 상태
 * @param pieceId 기물 ID
 * @return 합법 수 목록
 */
public static List<Move.LegalMove> generateLegalMoves(
    GameState state, 
    String pieceId
)
```

**동작 과정:**
1. 기물 정보 조회 (위치, 종류, 소유자)
2. Chessembly 보드 상태 생성 (`state.toChessemblyBoard()`)
3. `PieceKind.chessemblyScript(isWhite)` 로 행마법 스크립트 획득
4. Chessembly 인터프리터 실행
5. `Activation` → `LegalMove` 변환

**중립 기물 처리:**
- 중립 기물(owner=2)의 합법 수를 생성할 때, 현재 턴 플레이어의 시점으로 적/아군을 판정합니다.
- 중립 기물은 어느 플레이어의 턴에서든 이동 가능합니다.

**예제:**

```java
GameState state = GameState.newDefault();
state.setupInitialPosition();

// d4에 나이트 배치
Move.Square d4 = Move.Square.fromNotation("d4");
String knightId = state.placePiece(0, Piece.PieceKind.KNIGHT, d4);
state.endTurn();

// 합법 수 생성
List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, knightId);

System.out.println("나이트 합법 수: " + moves.size() + "개");
for (Move.LegalMove move : moves) {
    System.out.printf("  %s → %s (캡처: %b)%n",
        move.from.toNotation(), 
        move.to.toNotation(), 
        move.isCapture);
}
```

---

## StandardGenerators

`StandardGenerators`는 내장 기물들의 Chessembly 스크립트를 조회하는 유틸리티 클래스입니다.

### getScript()

기물 이름으로 내장 스크립트를 조회합니다.

```java
import com.chesstack.engine.movegen.StandardGenerators;

// 백 나이트의 행마법 스크립트 조회
String script = StandardGenerators.getScript("knight", true);
System.out.println(script);
// "take-move(1, 2); take-move(2, 1); ..."

// 흑 폰의 행마법 스크립트 조회
String pawnScript = StandardGenerators.getScript("pawn", false);
// "move(0, -1); take(1, -1); take(-1, -1);"
```

### getAllBuiltinScripts()

모든 내장 기물의 스크립트 맵을 반환합니다.

```java
Map<String, String> allScripts = StandardGenerators.getAllBuiltinScripts();

for (Map.Entry<String, String> entry : allScripts.entrySet()) {
    System.out.printf("%s: %s%n", entry.getKey(), entry.getValue());
}
```

**내장 기물 목록:**
- pawn, king, queen, rook, knight, bishop
- amazon, grasshopper, knightrider, archbishop
- dabbaba, alfil, ferz, centaur, camel
- tempestrook, cannon, bouncingbishop, experiment

---

## 고급 사용법

### 1. 이동 타입별 필터링

```java
/**
 * 캡처 이동만 반환
 */
public List<Move.LegalMove> getCapturesOnly(GameState state, String pieceId) {
    List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
    
    return allMoves.stream()
        .filter(m -> m.isCapture)
        .collect(Collectors.toList());
}

/**
 * 특정 MoveType만 필터링
 */
public List<Move.LegalMove> getByMoveType(
    GameState state, 
    String pieceId, 
    AST.MoveType type
) {
    return MoveGenerator.generateLegalMoves(state, pieceId).stream()
        .filter(m -> m.moveType == type)
        .collect(Collectors.toList());
}
```

### 2. 이동 범위 제한

```java
/**
 * 최대 이동 거리를 제한하는 필터
 */
public List<Move.LegalMove> getMovesWithinRange(
    GameState state, 
    String pieceId, 
    int maxDistance
) {
    List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
    List<Move.LegalMove> filtered = new ArrayList<>();
    
    for (Move.LegalMove move : allMoves) {
        int dx = Math.abs(move.to.x - move.from.x);
        int dy = Math.abs(move.to.y - move.from.y);
        int distance = Math.max(dx, dy); // 체비셰프 거리
        
        if (distance <= maxDistance) {
            filtered.add(move);
        }
    }
    
    return filtered;
}
```

### 3. 전체 보드 합법 수 생성

```java
/**
 * 특정 플레이어의 모든 기물에 대한 합법 수 생성
 * (중립 기물 포함)
 */
public Map<String, List<Move.LegalMove>> getAllPlayerMoves(
    GameState state, 
    int player
) {
    Map<String, List<Move.LegalMove>> allMoves = new HashMap<>();
    
    for (Piece.PieceData piece : state.getBoardPieces()) {
        // 자신의 기물 또는 중립 기물
        if ((piece.owner == player || piece.isNeutral()) && piece.canMove()) {
            List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(
                state, piece.id
            );
            if (!moves.isEmpty()) {
                allMoves.put(piece.id, moves);
            }
        }
    }
    
    return allMoves;
}
```

### 4. AI용 이동 평가

```java
/**
 * 이동의 가치를 평가하는 함수
 */
public static int evaluateMove(GameState state, Move.LegalMove move) {
    int score = 0;
    
    // 중앙 제어 보너스
    int centerDist = Math.abs(move.to.x - 3) + Math.abs(move.to.y - 3);
    score += (6 - centerDist) * 10;
    
    // 캡처 보너스
    if (move.isCapture) {
        Piece.PieceData captured = state.getPieceAt(move.to);
        if (captured != null) {
            score += captured.score() * 100;
        }
    }
    
    // 액션 태그 보너스 (소환, 자동 이동 등)
    if (move.tags != null && !move.tags.isEmpty()) {
        score += 50;
    }
    
    return score;
}

public static Move.LegalMove getBestMove(GameState state, String pieceId) {
    List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, pieceId);
    
    return moves.stream()
        .max(Comparator.comparingInt(m -> evaluateMove(state, m)))
        .orElse(null);
}
```

### 5. 디버그 모드로 행마법 검증

```java
public static void debugPieceMoves(GameState state, String pieceId) {
    // 디버그 모드 활성화
    state.setDebugMode(true);
    
    Piece.PieceData piece = state.getPiece(pieceId);
    System.out.printf("기물: %s at %s (owner=%d)%n", piece.kind, piece.pos, piece.owner);
    
    // 행마법 스크립트 출력
    String script = piece.kind.chessemblyScript(piece.isWhite());
    System.out.println("스크립트: " + script);
    
    // 합법 수 생성 (디버그 로그와 함께)
    List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, pieceId);
    
    System.out.println("결과: " + moves.size() + "개 합법 수");
    for (Move.LegalMove move : moves) {
        System.out.printf("  %s → %s (%s, 캡처=%b, 태그=%s)%n",
            move.from.toNotation(),
            move.to.toNotation(),
            move.moveType,
            move.isCapture,
            move.tags);
    }
    
    state.setDebugMode(false);
}
```

---

## 실전 예제

### 예제 1: 이동 힌트 시스템

```java
public class MoveHintSystem {
    
    public static List<Move.LegalMove> getRecommendedMoves(
        GameState state, 
        String pieceId, 
        int maxHints
    ) {
        List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
        
        allMoves.sort((m1, m2) -> {
            int score1 = evaluateMove(state, m1);
            int score2 = evaluateMove(state, m2);
            return Integer.compare(score2, score1);
        });
        
        return allMoves.subList(0, Math.min(maxHints, allMoves.size()));
    }
    
    private static int evaluateMove(GameState state, Move.LegalMove move) {
        int score = 0;
        if (move.isCapture) {
            Piece.PieceData target = state.getPieceAt(move.to);
            if (target != null) score += target.score() * 100;
        }
        int centerX = Math.abs(move.to.x - 3);
        int centerY = Math.abs(move.to.y - 3);
        score += (6 - centerX - centerY) * 10;
        return score;
    }
}
```

### 예제 2: 중립 기물과 자동 이동 활용

```java
public class NeutralPieceExample {
    
    public static void main(String[] args) {
        GameState state = GameState.newDefault();
        state.setupInitialPosition();
        
        // 중립 기물 배치
        String neutralRookId = state.placeNeutralPiece(
            Piece.PieceKind.ROOK, new Move.Square(3, 3));
        
        // 양쪽 모두 중립 기물을 이동시킬 수 있음
        List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, neutralRookId);
        System.out.println("중립 룩 합법 수: " + moves.size() + "개");
        
        // 중립 기물의 autoMove 확인
        Piece.PieceData np = state.getPiece(neutralRookId);
        if (np.autoMove != null) {
            System.out.printf("자동 이동: dx=%d, dy=%d, mode=%s%n",
                np.autoMove.dx, np.autoMove.dy, np.autoMove.mode);
        }
    }
}
```

---

## 성능 최적화 팁

### 합법 수 캐싱

```java
public class CachedMoveGenerator {
    private final Map<String, List<Move.LegalMove>> cache = new HashMap<>();
    
    public List<Move.LegalMove> getCachedMoves(GameState state, String pieceId) {
        String key = pieceId + "_" + state.hashCode();
        return cache.computeIfAbsent(key, k -> 
            MoveGenerator.generateLegalMoves(state, pieceId)
        );
    }
    
    public void invalidateCache() {
        cache.clear();
    }
}
```

### 병렬 처리

```java
import java.util.concurrent.*;

public List<Move.LegalMove> generateMovesParallel(
    GameState state, 
    List<String> pieceIds
) {
    return pieceIds.parallelStream()
        .flatMap(id -> MoveGenerator.generateLegalMoves(state, id).stream())
        .collect(Collectors.toList());
}
```

---

## 다음 단계

- [Chessembly DSL API](03-chessembly-dsl-api.md) - 직접 스크립트 작성하기
- [Minecraft Integration API](04-minecraft-integration-api.md) - 고수준 API 사용하기
