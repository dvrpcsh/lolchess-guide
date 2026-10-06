import { isValidStage } from '../gameState'

/**
 * [역할] 현재 게임 상태(레벨, 골드, 스테이지) 입력 컴포넌트.
 *
 * [Data Flow]
 *   App(gameState 상태) --props--> 입력값 표시
 *   사용자 입력 --> onChange({ ...gameState, [필드]: 값 }) --> App 상태 갱신
 *     --> App이 유효한 값만 currentLevel / currentGold / currentStage로 변환해 추천 요청에 포함
 *     --> 백엔드 TftSystemRuleEngine이 이자 경고·확률 팁·크립 라운드 안내를 계산
 *   빈 칸은 "입력 안 함"으로 보고 해당 피드백을 받지 않는다.
 */
export const STAGE_PATTERN = /^\d+-\d+$/

export default function GameStatePanel({ gameState, onChange }) {
  const { level, gold, stage } = gameState
  const update = (field, value) => onChange({ ...gameState, [field]: value })

  const stepLevel = (delta) => {
    const current = level === '' ? 1 : Number(level)
    update('level', String(Math.min(10, Math.max(1, current + delta))))
  }

  const isStageInvalid = stage !== '' && !isValidStage(stage)

  return (
    <section className="panel game-state">
      <div className="state-field">
        <span className="state-label" id="level-label">레벨</span>
        <div className="counter">
          <button type="button" onClick={() => stepLevel(-1)} disabled={level === '1'} aria-label="레벨 내리기">−</button>
          <input
            className="state-input"
            inputMode="numeric"
            placeholder="-"
            aria-labelledby="level-label"
            value={level}
            onChange={(e) => {
              const value = e.target.value.replace(/\D/g, '')
              update('level', value === '' ? '' : String(Math.min(10, Math.max(1, Number(value)))))
            }}
          />
          <button type="button" onClick={() => stepLevel(1)} disabled={level === '10'} aria-label="레벨 올리기">+</button>
        </div>
      </div>

      <label className="state-field">
        <span className="state-label">골드</span>
        <input
          className="state-input gold"
          inputMode="numeric"
          placeholder="0"
          value={gold}
          onChange={(e) => update('gold', e.target.value.replace(/\D/g, '').slice(0, 3))}
        />
      </label>

      <label className="state-field">
        <span className="state-label">스테이지</span>
        <input
          className={`state-input stage ${isStageInvalid ? 'is-invalid' : ''}`}
          placeholder="3-2"
          value={stage}
          aria-invalid={isStageInvalid}
          onChange={(e) => update('stage', e.target.value.replace(/[^\d-]/g, '').slice(0, 4))}
        />
      </label>
    </section>
  )
}
