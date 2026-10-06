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
 * GET /api/v1/meta-comps - 기물 선택 후보 목록을 만들기 위해 등록된 메타 덱 전체를 조회
 */
export async function fetchMetaComps() {
  const { data } = await client.get('/api/v1/meta-comps')
  return data
}
