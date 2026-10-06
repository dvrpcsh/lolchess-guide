import { useState } from 'react'
import ChampionIcon from './ChampionIcon'
import { DRAG_TYPE_CODEX } from '../boardState'

const COSTS = [1, 2, 3, 4, 5]

/**
 * [역할] 시즌 18 챔피언 도감. 코스트별 초상화 목록이며, 각 초상화가 체스판/벤치로 끌어다 놓는 드래그 소스가 된다.
 *
 * [Data Flow]
 *   App(GET /api/v1/champions 결과) --champions--> 코스트별로 묶어 렌더링 (검색어로 필터)
 *   - 초상화 드래그 --> dataTransfer(DRAG_TYPE_CODEX, { championId }) --> ChessBoard 칸의 drop 처리
 *   - 초상화 클릭(드래그가 어려운 터치 환경 대비) --> onQuickAdd(championId) --> 벤치 첫 빈 칸에 배치
 */
export default function ChampionCodex({ champions, status, onQuickAdd }) {
  const [query, setQuery] = useState('')

  const keyword = query.trim()
  const visible = keyword ? champions.filter((c) => c.name.includes(keyword)) : champions

  return (
    <div className="codex">
      <input
        className="search-input codex-search"
        type="search"
        placeholder="챔피언 검색"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <p className="codex-hint">초상화를 체스판으로 끌어다 놓거나, 클릭하면 벤치에 추가됩니다.</p>

      {status === 'loading' && <p className="message">챔피언 목록을 불러오는 중…</p>}
      {status === 'error' && <p className="message error">챔피언 목록을 불러오지 못했습니다. 백엔드(8080) 상태를 확인하세요.</p>}

      {COSTS.map((cost) => {
        const group = visible.filter((champion) => champion.cost === cost)
        if (group.length === 0) return null
        return (
          <div key={cost} className="codex-group">
            <span className={`cost-label cost-${cost}`}>{cost}코스트</span>
            <ul className="codex-grid">
              {group.map((champion) => (
                <li key={champion.championId}>
                  <button
                    type="button"
                    className={`codex-card cost-${cost}`}
                    draggable
                    onDragStart={(e) => {
                      e.dataTransfer.setData(DRAG_TYPE_CODEX, JSON.stringify({ championId: champion.championId }))
                      e.dataTransfer.effectAllowed = 'copy'
                    }}
                    onClick={() => onQuickAdd(champion.championId)}
                    title={`${champion.name} (${cost}코스트)`}
                  >
                    <ChampionIcon champion={champion} size={44} />
                    <span className="codex-name">{champion.name}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </div>
  )
}
