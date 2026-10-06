package com.lolchess.dto;

import com.lolchess.entity.ChampionEntity;

/**
 * [역할] 챔피언 목록 조회 API(GET /api/v1/champions)의 응답 항목.
 *
 * [Data Flow]
 *   ChampionRepository --> ChampionEntity --> ChampionResponse.from() --> Client JSON
 *   프론트엔드는 작은 아이콘을 spriteUrl + (spriteX, spriteY) 위치의 48x48 영역으로 표시하고,
 *   큰 이미지가 필요할 때만 iconUrl(원본 스플래시)을 사용한다.
 */
public record ChampionResponse(String championId, String name, int cost, String iconUrl,
                               String spriteUrl, Integer spriteX, Integer spriteY) {

    public static ChampionResponse from(ChampionEntity entity) {
        return new ChampionResponse(entity.getChampionId(), entity.getName(), entity.getCost(), entity.getIconUrl(),
                entity.getSpriteUrl(), entity.getSpriteX(), entity.getSpriteY());
    }
}
