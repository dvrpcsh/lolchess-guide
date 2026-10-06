import { useMemo, useState } from 'react'
import ChampionIcon from './ChampionIcon'

const MAX_SUGGESTIONS = 8

/**
 * [역할] 상점 5칸 입력 패널. 각 칸에 챔피언 이름을 입력하면 초상화가 달린 자동완성 목록을 보여 준다.
 *
 * [Data Flow]
 *   App(shopUnits 상태 + GET /api/v1/champions 결과) --props--> 5칸 렌더링
 *   칸 입력/자동완성 선택 --> onShopChange(길이 5 배열) --> App 상태 갱신
 *     --> 빈 칸을 제외한 이름이 추천 API의 shopUnits로 전송 (덱 핵심 기물이면 "상점 구매" 추천)
 */
export default function ShopPanel({ champions, shopUnits, onShopChange }) {
  const championByName = useMemo(
    () => new Map(champions.map((champion) => [champion.name, champion])),
    [champions],
  )

  const updateShopSlot = (index, value) => {
    const next = [...shopUnits]
    next[index] = value
    onShopChange(next)
  }

  return (
    <div className="shop-panel">
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
      </div>
      <button type="button" className="ghost-button" onClick={() => onShopChange(shopUnits.map(() => ''))}>
        상점 비우기
      </button>
    </div>
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
