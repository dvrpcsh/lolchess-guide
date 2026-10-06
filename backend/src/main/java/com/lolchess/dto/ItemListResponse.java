package com.lolchess.dto;

import java.util.List;

/**
 * [역할] 아이템 목록 조회 API(GET /api/v1/items)의 응답.
 *   프론트엔드가 재료 아이템 선택 UI와 조합 아이템 표시를 따로 그릴 수 있도록 두 목록으로 나눠 전달한다.
 *
 * @param components 기본 재료 아이템 (B.F. 대검, 곡궁 등)
 * @param combined   재료 2개로 만드는 조합 아이템 (무한의 대검 등)
 */
public record ItemListResponse(List<ItemResponse> components, List<ItemResponse> combined) {
}
