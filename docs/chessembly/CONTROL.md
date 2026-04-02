## 3.1 Procedure & flow
[2. Concepts] 까지의 표현식만을 사용하면 고정된 행마법을 모두 만들 수 있습니다.

[3. Controls] 에서는 보다 고차원적이고 유동적인 행마법을 만드는 도구를 소개합니다.

프로그래밍에 익숙하다면 쉽게 이해할 수 있을 것입니다.

`false`값 종료 규칙을 무시하거나 오히려 `false`를 이용하는 특별한 '제어식'들이 있습니다. 이 식들은 '식 연쇄'의 실행 흐름(어떤 식이 다음에 실행될지)을 직접 제어합니다.

### 연쇄 종료 규칙의 5가지 예외

다음 5가지 식은 직전 식이 `false`를 반환했더라도 식 연쇄를 종료시키지 않습니다.

1. `while`
2. `jmp(n)`
3. `jne(n)`
4. `not`
5. `label(n)`

### 1. `while`, `jmp(n)`, `jne(n)`: 점프 제어

이 식들은 `false`를 받아도 연쇄를 멈추는 대신, 항상 `true`를 반환하여 연쇄의 생명을 이어갑니다. 바로 이 식들이 행마법을 유연하게 만드는 초석이 됩니다.

- `jmp(n)` (JUMP): 직전 값이 `true`이면 `label(n)`으로 점프합니다. `false`이면 아무것도 안 합니다.
- `jne(n)` (JUMP if NOT): 직전 값이 `false`이면 `label(n)`으로 점프합니다. `true`이면 아무것도 안 합니다.
- `while`: 직전 값이 `true`이면 `do`로 점프합니다. `false`이면 아무것도 안 합니다.

