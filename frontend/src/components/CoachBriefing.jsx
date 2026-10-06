// 가이드 문장 앞의 이모지로 종류를 판별해 뱃지를 붙인다 (백엔드 ActionableGuideService의 문장 형식 기준)
const CATEGORIES = [
  { prefix: '⚔️', label: '아이템', className: 'is-item' },
  { prefix: '⬆️', label: '레벨업', className: 'is-level' },
  { prefix: '🌟', label: '3성 완성', className: 'is-shop' },
  { prefix: '⭐', label: '2성 완성', className: 'is-shop' },
  { prefix: '🛒', label: '상점', className: 'is-shop' },
  { prefix: '💰', label: '골드', className: 'is-gold' },
]
const DEFAULT_CATEGORY = { label: '코치', className: 'is-default' }

/**
 * [역할] "🎯 실시간 코치 브리핑" 배너. 지금 당장 할 행동을 우선순위 순으로 강조해 보여 준다.
 *
 * [Data Flow]
 *   POST /api/v1/recommend 응답의 1순위 덱 actionBriefings (백엔드 ActionableGuideService)
 *     --> RecommendationList.onBriefings --> App briefings 상태 --> CoachBriefing 렌더링
 *   첫 번째 항목은 "최우선" 뱃지로 강조하고, 각 항목은 종류(아이템/레벨업/상점/골드) 뱃지를 붙인다.
 */
export default function CoachBriefing({ briefings }) {
  return (
    <section className="coach-briefing" aria-live="polite" aria-label="실시간 코치 브리핑">
      <h2 className="coach-title">🎯 실시간 코치 브리핑</h2>
      {briefings.length === 0 ? (
        <p className="coach-empty">
          체스판에 기물을 배치하고 레벨·골드·스테이지를 입력하면, 지금 해야 할 행동을 알려 드립니다.
        </p>
      ) : (
        <ol className="coach-list">
          {briefings.map((text, index) => {
            const category = CATEGORIES.find((c) => text.startsWith(c.prefix)) ?? DEFAULT_CATEGORY
            return (
              <li key={text} className={`coach-item ${index === 0 ? 'is-top' : ''}`}>
                {index === 0 && <span className="coach-badge is-top">최우선</span>}
                <span className={`coach-badge ${category.className}`}>{category.label}</span>
                <span className="coach-text">{text}</span>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
