package com.lolchess.service;

import com.lolchess.dto.PlacedUnitRequest;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.dto.RecommendResponse;
import com.lolchess.entity.ChampionEntity;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.repository.ChampionRepository;
import com.lolchess.repository.MetaCompRepository;
import com.lolchess.rule.TftSystemRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RecommendationService 점수 계산 단위 테스트.
 * Repository를 Mock으로 대체하므로 MySQL 없이 실행된다.
 */
class RecommendationServiceTest {

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        MetaCompRepository repository = Mockito.mock(MetaCompRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                MetaCompEntity.builder().name("S덱").tier(Tier.S)
                        .coreUnits(List.of("드레이븐", "브랜드")).recommendedItems(List.of("무한의 대검", "곡궁")).build(),
                MetaCompEntity.builder().name("A덱").tier(Tier.A)
                        .coreUnits(List.of("아펠리오스", "트위치")).recommendedItems(List.of("곡궁")).build(),
                MetaCompEntity.builder().name("B덱").tier(Tier.B)
                        .coreUnits(List.of("아리")).recommendedItems(List.of("여신의 눈물")).build()
        ));
        ChampionRepository championRepository = Mockito.mock(ChampionRepository.class);
        when(championRepository.findAll()).thenReturn(List.of(
                champion("드레이븐", 5), champion("브랜드", 3), champion("아펠리오스", 4),
                champion("트위치", 2), champion("아리", 4)));
        ItemRecipeService itemRecipeService = Mockito.mock(ItemRecipeService.class);
        when(itemRecipeService.getComponentsByCompletedName()).thenReturn(Map.of(
                "구인수의 격노검", List.of("곡궁", "쓸데없이 큰 지팡이"),
                "붉은 덩굴정령", List.of("곡궁", "곡궁")));
        TftSystemRuleEngine ruleEngine = new TftSystemRuleEngine();
        service = new RecommendationService(repository, championRepository, ruleEngine, itemRecipeService,
                new ActionableGuideService(itemRecipeService, ruleEngine));
    }

    @Test
    void 기물_아이템_티어_점수를_합산해_내림차순_정렬한다() {
        RecommendRequest request = new RecommendRequest(
                List.of("브랜드", "트위치", "자야"),
                List.of("드레이븐", "아펠리오스"),
                Map.of("곡궁", 2, "무한의 대검", 1));

        List<RecommendResponse> result = service.recommend(request);

        // S덱: 보유 드레이븐 20 + 상점 브랜드 10 + 아이템 2개 30 + S 10 = 70
        // A덱: 보유 아펠리오스 20 + 상점 트위치 10 + 곡궁 1개(덱 필요 수량 상한) 15 + A 5 = 50
        // B덱: 일치 없음 -> 제외
        assertThat(result).extracting(RecommendResponse::compName).containsExactly("S덱", "A덱");
        assertThat(result.get(0).matchScore()).isEqualTo(70);
        assertThat(result.get(0).unitsToBuy()).containsExactly("브랜드");
        assertThat(result.get(0).matchedItems()).containsExactly("무한의 대검", "곡궁");
        assertThat(result.get(1).matchScore()).isEqualTo(50);
    }

    @Test
    void 아무것도_일치하지_않으면_티어와_관계없이_빈_목록을_반환한다() {
        assertThat(service.recommend(new RecommendRequest(null, null, null))).isEmpty();
    }

    @Test
    void 골드_레벨_스테이지를_입력하면_이자_확률_라운드_피드백을_포함한다() {
        RecommendRequest request = new RecommendRequest(
                List.of("브랜드"), List.of(), Map.of(), 7, 32, "3-6");

        RecommendResponse sComp = service.recommend(request).get(0);

        // 브랜드(3코) 구매: 32원 -> 29원, 이자 3 -> 2
        assertThat(sComp.interestWarnings())
                .containsExactly("⚠️ [브랜드] 이 기물을 사면 이자 1골드를 손해봅니다 (현재 32원 -> 구매 후 29원)");
        // 미보유 5코 드레이븐: 7레벨 등장 확률 1%
        assertThat(sComp.probabilityTips())
                .containsExactly("💡 현재 레벨(7렙)에서 5코스트 등장 확률은 1%입니다. (드레이븐)");
        // 3-6 다음은 3-7 크립 라운드
        assertThat(sComp.roundTip()).startsWith("🐉 다음 라운드는 크립 라운드입니다.");
    }

    @Test
    void 골드_레벨_스테이지를_입력하지_않으면_피드백이_비어_있다() {
        RecommendResponse sComp = service.recommend(
                new RecommendRequest(List.of("브랜드"), List.of(), Map.of())).get(0);

        assertThat(sComp.interestWarnings()).isEmpty();
        assertThat(sComp.probabilityTips()).isEmpty();
        assertThat(sComp.roundTip()).isNull();
    }

    @Test
    void 핵심_기물의_성급과_장착된_추천_아이템에_가중치를_준다() {
        // 드레이븐 3성 + 무한의 대검 장착(덱 추천 아이템), 브랜드 2성 + 여신의 눈물 장착(추천 아님)
        RecommendRequest request = new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null, List.of(
                new PlacedUnitRequest("드레이븐", 3, List.of("무한의 대검")),
                new PlacedUnitRequest("브랜드", 2, List.of("여신의 눈물"))));

        RecommendResponse sComp = service.recommend(request).get(0);

        // 보유 2기 40 + 아이템(장착분 포함) 무한의 대검 15 + 성급 3성 25 + 2성 10 + 장착 무한의 대검 10 + S 10 = 110
        assertThat(sComp.compName()).isEqualTo("S덱");
        assertThat(sComp.matchScore()).isEqualTo(110);
        assertThat(sComp.matchedItems()).containsExactly("무한의 대검");
    }

    @Test
    void 아이템을_인벤토리에서_장착으로_옮겨도_보유_아이템_점수는_유지된다() {
        RecommendRequest inInventory = new RecommendRequest(List.of(), List.of("아리"), Map.of("곡궁", 1));
        RecommendRequest equippedOnOther = new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null,
                List.of(new PlacedUnitRequest("아리", 1, List.of("곡궁"))));

        int before = scoreOf(service.recommend(inInventory), "A덱");
        int after = scoreOf(service.recommend(equippedOnOther), "A덱");

        // 아리는 A덱 핵심 기물이 아니므로 장착 가중치는 없고, 보유 아이템 점수(곡궁 15)만 동일하게 유지
        assertThat(after).isEqualTo(before).isEqualTo(15 + 5);
    }

    @Test
    void 완성_아이템은_재료로_분해해_추천_아이템과_비교한다() {
        // A덱 핵심 기물 아펠리오스에 구인수의 격노검(곡궁 + 쓸데없이 큰 지팡이) 장착, A덱 추천 아이템은 곡궁
        RecommendRequest request = new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null,
                List.of(new PlacedUnitRequest("아펠리오스", 1, List.of("구인수의 격노검"))));

        RecommendResponse aComp = service.recommend(request).stream()
                .filter(r -> r.compName().equals("A덱")).findFirst().orElseThrow();

        // 보유 20 + 아이템(분해된 곡궁) 15 + 장착(분해된 곡궁) 10 + A 5 = 50
        assertThat(aComp.matchScore()).isEqualTo(50);
        assertThat(aComp.matchedItems()).containsExactly("곡궁");
    }

    @Test
    void 장착_점수는_덱_추천_아이템_요구_개수를_넘지_않는다() {
        // A덱 추천 아이템: 곡궁 1개. 아펠리오스에 붉은 덩굴정령(곡궁 + 곡궁) 장착
        RecommendRequest request = new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null,
                List.of(new PlacedUnitRequest("아펠리오스", 1, List.of("붉은 덩굴정령"))));

        int score = scoreOf(service.recommend(request), "A덱");

        // 보유 20 + 아이템 곡궁(상한 1개) 15 + 장착 곡궁(상한 1개) 10 + A 5 = 50 (상한이 없으면 장착 20으로 60)
        assertThat(score).isEqualTo(50);
    }

    @Test
    void 장착_상한은_여러_핵심_기물에_나눠_장착해도_덱_전체_기준으로_적용된다() {
        // A덱 핵심 기물 아펠리오스와 트위치에 곡궁을 하나씩 장착 (추천 곡궁 1개)
        RecommendRequest request = new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null, List.of(
                new PlacedUnitRequest("아펠리오스", 1, List.of("곡궁")),
                new PlacedUnitRequest("트위치", 1, List.of("곡궁"))));

        // 보유 2기 40 + 아이템 곡궁 15 + 장착 곡궁 1개분 10 + A 5 = 70
        assertThat(scoreOf(service.recommend(request), "A덱")).isEqualTo(70);
    }

    @Test
    void 핵심_기물에_치감_방깎_유틸_아이템을_장착하면_종류당_15점을_더한다() {
        // 드레이븐(S덱 핵심)에 최후의 속삭임 2개 + 모렐로노미콘 -> 유틸 2종류(중복 1번만) = +30
        RecommendRequest request = new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null,
                List.of(new PlacedUnitRequest("드레이븐", 1, List.of("최후의 속삭임", "최후의 속삭임", "모렐로노미콘"))));

        // 보유 20 + S 10 + 유틸 30 = 60 (S덱 추천 아이템과는 일치하지 않음)
        assertThat(scoreOf(service.recommend(request), "S덱")).isEqualTo(60);
    }

    private static int scoreOf(List<RecommendResponse> results, String compName) {
        return results.stream().filter(r -> r.compName().equals(compName)).findFirst().orElseThrow().matchScore();
    }

    private static ChampionEntity champion(String name, int cost) {
        return ChampionEntity.builder().championId("TEST_" + name).name(name).cost(cost).patchVersion("test").build();
    }
}
