import { useEffect, useState } from 'react'
import { fetchMetaComps } from './api/client'
import ItemSelector from './components/ItemSelector'
import UnitSelector from './components/UnitSelector'
import RecommendationList from './components/RecommendationList'

const SHOP_SIZE = 5

/**
 * [역할] 메인 페이지. 입력 상태(상점/보유 기물, 보유 아이템)를 한 곳에서 관리하고
 *   입력 컴포넌트와 추천 결과 컴포넌트를 연결한다.
 *
 * [Data Flow]
 *   최초 렌더 --> GET /api/v1/meta-comps --> 덱들의 coreUnits를 모아 기물 선택 후보(knownUnits) 생성
 *   UnitSelector / ItemSelector --onChange--> App 상태 갱신
 *     --> 빈 칸 제거한 request 객체 생성 --> RecommendationList가 POST /api/v1/recommend 재요청
 */
export default function App() {
  const [knownUnits, setKnownUnits] = useState([])
  const [shopUnits, setShopUnits] = useState(Array(SHOP_SIZE).fill(''))
  const [boardUnits, setBoardUnits] = useState([])
  const [itemCounts, setItemCounts] = useState({})
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    fetchMetaComps()
      .then((comps) => setKnownUnits([...new Set(comps.flatMap((comp) => comp.coreUnits))]))
      .catch(() => setKnownUnits([])) // 실패해도 직접 입력으로 사용 가능
  }, [])

  const request = {
    shopUnits: shopUnits.map((unit) => unit.trim()).filter(Boolean),
    boardUnits,
    itemCounts,
  }

  const resetAll = () => {
    setShopUnits(Array(SHOP_SIZE).fill(''))
    setBoardUnits([])
    setItemCounts({})
  }

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <p className="eyebrow">TFT SEASON 18</p>
          <h1>실시간 덱 추천</h1>
        </div>
        <div className="header-actions">
          <button type="button" className="ghost-button" onClick={resetAll}>초기화</button>
          <button type="button" className="primary-button" onClick={() => setRefreshKey((k) => k + 1)}>
            추천 받기
          </button>
        </div>
      </header>

      <main className="layout">
        <div className="inputs">
          <UnitSelector
            knownUnits={knownUnits}
            shopUnits={shopUnits}
            boardUnits={boardUnits}
            onShopChange={setShopUnits}
            onBoardChange={setBoardUnits}
          />
          <ItemSelector itemCounts={itemCounts} onChange={setItemCounts} />
        </div>
        <RecommendationList request={request} refreshKey={refreshKey} />
      </main>
    </div>
  )
}
