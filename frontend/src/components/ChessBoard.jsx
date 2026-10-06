import { useRef, useState } from 'react'
import ChampionIcon from './ChampionIcon'
import {
  BOARD_COLS,
  BOARD_ROWS,
  DRAG_TYPE_CODEX,
  DRAG_TYPE_ITEM,
  DRAG_TYPE_PLACED,
  countPlaced,
  hasEmptyItemSlot,
} from '../utils/boardState'

const STAR_NAMES = { 1: '1성(동)', 2: '2성(은)', 3: '3성(금)' }

/**
 * [역할] TFT 전장 규격의 4x7 육각형(HEX) 체스판과 9칸 벤치. 기물 배치·성급·아이템 장착을 시각적으로 편집하는 컴포넌트.
 *
 * [Data Flow]
 *   App(slots 상태 + championsById + itemIconByName) --props--> 각 칸에 초상화/코스트 테두리/성급/아이템 슬롯 렌더링
 *   - 도감(ChampionCodex)에서 드래그 --> 칸에 drop --> onPlace(target, championId)
 *   - 놓인 기물을 다른 칸으로 드래그 --> drop --> onMove(from, to)  (기물이 있으면 성급·아이템째 자리 교환)
 *   - 놓인 기물을 판 밖으로 드래그 아웃 / 우클릭 --> onRemove(at)  (장착 아이템은 App이 인벤토리로 반환)
 *   - 성급(★) 클릭 --> onCycleStar(at)  1성 -> 2성 -> 3성 -> 1성
 *   - 인벤토리(ItemSelector) 아이템 드래그 --> 기물 위 drop --> onEquip(at, itemName)  (빈 슬롯이 있을 때만)
 *   - 장착 아이템 클릭 --> onUnequip(at, itemIndex)  (App이 인벤토리로 반환)
 *   - "판세 초기화" 버튼 --> onResetBoard()  (모든 기물·성급·장착 아이템·인벤토리 일괄 초기화)
 *   --> App이 slots / itemCounts 갱신 --> 이름·성급·장착 아이템이 추천 API로 자동 전송
 *
 * 칸 위치 표기: { area: 'board' | 'bench', index }
 */
