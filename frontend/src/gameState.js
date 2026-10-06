/**
 * [역할] 게임 상태(레벨/골드/스테이지/체력) 입력값의 초기값과 검증·변환 규칙.
 *
 * [Data Flow]
 *   GameStatePanel 입력(문자열) --> App gameState 상태
 *     --> toGameStateRequest() 로 유효한 값만 숫자/문자열로 변환 (빈 칸·잘못된 값은 null)
 *     --> POST /api/v1/recommend 의 currentLevel / currentGold / currentStage / playerHp
 */
// 게임 시작 레벨은 1, 체력은 100이므로 기본값으로 둔다 (골드/스테이지는 미입력)
export const INITIAL_GAME_STATE = { level: '1', gold: '', stage: '', hp: '100' }
export const MAX_HP = 100

export const STAGE_PATTERN = /^\d+-\d+$/

export function isValidStage(stage) {
  return STAGE_PATTERN.test(stage.trim())
}

export function toGameStateRequest({ level, gold, stage, hp }) {
  return {
    currentLevel: level === '' ? null : Number(level),
    currentGold: gold === '' ? null : Number(gold),
    currentStage: isValidStage(stage) ? stage.trim() : null,
    playerHp: hp === '' ? null : Number(hp), // 비우면 백엔드가 100으로 간주
  }
}
