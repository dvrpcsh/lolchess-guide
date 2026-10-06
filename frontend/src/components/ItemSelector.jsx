import { useEffect, useState } from 'react'
import { fetchItems } from '../api/client'

const MAX_COUNT = 9

/**
 * [역할] 서버에서 받은 TFT 기본 재료 아이템 목록을 아이콘과 함께 보여 주고,
 *   아이템별 보유 수량을 [-] [수량] [+] 버튼으로 조절하는 입력 컴포넌트.
 *
 * [Data Flow]
 *   최초 렌더 --> GET /api/v1/items --> components(재료 아이템 10개: 이름, iconUrl)로 UI 생성
 *   App(itemCounts 상태) --props--> 현재 수량 표시
 *   사용자 +/- 클릭 --> onChange({ ...itemCounts, [아이템 이름]: 새 수량 }) --> App 상태 갱신
 *     --> RecommendationList가 변경된 itemCounts로 추천 API 재요청
 *   수량이 0이 된 아이템은 객체에서 제거하여 { "B.F. 대검": 1, "연습용 장갑": 2 } 형태만 전달한다.
 *   (아이템 이름은 Data Dragon 한글명이며, 추천 점수 계산 시 메타 덱의 추천 아이템 이름과 비교된다.)
 */
export default function ItemSelector({ itemCounts, onChange }) {
  const [items, setItems] = useState([])
  const [status, setStatus] = useState('loading') // loading | done | error

  useEffect(() => {
    fetchItems()
      .then(({ components }) => {
        setItems(components)
        setStatus('done')
      })
      .catch(() => setStatus('error'))
  }, [])

  const updateCount = (name, delta) => {
    const next = Math.min(MAX_COUNT, Math.max(0, (itemCounts[name] ?? 0) + delta))
    const updated = { ...itemCounts }
    if (next === 0) {
      delete updated[name]
    } else {
      updated[name] = next
    }
    onChange(updated)
  }

  return (
    <section className="panel">
      <h2 className="panel-title">보유 아이템</h2>
      {status === 'loading' && <p className="message">아이템 목록을 불러오는 중…</p>}
      {status === 'error' && <p className="message error">아이템 목록을 불러오지 못했습니다. 백엔드(8080) 상태를 확인하세요.</p>}

      <ul className="item-grid">
        {items.map(({ itemId, name, iconUrl }) => {
          const count = itemCounts[name] ?? 0
          return (
            <li key={itemId} className={`item-card ${count > 0 ? 'is-owned' : ''}`}>
              <span className="item-name">{name}</span>
              <div className="counter">
                {iconUrl && <img className="item-icon" src={iconUrl} alt="" width="36" height="36" />}
                <button type="button" onClick={() => updateCount(name, -1)} disabled={count === 0}
                        aria-label={`${name} 하나 빼기`}>−</button>
                <span className="counter-value">{count}</span>
                <button type="button" onClick={() => updateCount(name, 1)} disabled={count === MAX_COUNT}
                        aria-label={`${name} 하나 더하기`}>+</button>
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
