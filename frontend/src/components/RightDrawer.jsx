import { useState } from 'react'

/**
 * [역할] 화면 오른쪽에서 펼치고 접는 서랍(Drawer) 패널. 입력 도구들을 탭으로 묶어 체스판 옆에 띄운다.
 *
 * [Data Flow]
 *   App이 탭 내용(상점 / 기물 도감 / 아이템[기본 재료·완성 아이템 서브 탭] / 게임 상태)을 tabs 배열로 전달
 *     --> RightDrawer는 열림 여부와 선택된 탭만 관리
 *   각 탭 컴포넌트의 입력은 그대로 App 상태를 갱신하고 --> 추천 API 재요청으로 이어진다.
 *   비활성 탭도 언마운트하지 않고 숨기기만 하여, 탭을 옮겨도 입력 중인 값과 불러온 목록이 유지된다.
 *
 * @param tabs   [{ id, label, content }]
 * @param isOpen 열림 여부 (App이 관리 - 레이아웃 여백 조정에 사용)
 */
export default function RightDrawer({ tabs, isOpen, onToggle }) {
  const [activeId, setActiveId] = useState(tabs[0].id)

  return (
    <aside className={`drawer ${isOpen ? 'is-open' : ''}`} aria-label="입력 도구 서랍">
      <button
        type="button"
        className="drawer-toggle"
        onClick={onToggle}
        aria-expanded={isOpen}
        aria-controls="drawer-body"
        title={isOpen ? '서랍 접기' : '서랍 펼치기'}
      >
        <span aria-hidden="true">{isOpen ? '›' : '‹'}</span>
        <span className="drawer-toggle-label">{isOpen ? '접기' : '도구'}</span>
      </button>

      <div id="drawer-body" className="drawer-body" hidden={!isOpen}>
        <div className="drawer-tabs" role="tablist">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`drawer-tab-${tab.id}`}
              aria-selected={tab.id === activeId}
              aria-controls={`drawer-panel-${tab.id}`}
              className={`drawer-tab ${tab.id === activeId ? 'is-active' : ''}`}
              onClick={() => setActiveId(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {tabs.map((tab) => (
          <div
            key={tab.id}
            role="tabpanel"
            id={`drawer-panel-${tab.id}`}
            aria-labelledby={`drawer-tab-${tab.id}`}
            className="drawer-panel"
            hidden={tab.id !== activeId}
          >
            {tab.content}
          </div>
        ))}
      </div>
    </aside>
  )
}

/**
 * [역할] 서랍 탭 안에서 다시 나누는 서브 탭 (예: 아이템 탭의 [기본 재료] / [완성 아이템]).
 *
 * [Data Flow]
 *   App --tabs([{ id, label, content }])--> SubTabs가 선택된 서브 탭만 보여 줌
 *   서랍 탭과 같은 이유로 비활성 서브 탭도 언마운트하지 않고 숨긴다.
 */
export function SubTabs({ tabs, ariaLabel }) {
  const [activeId, setActiveId] = useState(tabs[0].id)

  return (
    <div className="sub-tabs">
      <div className="sub-tab-list" role="tablist" aria-label={ariaLabel}>
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`sub-tab-${tab.id}`}
            aria-selected={tab.id === activeId}
            aria-controls={`sub-panel-${tab.id}`}
            className={`sub-tab ${tab.id === activeId ? 'is-active' : ''}`}
            onClick={() => setActiveId(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {tabs.map((tab) => (
        <div
          key={tab.id}
          role="tabpanel"
          id={`sub-panel-${tab.id}`}
          aria-labelledby={`sub-tab-${tab.id}`}
          hidden={tab.id !== activeId}
        >
          {tab.content}
        </div>
      ))}
    </div>
  )
}
