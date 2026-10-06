package com.lolchess.dto;

import java.util.List;

/**
 * [역할] 체스판/벤치에 놓인 기물 한 개의 상세 정보 (추천 요청의 일부).
 *
 * [Data Flow]
 *   Client 체스판 상태 { championId, starLevel, items[3] } --> 이름/성급/장착 아이템으로 변환
 *     --> RecommendRequest.placedUnits --> RecommendationService
 *       - starLevel: 덱 핵심 기물이 2성/3성이면 성급 가중치
 *       - items    : 덱 핵심 기물에 덱 추천 아이템이 장착되어 있으면 장착 가중치
 *
 * @param name      챔피언 한글 이름
 * @param starLevel 성급 (1~3, 범위를 벗어나면 1~3으로 보정)
 * @param items     장착 아이템 이름 목록 (최대 3개, 빈 슬롯은 제외하고 전송)
 */
public record PlacedUnitRequest(String name, Integer starLevel, List<String> items) {

    public static final int MIN_STAR = 1;
    public static final int MAX_STAR = 3;

    public PlacedUnitRequest {
        starLevel = starLevel == null ? MIN_STAR : Math.min(MAX_STAR, Math.max(MIN_STAR, starLevel));
        items = items == null ? List.of() : items.stream().filter(i -> i != null && !i.isBlank()).map(String::trim).toList();
    }
}
