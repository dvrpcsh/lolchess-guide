package com.lolchess.dto;

import java.util.List;
import java.util.Map;

/**
 * [역할] 덱 추천 API(POST /api/v1/recommend)의 요청 본문.
 *   사용자의 현재 게임 상황(상점 기물, 보유 기물, 보유 아이템)을 서버로 전달한다.
 *
 * [Data Flow]
 *   Client(React) JSON --> Jackson 역직렬화 --> RecommendRequest
 *     --> RecommendationController --> RecommendationService.recommend()
 *
 * @param shopUnits  현재 상점에 뜬 기물 이름 목록 (최대 5개)
 * @param boardUnits 필드/벤치에 보유한 기물 이름 목록
 * @param itemCounts 보유 아이템 이름 -> 수량 (예: {"B.F. 대검": 1, "곡궁": 2})
 */
public record RecommendRequest(
        List<String> shopUnits,
        List<String> boardUnits,
        Map<String, Integer> itemCounts
) {
    // 클라이언트가 필드를 생략해도 Service에서 null 체크 없이 다룰 수 있도록 빈 컬렉션으로 정규화
    public RecommendRequest {
        shopUnits = shopUnits != null ? shopUnits : List.of();
        boardUnits = boardUnits != null ? boardUnits : List.of();
        itemCounts = itemCounts != null ? itemCounts : Map.of();
    }
}
