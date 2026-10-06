// 백엔드 CompType enum 값 -> 화면 표시 이름/스타일
const COMP_TYPES = {
  REROLL: { label: '리롤 덱', className: 'is-reroll' },
  FAST_8: { label: 'Fast 8 덱', className: 'is-fast8' },
  VALUE_9: { label: '9렙 밸류 덱', className: 'is-value9' },
}

/**
 * [역할] 메타 덱 운영 타입 뱃지 ([리롤 덱] / [Fast 8 덱] / [9렙 밸류 덱]).
 *
 * [Data Flow]
 *   POST /api/v1/recommend 응답의 compType (백엔드 MetaCompEntity.compType)
 *     --> 추천 덱 카드 헤더 / 상세 가이드 모달 헤더에 표시. 값이 없으면 아무것도 그리지 않는다.
 */
export default function CompTypeBadge({ compType }) {
  const type = COMP_TYPES[compType]
  if (!type) return null
  return <span className={`comp-type-badge ${type.className}`}>[{type.label}]</span>
}
