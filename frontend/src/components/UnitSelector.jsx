import { useState } from 'react'

/**
 * [역할] 상점 5칸 기물 입력과 내 필드/벤치 보유 기물 선택을 담당하는 입력 컴포넌트.
 *
 * [Data Flow]
 *   App(shopUnits, boardUnits 상태) + knownUnits(GET /api/v1/meta-comps 의 coreUnits 모음) --props-->
 *   - 상점: 칸마다 텍스트 입력 (knownUnits 자동완성) --> onShopChange(길이 5 배열) --> App 상태 갱신
 *   - 보유: 기물 칩 클릭으로 토글, 목록에 없는 기물은 직접 입력해 추가 --> onBoardChange(배열)
 *   --> RecommendationList가 변경된 기물 정보로 추천 API 재요청
 */
export default function UnitSelector({ knownUnits, shopUnits, boardUnits, onShopChange, onBoardChange }) {
  const [customUnit, setCustomUnit] = useState('')

  const updateShopSlot = (index, value) => {
    const next = [...shopUnits]
    next[index] = value
    onShopChange(next)
  }

  const toggleBoardUnit = (unit) => {
    onBoardChange(boardUnits.includes(unit)
      ? boardUnits.filter((u) => u !== unit)
      : [...boardUnits, unit])
  }

  const addCustomUnit = (event) => {
    event.preventDefault()
    const unit = customUnit.trim()
    if (unit && !boardUnits.includes(unit)) {
      onBoardChange([...boardUnits, unit])
    }
    setCustomUnit('')
  }

  // 메타 덱 기물 + 직접 추가한 보유 기물을 합쳐 칩 목록 구성
  const chipUnits = [...new Set([...knownUnits, ...boardUnits])]

  return (
    <section className="panel">
      <h2 className="panel-title">상점</h2>
      <div className="shop-slots">
        {shopUnits.map((unit, index) => (
          <input
            key={index}
            className="shop-slot"
            list="known-units"
            placeholder={`슬롯 ${index + 1}`}
            value={unit}
            onChange={(e) => updateShopSlot(index, e.target.value)}
          />
        ))}
        <datalist id="known-units">
          {knownUnits.map((unit) => <option key={unit} value={unit} />)}
        </datalist>
        <button type="button" className="ghost-button" onClick={() => onShopChange(shopUnits.map(() => ''))}>
          비우기
        </button>
      </div>

      <h2 className="panel-title">내 필드 / 벤치</h2>
      <div className="unit-chips">
        {chipUnits.map((unit) => (
          <button
            key={unit}
            type="button"
            className={`unit-chip ${boardUnits.includes(unit) ? 'is-selected' : ''}`}
            onClick={() => toggleBoardUnit(unit)}
            aria-pressed={boardUnits.includes(unit)}
          >
            {unit}
          </button>
        ))}
      </div>
      <form className="custom-unit" onSubmit={addCustomUnit}>
        <input
          placeholder="목록에 없는 기물 직접 추가"
          value={customUnit}
          onChange={(e) => setCustomUnit(e.target.value)}
        />
        <button type="submit" className="ghost-button">추가</button>
      </form>
    </section>
  )
}
