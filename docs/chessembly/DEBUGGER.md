# Chessembly 디버거 사용 가이드

## 개요

Chessembly 인터프리터에 내장된 디버거를 사용하면 실행되는 모든 토큰을 추적할 수 있습니다.

## 활성화 방법

### Java 코드에서 활성화

```java
Interpreter interpreter = new Interpreter();
interpreter.setDebug(true);  // 디버그 활성화

interpreter.parse(script);
List<AST.Activation> activations = interpreter.execute(board);
// 실행 중 표준 출력(System.out)에 상세 로그가 출력됩니다.
```

## 디버그 출력 예시

디버그 모드가 활성화되면 기물을 클릭할 때마다 콘솔에 상세한 실행 로그가 출력됩니다:

```
[Chessembly] Executing script for rook at (3, 3)
[Chessembly] Total tokens: 28
  [PC:0] Token: TakeMove(1, 0) | Anchor: (0, 0) | LastValue: true
    → Activation: (1, 0) TakeMove
  [PC:1] Token: Repeat(1) | Anchor: (1, 0) | LastValue: true
  [PC:2] Token: TakeMove(1, 0) | Anchor: (1, 0) | LastValue: true
    → Activation: (2, 0) TakeMove
  [PC:3] Token: Repeat(1) | Anchor: (2, 0) | LastValue: true
  [PC:4] Token: TakeMove(1, 0) | Anchor: (2, 0) | LastValue: true
    → Activation: (3, 0) TakeMove
  ...
```

## 출력 정보 설명

각 로그 라인은 다음 정보를 포함합니다:

- **PC (Program Counter)**: 현재 실행 중인 토큰의 인덱스
- **Token**: 실행 중인 토큰의 종류와 매개변수
  - `TakeMove(dx, dy)`: 이동/잡기 행마
  - `Move(dx, dy)`: 이동만
  - `Take(dx, dy)`: 잡기만
  - `Catch(dx, dy)`: 제자리에서 잡기
  - `Shift(dx, dy)`: 자리 바꾸기
  - `Jump(dx, dy)`: take 후 점프
  - `Repeat(n)`: 반복
  - `Observe(dx, dy)`: 관찰 (조건)
  - `While`: do-while 루프
  - `Summon(kind)`: 기물 소환 (수식어)
  - `Auto(dx, dy)`: 자동 이동 설정 (수식어)
  - `AutoShift(dx, dy)`: 자동 shift 설정 (수식어)
  - `HistoryMoved(kind)`: 기물 이동 이력 조건
  - `HistoryExists(coords)`: 특정 이동 존재 조건
  - 등등...
- **Anchor**: 현재 앵커 위치 (누적 오프셋)
- **LastValue**: 마지막 실행 결과 (true/false)

### Activation 출력

`→ Activation` 라인은 실제로 이동 가능한 칸이 추가될 때 출력됩니다:

- **좌표**: `(dx, dy)` - 기물 위치로부터의 오프셋
- **타입**: `TakeMove`, `Move`, `Take`, `Catch`, `Shift`, `Jump`

## 사용 예시

### 1. 룩의 행마법 디버깅

```
[Chessembly] Executing script for rook at (4, 4)
[Chessembly] Total tokens: 28
  [PC:0] Token: TakeMove(1, 0) | Anchor: (0, 0) | LastValue: true
    → Activation: (1, 0) TakeMove
  [PC:1] Token: Repeat(1) | Anchor: (1, 0) | LastValue: true
  [PC:2] Token: TakeMove(1, 0) | Anchor: (1, 0) | LastValue: true
    → Activation: (2, 0) TakeMove
  [PC:3] Token: Repeat(1) | Anchor: (2, 0) | LastValue: true
  [PC:4] Token: TakeMove(1, 0) | Anchor: (2, 0) | LastValue: true
    → Activation: (3, 0) TakeMove
  [PC:5] Token: Repeat(1) | Anchor: (3, 0) | LastValue: true
  [PC:6] Token: TakeMove(1, 0) | Anchor: (3, 0) | LastValue: false
  [PC:7] Token: Repeat(1) | Anchor: (3, 0) | LastValue: false
  [PC:8] Token: Semicolon | Anchor: (3, 0) | LastValue: false
```

이 출력은:
- 룩이 오른쪽으로 3칸 이동 가능함
- 4번째 칸은 보드 밖이거나 막혀서 `LastValue: false`
- `Semicolon`에서 체인 종료

### 2. 나이트의 행마법 디버깅

```
[Chessembly] Executing script for knight at (1, 0)
[Chessembly] Total tokens: 16
  [PC:0] Token: TakeMove(1, 2) | Anchor: (0, 0) | LastValue: true
    → Activation: (1, 2) TakeMove
  [PC:1] Token: Semicolon | Anchor: (1, 2) | LastValue: true
  [PC:2] Token: TakeMove(2, 1) | Anchor: (0, 0) | LastValue: true
    → Activation: (2, 1) TakeMove
  [PC:3] Token: Semicolon | Anchor: (2, 1) | LastValue: true
  ...
```

나이트는 각 L자 이동이 별도의 체인으로 처리됩니다.

### 3. 조건부 행마법 디버깅

```
[Chessembly] Executing script for pawn at (4, 1)
  [PC:0] Token: Observe(0, 1) | Anchor: (0, 0) | LastValue: true
  [PC:1] Token: Move(0, 1) | Anchor: (0, 0) | LastValue: true
    → Activation: (0, 1) Move
  [PC:2] Token: Observe(0, 1) | Anchor: (0, 1) | LastValue: false
  [PC:3] Token: Move(0, 1) | Anchor: (0, 1) | LastValue: false
  [PC:4] Token: Semicolon | Anchor: (0, 1) | LastValue: false
```

`Observe`가 false를 반환하면 이후 행마가 실행되지 않습니다.

## 디버깅 팁

1. **특정 기물 분석**: 기물을 클릭하면 해당 기물의 행마법만 실행됩니다
2. **조건 확인**: `LastValue`를 보고 조건이 제대로 평가되는지 확인
3. **앵커 추적**: `Anchor` 값을 보고 누적 오프셋이 올바른지 확인
4. **활성화 검증**: 예상한 칸에 `Activation`이 추가되는지 확인
5. **성능 측정**: 디버그 모드는 성능에 영향을 줄 수 있으므로 필요할 때만 활성화

## Java 코드에서 사용

```java
Interpreter interpreter = new Interpreter();
interpreter.setDebug(true);  // 디버그 활성화
interpreter.parse(script);

BuiltinOps.BoardState board = new BuiltinOps.BoardState(8, 8, 3, 3, "rook", true);
List<AST.Activation> activations = interpreter.execute(board);
```

## 출력 대상

디버그 모드의 로그는 `System.out.printf()`를 통해 표준 출력에 기록됩니다.

## 문제 해결

### 디버그 출력이 보이지 않는 경우

1. `interpreter.setDebug(true)`가 `execute()` 호출 전에 실행되었는지 확인
2. 표준 출력이 리다이렉트되지 않았는지 확인
3. 테스트 프레임워크의 출력 캡처 설정 확인

### 너무 많은 로그가 출력되는 경우

1. 복잡한 기물(Queen, Amazon 등)은 많은 토큰을 실행합니다
2. 필요한 경우에만 디버그 모드를 활성화하세요
