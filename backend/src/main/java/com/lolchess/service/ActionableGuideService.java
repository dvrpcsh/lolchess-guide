package com.lolchess.service;

import com.lolchess.dto.ItemRecipeResponse;
import com.lolchess.dto.PlacedUnitRequest;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.rule.TftSystemRuleEngine;
import com.lolchess.util.KoreanJosa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [역할] 플레이어의 현재 상황과 1순위 추천 덱을 분석해 "지금 당장 할 행동"을 한 줄 문장 목록으로 만드는 코치 엔진.
 *
 * [Data Flow]
 *   RecommendationService (1순위 덱 결정 후) --> buildBriefings(request, topComp, unitsToBuy, unitCosts)
 *     --> ItemRecipeService(조합표) + TftSystemRuleEngine(레벨 타이밍, 이자, 크립 라운드) 참조
 *     --> 우선순위 순서의 문장 목록 --> RecommendResponse.actionBriefings (1순위 덱 카드에만 포함)
 *     --> 프론트엔드 "실시간 코치 브리핑" 배너
 *
 * [가이드 우선순위] (위에서부터 시급한 순서, 해당하는 것만 포함)
 *   1) 아이템 조합 & 장착 : 인벤토리 재료 2개로 덱에 맞는 완성 아이템을 만들 수 있고, 장착할 핵심 기물이 필드에 있을 때
 *   2) 레벨업            : 표준 레벨업 타이밍(2-1 4렙 ... 5-1 9렙)에 도달했는데 레벨이 낮을 때
 *   3) 상점 매수         : 상점 기물로 2성/3성이 완성되거나, 상점에 덱 핵심 기물이 있을 때
 *   4) 골드 킵           : 다음 라운드가 크립 라운드이거나, 조금만 모으면 이자가 늘어날 때
 */
@Service
@RequiredArgsConstructor
public class ActionableGuideService {

    private static final int MAX_ITEMS_PER_UNIT = 3;
    private static final int COPIES_FOR_STAR_UP = 3;
    private static final int GOLD_PER_INTEREST = 10;
    private static final int MAX_INTEREST_GOLD = 50;
    private static final int INTEREST_GAP_TO_SAVE = 3; // 다음 이자 구간까지 이 골드 이하로 남으면 "모으기" 권장

    private final ItemRecipeService itemRecipeService;
    private final TftSystemRuleEngine ruleEngine;

    /**
     * @param request    추천 요청 (게임 상태, 인벤토리, 배치 기물, 상점)
     * @param topComp    1순위 추천 덱
     * @param unitsToBuy 1순위 덱 기준 상점 매수 추천 기물
     * @param unitCosts  챔피언 이름 -> 코스트 (장착 대상 기물 선정용)
     */
    public List<String> buildBriefings(RecommendRequest request, MetaCompEntity topComp,
                                       List<String> unitsToBuy, Map<String, Integer> unitCosts) {
        List<String> briefings = new ArrayList<>();
        buildItemGuide(request, topComp, unitCosts).ifPresent(briefings::add);
        buildLevelGuide(request).ifPresent(briefings::add);
        briefings.addAll(buildShopGuides(request, unitsToBuy));
        buildGoldGuide(request).ifPresent(briefings::add);
        return briefings;
    }

    /**
     * 1) 인벤토리 재료로 덱에 맞는 완성 아이템을 만들 수 있으면, 빈 슬롯이 있는 가장 비싼 핵심 기물에게 장착을 권한다.
     *    "덱에 맞는 완성 아이템" = 덱 추천 목록에 그 완성 아이템이 있거나, 재료 2개가 모두 덱 추천 재료인 것
     */
    private Optional<String> buildItemGuide(RecommendRequest request, MetaCompEntity topComp, Map<String, Integer> unitCosts) {
        Map<String, Integer> inventory = request.itemCounts().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .collect(Collectors.toMap(e -> e.getKey().trim(), Map.Entry::getValue, Integer::sum));
        Set<String> recommended = new HashSet<>(topComp.getRecommendedItems());
        List<String> coreUnits = topComp.getCoreUnits();

        Optional<String> target = request.placedUnits().stream()
                .filter(unit -> coreUnits.contains(unit.name()) && unit.items().size() < MAX_ITEMS_PER_UNIT)
                .map(PlacedUnitRequest::name)
                .distinct()
                .max(Comparator.comparingInt((String name) -> unitCosts.getOrDefault(name, 0))
                        .thenComparing(name -> -coreUnits.indexOf(name))); // 코스트가 같으면 덱에 먼저 정의된 기물
        if (target.isEmpty()) {
            return Optional.empty();
        }

        return itemRecipeService.getRecipes().stream()
                .filter(r -> recommended.contains(r.result())
                        || (recommended.contains(r.componentA()) && recommended.contains(r.componentB())))
                .filter(r -> canCraft(r, inventory))
                .min(Comparator.comparingInt((ItemRecipeResponse r) -> recommended.contains(r.result()) ? 0 : 1)
                        .thenComparingInt(r -> recommendedOrder(topComp, r)))
                .map(r -> "⚔️ %s %s 합성하여 %s 만들고 [%s]에게 장착하세요!".formatted(
                        KoreanJosa.andOf(r.componentA()), KoreanJosa.objectOf(r.componentB()),
                        KoreanJosa.objectOf(r.result()), target.get()));
    }