export default function ChessBoard({
  slots, championsById, itemIconByName, onPlace, onMove, onRemove, onCycleStar, onEquip, onUnequip,
  canResetBoard, onResetBoard,
}) {
  // 드래그 중인 기물/아이템이 올라가 있는 칸 (하이라이트용) - 'board-3', 'bench-0' 형태
  const [hoverKey, setHoverKey] = useState(null)
  // 놓인 기물을 드래그하는 동안, 그 드래그가 체스판/벤치 칸에 drop 되었는지 기록
  // (dragend의 dropEffect 값은 자리 교환 직후 등에서 신뢰할 수 없어 직접 추적한다)
  const placedDropHandledRef = useRef(false)

  const handleDragOver = (event, key, unit) => {
    const types = event.dataTransfer.types
    if (types.includes(DRAG_TYPE_ITEM)) {
      if (!hasEmptyItemSlot(unit)) return // 빈 칸이거나 아이템 3개가 꽉 찬 기물에는 장착 불가
      event.preventDefault()
      event.dataTransfer.dropEffect = 'copy'
      setHoverKey(key)
      return
    }
    if (!types.includes(DRAG_TYPE_CODEX) && !types.includes(DRAG_TYPE_PLACED)) {
      return // 이 앱의 기물/아이템이 아닌 드래그(파일 등)는 받지 않음
    }
    event.preventDefault()
    event.dataTransfer.dropEffect = types.includes(DRAG_TYPE_PLACED) ? 'move' : 'copy'
    setHoverKey(key)
  }

  const handleDrop = (event, target) => {
    event.preventDefault()
    setHoverKey(null)
    const itemData = event.dataTransfer.getData(DRAG_TYPE_ITEM)
    if (itemData) {
      onEquip(target, JSON.parse(itemData).itemName)
      return
    }
    const codexData = event.dataTransfer.getData(DRAG_TYPE_CODEX)
    if (codexData) {
      onPlace(target, JSON.parse(codexData).championId)
      return
    }
    const placedData = event.dataTransfer.getData(DRAG_TYPE_PLACED)
    if (placedData) {
      placedDropHandledRef.current = true
      onMove(JSON.parse(placedData).from, target)
    }
  }

  const renderSlot = (area, index, className) => {
    const unit = slots[area][index]
    const champion = unit ? championsById.get(unit.championId) : null
    const key = `${area}-${index}`
    const at = { area, index }

    return (
      <div
        key={key}
        className={`${className} ${champion ? `is-occupied cost-${champion.cost}` : ''} ${hoverKey === key ? 'is-hover' : ''}`}
        onDragOver={(e) => handleDragOver(e, key, unit)}
        onDragLeave={() => setHoverKey((current) => (current === key ? null : current))}
        onDrop={(e) => handleDrop(e, at)}
        onContextMenu={(e) => {
          if (champion) {
            e.preventDefault()
            onRemove(at)
          }
        }}
      >
        {champion && (
          <div
            className="placed-unit"
            draggable
            title={`${champion.name} ${STAR_NAMES[unit.starLevel]} (${champion.cost}코스트) - 드래그로 이동, 판 밖으로 끌거나 우클릭하면 제거`}
            onDragStart={(e) => {
              placedDropHandledRef.current = false
              e.dataTransfer.setData(DRAG_TYPE_PLACED, JSON.stringify({ from: at }))
              e.dataTransfer.effectAllowed = 'move'
            }}
            onDragEnd={() => {
              setHoverKey(null)
              // 어느 칸에도 drop 되지 않았다면 판 밖으로 끌어낸 것으로 보고 제거
              if (!placedDropHandledRef.current) {
                onRemove(at)
              }
            }}
          >
            <ChampionIcon champion={champion} size={area === 'board' ? 82 : 56} />

            <button
              type="button"
              className={`unit-stars star-${unit.starLevel}`}
              onClick={() => onCycleStar(at)}
              aria-label={`${champion.name} 성급 ${STAR_NAMES[unit.starLevel]}, 클릭하여 변경`}
            >
              {'★'.repeat(unit.starLevel)}
            </button>

            <span className="placed-name">{champion.name}</span>

            <div className="unit-items">
              {unit.items.map((itemName, itemIndex) => (
                itemName ? (
                  <button
                    key={itemIndex}
                    type="button"
                    className="unit-item"
                    onClick={() => onUnequip(at, itemIndex)}
                    title={`${itemName} - 클릭하면 해제`}
                    aria-label={`${champion.name}의 ${itemName} 해제`}
                  >
                    {itemIconByName.get(itemName)
                      ? <img src={itemIconByName.get(itemName)} alt="" draggable={false} />
                      : itemName.slice(0, 1)}
                  </button>
                ) : (
                  <span key={itemIndex} className="unit-item is-empty" aria-hidden="true" />
                )
              ))}
            </div>
          </div>
        )}
      </div>
    )
  }

  const rows = Array.from({ length: BOARD_ROWS }, (_, row) => row)
  const cols = Array.from({ length: BOARD_COLS }, (_, col) => col)

  return (
    <section className="panel board-panel">
      <div className="board-header">
        <h2 className="panel-title">내 체스판</h2>
        <span className="board-count">
          전장 {countPlaced(slots, 'board')} · 벤치 {countPlaced(slots, 'bench')}
        </span>
        <button
          type="button"
          className="ghost-button reset-board-button"
          onClick={onResetBoard}
          disabled={!canResetBoard}
          title="체스판·벤치의 모든 기물, 성급, 장착 아이템과 인벤토리를 비웁니다"
        >
          판세 초기화 (Reset Board)
        </button>
      </div>

      <div className="hex-board" role="grid" aria-label="4x7 체스판">
        {rows.map((row) => (
          // 홀수 줄은 반 칸 오른쪽으로 밀어 육각형이 맞물리게 배치
          <div key={row} role="row" className={`hex-row ${row % 2 === 1 ? 'is-offset' : ''}`}>
            {cols.map((col) => renderSlot('board', row * BOARD_COLS + col, 'hex-tile'))}
          </div>
        ))}
      </div>

      <div className="bench" aria-label="벤치 9칸">
        {slots.bench.map((_, index) => renderSlot('bench', index, 'bench-slot'))}
      </div>

      <p className="board-hint">
        <b>기물 도감</b>에서 챔피언을 끌어다 놓고, <b>아이템</b> 탭의 아이템을 기물 위로 끌어 바로 장착하세요.
        ★을 누르면 성급이 바뀌고, 장착 아이템을 누르면 해제됩니다. 기물을 판 밖으로 끌거나 우클릭하면 제거됩니다.
      </p>
    </section>
  )
}
