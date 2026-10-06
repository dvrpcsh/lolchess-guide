/**
 * [역할] HEX 체스판(4x7) + 벤치(9칸)의 배치 상태와, 배치/이동/제거/성급/아이템 장착 규칙을 정의하는 순수 함수 모음.
 *   React 상태를 직접 바꾸지 않고 항상 새 객체를 반환하므로 App의 setState에 그대로 넘길 수 있다.
 *
 * [Data Flow]
 *   ChessBoard / ChampionCodex / ItemSelector 의 드래그 앤 드롭·클릭 이벤트
 *     --> App이 아래 함수 호출 --> 새 slots 상태 (+ 인벤토리로 되돌릴 아이템 목록)
 *     --> collectBoardUnitNames() / collectPlacedUnits() 로 배치 정보를 모아
 *     --> POST /api/v1/recommend 의 boardUnits / placedUnits 로 전송
 *
 * 상태 구조: { board: (Unit|null)[28], bench: (Unit|null)[9] }
 *   Unit = { championId, starLevel: 1~3, items: [itemName|null, itemName|null, itemName|null] }
 *     items의 각 칸은 재료 아이템 또는 완성 아이템 이름. 재료 2개가 모이면 recipeBook으로 완성 아이템 1칸으로 합성된다.
 *   Loadout = { slots, itemCounts }  체스판 배치 + 장착하지 않은 인벤토리 아이템 수량 (App이 하나의 상태로 관리)
 *   board 인덱스 = row * 7 + col  (row 0 = 맨 윗줄)
 *   기물을 옮기거나 자리를 바꿀 때 Unit 객체째 이동하므로 성급과 장착 아이템이 함께 따라간다.
 */
export const BOARD_ROWS = 4
export const BOARD_COLS = 7
export const BENCH_SIZE = 9
export const ITEM_SLOTS = 3
export const MAX_STAR = 3
export const MAX_ITEM_COUNT = 9

export const EMPTY_SLOTS = {
  board: Array(BOARD_ROWS * BOARD_COLS).fill(null),
  bench: Array(BENCH_SIZE).fill(null),
}

// 드래그 데이터 MIME 타입. dragover 단계에서는 데이터 내용을 읽을 수 없어 출처를 타입으로 구분한다.
export const DRAG_TYPE_CODEX = 'application/x-tft-codex' // 도감에서 새 기물 꺼내기 (copy)
export const DRAG_TYPE_PLACED = 'application/x-tft-placed' // 이미 놓인 기물 옮기기 (move)
export const DRAG_TYPE_ITEM = 'application/x-tft-item' // 인벤토리 재료 아이템을 기물에 장착 (copy)
export const DRAG_TYPE_COMPLETED_ITEM = 'application/x-tft-completed-item' // 완성 아이템을 기물에 바로 장착 (copy)

export function createUnit(championId) {
  return { championId, starLevel: 1, items: Array(ITEM_SLOTS).fill(null) }
}

export function getUnit(slots, { area, index }) {
  return slots[area][index]
}

/**
 * 도감의 챔피언을 target 칸에 새 기물(1성, 아이템 없음)로 놓는다.
 * 이미 기물이 있으면 교체하고, 교체된 기물의 장착 아이템을 returnedItems로 돌려준다.
 */
export function placeChampion(slots, target, championId) {
  const replaced = getUnit(slots, target)
  return { slots: withSlot(slots, target, createUnit(championId)), returnedItems: equippedItems(replaced) }
}

/** from 칸의 기물을 to 칸으로 옮긴다. to 칸에 기물이 있으면 성급·아이템째 서로 자리를 바꾼다. */
export function moveUnit(slots, from, to) {
  if (from.area === to.area && from.index === to.index) {
    return slots
  }
  const moving = getUnit(slots, from)
  const replaced = getUnit(slots, to)
  return withSlot(withSlot(slots, to, moving), from, replaced)
}

/** at 칸의 기물을 제거하고, 장착되어 있던 아이템을 returnedItems로 돌려준다. */
export function removeUnit(slots, at) {
  return { slots: withSlot(slots, at, null), returnedItems: equippedItems(getUnit(slots, at)) }
}

