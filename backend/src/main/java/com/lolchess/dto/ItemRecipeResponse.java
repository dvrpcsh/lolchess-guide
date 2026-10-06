package com.lolchess.dto;

/**
 * [역할] 아이템 조합법 한 개의 응답 표현 (한글 이름 기준). ItemListResponse.recipes의 원소.
 *
 * [Data Flow]
 *   ItemRecipeBook(id 조합표) --> ItemRecipeService가 item 테이블로 이름 변환 --> ItemRecipeResponse
 *     --> GET /api/v1/items --> 프론트엔드 itemRecipes.js (자동 합성 / 완성 아이템 분해)
 *
 * @param componentA 재료 아이템 이름
 * @param componentB 재료 아이템 이름 (componentA와 같을 수 있음, 예: 대검 + 대검)
 * @param result     완성 아이템 이름
 */
public record ItemRecipeResponse(String componentA, String componentB, String result) {
}
