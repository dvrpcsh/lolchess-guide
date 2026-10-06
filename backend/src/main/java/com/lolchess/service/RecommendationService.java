package com.lolchess.service;

import com.lolchess.dto.MetaCompResponse;
import com.lolchess.dto.PlacedUnitRequest;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.dto.RecommendResponse;
import com.lolchess.entity.ChampionEntity;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.repository.ChampionRepository;
import com.lolchess.repository.MetaCompRepository;
import com.lolchess.rule.TftSystemRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [역할] 사용자의 현재 게임 상황(상점/보유 기물, 보유 아이템, 레벨/골드/스테이지)과 DB의 메타 덱을 비교해
 *   가장 적합한 덱을 점수순으로 추천하고, TFT 시스템 규칙에 따른 운영 피드백을 덧붙이는 비즈니스 로직 계층.
 *
 * [Data Flow]
 *   RecommendationController --> recommend(RecommendRequest)
 *     --> MetaCompRepository.findAll() 로 전체 메타 덱 조회 (MySQL meta_comp + 컬렉션 테이블)
 *     --> ChampionRepository.findAll() 로 기물 이름 -> 코스트 맵 구성 (Data Dragon 동기화 데이터)
 *     --> placedUnits(체스판 기물 상세)에서 성급·장착 아이템을 모으고, 장착 아이템도 보유 아이템에 합산
 *     --> 덱마다 기물/아이템/티어 점수 계산
 *     --> 매칭 점수(기물+아이템)가 0점인 덱 제외 --> 총점 내림차순 정렬
 *     --> TftSystemRuleEngine으로 덱별 피드백(이자 경고, 확률 팁, 크립 라운드 안내) 생성
 *     --> RecommendResponse DTO 목록으로 변환하여 Controller에 반환
 *
 * [점수 규칙]
 *   - 기물 점수  : 덱 핵심 기물 중 보유(boardUnits) 기물당 +20, 상점(shopUnits) 등장 기물당 +10
 *   - 아이템 점수: 덱 추천 아이템과 일치하는 보유 아이템 개수당 +15
 *                 (보유 수량만큼 인정하되, 덱 추천 목록에 그 아이템이 등장하는 횟수를 상한으로 함)
 *   - 성급 점수  : 덱 핵심 기물이 2성이면 +10, 3성이면 +25 (같은 기물이 여러 개면 가장 높은 성급 기준)
 *   - 장착 점수  : 덱 핵심 기물에 장착된 아이템이 덱 추천 아이템이면 개당 +10
 *                 (장착 아이템은 위 "아이템 점수"의 보유 수량에도 포함되므로, 인벤토리에서 장착으로 옮겨도 점수가 줄지 않는다)
 *   - 티어 점수  : S +10, A +5, B +0
 *   티어 점수는 "현재 상황과의 일치도"가 아니므로, 기물/아이템이 하나도 맞지 않는 덱은
 *   티어와 관계없이 결과에서 제외한다. (빈 요청에 S티어 덱이 무조건 추천되는 것을 방지)
 *
 * [피드백 규칙]
 *   - 이자 경고 : 골드 입력 시, 매수 추천 기물을 하나 샀을 때 이자 구간이 내려가면 경고
 *   - 확률 팁   : 레벨 입력 시, 아직 보유하지 않은 4~5코스트 핵심 기물의 등장 확률이 20% 미만이면 안내
 *   - 라운드 안내: 스테이지 입력 시, 다음 라운드가 크립 라운드면 골드 모으기 권장
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 전용: 지연 로딩 컬렉션(coreUnits, recommendedItems)을 트랜잭션 안에서 접근
public class RecommendationService {

    private static final int BOARD_UNIT_SCORE = 20;
    private static final int SHOP_UNIT_SCORE = 10;
    private static final int ITEM_SCORE = 15;
    private static final int TWO_STAR_SCORE = 10;
    private static final int THREE_STAR_SCORE = 25;
    private static final int EQUIPPED_ITEM_SCORE = 10;

    private static final int HIGH_COST_THRESHOLD = 4;      // 확률 팁 대상 코스트 (4코 이상)
    private static final int LOW_PROBABILITY_PERCENT = 20; // 이 확률 미만이면 "잘 안 나온다"로 판단

    private final MetaCompRepository metaCompRepository;
    private final ChampionRepository championRepository;
    private final TftSystemRuleEngine ruleEngine;

