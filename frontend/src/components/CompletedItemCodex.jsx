import { DRAG_TYPE_COMPLETED_ITEM } from '../utils/boardState'

/**
 * [역할] 완성 아이템 도감. 조합표에 있는 시즌 18 주요 완성 아이템을 조합식과 함께 보여 주고,
 *   각 아이템을 체스판 기물 위로 끌어 완성 상태로 바로 장착하게 하는 드래그 소스.
 *
 * [Data Flow]
 *   App(GET /api/v1/items 의 recipes -> recipeBook, 아이콘 맵) --props--> 완성 아이템 + 재료 2개 아이콘 렌더링
 *   카드 드래그 --> dataTransfer(DRAG_TYPE_COMPLETED_ITEM, { itemName })
 *     --> ChessBoard 기물 위 drop --> App이 빈 슬롯에 완성 아이템 장착 (인벤토리 변화 없음)
 *     --> 해제 시에는 재료 2개로 분해되어 인벤토리로 반환
 */
export default function CompletedItemCodex({ recipeBook, itemIconByName, status }) {
  const completedItems = [...recipeBook.completedItems].sort((a, b) => a.name.localeCompare(b.name, 'ko'))

  return (
    <div className="completed-codex">
      <p className="codex-hint">완성 아이템을 체스판 기물 위로 끌어다 놓으면 완성 상태로 바로 장착됩니다.</p>
      {status === 'loading' && <p className="message">아이템 목록을 불러오는 중…</p>}
      {status === 'error' && <p className="message error">아이템 목록을 불러오지 못했습니다. 백엔드(8080) 상태를 확인하세요.</p>}

      <ul className="completed-grid">
        {completedItems.map(({ name, components }) => (
          <li
            key={name}
            className="completed-card"
            draggable
            onDragStart={(e) => {
              e.dataTransfer.setData(DRAG_TYPE_COMPLETED_ITEM, JSON.stringify({ itemName: name }))
              e.dataTransfer.effectAllowed = 'copy'
            }}
            title={`${name} = ${components.join(' + ')} - 기물 위로 끌어 장착`}
          >
            <ItemIcon name={name} iconUrl={itemIconByName.get(name)} size={40} />
            <span className="completed-name">{name}</span>
            <span className="completed-recipe" aria-label={`조합식 ${components.join(' 더하기 ')}`}>
              <ItemIcon name={components[0]} iconUrl={itemIconByName.get(components[0])} size={18} />
              <span aria-hidden="true">+</span>
              <ItemIcon name={components[1]} iconUrl={itemIconByName.get(components[1])} size={18} />
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function ItemIcon({ name, iconUrl, size }) {
  return iconUrl
    ? <img className="completed-icon" src={iconUrl} alt="" width={size} height={size} draggable={false} />
    : <span className="completed-icon is-missing" style={{ width: size, height: size }}>{name.slice(0, 1)}</span>
}
