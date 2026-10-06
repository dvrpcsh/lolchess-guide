package com.lolchess.dto;

import com.lolchess.entity.MetaCompEntity;

import java.util.List;

/**
 * [역할] 메타 덱 목록 조회 API(GET /api/v1/meta-comps)의 응답 항목.
 *   MetaCompEntity를 외부에 직접 노출하지 않기 위한 응답 전용 DTO.
 *
 * [Data Flow]
 *   MetaCompRepository 조회 --> MetaCompEntity
 *     --> RecommendationService에서 MetaCompResponse.from() 으로 변환 (트랜잭션 안에서 지연 로딩 컬렉션 접근)
 *     --> RecommendationController --> Client JSON
 */
public record MetaCompResponse(
        Long id,
        String name,
        String tier,
        List<String> coreUnits,
        List<String> recommendedItems,
        String description
) {
    public static MetaCompResponse from(MetaCompEntity entity) {
        return new MetaCompResponse(
                entity.getId(),
                entity.getName(),
                entity.getTier().name(),
                List.copyOf(entity.getCoreUnits()),
                List.copyOf(entity.getRecommendedItems()),
                entity.getDescription()
        );
    }
}
