# ChessStack Core API 사용법

ChessStack의 핵심 엔진 API 문서입니다. 게임 상태 관리, 보드, 기물, 이동 등의 기본 기능을 제공합니다.

## 목차
- [Board](#board) - 체스판 관리
- [GameState](#gamestate) - 게임 상태 관리
- [Move](#move) - 이동 및 액션 정의
- [Piece](#piece) - 기물 정의
- [RuleSet](#ruleset) - 게임 규칙

---

## Board

`Board`는 체스판의 기물 배치를 관리하는 클래스입니다. HashMap 기반으로 구현되어 있으며, Square(좌표)를 키로 사용하여 기물 ID를 저장합니다.

### 주요 메서드

```java
// 기물 배치
void put(Move.Square sq, String pieceId)

// 기물 조회
String get(Move.Square sq)

// 기물 제거
String remove(Move.Square sq)

// 기물 존재 확인
boolean contains(Move.Square sq)

// 모든 기물 정보 조회
Map<Move.Square, String> asMap()
```

### 사용 예제

```java
import com.chesstack.engine.core.*;

// 보드 생성
Board board = new Board();

// e4에 기물 배치
Move.Square e4 = new Move.Square(4, 3); // e4 = (4, 3)
board.put(e4, "piece_1");

// 기물 조회
String pieceId = board.get(e4); // "piece_1"

// 기물 존재 확인
boolean hasPiece = board.contains(e4); // true

// 기물 제거
board.remove(e4);

// 모든 기물 조회
Map<Move.Square, String> allPieces = board.asMap();
```

---

## GameState

`GameState`는 게임의 전체 상태를 관리하는 핵심 클래스입니다. 보드, 포켓, 기물 정보, 턴 관리, 승리 조건 확인, 중립 기물, 자동 이동, 이동 히스토리 등을 담당합니다.

### 생성 및 초기화

```java
// 기본 게임 생성 (백 플레이어부터 시작)
GameState state = GameState.newDefault();

// 표준 포켓으로 초기 포지션 설정
state.setupInitialPosition();

// 실험용 포켓 설정 (특수 기물 포함)
state.setupExperimentalPocket();
```

### 포켓 관리

```java
// 커스텀 포켓 설정 (점수 제한 검증, 최대 39점)
List<Piece.PieceSpec> pocket = Arrays.asList(
    new Piece.PieceSpec(Piece.PieceKind.QUEEN),
    new Piece.PieceSpec(Piece.PieceKind.ROOK),
    new Piece.PieceSpec(Piece.PieceKind.BISHOP)
);
state.setupPocket(0, pocket); // 플레이어 0 (백)

// 포켓 조회
List<Piece.PieceSpec> playerPocket = state.getPocket(0);

// 점수 제한 없는 포켓 설정 (실험용)
state.setupPocketUnchecked(1, experimentalPocket);
```

### 기물 착수 (Drop)

```java
// 착수 가능 여부 확인
Move.Square target = new Move.Square(3, 3); // d4
state.canPlace(0, Piece.PieceKind.KNIGHT, target);

// 착수 실행 → 기물 ID 반환
String pieceId = state.placePiece(0, Piece.PieceKind.KNIGHT, target);

// 착수 시 자동으로:
// - 포켓에서 기물 제거
// - 보드에 기물 배치
// - actionTaken = true
```

### 중립 기물 배치

```java
// 중립 기물 배치 (owner=2, 양측 모두에게 아군으로 취급)
// 포켓 불필요, 직접 보드에 배치
String neutralId = state.placeNeutralPiece(Piece.PieceKind.ROOK, new Move.Square(3, 3));

// 중립 기물 특성:
// - 어느 플레이어든 이동 가능
// - enemy() 조건에서 적이 아님
// - friendly() 조건에서 아군으로 판정
// - 계승(crown) 불가
```

### 기물 이동

```java
// 특정 위치의 합법 수 조회
Move.Square from = Move.Square.fromNotation("e2");
List<Move.LegalMove> legalMoves = state.getLegalMovesAt(from);

// 합법 수 실행 → 캡처된 기물 ID 반환 (없으면 null)
for (Move.LegalMove move : legalMoves) {
    if (move.to.equals(Move.Square.fromNotation("e4"))) {
        String captured = state.movePieceByLegalMove(move);
        if (captured != null) {
            System.out.println("캡처: " + captured);
        }
        break;
    }
}

// 이동 유효성 확인
boolean valid = state.isValidMove("piece_1", from, to);
boolean validAt = state.isValidMoveAt(from, to);
```

### 기물 조회

```java
// 특정 위치의 기물 조회
Move.Square sq = Move.Square.fromNotation("e4");
Piece.PieceData piece = state.getPieceAt(sq);

// ID로 기물 조회
Piece.PieceData piece = state.getPiece("piece_1");

// 보드 위 모든 기물 조회
List<Piece.PieceData> allPieces = state.getBoardPieces();

// 전체 기물 맵 (포켓 포함)
Map<String, Piece.PieceData> allMap = state.getAllPieces();
```

### 턴 관리

```java
// 현재 턴 조회 (0=백, 1=흑)
int currentPlayer = state.getTurn();

// 턴 번호 조회
int turnNum = state.getTurnNumber();

// 턴 종료 → 다음 플레이어로 전환
state.endTurn();
// - 자동 이동(autoMove) 처리
// - turn을 상대 플레이어로 전환
// - turnNumber 증가
// - activePiece, actionTaken 초기화
```

### 계승 (Crown)

```java
// 기물을 로얄 피스(왕족)로 만들기
state.crownPiece(0, "piece_1"); // 플레이어 0의 기물을 계승

// 제약 조건:
// - 자신의 턴이어야 함
// - 이번 턴에 다른 행동을 하지 않았어야 함
// - 자신의 기물이어야 함 (중립 기물은 계승 불가)
// - 보드 위에 있는 기물이어야 함
```

### 프로모션

```java
// 프로모션 실행
state.promote("piece_1", Piece.PieceKind.QUEEN);

// 제약 조건:
// - 프로모션 가능한 기물 (현재 폰만 가능)
// - 유효한 프로모션 대상 (퀸, 룩, 비숍, 나이트)
// - 프로모션 칸에 위치해야 함
```

### 승리 조건 확인

```java
Move.GameResult result = state.checkVictory();

switch (result) {
    case ONGOING:
        System.out.println("게임 진행 중");
        break;
    case WHITE_WINS:
        System.out.println("백 승리!");
        break;
    case BLACK_WINS:
        System.out.println("흑 승리!");
        break;
}
```

### 이동 히스토리

```java
// 이동 히스토리 조회
List<GameState.MoveRecord> history = state.getMoveHistory();

for (GameState.MoveRecord record : history) {
    System.out.printf("%s(%s): %s → %s (턴 %d)%n",
        record.pieceKind, record.pieceId,
        record.from, record.to, record.turnNumber);
}

// MoveRecord 필드:
// - pieceId: 이동한 기물 ID
// - pieceKind: 이동한 기물 종류
// - from: 출발 좌표
// - to: 도착 좌표
// - turnNumber: 이동 시점의 턴 번호
```

### 디버그 모드

```java
// 디버그 모드 활성화 → Chessembly 실행 로그 출력
state.setDebugMode(true);

// Chessembly 보드 상태 생성 (디버깅용)
BuiltinOps.BoardState chessemblyBoard = state.toChessemblyBoard("piece_1");
```

### 전체 예제

```java
import com.chesstack.engine.core.*;
import java.util.*;

public class CoreAPIExample {
    public static void main(String[] args) {
        // 1. 게임 생성 및 초기화
        GameState state = GameState.newDefault();
        state.setupInitialPosition();
        
        // 2. 백 플레이어가 나이트 착수
        Move.Square d4 = Move.Square.fromNotation("d4");
        String knightId = state.placePiece(0, Piece.PieceKind.KNIGHT, d4);
        System.out.println("나이트 배치: " + knightId);
        
        // 3. 턴 종료
        state.endTurn();
        
        // 4. 흑 플레이어가 폰 착수
        Move.Square e5 = Move.Square.fromNotation("e5");
        String pawnId = state.placePiece(1, Piece.PieceKind.PAWN, e5);
        System.out.println("폰 배치: " + pawnId);
        
        // 5. 턴 종료
        state.endTurn();
        
        // 6. 백 플레이어의 나이트 이동
        List<Move.LegalMove> moves = state.getLegalMovesAt(d4);
        System.out.println("나이트 합법 수: " + moves.size() + "개");
        
        for (Move.LegalMove move : moves) {
            System.out.println("  " + move);
        }
        
        // 7. e5로 이동 (캡처)
        for (Move.LegalMove move : moves) {
            if (move.to.equals(e5)) {
                String captured = state.movePieceByLegalMove(move);
                System.out.println("캡처된 기물: " + captured);
                break;
            }
        }
        
        // 8. 게임 결과 확인
        Move.GameResult result = state.checkVictory();
        System.out.println("게임 상태: " + result);
        
        // 9. 이동 히스토리 확인
        for (GameState.MoveRecord r : state.getMoveHistory()) {
            System.out.printf("  %s: %s → %s%n", r.pieceKind, r.from, r.to);
        }
    }
}
```

---

## Move

`Move`는 게임 내 이동과 액션을 정의하는 유틸리티 클래스입니다.

### Square (좌표)

체스판의 좌표를 나타냅니다 (0-based).

```java
// 좌표 생성
Move.Square sq = new Move.Square(4, 3); // e4

// 체스 표기법에서 파싱
Move.Square e4 = Move.Square.fromNotation("e4");

// 체스 표기법으로 변환
String notation = e4.toNotation(); // "e4"

// 유효성 검사
boolean valid = sq.isValid(); // 0 <= x,y < 8
```

### LegalMove (합법 수)

합법 수는 Chessembly 계산 결과물입니다.

```java
// 합법 수 필드
public final Square from;              // 출발 좌표
public final Square to;               // 도착 좌표
public final AST.MoveType moveType;   // 이동 타입
public final boolean isCapture;       // 캡처 여부
public final List<AST.ActionTag> tags; // 액션 태그 (TRANSITION, SET_STATE)
public final Square catchTo;          // JUMP용 잡기 위치
public final String strArg;           // SUMMON: 소환할 기물 이름 (nullable)
```

### MoveType 종류

```java
AST.MoveType.MOVE       // 빈 칸으로만 이동
AST.MoveType.TAKE       // 적 기물 칸으로만 이동
AST.MoveType.TAKE_MOVE  // 빈 칸 또는 적 기물 칸
AST.MoveType.CATCH      // 캡처 전용 (이동 안함, 원거리)
AST.MoveType.SHIFT      // 자리 바구기
AST.MoveType.JUMP       // 뛰어넘기 (catchTo: 잡을 위치)
AST.MoveType.SUMMON     // 기물 소환 이동 없음, strArg에 소환 기물 이름
AST.MoveType.AUTO_MOVE  // 이동/잡기 + 자동 이동 설정 (take-move 모드)
AST.MoveType.AUTO_SHIFT // 위치 교환 + 자동 이동 설정 (shift 모드)
```

### Action (플레이어 액션)

```java
// 착수
Move.Action placeAction = Move.Action.place("piece_1", target);

// 이동
Move.Action moveAction = Move.Action.move("piece_1", from, to);

// 계승
Move.Action crownAction = Move.Action.crown("piece_1");

// 액션 실행
state.applyAction(action);
```

### ActionType 종류

```java
ActionType.PLACE    // 착수
ActionType.MOVE     // 이동
ActionType.CROWN    // 계승
```

---

## Piece

`Piece`는 기물 종류와 데이터를 정의합니다.

### PieceKind (기물 종류)

```java
// 표준 기물
Piece.PieceKind.PAWN
Piece.PieceKind.KING
Piece.PieceKind.QUEEN
Piece.PieceKind.ROOK
Piece.PieceKind.KNIGHT
Piece.PieceKind.BISHOP

// 특수 기물
Piece.PieceKind.AMAZON         // 퀸 + 나이트
Piece.PieceKind.GRASSHOPPER    // 호퍼
Piece.PieceKind.KNIGHTRIDER    // 연속 나이트
Piece.PieceKind.ARCHBISHOP     // 비숍 + 나이트
Piece.PieceKind.DABBABA        // 2칸 직선 도약
Piece.PieceKind.ALFIL          // 2칸 대각선 도약
Piece.PieceKind.FERZ           // 1칸 대각선
Piece.PieceKind.CENTAUR        // 킹 + 나이트
Piece.PieceKind.CAMEL          // (3,1) 도약
Piece.PieceKind.TEMPEST_ROOK   // 폭풍 룩 (대각 시작 → 직선 분기)
Piece.PieceKind.CANNON         // 캐논 (포 점프)
Piece.PieceKind.BOUNCING_BISHOP // 반사 비숍 (벽에서 반사)
Piece.PieceKind.EXPERIMENT     // 실험용
Piece.PieceKind.DSL_TESTING_PIECE // DSL 테스트 전용 (행마법 없음, 스크립트="")
```

### PieceKind 메서드

```java
// 점수 조회
int score = Piece.PieceKind.QUEEN.score(); // 9

// 스크립트 이름
String name = Piece.PieceKind.KNIGHT.scriptName(); // "knight"

// Chessembly 행마법 스크립트
String script = Piece.PieceKind.KNIGHT.chessemblyScript(true); // 백
String scriptBlack = Piece.PieceKind.PAWN.chessemblyScript(false); // 흑

// 프로모션 가능 여부
boolean canPromote = Piece.PieceKind.PAWN.canPromote(); // true

// 프로모션 대상 목록
List<Piece.PieceKind> targets = Piece.PieceKind.PAWN.promotionTargets();
// [QUEEN, ROOK, BISHOP, KNIGHT]

// 프로모션 칸 확인
boolean isPromoSq = Piece.PieceKind.PAWN.isPromotionSquare(sq, true);

// 문자열로 파싱
Piece.PieceKind kind = Piece.PieceKind.fromString("knight");
```

### PieceSpec (포켓용 기물 스펙)

```java
// 기물 스펙 생성
Piece.PieceSpec spec = new Piece.PieceSpec(Piece.PieceKind.QUEEN);

// 점수 조회
int score = spec.score(); // kind 기준
```

### PieceData (게임 내 기물)

```java
// 필드
public final String id;           // 고유 ID
public PieceKind kind;             // 기물 종류
public final int owner;            // 소유자 (0=백, 1=흑, 2=중립)
public Move.Square pos;            // 위치 (null이면 포켓)
public boolean isRoyal;            // 왕족 여부
public AutoMove autoMove;          // 자동 이동 설정 (nullable)

// 유틸리티 메서드
boolean canMove = piece.canMove();   // pos != null
boolean isWhite = piece.isWhite();   // owner == 0
boolean isNeutral = piece.isNeutral(); // owner == 2
int score = piece.score();
```

### AutoMove (자동 이동)

```java
// AutoMoveMode
Piece.AutoMoveMode.TAKE_MOVE  // 빈 칸 이동 / 적 잡기, 그 외 멈춤
Piece.AutoMoveMode.SHIFT      // 빈 칸 이동 / 기물과 위치 교환, 그 외 멈춤

// AutoMove 필드
public final int dx;                 // X 방향
public final int dy;                 // Y 방향
public final AutoMoveMode mode;      // 모드
```

### 예제: 커스텀 포켓

```java
// 특수 기물로만 구성된 포켓 만들기
List<Piece.PieceSpec> customPocket = Arrays.asList(
    new Piece.PieceSpec(Piece.PieceKind.AMAZON),
    new Piece.PieceSpec(Piece.PieceKind.GRASSHOPPER),
    new Piece.PieceSpec(Piece.PieceKind.KNIGHTRIDER),
    new Piece.PieceSpec(Piece.PieceKind.ARCHBISHOP)
);

GameState state = GameState.newDefault();
state.setupPocketUnchecked(0, customPocket);
state.setupPocketUnchecked(1, customPocket);
```

---

## RuleSet

`RuleSet`은 게임 규칙 상수를 정의합니다.

### 상수

```java
// 보드 크기
RuleSet.BOARD_WIDTH   // 8
RuleSet.BOARD_HEIGHT  // 8

// 점수 제한
RuleSet.MAX_POCKET_SCORE  // 39

// 플레이어 ID
RuleSet.WHITE    // 0
RuleSet.BLACK    // 1
RuleSet.NEUTRAL  // 2
```

---

## 전체 워크플로우 예제

```java
import com.chesstack.engine.core.*;
import java.util.*;

public class CompleteExample {
    public static void main(String[] args) {
        // 게임 설정
        GameState game = GameState.newDefault();
        game.setupInitialPosition();
        
        playGame(game);
    }
    
    static void playGame(GameState game) {
        Scanner scanner = new Scanner(System.in);
        
        while (game.checkVictory() == Move.GameResult.ONGOING) {
            displayBoard(game);
            
            int player = game.getTurn();
            String color = player == 0 ? "백" : "흑";
            
            System.out.println("\n" + color + " 플레이어 턴 (턴 " + game.getTurnNumber() + ")");
            System.out.println("포켓: " + game.getPocket(player));
            System.out.println("1=착수, 2=이동, 3=계승, 4=턴 종료: ");
            
            int choice = scanner.nextInt();
            
            try {
                if (choice == 1) {
                    // 착수
                    System.out.print("기물 종류 (예: knight): ");
                    String kindStr = scanner.next();
                    Piece.PieceKind kind = Piece.PieceKind.fromString(kindStr);
                    
                    System.out.print("좌표 (예: d4): ");
                    String coord = scanner.next();
                    Move.Square square = Move.Square.fromNotation(coord);
                    
                    String pieceId = game.placePiece(player, kind, square);
                    System.out.println("배치 완료: " + pieceId);
                    
                } else if (choice == 2) {
                    // 이동
                    System.out.print("출발 좌표: ");
                    Move.Square from = Move.Square.fromNotation(scanner.next());
                    
                    List<Move.LegalMove> moves = game.getLegalMovesAt(from);
                    System.out.println("가능한 이동:");
                    for (int i = 0; i < moves.size(); i++) {
                        System.out.println(i + ": " + moves.get(i));
                    }
                    
                    System.out.print("선택: ");
                    int idx = scanner.nextInt();
                    
                    String captured = game.movePieceByLegalMove(moves.get(idx));
                    if (captured != null) {
                        System.out.println("캡처: " + captured);
                    }
                    
                } else if (choice == 3) {
                    // 계승
                    System.out.print("기물 ID: ");
                    String pid = scanner.next();
                    game.crownPiece(player, pid);
                    System.out.println("계승 완료");
                    
                } else if (choice == 4) {
                    game.endTurn();
                }
                
            } catch (Exception e) {
                System.out.println("오류: " + e.getMessage());
            }
        }
        
        System.out.println("\n게임 종료: " + game.checkVictory());
    }
    
    static void displayBoard(GameState game) {
        System.out.println("\n현재 보드:");
        for (Piece.PieceData p : game.getBoardPieces()) {
            String ownerStr = p.isNeutral() ? "중립" : (p.isWhite() ? "백" : "흑");
            System.out.printf("  %s %s at %s%s%s%n",
                ownerStr, p.kind, p.pos,
                p.isRoyal ? " [ROYAL]" : "",
                p.autoMove != null ? " [AUTO:" + p.autoMove + "]" : "");
        }
    }
}
```

---

## 다음 단계

- [Move Generation API](02-move-generation-api.md) - Chessembly 기반 합법 수 생성
- [Chessembly DSL API](03-chessembly-dsl-api.md) - DSL 직접 사용하기
- [Minecraft Integration API](04-minecraft-integration-api.md) - 고수준 API로 간편하게 사용하기
