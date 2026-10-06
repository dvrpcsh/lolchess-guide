package com.lolchess.dto;

import com.lolchess.entity.ItemEntity;

/**
 * [역할] 아이템 한 개의 응답 표현. ItemListResponse의 재료/조합 목록 원소로 사용된다.
 *
 * [Data Flow]
 *   ItemRepository --> ItemEntity --> ItemResponse.from() --> ItemListResponse --> Client JSON
 */
public record ItemResponse(String itemId, String name, String iconUrl) {

    public static ItemResponse from(ItemEntity entity) {
        return new ItemResponse(entity.getItemId(), entity.getName(), entity.getIconUrl());
    }
}
