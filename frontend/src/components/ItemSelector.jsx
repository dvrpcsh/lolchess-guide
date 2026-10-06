import { DRAG_TYPE_ITEM, MAX_ITEM_COUNT } from '../utils/boardState'

/**
 * [역할] 장착하지 않은 보유 아이템(인벤토리)의 수량을 조절하고, 보유 중인 아이템을 체스판 기물로 끌어 장착하게 하는 컴포넌트.
 *
 * [Data Flow]
 *   App(GET /api/v1/items 의 재료 아이템 목록 + itemCounts 상태) --props--> 아이콘·수량 렌더링
 *   - 사용자 +/- 클릭 --> onAdjust(아이템 이름, +1 | -1) --> App이 이전 상태 기준으로 수량 갱신
 *   - 수량이 1개 이상인 아이템 드래그 --> dataTransfer(DRAG_TYPE_ITEM, { itemName })
 *       --> ChessBoard 기물 위 drop --> App이 기물 슬롯에 장착하고 인벤토리에서 1개 차감
 *   --> RecommendationList가 변경된 itemCounts / 장착 정보로 추천 API 재요청
 *   수량이 0이 된 아이템은 객체에서 제거하여 { "B.F. 대검": 1, "연습용 장갑": 2 } 형태만 전달한다.
 */
export default function ItemSelector({ items, status, itemCounts, onAdjust }) {
  return (
    <section className="panel">
      <h2 className="panel-title">보유 아이템</h2>
      <p className="codex-hint">수량을 올린 아이템은 체스판 기물 위로 끌어다 놓아 장착할 수 있습니다.</p>
      {status === 'loading' && <p className="message">아이템 목록을 불러오는 중…</p>}
      {status === 'error' && <p className="message error">아이템 목록을 불러오지 못했습니다. 백엔드(8080) 상태를 확인하세요.</p>}

      <ul className="item-grid">
        {items.map(({ itemId, name, iconUrl }) => {
          const count = itemCounts[name] ?? 0
          const canDrag = count > 0
          return (
            <li
              key={itemId}
              className={`item-card ${canDrag ? 'is-owned is-draggable' : ''}`}
              draggable={canDrag}
              onDragStart={(e) => {
                e.dataTransfer.setData(DRAG_TYPE_ITEM, JSON.stringify({ itemName: name }))
                e.dataTransfer.effectAllowed = 'copy'
              }}
              title={canDrag ? `${name} - 기물 위로 끌어 장착` : name}
            >
              <span className="item-name">{name}</span>
              <div className="counter">
                {iconUrl && <img className="item-icon" src={iconUrl} alt="" width="36" height="36" draggable={false} />}
                <button type="button" onClick={() => onAdjust(name, -1)} disabled={count === 0}
                        aria-label={`${name} 하나 빼기`}>−</button>
                <span className="counter-value">{count}</span>
                <button type="button" onClick={() => onAdjust(name, 1)} disabled={count === MAX_ITEM_COUNT}
                        aria-label={`${name} 하나 더하기`}>+</button>
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