/** 벤치의 첫 빈 칸에 새 기물을 놓는다. 벤치가 가득 차면 상태를 그대로 반환한다. */
export function addToBench(slots, championId) {
  const index = slots.bench.indexOf(null)
  return index === -1 ? slots : withSlot(slots, { area: 'bench', index }, createUnit(championId))
}

/** 성급을 1 -> 2 -> 3 -> 1 순서로 바꾼다. */
export function cycleStarLevel(slots, at) {
  const unit = getUnit(slots, at)
  if (!unit) return slots
  return withSlot(slots, at, { ...unit, starLevel: (unit.starLevel % MAX_STAR) + 1 })
}

/** 기물에 빈 아이템 슬롯이 있는지 (드롭 허용 여부 판단용) */
export function hasEmptyItemSlot(unit) {
  return Boolean(unit) && unit.items.includes(null)
}

/**
 * 드래그 중인 아이템을 이 기물 위에 놓을 수 있는지 (dragover 단계 판단용, 아이템 이름은 아직 알 수 없음)
 * - 완성 아이템: 빈 슬롯이 있어야 함
 * - 재료 아이템: 빈 슬롯이 있거나, 합성 대기 중인 재료가 있으면(슬롯이 꽉 차 있어도 합성으로 장착 가능) 허용
 */
export function canAcceptItem(unit, isCompletedDrag, recipeBook) {
  if (!unit) return false
  if (hasEmptyItemSlot(unit)) return true
  return !isCompletedDrag && unit.items.some((item) => item && !recipeBook.isCompleted(item))
}

/**
 * 기물에 아이템을 장착한다.
 * - 재료 아이템: 슬롯에 조합 가능한 재료가 있으면 그 칸을 완성 아이템으로 합성 (재료 2칸 -> 완성 1칸),
 *               없으면 첫 빈 슬롯에 재료로 장착
 * - 완성 아이템: 첫 빈 슬롯에 그대로 장착
 * 기물당 최대 3칸이므로 완성 아이템은 최대 3개까지 장착된다.
 * @returns {{ slots, equipped: boolean, synthesized: string|null }} 장착 불가면 equipped = false
 */
export function equipItem(slots, at, itemName, recipeBook) {
  const unit = getUnit(slots, at)
  if (!unit) {
    return { slots, equipped: false, synthesized: null }
  }
  const items = [...unit.items]

  if (!recipeBook.isCompleted(itemName)) {
    const partnerIndex = items.findIndex(
      (item) => item && !recipeBook.isCompleted(item) && recipeBook.combine(item, itemName),
    )
    if (partnerIndex !== -1) {
      const synthesized = recipeBook.combine(items[partnerIndex], itemName)
      items[partnerIndex] = synthesized
      return { slots: withSlot(slots, at, { ...unit, items }), equipped: true, synthesized }
    }
  }

  const emptyIndex = items.indexOf(null)
  if (emptyIndex === -1) {
    return { slots, equipped: false, synthesized: null }
  }
  items[emptyIndex] = itemName
  return { slots: withSlot(slots, at, { ...unit, items }), equipped: true, synthesized: null }
}

/**
 * 기물의 itemIndex 슬롯 아이템을 해제한다. 뒤의 아이템을 앞으로 당겨 빈 칸이 항상 뒤에 오게 한다.
 * 완성 아이템은 조합에 들어간 재료 2개로 분해되어 returnedItems로 돌아간다.
 * @returns {{ slots, returnedItems: string[] }}
 */
export function unequipItem(slots, at, itemIndex, recipeBook) {
  const unit = getUnit(slots, at)
  const removed = unit?.items[itemIndex] ?? null
  if (!removed) {
    return { slots, returnedItems: [] }
  }
  const remaining = unit.items.filter((item, i) => item && i !== itemIndex)
  const items = [...remaining, ...Array(ITEM_SLOTS - remaining.length).fill(null)]
  return { slots: withSlot(slots, at, { ...unit, items }), returnedItems: recipeBook.toComponents([removed]) }
}

/** 체스판 + 벤치에 놓인 챔피언의 이름 목록 (중복 제거, 추천 API의 boardUnits 용) */
export function collectBoardUnitNames(slots, championsById) {
  return [...new Set(collectPlacedUnits(slots, championsById).map((unit) => unit.name))]
}

