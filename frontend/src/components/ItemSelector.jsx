/**
 * [역할] TFT 기본 재료 아이템 8종의 보유 수량을 [-] [수량] [+] 버튼으로 조절하는 입력 컴포넌트.
 *
 * [Data Flow]
 *   App(itemCounts 상태) --props--> ItemSelector 화면 표시
 *   사용자 +/- 클릭 --> onChange({ ...itemCounts, [아이템]: 새 수량 }) --> App 상태 갱신
 *     --> RecommendationList가 변경된 itemCounts로 추천 API 재요청
 *   수량이 0이 된 아이템은 객체에서 제거하여 { "B.F. 대검": 1, "연습용 장갑": 2 } 형태만 전달한다.
 */

// 이름은 백엔드 meta_comp_recommended_items 데이터와 정확히 일치해야 매칭 점수에 반영된다.
const BASE_ITEMS = [
  { name: 'B.F. 대검', icon: '⚔️' },
  { name: '곡궁', icon: '🏹' },
  { name: '쇠사슬 조끼', icon: '🛡️' },
  { name: '음전자 망토', icon: '🧥' },
  { name: '쓸데없이 큰 지팡이', icon: '🪄' },
  { name: '여신의 눈물', icon: '💧' },
  { name: '거인의 허리띠', icon: '🎗️' },
  { name: '연습용 장갑', icon: '🧤' },
]

const MAX_COUNT = 9

export default function ItemSelector({ itemCounts, onChange }) {
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
      <ul className="item-grid">
        {BASE_ITEMS.map(({ name, icon }) => {
          const count = itemCounts[name] ?? 0
          return (
            <li key={name} className={`item-card ${count > 0 ? 'is-owned' : ''}`}>
              <span className="item-icon" aria-hidden="true">{icon}</span>
              <span className="item-name">{name}</span>
              <div className="counter">
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