    /**
     * 현재 게임 상황에 맞는 메타 덱을 총점 내림차순으로 반환한다.
     */
    public List<RecommendResponse> recommend(RecommendRequest request) {
        BoardContext board = BoardContext.from(request, normalize(request.boardUnits()));
        Set<String> boardUnits = board.units();
        // 요청 값의 앞뒤 공백을 제거하고 중복을 없애, 같은 기물이 상점에 2번 떠도 1번만 점수에 반영
        Set<String> shopUnits = normalize(request.shopUnits());

        Map<String, Integer> unitCosts = championRepository.findAll().stream()
                .collect(Collectors.toMap(ChampionEntity::getName, ChampionEntity::getCost, (a, b) -> a));
        String roundTip = buildRoundTip(request.currentStage());

        return metaCompRepository.findAll().stream()
                .map(comp -> score(comp, board, shopUnits))
                .filter(scored -> scored.matchScore() > 0)
                .sorted(Comparator.comparingInt(ScoredComp::totalScore).reversed()
                        .thenComparing(scored -> scored.comp().getTier())) // 동점이면 상위 티어 우선
                .map(scored -> scored.toResponse(
                        buildInterestWarnings(scored.unitsToBuy(), unitCosts, request.currentGold()),
                        buildProbabilityTips(scored.comp().getCoreUnits(), boardUnits, unitCosts, request.currentLevel()),
                        roundTip))
                .toList();
    }

    /**
     * 등록된 모든 메타 덱을 응답 DTO로 변환해 반환한다. (GET /api/v1/meta-comps)
     */
    public List<MetaCompResponse> getAllMetaComps() {
        return metaCompRepository.findAll().stream()
                .map(MetaCompResponse::from)
                .toList();
    }

    // 덱 하나에 대해 기물/아이템/성급/장착/티어 점수를 계산
    private ScoredComp score(MetaCompEntity comp, BoardContext board, Set<String> shopUnits) {
        List<String> coreUnits = comp.getCoreUnits();
        Map<String, Integer> itemCounts = board.ownedItemCounts();

        long boardMatches = coreUnits.stream().filter(board.units()::contains).count();
        // 매수 추천 기물: 덱 핵심 기물 중 상점에 뜬 것 (덱에 정의된 순서 유지)
        List<String> unitsToBuy = coreUnits.stream().filter(shopUnits::contains).toList();

        // 덱 추천 아이템별 필요 개수 (같은 아이템이 여러 번 등록되어 있으면 그만큼 필요)
        Map<String, Long> requiredItems = comp.getRecommendedItems().stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        int itemMatches = 0;
        List<String> matchedItems = new ArrayList<>();
        for (Map.Entry<String, Long> required : requiredItems.entrySet()) {
            int owned = itemCounts.getOrDefault(required.getKey(), 0);
            int matched = (int) Math.min(owned, required.getValue());
            if (matched > 0) {
                itemMatches += matched;
                matchedItems.add(required.getKey());
            }
        }

        // 핵심 기물의 성급 가중치와, 핵심 기물에 덱 추천 아이템을 장착한 경우의 가중치
        Set<String> recommendedItemSet = requiredItems.keySet();
        int starScore = 0;
        int equippedMatches = 0;
        for (String unit : coreUnits) {
            starScore += switch (board.starLevels().getOrDefault(unit, 1)) {
                case 3 -> THREE_STAR_SCORE;
                case 2 -> TWO_STAR_SCORE;
                default -> 0;
            };
            equippedMatches += (int) board.equippedItems().getOrDefault(unit, List.of()).stream()
                    .filter(recommendedItemSet::contains).count();
        }

        int matchScore = (int) boardMatches * BOARD_UNIT_SCORE
                + unitsToBuy.size() * SHOP_UNIT_SCORE
                + itemMatches * ITEM_SCORE
                + starScore
                + equippedMatches * EQUIPPED_ITEM_SCORE;
        return new ScoredComp(comp, matchScore, tierBonus(comp.getTier()), unitsToBuy, matchedItems);
    }

    /**
     * 매수 추천 기물을 각각 하나씩 샀을 때 이자 구간이 깨지면 경고 메시지를 만든다.
     * 골드가 없거나(미입력) 코스트를 모르는 기물, 골드가 부족해 살 수 없는 기물은 제외한다.
     */
    private List<String> buildInterestWarnings(List<String> unitsToBuy, Map<String, Integer> unitCosts, Integer gold) {
        if (gold == null || gold < 0) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        for (String unit : unitsToBuy) {
            Integer cost = unitCosts.get(unit);
            if (cost == null || cost > gold || !ruleEngine.willBreakInterest(gold, cost)) {
                continue;
            }
            warnings.add("⚠️ [%s] 이 기물을 사면 이자 %d골드를 손해봅니다 (현재 %d원 -> 구매 후 %d원)"
                    .formatted(unit, ruleEngine.calculateInterestLoss(gold, cost), gold, gold - cost));
        }
        return warnings;
    }

