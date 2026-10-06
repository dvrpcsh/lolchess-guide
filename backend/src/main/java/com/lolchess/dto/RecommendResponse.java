package com.lolchess.dto;

import java.util.List;

/**
 * [역할] 덱 추천 API(POST /api/v1/recommend)의 응답 항목. 추천 덱 1개를 나타낸다.
 *
 * [Data Flow]
 *   RecommendationService가 MetaCompEntity + 점수 계산 결과로 생성
 *     --> RecommendationController --> Jackson 직렬화 --> Client(React) JSON
 *
 * @param compName     덱 이름
 * @param tier         덱 티어 ("S" / "A" / "B")
 * @param matchScore   기물 + 아이템 + 티어 점수의 총합 (높을수록 현재 상황에 적합)
 * @param unitsToBuy   현재 상점에서 매수하면 좋은 이 덱의 핵심 기물
 * @param matchedItems 보유 아이템 중 이 덱의 추천 아이템과 일치하는 목록
 * @param description  덱 운영 및 아이템 팁
 */
public record RecommendResponse(
        String compName,
        String tier,
        int matchScore,
        List<String> unitsToBuy,
        List<String> matchedItems,
        String description
) {
}