    private boolean canCraft(ItemRecipeResponse recipe, Map<String, Integer> inventory) {
        if (recipe.componentA().equals(recipe.componentB())) {
            return inventory.getOrDefault(recipe.componentA(), 0) >= 2;
        }
        return inventory.getOrDefault(recipe.componentA(), 0) >= 1 && inventory.getOrDefault(recipe.componentB(), 0) >= 1;
    }

    // 덱 추천 목록에서 먼저 나오는 재료로 만드는 조합을 우선 (추천 목록 순서 = 우선순위)
    private int recommendedOrder(MetaCompEntity comp, ItemRecipeResponse recipe) {
        List<String> items = comp.getRecommendedItems();
        int a = items.indexOf(recipe.componentA());
        int b = items.indexOf(recipe.componentB());
        return (a < 0 ? items.size() : a) + (b < 0 ? items.size() : b);
    }

    /**
     * 2) 가장 최근에 도달한 레벨업 타이밍보다 현재 레벨이 낮으면 레벨업을 권한다.
     */
    private Optional<String> buildLevelGuide(RecommendRequest request) {
        Integer level = request.currentLevel();
        String stage = request.currentStage();
        if (level == null || stage == null) {
            return Optional.empty();
        }
        return ruleEngine.latestLevelTiming(stage)
                .filter(timing -> level < timing.level())
                .map(timing -> timing.stage().equals(stage.trim())
                        ? "⬆️ %s 라운드입니다. 골드를 사용하여 %d레벨을 달성하세요.".formatted(stage.trim(), timing.level())
                        : "⬆️ %s 라운드입니다. 표준 운영(%s %d렙)보다 늦었으니 골드를 사용하여 %d레벨을 달성하세요."
                        .formatted(stage.trim(), timing.stage(), timing.level(), timing.level()));
    }

    /**
     * 3) 상점 기물로 2성/3성을 완성할 수 있으면 먼저 알리고, 그 외 덱 핵심 기물은 매수를 권한다.
     *    필드/벤치의 1성 기물 + 상점의 같은 기물이 3개 이상이면 2성 완성,
     *    2성 기물이 2개 이상인데 다시 2성이 완성되면 3성 완성으로 판단한다.
     */
    private List<String> buildShopGuides(RecommendRequest request, List<String> unitsToBuy) {
        Map<String, Long> shopCopies = request.shopUnits().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<String> guides = new ArrayList<>();
        Set<String> alreadyGuided = new LinkedHashSet<>();
        shopCopies.forEach((name, inShop) -> {
            long oneStar = countPlaced(request, name, 1);
            long twoStar = countPlaced(request, name, 2);
            if (oneStar + inShop >= COPIES_FOR_STAR_UP) {
                boolean threeStar = twoStar >= COPIES_FOR_STAR_UP - 1;
                guides.add(threeStar
                        ? "🌟 상점의 %s 매수하면 3성이 완성됩니다!".formatted(KoreanJosa.objectOf(name))
                        : "⭐ 상점의 %s 매수하면 2성이 완성됩니다!".formatted(KoreanJosa.objectOf(name)));
                alreadyGuided.add(name);
            }
        });
        unitsToBuy.stream()
                .filter(name -> !alreadyGuided.contains(name))
                .forEach(name -> guides.add("🛒 상점의 %s 매수하세요!".formatted(KoreanJosa.objectOf(name))));
        return guides;
    }

    private long countPlaced(RecommendRequest request, String name, int starLevel) {
        return request.placedUnits().stream()
                .filter(unit -> unit.name().trim().equals(name) && unit.starLevel() == starLevel)
                .count();
    }

    /**
     * 4) 다음 라운드가 크립 라운드면 골드 아끼기, 아니면 다음 이자 구간까지 조금 남았을 때 모으기를 권한다.
     */
    private Optional<String> buildGoldGuide(RecommendRequest request) {
        boolean nextIsPve = ruleEngine.nextRound(request.currentStage()).filter(ruleEngine::isPveRound).isPresent();
        if (nextIsPve) {
            return Optional.of("💰 다음 라운드는 크립 라운드입니다. 골드를 아껴 이자를 챙기세요.");
        }
        Integer gold = request.currentGold();
        if (gold == null || gold < 0 || gold >= MAX_INTEREST_GOLD) {
            return Optional.empty();
        }
        int gap = GOLD_PER_INTEREST - gold % GOLD_PER_INTEREST;
        if (gap > INTEREST_GAP_TO_SAVE) {
            return Optional.empty();
        }
        return Optional.of("💰 %d골드만 더 모으면 이자가 %d골드로 늘어납니다. 불필요한 소비를 아끼세요."
                .formatted(gap, ruleEngine.calculateInterest(gold) + 1));
    }
}
