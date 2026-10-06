package com.lolchess.dto;

import com.lolchess.entity.ChampionEntity;

/**
 * [역할] 챔피언 목록 조회 API(GET /api/v1/champions)의 응답 항목.
 *
 * [Data Flow]
 *   ChampionRepository --> ChampionEntity --> ChampionResponse.from() --> Client JSON
 */
public record ChampionResponse(String championId, String name, int cost, String iconUrl) {

    public static ChampionResponse from(ChampionEntity entity) {
        return new ChampionResponse(entity.getChampionId(), entity.getName(), entity.getCost(), entity.getIconUrl());
    }
}
