import { useEffect, useState } from 'react'
import { fetchRecommendations } from '../api/client'
import CompDetailModal from './CompDetailModal'
import CompTypeBadge from './CompTypeBadge'

const DEBOUNCE_MS = 300

/**
 * [역할] 현재 입력값(기물/아이템)으로 추천 API를 호출하고 추천 덱 카드 목록을 렌더링한다.
 *
 * [Data Flow]
 *   App --request({ shopUnits, boardUnits, itemCounts }), refreshKey--> RecommendationList
 *     --> 입력이 멈추고 300ms 후 POST /api/v1/recommend (이전 요청은 AbortController로 취소)
 *     --> Spring Boot RecommendationService가 점수순 List<RecommendResponse> 반환
 *     --> 카드로 렌더링: 덱 이름, 티어, 점수, 사야 할 기물(상점 하이라이트), 적합 아이템, 운영 팁,
 *         이자 손실 경고 뱃지(interestWarnings), 레벨별 확률 팁(probabilityTips)
 *     --> 1순위 덱의 actionBriefings(실시간 행동 가이드)는 onBriefings로 App에 올려 화면 상단 코치 배너에 표시
 *         (크립 라운드 안내도 코치 브리핑의 골드 킵 가이드에 포함되므로 목록 위 별도 배너는 두지 않음)
 *     --> 카드 클릭(또는 Enter/Space) --> CompDetailModal로 레벨별 빌드업·기물별 추천 아이템 상세 가이드 표시
 *   refreshKey는 "추천 받기" 버튼 클릭 시 증가하여 입력 변경 없이도 재요청하게 한다.
 */
export default function RecommendationList({ request, refreshKey, onBriefings, championByName, itemIconByName }) {
  const [results, setResults] = useState([])
  const [detailComp, setDetailComp] = useState(null) // 상세 가이드 모달에 보여 줄 덱 (null이면 닫힘)
  const [status, setStatus] = useState('idle') // idle | loading | done | error

  // 객체 참조가 매 렌더마다 바뀌므로 내용 기준으로 요청 여부를 판단
  const requestKey = JSON.stringify(request)

  useEffect(() => {
    const controller = new AbortController()
    const timer = setTimeout(async () => {
      setStatus('loading')
      try {
        const data = await fetchRecommendations(JSON.parse(requestKey), controller.signal)
        setResults(data)
        setStatus('done')
        onBriefings(data[0]?.actionBriefings ?? [])
      } catch (error) {
        if (error.name !== 'CanceledError') {
          setStatus('error')
          onBriefings([])
        }
      }
    }, DEBOUNCE_MS)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [requestKey, refreshKey, onBriefings])

  return (
    <section className="panel results">
      <h2 className="panel-title">
        추천 덱 {status === 'loading' && <span className="loading-dot">갱신 중…</span>}
      </h2>

      {status === 'error' && (
        <p className="message error">추천 서버에 연결할 수 없습니다. 백엔드(8080)가 실행 중인지 확인하세요.</p>
      )}
      {status === 'done' && results.length === 0 && (
        <p className="message">일치하는 덱이 없습니다. 상점 기물이나 보유 아이템을 입력해 보세요.</p>
      )}

      <ol className="comp-list">
        {results.map((comp, index) => (
          <li
            key={comp.compName}
            className={`comp-card is-clickable tier-${comp.tier.toLowerCase()}`}
            role="button"
            tabIndex={0}
            aria-haspopup="dialog"
            aria-label={`${comp.compName} 상세 가이드 열기`}
            onClick={() => setDetailComp(comp)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                setDetailComp(comp)
              }
            }}
          >
            <header className="comp-header">
              <span className="comp-rank">#{index + 1}</span>
              <span className={`tier-badge tier-${comp.tier.toLowerCase()}`}>{comp.tier}</span>
              <h3 className="comp-name">{comp.compName}</h3>
              <CompTypeBadge compType={comp.compType} />
              <span className="comp-score">{comp.matchScore}<small>점</small></span>
            </header>
            <span className="comp-detail-hint" aria-hidden="true">클릭하여 빌드업·아이템 상세 가이드 보기 ›</span>

            <div className="comp-row">
              <span className="row-label">상점 구매</span>
              {comp.unitsToBuy.length > 0
                ? comp.unitsToBuy.map((unit) => <span key={unit} className="tag tag-buy">{unit}</span>)
                : <span className="row-empty">없음</span>}
            </div>
            <div className="comp-row">
              <span className="row-label">적합 아이템</span>
              {comp.matchedItems.length > 0
                ? comp.matchedItems.map((item) => <span key={item} className="tag tag-item">{item}</span>)
                : <span className="row-empty">없음</span>}
            </div>
            {comp.interestWarnings?.length > 0 && (
              <ul className="feedback-list">
                {comp.interestWarnings.map((warning) => (
                  <li key={warning} className="badge-warning">{warning}</li>
                ))}
              </ul>
            )}
            {comp.probabilityTips?.length > 0 && (
              <ul className="feedback-list">
                {comp.probabilityTips.map((tip) => (
                  <li key={tip} className="badge-tip">{tip}</li>
                ))}
              </ul>
            )}
            {comp.description && <p className="comp-tip">{comp.description}</p>}
          </li>
        ))}
      </ol>

      {detailComp && (
        <CompDetailModal
          comp={detailComp}
          championByName={championByName}
          itemIconByName={itemIconByName}
          ownedUnitNames={new Set(request.boardUnits)}
          onClose={() => setDetailComp(null)}
        />
      )}
    </section>
  )
}
