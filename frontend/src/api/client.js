import axios from 'axios'

/**
 * [역할] 백엔드(Spring Boot) REST API 호출을 담당하는 Axios 인스턴스와 API 함수 모음.
 *
 * [Data Flow]
 *   컴포넌트 --> fetchRecommendations / fetchMetaComps
 *     --> axios 요청 (/api/v1/...)
 *     --> 개발 환경: Vite proxy가 http://localhost:8080 으로 전달 (vite.config.js)
 *         그 외 환경: VITE_API_BASE_URL 로 직접 요청
 *     --> Spring Boot RecommendationController --> JSON 응답 --> 컴포넌트 상태로 저장
 */
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 5000,
})

/**
 * POST /api/v1/recommend
 * @param {{ shopUnits: string[], boardUnits: string[], itemCounts: Record<string, number> }} request
 * @param {AbortSignal} [signal] 입력이 바뀌어 이전 요청이 필요 없어졌을 때 취소하기 위한 신호
 * @returns {Promise<Array<{compName, tier, matchScore, unitsToBuy, matchedItems, description}>>}
 */
export async function fetchRecommendations(request, signal) {
  const { data } = await client.post('/api/v1/recommend', request, { signal })
  return data
}

/**
 * GET /api/v1/champions - 시즌 전체 챔피언 목록 (Data Dragon 동기화 데이터, 코스트 순)
 * @returns {Promise<Array<{championId, name, cost, iconUrl, spriteUrl, spriteX, spriteY}>>}
 */
export async function fetchChampions() {
  const { data } = await client.get('/api/v1/champions')
  return data
}

/**
 * GET /api/v1/items - 재료 아이템 / 조합 아이템 목록 (Data Dragon 동기화 데이터)
 * @returns {Promise<{ components: Array<{itemId, name, iconUrl}>, combined: Array<{itemId, name, iconUrl}> }>}
 */
export async function fetchItems() {
  const { data } = await client.get('/api/v1/items')
  return data
}
