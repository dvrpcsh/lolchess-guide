package com.lolchess.service;

import com.lolchess.dto.ItemRecipeResponse;
import com.lolchess.entity.ItemEntity;
import com.lolchess.repository.ItemRepository;
import com.lolchess.rule.ItemRecipeBook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [역할] id 기반 아이템 조합표(ItemRecipeBook)를 동기화된 한글 아이템 이름으로 변환해 제공하는 서비스.
 *
 * [Data Flow]
 *   ItemRepository.findAll() --> id -> 한글 이름 맵
 *   ItemRecipeBook.recipes() --> 이름으로 변환 (item 테이블에 없는 id가 섞인 조합은 제외)
 *     --> RiotDataDragonService.getItems()  : GET /api/v1/items 응답의 recipes
 *     --> RecommendationService             : 완성 아이템 이름 -> 재료 2개 분해
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemRecipeService {

    private final ItemRepository itemRepository;
    private final ItemRecipeBook itemRecipeBook;

    /**
     * 한글 이름 기준 조합법 목록
     */
    public List<ItemRecipeResponse> getRecipes() {
        Map<String, String> nameById = itemRepository.findAll().stream()
                .collect(Collectors.toMap(ItemEntity::getItemId, ItemEntity::getName, (a, b) -> a));
        return itemRecipeBook.recipes().stream()
                .filter(r -> nameById.containsKey(r.componentA())
                        && nameById.containsKey(r.componentB())
                        && nameById.containsKey(r.result()))
                .map(r -> new ItemRecipeResponse(
                        nameById.get(r.componentA()), nameById.get(r.componentB()), nameById.get(r.result())))
                .toList();
    }

    /**
     * 완성 아이템 이름 -> 재료 아이템 이름 2개
     */
    public Map<String, List<String>> getComponentsByCompletedName() {
        return getRecipes().stream()
                .collect(Collectors.toMap(ItemRecipeResponse::result,
                        r -> List.of(r.componentA(), r.componentB()), (a, b) -> a));
    }
}
