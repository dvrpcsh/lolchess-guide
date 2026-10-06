package com.lolchess.service;

import com.lolchess.dto.MetaCompResponse;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.dto.RecommendResponse;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.repository.MetaCompRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [역할] 사용자의 현재 게임 상황(상점/보유 기물, 보유 아이템)과 DB의 메타 덱을 비교해
 *   가장 적합한 덱을 점수순으로 추천하는 비즈니스 로직 계층.
 *
 * [Data Flow]
 *   RecommendationController --> recommend(RecommendRequest)
 *     --> MetaCompRepository.findAll() 로 전체 메타 덱 조회 (MySQL meta_comp + 컬렉션 테이블)
 *     --> 덱마다 기물/아이템/티어 점수 계산
 *     --> 매칭 점수(기물+아이템)가 0점인 덱 제외 --> 총점 내림차순 정렬
 *     --> RecommendResponse DTO 목록으로 변환하여 Controller에 반환
 *
 * [점수 규칙]
 *   - 기물 점수  : 덱 핵심 기물 중 보유(boardUnits) 기물당 +20, 상점(shopUnits) 등장 기물당 +10
 *   - 아이템 점수: 덱 추천 아이템과 일치하는 보유 아이템 개수당 +15
 *                 (보유 수량만큼 인정하되, 덱 추천 목록에 그 아이템이 등장하는 횟수를 상한으로 함)
 *   - 티어 점수  : S +10, A +5, B +0
 *   티어 점수는 "현재 상황과의 일치도"가 아니므로, 기물/아이템이 하나도 맞지 않는 덱은
 *   티어와 관계없이 결과에서 제외한다. (빈 요청에 S티어 덱이 무조건 추천되는 것을 방지)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 전용: 지연 로딩 컬렉션(coreUnits, recommendedItems)을 트랜잭션 안에서 접근
public class RecommendationService {

    private static final int BOARD_UNIT_SCORE = 20;
    private static final int SHOP_UNIT_SCORE = 10;
    private static final int ITEM_SCORE = 15;

    private final MetaCompRepository metaCompRepository;

    /**
     * 현재 게임 상황에 맞는 메타 덱을 총점 내림차순으로 반환한다.
     */
    public List<RecommendResponse> recommend(RecommendRequest request) {
        // 요청 값의 앞뒤 공백을 제거하고 중복을 없애, 같은 기물이 상점에 2번 떠도 1번만 점수에 반영
        Set<String> boardUnits = normalize(request.boardUnits());
        Set<String> shopUnits = normalize(request.shopUnits());
        Map<String, Integer> itemCounts = request.itemCounts().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .collect(Collectors.toMap(e -> e.getKey().trim(), Map.Entry::getValue, Integer::sum));

        return metaCompRepository.findAll().stream()
                .map(comp -> score(comp, boardUnits, shopUnits, itemCounts))
                .filter(scored -> scored.matchScore() > 0)
                .sorted(Comparator.comparingInt(ScoredComp::totalScore).reversed()
                        .thenComparing(scored -> scored.comp().getTier())) // 동점이면 상위 티어 우선
                .map(ScoredComp::toResponse)
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

    // 덱 하나에 대해 기물/아이템/티어 점수를 계산
    private ScoredComp score(MetaCompEntity comp, Set<String> boardUnits, Set<String> shopUnits,
                             Map<String, Integer> itemCounts) {
        List<String> coreUnits = comp.getCoreUnits();

        long boardMatches = coreUnits.stream().filter(boardUnits::contains).count();
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

        int matchScore = (int) boardMatches * BOARD_UNIT_SCORE
                + unitsToBuy.size() * SHOP_UNIT_SCORE
                + itemMatches * ITEM_SCORE;
        return new ScoredComp(comp, matchScore, tierBonus(comp.getTier()), unitsToBuy, matchedItems);
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

        RecommendResponse toResponse() {
            return new RecommendResponse(
                    comp.getName(),
                    comp.getTier().name(),
                    totalScore(),
                    unitsToBuy,
                    List.copyOf(matchedItems),
                    comp.getDescription()
            );
        }
    }
}
