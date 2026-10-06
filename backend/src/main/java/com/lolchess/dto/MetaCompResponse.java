package com.lolchess.dto;

import com.lolchess.entity.MetaCompEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [역할] 메타 덱 목록 조회 API(GET /api/v1/meta-comps)의 응답 항목.
 *   MetaCompEntity를 외부에 직접 노출하지 않기 위한 응답 전용 DTO.
 *
 * [Data Flow]
 *   MetaCompRepository 조회 --> MetaCompEntity
 *     --> RecommendationService에서 MetaCompResponse.from() 으로 변환 (트랜잭션 안에서 지연 로딩 컬렉션 접근)
 *     --> RecommendationController --> Client JSON
 *
 * buildUpGuide: 레벨(4~9) -> 추천 빌드업 기물 / unitItemMap: 핵심 기물 -> 추천 완성 아이템
 */
public record MetaCompResponse(
        Long id,
        String name,
        String tier,
        String compType,
        List<String> coreUnits,
        List<String> recommendedItems,
        String description,
        Map<Integer, List<String>> buildUpGuide,
        Map<String, List<String>> unitItemMap
) {
    public static MetaCompResponse from(MetaCompEntity entity) {
        return new MetaCompResponse(
                entity.getId(),
                entity.getName(),
                entity.getTier().name(),
                entity.getCompType() != null ? entity.getCompType().name() : null,
                List.copyOf(entity.getCoreUnits()),
                List.copyOf(entity.getRecommendedItems()),
                entity.getDescription(),
                Collections.unmodifiableMap(new LinkedHashMap<>(entity.getBuildUpGuide())),
                Collections.unmodifiableMap(new LinkedHashMap<>(entity.getUnitItemMap()))
        );
    }
}