> `jne`로 프로그래밍의 `if`를 만들 수 있습니다. 바로 앞의 식이 `false`면 점프하는 행동을 이용해서, 특정 구간 뒤로 점프하도록 한다면 그 사이의 식들은 `jne` 앞의 조건이 만족될 때만 실행됩니다. [4.1 Example: Bouncing-Bishop](https://www.notion.so/4-1-Example-Bouncing-Bishop-2ecd70bcd06c81cebadacbaf86008239?pvs=21) 에 이러한 사용법이 등장합니다.
> 

### 2. `not`: 값 반전

`not`은 논리를 제어하는 가장 간단한 식입니다. [3.3 Look around] 과 함께 유용하게 쓰일 수 있습니다.

- 직전 값이 `true`였다면, `false`를 반환합니다.
- 직전 값이 `false`였다면, `true`를 반환합니다.

### 3. `label(n)`: 투명한 식

`label(n)`은 `jmp`나 `jne`가 찾아올 수 있는 표지판 역할만 합니다. 실행 흐름에 아무런 영향을 주지 않습니다.

- 직전 값이 `true`였다면, `true`를 그대로 다음 식에 전달합니다.
- 직전 값이 `false`였다면, `false`를 그대로 다음 식에 전달합니다. (연쇄가 종료되어야 했다면, `label`을 지나도 여전히 종료됩니다.)

### 예외가 아닌 제어식: `do` 와 `repeat`

`do`와 `repeat`는 저 5가지 예외에 포함되지 않습니다. 즉, 이 식들은 일반 식처럼 직전 값이 `false`이면 식 연쇄를 종료시킵니다.

- **`do`:** `do` 앞에 `false`를 반환하는 식이 있다면, `do` 블록은 시작하지 않고 연쇄가 종료됩니다. (예: `enemy(0, 0) do ...` → 적이 없으면 `false`가 되어 `do` 실행 안 됨)
- **`repeat(n)`:** 튜토리얼의 룩(Rook)이 멈췄던 이유입니다.
    1. `take-move` (빈 칸) → `true` 반환
    2. `repeat(1)`이 `true`를 받고, 1칸 뒤로 점프 실행
    3. `take-move` (벽) → `false` 반환
    4. `repeat(1)`이 `false`를 받고, 일반 식이므로 식 연쇄를 종료.

## 3.2 States

체스판만 보고는 캐슬링이 가능한지 알 수 없는 경우도 있습니다. 따라서 게임을 중단했다가 이어나가기 위해서는 캐슬링 가능 여부에 대한 추가적인 정보가 필요합니다.

Chessembly는 단순한 행마 정의를 넘어, 게임의 "상태(State)"를 기억하고, 그 상태에 따라 행마를 바꾸는 강력한 기능을 제공합니다. 캐슬링 가능 여부도 하나의 State입니다.

예를 들어, "이 기물이 이번에 rook처럼 움직였다면, 다음 턴에는 bishop처럼 움직이게 하라"와 같은 규칙을 만들 수 있습니다.

상태를 관리하는 식은 두 종류로 나뉩니다.

1. **조건식:** 현재 상태를 읽고 `true` / `false`를 반환합니다. (`if-state`, `piece`)
2. **수식어(Modifier):** 이후에 활성화될 칸에 **특별한 액션을 부착**합니다. (`set-state`, `transition`)

---

### 1. 상태 조건식 (읽기)

이 식들은 관문 역할을 합니다. 뒤에 `not` 을 붙여 반전할 수도 있습니다.

- **`piece(piece_name)`**
    - 이 코드를 실행하는 **기물 자체의 종류**가 `piece_name` (예: `windmill-rook`)과 일치하면 `true`를 반환합니다.
    - `piece(rook)`이 `false`를 반환하면 그 즉시 연쇄가 종료되므로, 특정 기물 전용 행마를 정의할 때 사용합니다.
- **`if-state(key, n)`**
    - 게임에 저장된 전역 상태 `key`의 값이 `n`과 같으면 `true`를 반환합니다.
    - (만약 `key`가 한 번도 설정된 적 없다면, 기본값 0으로 간주합니다.)

---

### 2. 상태 수식어 (쓰기)

이 식들은 튜토리얼의 `move`나 `take-move`와 약간 다르게 작동합니다.

- `move`는 칸을 활성화하는 식입니다.
- `set-state`나 `transition`은 **"다음에 실행될 `move`/`take` 등이 활성화할 칸에 액션 태그를 미리 붙여두는"** 식입니다.

이 식들 자체는 (실패하지 않는 한) 항상 `true`를 반환하여 연쇄를 계속 진행시킵니다.

- **`transition(piece_name)`**
    - 이 식 **이후에** 활성화되는 모든 칸(🔵)에 "클릭 시 `piece_name`으로 기물 교체(승급)"하는 액션 태그를 부착합니다.
- **`set-state(key, n)`**
    - 이 식 **이후에** 활성화되는 모든 칸(🔵)에 "클릭 시 전역 변수 `key`의 값을 `n`으로 설정"하는 액션 태그를 부착합니다.
- **`set-state` (단독 사용)**
    - `set-state`를 인자 없이 단독으로 사용하면, **이전에 설정된 마지막 액션 태그를 하나 지웁니다.**
    - 이 식 이후에 활성화되는 칸은 아무 상태도 변경하지 않습니다.

---

### 예시 분석: Windmill (풍차)

https://youtube.com/shorts/JmcYn3n2MzQ?si=gbXK8M6MSiX6RbKE

Windmill 예시는 이 상태 관리 기능이 어떻게 작동하는지 보여주는 예제입니다. 이 기물은 움직일 때마다 룩과 비숍의 행마를 번갈아 수행합니다.

### 버전 1: `transition`과 `piece` 사용

이 버전은 기물 자체의 종류(`windmill-bishop`, `windmill-rook`)를 바꿔버립니다.

```less
# 1번 연쇄: 만약 내 기물이 '비숍'이라면...;

piece(windmill-bishop) transition(windmill-rook)
    { take-move(1, 1) repeat(1) }
    { take-move(-1, 1) repeat(1) }
    { take-move(1, -1) repeat(1) }
    { take-move(-1, -1) repeat(1) };

# 2번 연쇄: 만약 내 기물이 '룩'이라면...;

piece(windmill-rook) transition(windmill-bishop)
    { take-move(1, 0) repeat(1) }
    { take-move(0, 1) repeat(1) }
    { take-move(-1, 0) repeat(1) }
    { take-move(0, -1) repeat(1) };
```

**`windmill-bishop` 턴일 때:**

1. **1번 연쇄:** `piece(windmill-bishop)`이 ✅ `true`를 반환합니다.
2. `transition(windmill-rook)`이 실행되어, "이후 활성화될 칸에 '룩으로 변신' 태그 부착"이 설정됩니다.
3. 4개의 `{ }` 블록이 실행되어 **비숍 행마(대각선)** 칸 🔵들을 활성화합니다. 이 모든 칸에는 '룩으로 변신' 태그가 붙습니다. `piece(windmill-bishop) transition(windmill-rook)`을 4번 적고 각각 적을 수도 있지만, `{ }` 블록을 사용해서 한번에 모든 행마에 액션을 부착할 수 있습니다.
4. **2번 연쇄:** `piece(windmill-rook)`이 ❌ `false`를 반환하여 연쇄가 바로 종료됩니다. (룩 행마는 활성화되지 않음)

➡️ **결과:** 사용자는 비숍 행마만 보게 됩니다. 칸을 클릭하면 기물은 룩으로 변신합니다. 다음 턴에는 2번 연쇄만 실행됩니다.

### 버전 2: `if-state`와 `set-state` 사용

이 버전은 기물 종류는 그대로 두고, `mode`라는 전역 변수(0 또는 1)를 토글(Toggle)합니다.

```less
# 1번 연쇄: 만약 mode가 0이라면...;

if-state(mode, 0) set-state(mode, 1)
    { take-move(1, 1) repeat(1) }
    { take-move(1, -1) repeat(1) }
    { take-move(-1, 1) repeat(1) }
    { take-move(-1, -1) repeat(1) };

# 2번 연쇄: 만약 mode가 1이라면...;

if-state(mode, 1) set-state(mode, 0)
    { take-move(1, 0) repeat(1) }
    { take-move(-1, 0) repeat(1) }
    { take-move(0, 1) repeat(1) }
    { take-move(0, -1) repeat(1) };
```

**`mode`가 0일 때 (기본값):**

1. **1번 연쇄:** `if-state(mode, 0)`이 ✅ `true`를 반환합니다.
2. `set-state(mode, 1)`이 실행되어, "이후 활성화될 칸에 🏷️'mode=1' 태그 부착"이 설정됩니다.
3. 4개의 `{ }` 블록이 실행되어 **비숍 행마(대각선)** 칸 🔵들을 활성화합니다. 이 모든 칸에는 🏷️'mode=1' 태그가 붙습니다.
4. **2번 연쇄:** `if-state(mode, 1)`이 ❌ `false`를 반환하여 연쇄가 즉시 종료됩니다.

➡️ **결과:** 사용자는 비숍 행마만 보게 됩니다. 칸을 클릭하면 전역 변수 `mode`가 1로 바뀝니다. 다음 턴에는 2번 연쇄만 실행됩니다.

## 3.3 Look around

지금까지 배운 행마식(`move`, `take`)은 칸을 활성화하는 것이 주 목적이었습니다.

하지만 때로는 칸을 활성화하기 **전에**, 그곳의 상태를 **미리 확인만** 하고 싶을 때가 있습니다. 예를 들어, "만약 (1, 0) 위치가 **비어있다면**, (2, 1)로 점프하라"와 같은 규칙입니다.

이때 조건식(Conditional Expression)을 사용합니다. 조건식은 기준 위치를 옮기거나(peek 제외) 칸을 활성화하지 않고, 단지 보드를 관찰한 뒤 ✅ `true` 또는 ❌ `false`만 반환합니다.

행마법이 꺾이는 경우, 행마법이 언제 어디로 꺾여야 할 지 판단해야 한다면 필수적으로 사용되어야 합니다. [4.1 Example: Bouncing-Bishop] 에서 이 부분을 집중적으로 사용합니다.

### 1. `observe` vs `peek` **(엿보기)**

두 식 모두 해당 칸이 **비어있는지** 확인합니다. 비어있으면 ✅ `true`, 기물이 있으면 ❌ `false`를 반환합니다.

- **`observe(dx, dy)`** (관찰):
    - (dx, dy) 위치를 확인하고 `true`/`false`를 반환합니다.
    - '기준 위치'는 움직이지 않습니다.
- **`peek(dx, dy)` (이동하며 엿보기):**
    - (dx, dy) 위치를 확인합니다.
    - 만약 비어있어서 `true`를 반환한다면, '기준 위치'도 **(dx, dy)만큼 이동합니다.**
    - (기물에 막힌 경우 `false`를 반환하되, '기준 위치'는 **(dx, dy)만큼 이동합니다.**)
    - (벽에 막혀서 `false`를 반환하면 '기준 위치'는 움직이지 않습니다.)

### 2. 기물/상태 확인

- **`enemy(dx, dy)`:** (dx, dy) 위치에 **적** 기물이 있으면 `true`를 반환합니다.
- **`friendly(dx, dy)`:** (dx, dy) 위치에 **아군** 기물이 있으면 `true`를 반환합니다.
- **`piece-on(piece, dx, dy)`:** (dx, dy) 위치에 `piece` (예: 'rook') 기물이 있으면 `true`를 반환합니다.
- **`danger(dx, dy)`:** (dx, dy) 위치가 현재 **적에게 공격받고 있으면** `true`를 반환합니다.
- **`check`:** (위치와 상관없이) 현재 **아군이 체크 상태**이면 `true`를 반환합니다.

### 3. 경계 확인 (Board Bounds)

이 식들은 `Bouncing-Bishop` 예시의 핵심입니다.

- **`bound(dx, dy)`:** (dx, dy) 위치가 보드 밖이면(어느 방향이든) `true`를 반환합니다.
- **`edge(dx, dy)`:** (dx, dy) 위치가 보드의 변을 벗어나면 `true`를 반환합니다.
- **`corner(dx, dy)`:** (dx, dy) 위치가 보드의 모서리를 벗어나면 `true`를 반환합니다.

세부 경계 확인:

edge와 corner는 더 구체적인 방향을 지정할 수 있습니다.

- **`edge-top(dx, dy)`:** (dx, dy) 위치가 보드의 윗(Top) 변을 벗어나면 `true`입니다.
- **`edge-bottom(dx, dy)`:** (dx, dy) 위치가 아랫(Bottom) 변을 벗어나면 `true`입니다.
- **`edge-left(dx, dy)`:** (dx, dy) 위치가 왼쪽(Left) 변을 벗어나면 `true`입니다.
- **`edge-right(dx, dy)`:** (dx, dy) 위치가 오른쪽(Right) 변을 벗어나면 `true`입니다.
- **`corner-top-left(dx, dy)`:** (dx, dy) 위치가 좌측 상단(Top-Left) 모서리를 벗어나면 `true`입니다.
- (기타 3방향 모서리): `corner-top-right`, `corner-bottom-left`, `corner-bottom-right`도 동일하게 작동합니다.

---

### 예시 분석: 장기의 '마' (막히는 나이트)

'조건부 탐색'의 가장 완벽한 예시는 장기의 '마(馬)'입니다. '마'는 나이트와 같지만, 경로가 막혀있으면 뛸 수 없습니다.

`[c2]`의 '마'가 `[e3]`(2, 1)로 뛰려면, `[d2]`(1, 0)가 비어있어야 합니다.

이때 `observe`를 '관문'처럼 사용합니다.

```less
observe(1, 0) take-move(2, 1);
observe(1, 0) take-move(2, -1);
observe(-1, 0) take-move(-2, 1);
observe(-1, 0) take-move(-2, -1);
observe(0, 1) take-move(1, 2);
observe(0, 1) take-move(-1, 2);
observe(0, -1) take-move(1, -2);
observe(0, -1) take-move(-1, -2);
```

Chessembly 실행 흐름

- 시나리오 1: `[d2]`가 비어있음
    1. `observe(1, 0)` 실행: `[d2]`가 비어있으므로 `true`를 반환합니다.
    2. 다음 식 `take-move(2, 1)`이 실행됩니다. `[e3]` 칸 🔵이 활성화됩니다.
- 시나리오 2: `[d2]`가 막혀있음 (아군/적)
    1. `observe(1, 0)` 실행: `[d2]`가 막혀있으므로 `false`를 반환합니다.
    2. 일반 식이 `false`를 반환했으므로 식 연쇄가 종료됩니다.
    3. `take-move(2, 1)` 식은 실행되지 않습니다.

**결과:** `[e3]` 칸은 활성화되지 않습니다. 마(馬)의 행마가 구현되었습니다.

## 3.4 JUMP

`jump` 식은 11월 25일 업데이트로 새로 생긴 행마식입니다.

`jump` 식은 행마식임에도 불구하고, 예시에서 다양한 식이 동원되기 때문에 3.4에 수록하였습니다.

> `jump`식은 `jmp`식 및 `jne`식과 다릅니다. [3.1 Procedure & flow] 에 등장하는 `jmp`식은 제어식이지만, `jump`식은 행마식입니다.
> 

`jump`식은 `take`식과 짝을 이룹니다. `jump`식이 후행하는 `take`식은 일반 `take`식과 다르게 동작합니다. `take`식이 실행된 직후에 `jump`식을 만난다면, `jump`식이 `true`를 내놓건 `false`를 내놓건, **마지막에 실행된 `take`식은 `take`행마법을 활성화하지 않습니다.**

> `jump`식 이전에 쓰인 `take`식은 `take-jump`식의 일부로도 볼 수 있습니다.
> 

> 앞에 `take` 식이 없는데 `jump`식이 있었다면, `false`를 내놓고 아무것도 하지 않습니다.
> 

이는 `take-jump`식을 따로 정의하는 대신, 뒤에 `jump`가 쓰인 `take`식을 `jump`식과 함께 묶어 `take-jump`식으로 만들기 위한 설계입니다. `take-jump`를 한번에 쓰는 것은 보드상에서 2개의 위치를 필요로 하기 때문에, 한 번에 4개의 좌표를 입력하기보다는 `take`와 `jump`가 좌표를 따로 지정하게 하기 위한 것입니다.

`jump` 행마법의 기준 위치 이동 방법과 `true`, `false` 반환 조건은 `move`식과 동일합니다.

### 예시 분석: Cannon

```cpp
do take(1, 0) enemy(0, 0) not while jump(1, 0) repeat(1);
do take(-1, 0) enemy(0, 0) not while jump(-1, 0) repeat(1);
do take(0, 1) enemy(0, 0) not while jump(0, 1) repeat(1);
do take(0, -1) enemy(0, 0) not while jump(0, -1) repeat(1);

do peek(1, 0) while friendly(0, 0) move(1, 0) repeat(1);
do peek(-1, 0) while friendly(0, 0) move(-1, 0) repeat(1);
do peek(0, 1) while friendly(0, 0) move(0, 1) repeat(1);
do peek(0, -1) while friendly(0, 0) move(0, -1) repeat(1);
```

실질적으로 분석할 것은 위의 4줄입니다. 아래의 4줄은 아군 기물을 뛰어넘는 식 연쇄로, [3.3 Look around] 까지의 내용을 바탕으로 직접 해석해보시길 권장드립니다.

- 위 4줄의 설명
1. `do … while` 문의 시작 지점인 `do` 식
2. 한 칸 앞에 `take`를 설치하고 (적이 없으면 `anchor`만 이동하고 `take` 행마는 설치되지 않음)
3. 그곳에 적이 있어서 `enemy(0, 0)`이 `true`면
4. `not`을 거쳐 `false`로 반전되고 `do ~ while` 루프를 탈출
5. 적이 없었다면 2를 다시 반복
6. `jump`식은 `take`식으로 지정된 위치를 `take`하면서 자기 칸으로 `jump`하는 행마를 대신 설치
7. 6을 반복

(1~7 을 동서남북으로 4번씩 실행)

## 3.5 Summon & Auto-move

### 1. `summon(kind, dx, dy)` (소환)

`summon`은 `set-state`나 `transition`처럼 **수식어(Modifier)** 역할을 합니다.

이 식 **이후에** 활성화되는 모든 칸(🔵)에 "클릭 시 `(dx, dy)` 위치에 `kind` 기물을 소환"하는 액션 태그를 부착합니다.

```less
# 이동하면서 (1, 0) 위치에 pawn을 소환
summon(pawn, 1, 0) take-move(0, 1);
```

소환된 기물은 현재 플레이어 소유의 기물로 배치됩니다.

### 2. `auto(dx, dy)` (자동 이동)

`auto`는 수식어 역할을 합니다.

이 식 **이후에** 활성화되는 모든 칸(🔵)에 "클릭 시 해당 기물에 `(dx, dy)` 방향 자동 이동(take-move 모드)을 설정"하는 액션 태그를 부착합니다.

자동 이동이 설정된 기물은 매 턴 종료 시 자동으로 해당 방향으로 `take-move` 동작을 수행합니다.

```less
# 이동하면, 이후 매 턴마다 (0, 1) 방향으로 자동 이동
auto(0, 1) take-move(1, 0);
```

### 3. `auto-shift(dx, dy)` (자동 자리 바꾸기)

`auto-shift`는 `auto`와 동일하지만, `take-move` 모드 대신 `shift` 모드로 자동 이동을 설정합니다.

```less
# 이동하면, 이후 매 턴마다 (1, 0) 방향으로 shift 자동 이동
auto-shift(1, 0) take-move(0, 1);
```

## 3.6 History

게임의 이동 기록(History)을 참조하는 조건식입니다. 캐슬링과 같이 "특정 기물이 이동한 적이 있는가"를 확인해야 하는 규칙에 사용됩니다.

### 1. `history-moved(kind)` (기물 이동 이력)

`history-moved`는 조건식입니다. 해당 종류(`kind`)의 기물이 게임 중 한 번이라도 이동한 적이 있으면 `true`를 반환합니다.

```less
# 킹이 이동한 적이 없을 때만 캐슬링 허용
history-moved(king) not
    move(2, 0);
```

### 2. `history-exists(fromX, fromY, toX, toY)` (특정 이동 존재 여부)

`history-exists`는 조건식입니다. 특정 좌표에서 특정 좌표로의 이동이 게임 기록에 존재하면 `true`를 반환합니다.

```less
# (4, 0)에서 (4, 1)로의 이동이 있었다면
history-exists(4, 0, 4, 1) take-move(0, 1);
```

> `history-moved`와 `history-exists`는 모두 **일반 식**이므로, `false`를 반환하면 식 연쇄가 종료됩니다. `not`을 뒤에 붙여 반전할 수 있습니다.