    /**
     * 아직 보유하지 않은 고코스트 핵심 기물이 현재 레벨에서 잘 나오지 않으면 코스트별로 확률을 안내한다.
     */
    private List<String> buildProbabilityTips(List<String> coreUnits, Set<String> boardUnits,
                                              Map<String, Integer> unitCosts, Integer level) {
        if (level == null || level < TftSystemRuleEngine.MIN_LEVEL || level > TftSystemRuleEngine.MAX_LEVEL) {
            return List.of();
        }
        // 코스트 -> 해당 코스트의 미보유 핵심 기물 (코스트 오름차순)
        Map<Integer, List<String>> missingByCost = new TreeMap<>();
        for (String unit : coreUnits) {
            Integer cost = unitCosts.get(unit);
            if (cost != null && cost >= HIGH_COST_THRESHOLD && !boardUnits.contains(unit)) {
                missingByCost.computeIfAbsent(cost, c -> new ArrayList<>()).add(unit);
            }
        }

        List<String> tips = new ArrayList<>();
        missingByCost.forEach((cost, units) -> {
            int probability = ruleEngine.getShopProbability(level, cost);
            if (probability < LOW_PROBABILITY_PERCENT) {
                tips.add("💡 현재 레벨(%d렙)에서 %d코스트 등장 확률은 %d%%입니다. (%s)"
                        .formatted(level, cost, probability, String.join(", ", units)));
            }
        });
        return tips;
    }

    // 다음 라운드가 크립 라운드면 안내 메시지, 아니면 null
    private String buildRoundTip(String currentStage) {
        return ruleEngine.nextRound(currentStage)
                .filter(ruleEngine::isPveRound)
                .map(next -> "🐉 다음 라운드는 크립 라운드입니다. 골드 모으기를 권장합니다. (" + next + ")")
                .orElse(null);
    }

    private int tierBonus(Tier tier) {
        return switch (tier) {
            case S -> 10;
            case A -> 5;
            case B -> 0;
        };
    }

    private Set<String> normalize(List<String> names) {
        if (names.isEmpty()) {
            return Collections.emptySet();
        }
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 요청에서 정리한 체스판 정보. 점수 계산 시 덱마다 반복해서 참조한다.
     *
     * @param units           보유 기물 이름 (boardUnits + placedUnits 이름)
     * @param starLevels      기물 이름 -> 최고 성급
     * @param equippedItems   기물 이름 -> 장착 아이템 목록 (같은 기물이 여러 개면 합침)
     * @param ownedItemCounts 아이템 이름 -> 보유 수량 (인벤토리 itemCounts + 장착 아이템)
     */
    private record BoardContext(Set<String> units, Map<String, Integer> starLevels,
                                Map<String, List<String>> equippedItems, Map<String, Integer> ownedItemCounts) {

        static BoardContext from(RecommendRequest request, Set<String> boardUnits) {
            Set<String> units = new LinkedHashSet<>(boardUnits);
            Map<String, Integer> starLevels = new LinkedHashMap<>();
            Map<String, List<String>> equippedItems = new LinkedHashMap<>();
            Map<String, Integer> ownedItemCounts = new LinkedHashMap<>();

            request.itemCounts().forEach((item, count) -> {
                if (item != null && count != null && count > 0) {
                    ownedItemCounts.merge(item.trim(), count, Integer::sum);
                }
            });
            for (PlacedUnitRequest placed : request.placedUnits()) {
                String name = placed.name().trim();
                units.add(name);
                starLevels.merge(name, placed.starLevel(), Math::max);
                equippedItems.computeIfAbsent(name, n -> new ArrayList<>()).addAll(placed.items());
                placed.items().forEach(item -> ownedItemCounts.merge(item, 1, Integer::sum));
            }
            return new BoardContext(units, starLevels, equippedItems, ownedItemCounts);
        }
    }

    /**
     * 점수 계산 중간 결과. Service 내부에서만 사용하며 최종적으로 RecommendResponse로 변환된다.
     *
     * @param matchScore 현재 상황과의 일치 점수 (기물 + 아이템) - 결과 포함 여부 판단에 사용
     * @param tierBonus  티어 가산점
     */
    private record ScoredComp(MetaCompEntity comp, int matchScore, int tierBonus,
                              List<String> unitsToBuy, List<String> matchedItems) {

        int totalScore() {
            return matchScore + tierBonus;
        }

        RecommendResponse toResponse(List<String> interestWarnings, List<String> probabilityTips, String roundTip) {
            return new RecommendResponse(
                    comp.getName(),
                    comp.getTier().name(),
                    totalScore(),
                    unitsToBuy,
                    List.copyOf(matchedItems),
                    comp.getDescription(),
                    interestWarnings,
                    probabilityTips,
                    roundTip
            );
        }
    }
}
