import { useEffect, useRef } from 'react'
import ChampionIcon from './ChampionIcon'
import CompTypeBadge from './CompTypeBadge'

/**
 * [역할] 추천 덱 상세 가이드 모달. 레벨별(4~9렙) 빌드업 기물과 핵심 기물별 추천 완성 아이템을 보여 준다.
 *
 * [Data Flow]
 *   POST /api/v1/recommend 응답의 덱 카드 { buildUpGuide, unitItemMap } (백엔드 MetaCompEntity -> RecommendResponse)
 *     --> RecommendationList에서 카드 클릭 --> comp 전달 --> 네이티브 <dialog>.showModal()로 표시
 *   championByName(GET /api/v1/champions) / itemIconByName(GET /api/v1/items)으로 초상화·아이콘을 찾고,
 *   ownedUnitNames(현재 체스판·벤치 기물)는 빌드업 칩에서 "보유" 표시에 사용한다.
 *   Esc / 닫기 버튼 / 바깥 영역 클릭 --> onClose() --> 부모가 comp를 null로 바꿔 모달 제거
 */
export default function CompDetailModal({ comp, championByName, itemIconByName, ownedUnitNames, onClose }) {
  const dialogRef = useRef(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) {
      dialog.showModal() // 포커스 가두기·Esc 닫기·배경 비활성화를 브라우저가 처리
    }
  }, [])

  // JSON 키는 문자열("4")로 오므로 숫자로 정렬
  const levels = Object.entries(comp.buildUpGuide ?? {})
    .map(([level, units]) => [Number(level), units])
    .sort(([a], [b]) => a - b)
  const carries = Object.entries(comp.unitItemMap ?? {})
  const carryNames = new Set(carries.map(([name]) => name))

  return (
    <dialog
      ref={dialogRef}
      className="comp-modal"
      aria-labelledby="comp-modal-title"
      onClose={onClose}
      onClick={(e) => {
        if (e.target === dialogRef.current) dialogRef.current.close() // 바깥(배경) 클릭 시 닫기
      }}
    >
      <div className="comp-modal-body">
        <header className="comp-modal-header">
          <span className={`tier-badge tier-${comp.tier.toLowerCase()}`}>{comp.tier}</span>
          <h2 id="comp-modal-title">{comp.compName}</h2>
          <CompTypeBadge compType={comp.compType} />
          <button type="button" className="ghost-button comp-modal-close" onClick={() => dialogRef.current.close()}>
            닫기
          </button>
        </header>
        {comp.description && <p className="comp-tip">{comp.description}</p>}

        <section className="guide-section">
          <h3 className="panel-title">레벨별 빌드업</h3>
          {levels.length === 0 ? (
            <p className="message">이 덱에는 빌드업 가이드가 아직 없습니다.</p>
          ) : (
            <ol className="build-up-list">
              {levels.map(([level, units]) => (
                <li key={level} className="build-up-row">
                  <span className="level-badge" aria-label={`${level}레벨`}>Lv.{level}</span>
                  <ul className="build-up-units">
                    {units.map((name) => (
                      <li
                        key={name}
                        className={`build-up-chip ${ownedUnitNames.has(name) ? 'is-owned' : ''} ${carryNames.has(name) ? 'is-carry' : ''}`}
                        title={`${name}${carryNames.has(name) ? ' (핵심 기물)' : ''}${ownedUnitNames.has(name) ? ' - 보유 중' : ''}`}
                      >
                        <ChampionIcon champion={championByName.get(name)} size={26} />
                        <span>{name}</span>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ol>
          )}
          <p className="guide-legend">
            <span className="legend-swatch is-owned" /> 현재 보유 <span className="legend-swatch is-carry" /> 핵심 기물
          </p>
        </section>

        <section className="guide-section">
          <h3 className="panel-title">핵심 기물별 추천 아이템</h3>
          {carries.length === 0 ? (
            <p className="message">이 덱에는 기물별 아이템 가이드가 아직 없습니다.</p>
          ) : (
            <ul className="carry-grid">
              {carries.map(([name, items]) => (
                <li key={name} className="carry-card">
                  <ChampionIcon champion={championByName.get(name)} size={64} />
                  <span className="carry-name">{name}</span>
                  <ul className="carry-items">
                    {items.map((item) => (
                      <li key={item} className="carry-item">
                        {itemIconByName.get(item)
                          ? <img src={itemIconByName.get(item)} alt="" width="32" height="32" />
                          : <span className="carry-item-fallback" aria-hidden="true">{item.slice(0, 1)}</span>}
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </dialog>
  )
}
