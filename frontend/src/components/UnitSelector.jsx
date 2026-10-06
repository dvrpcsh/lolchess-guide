import { useEffect, useMemo, useState } from 'react'
import { fetchChampions } from '../api/client'
import ChampionIcon from './ChampionIcon'

const COSTS = [1, 2, 3, 4, 5]
const MAX_SUGGESTIONS = 8

/**
 * [역할] 상점 5칸 기물 입력과 내 필드/벤치 보유 기물 선택을 담당하는 입력 컴포넌트.
 *   챔피언 목록은 서버(Data Dragon 동기화 데이터)에서 받아 초상화 아이콘과 함께 보여 준다.
 *
 * [Data Flow]
 *   최초 렌더 --> GET /api/v1/champions --> champions(이름, 코스트, 아이콘) 상태 저장
 *   App(shopUnits, boardUnits 상태) --props--> 화면 표시
 *   - 상점: 칸에 이름 입력 --> 아이콘이 달린 자동완성 목록에서 선택 --> onShopChange(길이 5 배열)
 *   - 보유: 코스트별로 묶인 챔피언 칩 클릭으로 토글 (검색어로 필터) --> onBoardChange(이름 배열)
 *   --> App 상태 갱신 --> RecommendationList가 변경된 기물 정보로 추천 API 재요청
 *   (서버로는 챔피언 한글 이름만 전달하며, 메타 덱의 coreUnits 이름과 비교된다.)
 */
export default function UnitSelector({ shopUnits, boardUnits, onShopChange, onBoardChange }) {
  const [champions, setChampions] = useState([])
  const [status, setStatus] = useState('loading') // loading | done | error
  const [boardQuery, setBoardQuery] = useState('')

  useEffect(() => {
    fetchChampions()
      .then((data) => {
        setChampions(data)
        setStatus('done')
      })
      .catch(() => setStatus('error'))
  }, [])

  const championByName = useMemo(
    () => new Map(champions.map((champion) => [champion.name, champion])),
    [champions],
  )

  const updateShopSlot = (index, value) => {
    const next = [...shopUnits]
    next[index] = value
    onShopChange(next)
  }

  const toggleBoardUnit = (name) => {
    onBoardChange(boardUnits.includes(name)
      ? boardUnits.filter((unit) => unit !== name)
      : [...boardUnits, name])
  }

  const query = boardQuery.trim()
  const visibleChampions = query ? champions.filter((c) => c.name.includes(query)) : champions

  return (
    <section className="panel">
      <h2 className="panel-title">상점</h2>
      {status === 'error' && <p className="message error">챔피언 목록을 불러오지 못했습니다. 이름을 직접 입력할 수 있습니다.</p>}
      <div className="shop-slots">
        {shopUnits.map((unit, index) => (
          <ShopSlot
            key={index}
            index={index}
            value={unit}
            champions={champions}
            selected={championByName.get(unit.trim())}
            onChange={(value) => updateShopSlot(index, value)}
          />
        ))}
        <button type="button" className="ghost-button" onClick={() => onShopChange(shopUnits.map(() => ''))}>
          비우기
        </button>
      </div>

      <div className="panel-title-row">
        <h2 className="panel-title">내 필드 / 벤치 <small>{boardUnits.length}명 선택</small></h2>
        <input
          className="search-input"
          type="search"
          placeholder="챔피언 검색"
          value={boardQuery}
          onChange={(e) => setBoardQuery(e.target.value)}
        />
      </div>
      {status === 'loading' && <p className="message">챔피언 목록을 불러오는 중…</p>}

      {COSTS.map((cost) => {
        const group = visibleChampions.filter((champion) => champion.cost === cost)
        if (group.length === 0) return null
        return (
          <div key={cost} className="cost-group">
            <span className={`cost-label cost-${cost}`}>{cost}코스트</span>
            <div className="unit-chips">
              {group.map((champion) => {
                const isSelected = boardUnits.includes(champion.name)
                return (
                  <button
                    key={champion.championId}
                    type="button"
                    className={`unit-chip cost-${cost} ${isSelected ? 'is-selected' : ''}`}
                    onClick={() => toggleBoardUnit(champion.name)}
                    aria-pressed={isSelected}
                  >
                    <ChampionIcon champion={champion} size={24} />
                    {champion.name}
                  </button>
                )
              })}
            </div>
          </div>
        )
      })}
    </section>
  )
}

/**
 * 상점 한 칸: 입력한 글자로 챔피언을 검색해 아이콘이 달린 자동완성 목록을 띄운다.
 * 방향키(↑↓)로 이동, Enter로 선택, Esc로 닫기.
 */
function ShopSlot({ index, value, champions, selected, onChange }) {
  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)

  const query = value.trim()
  const suggestions = champions
    .filter((champion) => !query || champion.name.includes(query))
    .slice(0, MAX_SUGGESTIONS)
  const listId = `shop-slot-${index}-list`

  const select = (champion) => {
    onChange(champion.name)
    setIsOpen(false)
  }

  const handleKeyDown = (event) => {
    if (!isOpen || suggestions.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((i) => (i + 1) % suggestions.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((i) => (i - 1 + suggestions.length) % suggestions.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      select(suggestions[Math.min(activeIndex, suggestions.length - 1)])
    } else if (event.key === 'Escape') {
      setIsOpen(false)
    }
  }

  return (
    <div className={`shop-slot ${selected ? `is-filled cost-${selected.cost}` : ''}`}>
      {selected && <ChampionIcon champion={selected} size={32} />}
      <input
        role="combobox"
        aria-expanded={isOpen}
        aria-controls={listId}
        aria-autocomplete="list"
        placeholder={`슬롯 ${index + 1}`}
        value={value}
        onChange={(e) => {
          onChange(e.target.value)
          setActiveIndex(0)
          setIsOpen(true)
        }}
        onFocus={() => setIsOpen(true)}
        onBlur={() => setIsOpen(false)}
        onKeyDown={handleKeyDown}
      />
      {isOpen && suggestions.length > 0 && (
        <ul id={listId} role="listbox" className="suggestions">
          {suggestions.map((champion, i) => (
            <li
              key={champion.championId}
              role="option"
              aria-selected={i === activeIndex}
              className={i === activeIndex ? 'is-active' : ''}
              // blur보다 먼저 선택되도록 mousedown 사용
              onMouseDown={(e) => {
                e.preventDefault()
                select(champion)
              }}
              onMouseEnter={() => setActiveIndex(i)}
            >
              <ChampionIcon champion={champion} size={24} />
              <span>{champion.name}</span>
              <span className={`suggestion-cost cost-${champion.cost}`}>{champion.cost}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
