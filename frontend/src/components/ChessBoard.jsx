import { useRef, useState } from 'react'
import ChampionIcon from './ChampionIcon'
import {
  BOARD_COLS,
  BOARD_ROWS,
  DRAG_TYPE_CODEX,
  DRAG_TYPE_PLACED,
  countPlaced,
} from '../boardState'

/**
 * [역할] TFT 전장 규격의 4x7 육각형(HEX) 체스판과 9칸 벤치. 기물 배치를 시각적으로 편집하는 컴포넌트.
 *
 * [Data Flow]
 *   App(slots 상태 + championsById) --props--> 각 칸에 챔피언 초상화/코스트 테두리 렌더링
 *   - 도감(ChampionCodex)에서 드래그 --> 칸에 drop --> onPlace(target, championId)
 *   - 놓인 기물을 다른 칸으로 드래그 --> drop --> onMove(from, to)  (기물이 있으면 자리 교환)
 *   - 놓인 기물을 판 밖으로 드래그 아웃 / 우클릭 --> onRemove(at)
 *   --> App이 slots 갱신 --> 배치된 챔피언 이름이 추천 API의 boardUnits로 자동 전송
 *
 * 칸 위치 표기: { area: 'board' | 'bench', index }
 */
export default function ChessBoard({ slots, championsById, onPlace, onMove, onRemove }) {
  // 드래그 중인 기물이 올라가 있는 칸 (하이라이트용) - 'board-3', 'bench-0' 형태
  const [hoverKey, setHoverKey] = useState(null)
  // 놓인 기물을 드래그하는 동안, 그 드래그가 체스판/벤치 칸에 drop 되었는지 기록
  // (dragend의 dropEffect 값은 자리 교환 직후 등에서 신뢰할 수 없어 직접 추적한다)
  const placedDropHandledRef = useRef(false)

  const handleDragOver = (event, key) => {
    const types = event.dataTransfer.types
    if (!types.includes(DRAG_TYPE_CODEX) && !types.includes(DRAG_TYPE_PLACED)) {
      return // 이 앱의 기물이 아닌 드래그(파일 등)는 받지 않음
    }
    event.preventDefault()
    event.dataTransfer.dropEffect = types.includes(DRAG_TYPE_PLACED) ? 'move' : 'copy'
    setHoverKey(key)
  }

  const handleDrop = (event, target) => {
    event.preventDefault()
    setHoverKey(null)
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
    const championId = slots[area][index]
    const champion = championId ? championsById.get(championId) : null
    const key = `${area}-${index}`
    const at = { area, index }

    return (
      <div
        key={key}
        className={`${className} ${champion ? `is-occupied cost-${champion.cost}` : ''} ${hoverKey === key ? 'is-hover' : ''}`}
        onDragOver={(e) => handleDragOver(e, key)}
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
            title={`${champion.name} (${champion.cost}코스트) - 드래그로 이동, 판 밖으로 끌거나 우클릭하면 제거`}
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
            <ChampionIcon champion={champion} size={area === 'board' ? 70 : 48} />
            <span className="placed-name">{champion.name}</span>
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
        오른쪽 서랍의 <b>기물 도감</b>에서 챔피언을 끌어다 놓으세요. 기물을 판 밖으로 끌거나 우클릭하면 제거됩니다.
      </p>
    </section>
  )
}
