package com.lolchess.service;

import com.lolchess.dto.ItemRecipeResponse;
import com.lolchess.dto.PlacedUnitRequest;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.entity.CompType;
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
 *   0) 체력 위기         : 체력 35 이하이고 Fast 8 / 9렙 밸류 덱이면 이자를 깨고 즉시 8렙 리롤 (구루루 챌린저 피관리)
 *   1) 아이템 조합 & 장착 : 인벤토리 재료 2개로 덱에 맞는 완성 아이템을 만들 수 있고, 장착할 핵심 기물이 필드에 있을 때
 *      + 도적의 장갑     : 핵심 딜러에게 필요한 장갑을 빼고도 연습용 장갑이 2개 이상 남으면 아이템 없는 서브 기물에게 도적의 장갑
 *   2) 레벨업            : 표준 레벨업 타이밍(2-1 4렙 ... 5-1 9렙)에 도달했는데 레벨이 낮을 때
 *      + 운영 타입 가이드 : 리롤 덱은 50원 유지 3성작, 9렙 밸류 덱은 체력 70 이상이면 9렙 고밸류 전환
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
    private static final int DANGER_HP = 35;   // 이하이면 피관리 위기
    private static final int HEALTHY_HP = 70;  // 이상이면 체력 여유
    private static final String SPARRING_GLOVES = "연습용 장갑";
    private static final String THIEFS_GLOVES_FALLBACK = "도적의 장갑";

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
        buildHpDangerGuide(request, topComp).ifPresent(briefings::add);
        buildItemGuide(request, topComp, unitCosts).ifPresent(briefings::add);
        buildThiefsGlovesGuide(request, topComp, unitCosts).ifPresent(briefings::add);
        buildLevelGuide(request).ifPresent(briefings::add);
        buildCompTypeGuide(request, topComp).ifPresent(briefings::add);
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
                // 도적의 장갑(장갑 + 장갑)은 슬롯 3칸을 모두 차지해 딜러용이 아니므로 별도 가이드(buildThiefsGlovesGuide)에서만 다룬다
                .filter(r -> !(SPARRING_GLOVES.equals(r.componentA()) && SPARRING_GLOVES.equals(r.componentB())))
                .filter(r -> recommended.contains(r.result())
                        || (recommended.contains(r.componentA()) && recommended.contains(r.componentB())))
                .filter(r -> canCraft(r, inventory))
                .min(Comparator.comparingInt((ItemRecipeResponse r) -> recommended.contains(r.result()) ? 0 : 1)
                        .thenComparingInt(r -> recommendedOrder(topComp, r)))
                .map(r -> "⚔️ %s %s 합성하여 %s 만들고 [%s]에게 장착하세요!".formatted(
                        KoreanJosa.andOf(r.componentA()), KoreanJosa.objectOf(r.componentB()),
                        KoreanJosa.objectOf(r.result()), target.get()));
    }

    /**
     * 0) 체력이 위험한데 고레벨을 노리는 덱(Fast 8 / 9렙 밸류)이면 이자를 포기하고 즉시 전력을 올리게 한다.
     */
    private Optional<String> buildHpDangerGuide(RecommendRequest request, MetaCompEntity topComp) {
        CompType type = topComp.getCompType();
        boolean highLevelComp = type == CompType.FAST_8 || type == CompType.VALUE_9;
        if (highLevelComp && request.playerHp() <= DANGER_HP) {
            return Optional.of("🚨 체력이 위험합니다! 50원 이자를 깨고 즉시 8레벨 리롤을 돌려 2성작 보드를 완성하세요!");
        }
        return Optional.empty();
    }

    /**
     * 2-1) 운영 타입별 기본 운영 가이드.
     *   - 리롤 덱: 이자를 유지하며 낮은 레벨에서 3성작
     *   - 9렙 밸류 덱: 체력이 넉넉하면 8렙 최소 전력으로 버티고 9렙 고밸류 전환 (체력 위기면 0)번 가이드가 대신 나온다)
     */
    private Optional<String> buildCompTypeGuide(RecommendRequest request, MetaCompEntity topComp) {
        CompType type = topComp.getCompType();
        if (type == CompType.REROLL) {
            return Optional.of("⭐ 50원 이자를 유지하며 해당 레벨(6/7렙)에서 핵심 3성작을 완료하세요.");
        }
        if (type == CompType.VALUE_9 && request.playerHp() >= HEALTHY_HP) {
            return Optional.of("👑 체력이 유복합니다. 8렙에서 필드 최소 전력만 갖추고 9레벨 고밸류 전환을 노리세요.");
        }
        return Optional.empty();
    }

    /**
     * 1-1) 핵심 딜러(덱 기물별 아이템 가이드의 첫 기물)에게 필요한 연습용 장갑을 빼고도 2개 이상 남으면,
     *      아이템이 없는 다른 핵심 기물(없으면 다른 배치 기물)에게 도적의 장갑을 만들어 주도록 권한다.
     *      도적의 장갑은 아이템 슬롯 3칸을 모두 차지하므로 아이템이 하나도 없는 기물만 대상으로 한다.
     */
    private Optional<String> buildThiefsGlovesGuide(RecommendRequest request, MetaCompEntity topComp,
                                                    Map<String, Integer> unitCosts) {
        int gloves = request.itemCounts().entrySet().stream()
                .filter(e -> e.getKey() != null && SPARRING_GLOVES.equals(e.getKey().trim()) && e.getValue() != null)
                .mapToInt(Map.Entry::getValue).sum();
        if (gloves < 2) {
            return Optional.empty();
        }

        List<ItemRecipeResponse> recipes = itemRecipeService.getRecipes();
        Map<String, List<String>> componentsByCompleted = recipes.stream()
                .collect(Collectors.toMap(ItemRecipeResponse::result,
                        r -> List.of(r.componentA(), r.componentB()), (a, b) -> a));
        Set<String> placedNames = request.placedUnits().stream().map(u -> u.name().trim())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 핵심 딜러: 기물별 아이템 가이드 중 필드에 있는 첫 기물 (없으면 가이드의 첫 기물)
        Map<String, List<String>> unitItemMap = topComp.getUnitItemMap();
        Optional<String> carry = unitItemMap.keySet().stream().filter(placedNames::contains).findFirst()
                .or(() -> unitItemMap.keySet().stream().findFirst());

        // 딜러가 아직 장착하지 않은 추천 아이템에 들어가는 연습용 장갑 수
        List<String> carryEquipped = carry.map(name -> request.placedUnits().stream()
                .filter(u -> u.name().trim().equals(name))
                .flatMap(u -> u.items().stream()).toList()).orElse(List.of());
        long glovesForCarry = carry.map(name -> unitItemMap.get(name).stream()
                .filter(item -> !carryEquipped.contains(item))
                .flatMap(item -> componentsByCompleted.getOrDefault(item, List.of()).stream())
                .filter(SPARRING_GLOVES::equals)
                .count()).orElse(0L);
        if (gloves - glovesForCarry < 2) {
            return Optional.empty();
        }

        List<String> coreUnits = topComp.getCoreUnits();
        Optional<String> target = request.placedUnits().stream()
                .filter(u -> u.items().isEmpty() && carry.map(c -> !c.equals(u.name().trim())).orElse(true))
                .map(u -> u.name().trim())
                .distinct()
                .max(Comparator.comparing((String name) -> coreUnits.contains(name)) // 핵심 기물 우선
                        .thenComparingInt(name -> unitCosts.getOrDefault(name, 0)));
        if (target.isEmpty()) {
            return Optional.empty();
        }

        String thiefsGloves = recipes.stream()
                .filter(r -> SPARRING_GLOVES.equals(r.componentA()) && SPARRING_GLOVES.equals(r.componentB()))
                .map(ItemRecipeResponse::result).findFirst().orElse(THIEFS_GLOVES_FALLBACK);
        String carryNote = carry.map(c -> " (핵심 딜러 [%s] 아이템을 챙기고 남는 장갑 활용)".formatted(c)).orElse("");
        return Optional.of("🧤 남는 [%s] 2개로 %s 만들어 [%s]에게 장착하세요!%s".formatted(
                SPARRING_GLOVES, KoreanJosa.objectOf(thiefsGloves), target.get(), carryNote));
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
