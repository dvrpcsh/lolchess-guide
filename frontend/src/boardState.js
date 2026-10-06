/**
 * [역할] HEX 체스판(4x7) + 벤치(9칸)의 배치 상태와, 배치/이동/제거 규칙을 정의하는 순수 함수 모음.
 *   React 상태를 직접 바꾸지 않고 항상 새 객체를 반환하므로 App의 setState에 그대로 넘길 수 있다.
 *
 * [Data Flow]
 *   ChessBoard / ChampionCodex 의 드래그 앤 드롭·클릭 이벤트
 *     --> App이 placeChampion / moveUnit / removeUnit / addToBench 호출 --> 새 slots 상태
 *     --> collectBoardUnitNames() 로 배치된 챔피언 이름을 모아
 *     --> POST /api/v1/recommend 의 boardUnits 로 전송
 *
 * 상태 구조: { board: (championId|null)[28], bench: (championId|null)[9] }
 *   board 인덱스 = row * 7 + col  (row 0 = 맨 윗줄)
 */
export const BOARD_ROWS = 4
export const BOARD_COLS = 7
export const BENCH_SIZE = 9

export const EMPTY_SLOTS = {
  board: Array(BOARD_ROWS * BOARD_COLS).fill(null),
  bench: Array(BENCH_SIZE).fill(null),
}

// 드래그 데이터 MIME 타입. dragover 단계에서는 데이터 내용을 읽을 수 없어 출처를 타입으로 구분한다.
export const DRAG_TYPE_CODEX = 'application/x-tft-codex' // 도감에서 새 기물 꺼내기 (copy)
export const DRAG_TYPE_PLACED = 'application/x-tft-placed' // 이미 놓인 기물 옮기기 (move)

/** 도감의 챔피언을 target 칸에 놓는다. 이미 기물이 있으면 교체한다. */
export function placeChampion(slots, target, championId) {
  return withSlot(slots, target, championId)
}

/** from 칸의 기물을 to 칸으로 옮긴다. to 칸에 기물이 있으면 서로 자리를 바꾼다. */
export function moveUnit(slots, from, to) {
  if (from.area === to.area && from.index === to.index) {
    return slots
  }
  const moving = slots[from.area][from.index]
  const replaced = slots[to.area][to.index]
  return withSlot(withSlot(slots, to, moving), from, replaced)
}

/** at 칸의 기물을 제거한다. */
export function removeUnit(slots, at) {
  return withSlot(slots, at, null)
}

/** 벤치의 첫 빈 칸에 챔피언을 놓는다. 벤치가 가득 차면 상태를 그대로 반환한다. */
export function addToBench(slots, championId) {
  const index = slots.bench.indexOf(null)
  return index === -1 ? slots : withSlot(slots, { area: 'bench', index }, championId)
}

/** 체스판 + 벤치에 놓인 챔피언의 이름 목록 (중복 제거, 추천 API의 boardUnits 용) */
export function collectBoardUnitNames(slots, championsById) {
  const names = [...slots.board, ...slots.bench]
    .filter(Boolean)
    .map((id) => championsById.get(id)?.name)
    .filter(Boolean)
  return [...new Set(names)]
}

export function countPlaced(slots, area) {
  return slots[area].filter(Boolean).length
}

function withSlot(slots, { area, index }, value) {
  const next = [...slots[area]]
  next[index] = value
  return { ...slots, [area]: next }
}
