import { useEffect, useMemo, useState } from 'react'
import { fetchChampions, fetchItems } from './api/client'
import { INITIAL_GAME_STATE, toGameStateRequest } from './gameState'
import {
  addItemsToInventory,
  addToBench,
  adjustItemCount,
  collectBoardUnitNames,
  collectPlacedUnits,
  cycleStarLevel,
  equipFromInventory,
  isLoadoutEmpty,
  moveUnit,
  placeChampion,
  removeUnit,
  resetLoadout,
  unequipItem,
} from './utils/boardState'
import { createRecipeBook } from './utils/itemRecipes'
import ChessBoard from './components/ChessBoard'
import RightDrawer, { SubTabs } from './components/RightDrawer'
import CompletedItemCodex from './components/CompletedItemCodex'
import ShopPanel from './components/ShopPanel'
import ChampionCodex from './components/ChampionCodex'
import ItemSelector from './components/ItemSelector'
import GameStatePanel from './components/GameStatePanel'
import RecommendationList from './components/RecommendationList'

const SHOP_SIZE = 5

/**
 * [역할] 메인 페이지. 모든 입력 상태(체스판 배치·성급·장착 아이템, 상점, 인벤토리 아이템, 레벨/골드/스테이지)를
 *   한 곳에서 관리하고, 좌측/중앙의 체스판 + 추천 결과와 우측 서랍의 입력 도구를 연결한다.
 *
 * [Data Flow]
 *   최초 렌더 --> GET /api/v1/champions --> champions (도감·상점 자동완성·체스판 초상화가 공유)
 *            --> GET /api/v1/items     --> 재료/완성 아이템 목록 + 조합법 (recipeBook: 자동 합성·분해 규칙)
 *   - 서랍 > 기물 도감에서 드래그 --> ChessBoard drop --> slots 갱신
 *   - 서랍 > 아이템 > [기본 재료] / [완성 아이템]에서 드래그 --> 기물 위 drop --> equipFromInventory()로 장착
 *       (재료는 조합 가능한 재료와 자동 합성, 인벤토리에 있으면 1개 차감 / 0개·완성 아이템은 즉시 획득 처리)
 *   - 완성 아이템 해제·기물 제거 --> 재료 2개로 분해되어 인벤토리로 반환
 *   - 체스판 "판세 초기화" --> resetLoadout()으로 기물·성급·장착 아이템·인벤토리 일괄 초기화
 *   - 장착 해제 / 기물 제거·교체 --> 장착돼 있던 아이템을 itemCounts로 반환
 *   - 서랍 > 상점 / 게임 상태 입력 --> 각 상태 갱신
 *   --> request { boardUnits(이름), placedUnits(이름·성급·장착 아이템), itemCounts(인벤토리), ... }
 *   --> RecommendationList가 POST /api/v1/recommend 재요청 (입력이 바뀔 때마다 실시간)
 */
export default function App() {
  const [champions, setChampions] = useState([])
  const [championStatus, setChampionStatus] = useState('loading') // loading | done | error
  const [itemData, setItemData] = useState({ components: [], combined: [], recipes: [] })
  const [itemStatus, setItemStatus] = useState('loading') // loading | done | error
  // 체스판 배치와 인벤토리는 아이템 장착/반환으로 항상 함께 바뀌므로 하나의 상태로 관리한다.
  // 모든 갱신은 setLoadout(prev => ...) 형태로 이전 상태를 기준으로 계산해, 연속 이벤트에서도 값이 유실되지 않게 한다.
  const [loadout, setLoadout] = useState(resetLoadout)
  const { slots, itemCounts } = loadout
  const [shopUnits, setShopUnits] = useState(Array(SHOP_SIZE).fill(''))
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
    fetchItems()
      .then((data) => {
        setItemData(data)
        setItemStatus('done')
      })
      .catch(() => setItemStatus('error'))
  }, [])

  const championsById = useMemo(
    () => new Map(champions.map((champion) => [champion.championId, champion])),
    [champions],
  )
  const itemIconByName = useMemo(
    () => new Map([...itemData.components, ...itemData.combined].map((item) => [item.name, item.iconUrl])),
    [itemData],
  )
  const recipeBook = useMemo(() => createRecipeBook(itemData.recipes), [itemData])

  const request = {
    shopUnits: shopUnits.map((unit) => unit.trim()).filter(Boolean),
    boardUnits: collectBoardUnitNames(slots, championsById),
    placedUnits: collectPlacedUnits(slots, championsById),
    itemCounts,
    ...toGameStateRequest(gameState),
  }

  const updateSlots = (update) => setLoadout((prev) => ({ ...prev, slots: update(prev.slots) }))

  // 기물 제거/교체 결과의 slots를 반영하고, 장착돼 있던 아이템(returnedItems)은 인벤토리로 돌려준다.
  const updateSlotsReturningItems = (update) => setLoadout((prev) => {
    const { slots: nextSlots, returnedItems } = update(prev.slots)
    return { slots: nextSlots, itemCounts: addItemsToInventory(prev.itemCounts, returnedItems, recipeBook) }
  })

  const handleEquip = (at, itemName) => setLoadout((prev) => equipFromInventory(prev, at, itemName, recipeBook))

  const handleUnequip = (at, itemIndex) => updateSlotsReturningItems(
    (prevSlots) => unequipItem(prevSlots, at, itemIndex, recipeBook),
  )

  const resetAll = () => {
    setLoadout(resetLoadout())
    setShopUnits(Array(SHOP_SIZE).fill(''))
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
          onQuickAdd={(championId) => updateSlots((prev) => addToBench(prev, championId))}
        />
      ),
    },
    {
      id: 'items',
      label: '아이템',
      content: (
        <SubTabs
          ariaLabel="아이템 종류"
          tabs={[
            {
              id: 'components',
              label: '기본 재료',
              content: (
                <ItemSelector
                  items={itemData.components}
                  status={itemStatus}
                  itemCounts={itemCounts}
                  onAdjust={(itemName, delta) => setLoadout((prev) => ({
                    ...prev, itemCounts: adjustItemCount(prev.itemCounts, itemName, delta),
                  }))}
                />
              ),
            },
            {
              id: 'completed',
              label: '완성 아이템',
              content: <CompletedItemCodex recipeBook={recipeBook} itemIconByName={itemIconByName} status={itemStatus} />,
            },
          ]}
        />
      ),
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
            itemIconByName={itemIconByName}
            recipeBook={recipeBook}
            onPlace={(target, championId) => updateSlotsReturningItems((prev) => placeChampion(prev, target, championId))}
            onMove={(from, to) => updateSlots((prev) => moveUnit(prev, from, to))}
            onRemove={(at) => updateSlotsReturningItems((prev) => removeUnit(prev, at))}
            onCycleStar={(at) => updateSlots((prev) => cycleStarLevel(prev, at))}
            onEquip={handleEquip}
            canResetBoard={!isLoadoutEmpty(loadout)}
            onResetBoard={() => setLoadout(resetLoadout())}
            onUnequip={handleUnequip}
          />
          <RecommendationList request={request} refreshKey={refreshKey} />
        </main>
      </div>

      <RightDrawer tabs={drawerTabs} isOpen={isDrawerOpen} onToggle={() => setIsDrawerOpen((open) => !open)} />
    </div>
  )
}
