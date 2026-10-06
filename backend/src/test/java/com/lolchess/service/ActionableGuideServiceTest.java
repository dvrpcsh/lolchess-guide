package com.lolchess.service;

import com.lolchess.dto.ItemRecipeResponse;
import com.lolchess.dto.PlacedUnitRequest;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.rule.TftSystemRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ActionableGuideService 실시간 행동 가이드 생성 단위 테스트. (DB 없이 조합표만 Mock)
 */
class ActionableGuideServiceTest {

    private static final MetaCompEntity DRAVEN_COMP = MetaCompEntity.builder()
            .name("드레이븐 덱").tier(Tier.S)
            .coreUnits(List.of("드레이븐", "자야", "애쉬", "아무무"))
            .recommendedItems(List.of("곡궁", "B.F. 대검", "쇠사슬 조끼", "연습용 장갑"))
            .build();
    private static final Map<String, Integer> UNIT_COSTS = Map.of("드레이븐", 5, "자야", 1, "애쉬", 5, "아무무", 4);

    private ActionableGuideService guideService;

    @BeforeEach
    void setUp() {
        ItemRecipeService itemRecipeService = Mockito.mock(ItemRecipeService.class);
        when(itemRecipeService.getRecipes()).thenReturn(List.of(
                new ItemRecipeResponse("B.F. 대검", "연습용 장갑", "무한의 대검"),
                new ItemRecipeResponse("쓸데없이 큰 지팡이", "여신의 눈물", "대천사의 지팡이")));
        guideService = new ActionableGuideService(itemRecipeService, new TftSystemRuleEngine());
    }

    @Test
    void 우선순위_순서대로_아이템_레벨업_상점_골드_가이드를_만든다() {
        RecommendRequest request = new RecommendRequest(
                List.of("드레이븐", "자야", "자야"),
                List.of(),
                Map.of("B.F. 대검", 1, "연습용 장갑", 1),
                5, 28, "3-2",
                List.of(new PlacedUnitRequest("드레이븐", 1, List.of()), new PlacedUnitRequest("자야", 1, List.of())));

        List<String> briefings = guideService.buildBriefings(request, DRAVEN_COMP, List.of("드레이븐", "자야"), UNIT_COSTS);

        assertThat(briefings).containsExactly(
                "⚔️ [B.F. 대검]과 [연습용 장갑]을 합성하여 [무한의 대검]을 만들고 [드레이븐]에게 장착하세요!",
                "⬆️ 3-2 라운드입니다. 골드를 사용하여 6레벨을 달성하세요.",
                "⭐ 상점의 [자야]를 매수하면 2성이 완성됩니다!",
                "🛒 상점의 [드레이븐]을 매수하세요!",
                "💰 2골드만 더 모으면 이자가 3골드로 늘어납니다. 불필요한 소비를 아끼세요.");
    }

    @Test
    void 덱과_무관한_조합이거나_장착할_핵심_기물이_없으면_아이템_가이드를_만들지_않는다() {
        RecommendRequest noTarget = new RecommendRequest(List.of(), List.of(),
                Map.of("B.F. 대검", 1, "연습용 장갑", 1), null, null, null,
                List.of(new PlacedUnitRequest("아리", 1, List.of())));
        RecommendRequest offDeck = new RecommendRequest(List.of(), List.of(),
                Map.of("쓸데없이 큰 지팡이", 1, "여신의 눈물", 1), null, null, null,
                List.of(new PlacedUnitRequest("드레이븐", 1, List.of())));

        assertThat(guideService.buildBriefings(noTarget, DRAVEN_COMP, List.of(), UNIT_COSTS)).isEmpty();
        assertThat(guideService.buildBriefings(offDeck, DRAVEN_COMP, List.of(), UNIT_COSTS)).isEmpty();
    }

    @Test
    void 레벨업_타이밍이_지났으면_늦었다고_안내하고_다음이_크립_라운드면_골드_킵을_권한다() {
        RecommendRequest request = new RecommendRequest(List.of(), List.of(), Map.of(), 5, 40, "3-6");

        assertThat(guideService.buildBriefings(request, DRAVEN_COMP, List.of(), UNIT_COSTS)).containsExactly(
                "⬆️ 3-6 라운드입니다. 표준 운영(3-2 6렙)보다 늦었으니 골드를 사용하여 6레벨을 달성하세요.",
                "💰 다음 라운드는 크립 라운드입니다. 골드를 아껴 이자를 챙기세요.");
    }

    @Test
    void 이성_기물이_두개_있고_다시_이성이_완성되면_삼성_완성을_안내한다() {
        RecommendRequest request = new RecommendRequest(List.of("자야", "자야"), List.of(), Map.of(), null, null, null,
                List.of(new PlacedUnitRequest("자야", 2, List.of()), new PlacedUnitRequest("자야", 2, List.of()),
                        new PlacedUnitRequest("자야", 1, List.of())));

        assertThat(guideService.buildBriefings(request, DRAVEN_COMP, List.of("자야"), UNIT_COSTS))
                .containsExactly("🌟 상점의 [자야]를 매수하면 3성이 완성됩니다!");
    }
}
