import { useEffect, useMemo, useState } from 'react'
import { fetchChampions } from './api/client'
import { INITIAL_GAME_STATE, toGameStateRequest } from './gameState'
import {
  EMPTY_SLOTS,
  addToBench,
  collectBoardUnitNames,
  moveUnit,
  placeChampion,
  removeUnit,
} from './boardState'
import ChessBoard from './components/ChessBoard'
import RightDrawer from './components/RightDrawer'
import ShopPanel from './components/ShopPanel'
import ChampionCodex from './components/ChampionCodex'
import ItemSelector from './components/ItemSelector'
import GameStatePanel from './components/GameStatePanel'
import RecommendationList from './components/RecommendationList'

const SHOP_SIZE = 5

/**
 * [역할] 메인 페이지. 모든 입력 상태(체스판 배치, 상점, 아이템, 레벨/골드/스테이지)를 한 곳에서 관리하고
 *   좌측/중앙의 체스판 + 추천 결과, 우측 서랍의 입력 도구를 연결한다.
 *
 * [Data Flow]
 *   최초 렌더 --> GET /api/v1/champions --> champions (도감·상점 자동완성·체스판 초상화가 공유)
 *   - 서랍 > 기물 도감에서 드래그 --> ChessBoard drop --> slots 상태 갱신
 *   - 서랍 > 상점 / 아이템 / 게임 상태 입력 --> 각 상태 갱신
 *   --> 체스판 + 벤치의 챔피언 이름을 collectBoardUnitNames()로 모아 boardUnits 생성
 *   --> request 객체 --> RecommendationList가 POST /api/v1/recommend 재요청 (입력이 바뀔 때마다 실시간)
 */
export default function App() {
  const [champions, setChampions] = useState([])
  const [championStatus, setChampionStatus] = useState('loading') // loading | done | error
  const [slots, setSlots] = useState(EMPTY_SLOTS)
  const [shopUnits, setShopUnits] = useState(Array(SHOP_SIZE).fill(''))
  const [itemCounts, setItemCounts] = useState({})
  const [gameState, setGameState] = useState(INITIAL_GAME_STATE)
  const [refreshKey, setRefreshKey] = useState(0)
  const [isDrawerOpen, setIsDrawerOpen] = useState(true)

  useEffect(() => {
    fetchChampions()
      .then((data) => {
        setChampions(data)
        setChampionStatus('done')
      })
      .catch(() => setChampionStatus('error'))
  }, [])

  const championsById = useMemo(
    () => new Map(champions.map((champion) => [champion.championId, champion])),
    [champions],
  )

  const request = {
    shopUnits: shopUnits.map((unit) => unit.trim()).filter(Boolean),
    boardUnits: collectBoardUnitNames(slots, championsById),
    itemCounts,
    ...toGameStateRequest(gameState),
  }

  const resetAll = () => {
    setSlots(EMPTY_SLOTS)
    setShopUnits(Array(SHOP_SIZE).fill(''))
    setItemCounts({})
    setGameState(INITIAL_GAME_STATE)
  }

  const drawerTabs = [
    {
      id: 'shop',
      label: '상점',
      content: <ShopPanel champions={champions} shopUnits={shopUnits} onShopChange={setShopUnits} />,
    },
    {
      id: 'codex',
      label: '기물 도감',
      content: (
        <ChampionCodex
          champions={champions}
          status={championStatus}
          onQuickAdd={(championId) => setSlots((s) => addToBench(s, championId))}
        />
      ),
    },
    {
      id: 'items',
      label: '아이템',
      content: <ItemSelector itemCounts={itemCounts} onChange={setItemCounts} />,
    },
    {
      id: 'state',
      label: '게임 상태',
      content: <GameStatePanel gameState={gameState} onChange={setGameState} />,
    },
  ]

  return (
    <div className={`app-shell ${isDrawerOpen ? 'drawer-open' : ''}`}>
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
          <ChessBoard
            slots={slots}
            championsById={championsById}
            onPlace={(target, championId) => setSlots((s) => placeChampion(s, target, championId))}
            onMove={(from, to) => setSlots((s) => moveUnit(s, from, to))}
            onRemove={(at) => setSlots((s) => removeUnit(s, at))}
          />
          <RecommendationList request={request} refreshKey={refreshKey} />
        </main>
      </div>

      <RightDrawer tabs={drawerTabs} isOpen={isDrawerOpen} onToggle={() => setIsDrawerOpen((open) => !open)} />
    </div>
  )
}
