package com.lolchess.dto;

import java.util.List;
import java.util.Map;

/**
 * [역할] 덱 추천 API(POST /api/v1/recommend)의 응답 항목. 추천 덱 1개와 운영 피드백을 나타낸다.
 *
 * [Data Flow]
 *   RecommendationService가 MetaCompEntity + 점수 계산 결과 + TftSystemRuleEngine 피드백으로 생성
 *     --> RecommendationController --> Jackson 직렬화 --> Client(React) JSON
 *
 * @param compName         덱 이름
 * @param tier             덱 티어 ("S" / "A" / "B")
 * @param compType         운영 타입 ("REROLL" / "FAST_8" / "VALUE_9", 미지정이면 null)
 * @param matchScore       기물 + 아이템 + 티어 점수의 총합 (높을수록 현재 상황에 적합)
 * @param unitsToBuy       현재 상점에서 매수하면 좋은 이 덱의 핵심 기물
 * @param matchedItems     보유 아이템 중 이 덱의 추천 아이템과 일치하는 목록
 * @param description      덱 운영 및 아이템 팁
 * @param interestWarnings 매수 추천 기물을 샀을 때 이자 구간이 깨지는 경우의 경고 (골드 미입력 시 빈 목록)
 * @param probabilityTips  현재 레벨에서 이 덱의 고코스트 핵심 기물이 잘 나오지 않을 때의 확률 안내 (레벨 미입력 시 빈 목록)
 * @param roundTip         다음 라운드가 크립 라운드일 때의 안내 (해당 없으면 null)
 * @param actionBriefings  지금 당장 할 행동 가이드 (우선순위 순, 1순위 덱에만 포함되고 나머지 덱은 빈 목록)
 * @param buildUpGuide     레벨(4~9) -> 그 레벨에서 올릴 추천 기물 (덱 카드 클릭 시 상세 가이드 모달에 표시)
 * @param unitItemMap      핵심 기물 -> 추천 완성 아이템 (상세 가이드 모달에 표시)
 */
public record RecommendResponse(
        String compName,
        String tier,
        String compType,
        int matchScore,
        List<String> unitsToBuy,
        List<String> matchedItems,
        String description,
        List<String> interestWarnings,
        List<String> probabilityTips,
        String roundTip,
        List<String> actionBriefings,
        Map<Integer, List<String>> buildUpGuide,
        Map<String, List<String>> unitItemMap
) {
}