/** 추천 API의 placedUnits 용: [{ name, starLevel, items }] (빈 아이템 슬롯 제외) */
export function collectPlacedUnits(slots, championsById) {
  return [...slots.board, ...slots.bench]
    .filter(Boolean)
    .map((unit) => ({
      name: championsById.get(unit.championId)?.name,
      starLevel: unit.starLevel,
      items: equippedItems(unit),
    }))
    .filter((unit) => unit.name)
}

export function countPlaced(slots, area) {
  return slots[area].filter(Boolean).length
}

/** 판세 초기화: 모든 기물·성급·장착 아이템과 인벤토리를 비운 빈 Loadout을 반환한다. */
export function resetLoadout() {
  return { slots: EMPTY_SLOTS, itemCounts: {} }
}

/** 체스판·벤치·인벤토리가 모두 비어 있는지 (판세 초기화 버튼 활성화 판단용) */
export function isLoadoutEmpty({ slots, itemCounts }) {
  return countPlaced(slots, 'board') === 0 && countPlaced(slots, 'bench') === 0 && Object.keys(itemCounts).length === 0
}

/**
 * 아이템을 at 칸 기물에 장착한다. (재료는 조합 가능한 재료가 있으면 자동 합성)
 * - 재료 아이템이 인벤토리에 1개 이상 있으면: 1개를 꺼내 장착 (인벤토리 -1, 장착 +1)
 * - 재료 아이템이 인벤토리에 0개이거나 완성 아이템이면: 그 자리에서 획득해 바로 장착한 것으로 처리 (인벤토리 변화 없음)
 * 어느 경우든 보유 아이템 총량(인벤토리 + 장착)은 "실제로 가진 개수"와 같게 유지되어 추천 점수가 이중 계산되지 않는다.
 * 빈 칸이거나 장착할 슬롯이 없는 기물이면 그대로 반환한다.
 */
export function equipFromInventory(loadout, at, itemName, recipeBook) {
  const { slots: nextSlots, equipped } = equipItem(loadout.slots, at, itemName, recipeBook)
  if (!equipped) return loadout
  const hasInInventory = !recipeBook.isCompleted(itemName) && (loadout.itemCounts[itemName] ?? 0) > 0
  return {
    slots: nextSlots,
    itemCounts: hasInInventory ? takeItemFromInventory(loadout.itemCounts, itemName) : loadout.itemCounts,
  }
}

/** 인벤토리 아이템 수량을 delta만큼 조절한다 (0~MAX_ITEM_COUNT). 0개가 되면 키를 제거한다. */
export function adjustItemCount(itemCounts, itemName, delta) {
  const nextCount = Math.min(MAX_ITEM_COUNT, Math.max(0, (itemCounts[itemName] ?? 0) + delta))
  const next = { ...itemCounts }
  if (nextCount === 0) {
    delete next[itemName]
  } else {
    next[itemName] = nextCount
  }
  return next
}

/**
 * 인벤토리 수량 객체에 아이템들을 1개씩 더한다.
 * 인벤토리는 재료 아이템만 관리하므로, 완성 아이템은 recipeBook으로 재료 2개로 분해해 더한다.
 */
export function addItemsToInventory(itemCounts, itemNames, recipeBook) {
  const components = recipeBook.toComponents(itemNames)
  if (components.length === 0) return itemCounts
  const next = { ...itemCounts }
  components.forEach((name) => {
    next[name] = (next[name] ?? 0) + 1
  })
  return next
}

/** 인벤토리에서 아이템 1개를 뺀다. 0개가 되면 키를 제거한다. */
export function takeItemFromInventory(itemCounts, itemName) {
  const next = { ...itemCounts }
  if ((next[itemName] ?? 0) <= 1) {
    delete next[itemName]
  } else {
    next[itemName] -= 1
  }
  return next
}

function equippedItems(unit) {
  return unit ? unit.items.filter(Boolean) : []
}

function withSlot(slots, { area, index }, value) {
  const next = [...slots[area]]
  next[index] = value
  return { ...slots, [area]: next }
}
