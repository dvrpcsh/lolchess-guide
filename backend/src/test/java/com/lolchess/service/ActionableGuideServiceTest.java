package com.lolchess.service;

import com.lolchess.dto.ItemRecipeResponse;
import com.lolchess.dto.PlacedUnitRequest;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.entity.CompType;
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
                new ItemRecipeResponse("쓸데없이 큰 지팡이", "여신의 눈물", "대천사의 지팡이"),
                new ItemRecipeResponse("곡궁", "연습용 장갑", "최후의 속삭임"),
                new ItemRecipeResponse("연습용 장갑", "연습용 장갑", "도적의 장갑")));
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
    void 체력이_위험한_Fast8_덱이면_가장_먼저_리롤을_권한다() {
        RecommendRequest request = hpRequest(30);

        assertThat(guideService.buildBriefings(request, comp(CompType.FAST_8), List.of(), UNIT_COSTS))
                .containsExactly("🚨 체력이 위험합니다! 50원 이자를 깨고 즉시 8레벨 리롤을 돌려 2성작 보드를 완성하세요!");
    }

    @Test
    void 체력이_유복한_9렙_밸류_덱이면_고밸류_전환을_권하고_리롤_덱은_3성작을_권한다() {
        assertThat(guideService.buildBriefings(hpRequest(80), comp(CompType.VALUE_9), List.of(), UNIT_COSTS))
                .containsExactly("👑 체력이 유복합니다. 8렙에서 필드 최소 전력만 갖추고 9레벨 고밸류 전환을 노리세요.");
        assertThat(guideService.buildBriefings(hpRequest(50), comp(CompType.VALUE_9), List.of(), UNIT_COSTS)).isEmpty();
        assertThat(guideService.buildBriefings(hpRequest(20), comp(CompType.REROLL), List.of(), UNIT_COSTS))
                .containsExactly("⭐ 50원 이자를 유지하며 해당 레벨(6/7렙)에서 핵심 3성작을 완료하세요.");
    }

    @Test
    void 딜러_몫을_빼고_장갑이_2개_이상_남으면_아이템_없는_서브_기물에게_도적의_장갑을_권한다() {
        // 드레이븐 추천 아이템: 무한의 대검(대검+장갑), 최후의 속삭임(곡궁+장갑) -> 딜러 몫 장갑 2개
        MetaCompEntity comp = MetaCompEntity.builder().name("드레이븐 덱").tier(Tier.S).compType(CompType.FAST_8)
                .coreUnits(List.of("드레이븐", "아무무")).recommendedItems(List.of("연습용 장갑"))
                .unitItemMap(Map.of("드레이븐", List.of("무한의 대검", "최후의 속삭임"))).build();
        List<PlacedUnitRequest> placed = List.of(
                new PlacedUnitRequest("드레이븐", 1, List.of()), new PlacedUnitRequest("아무무", 1, List.of()));

        RecommendRequest fourGloves = new RecommendRequest(List.of(), List.of(), Map.of("연습용 장갑", 4),
                null, null, null, placed);
        RecommendRequest threeGloves = new RecommendRequest(List.of(), List.of(), Map.of("연습용 장갑", 3),
                null, null, null, placed);

        assertThat(guideService.buildBriefings(fourGloves, comp, List.of(), UNIT_COSTS)).containsExactly(
                "🧤 남는 [연습용 장갑] 2개로 [도적의 장갑]을 만들어 [아무무]에게 장착하세요! (핵심 딜러 [드레이븐] 아이템을 챙기고 남는 장갑 활용)");
        assertThat(guideService.buildBriefings(threeGloves, comp, List.of(), UNIT_COSTS)).isEmpty();
    }

    private static RecommendRequest hpRequest(int hp) {
        return new RecommendRequest(List.of(), List.of(), Map.of(), null, null, null, List.of(), hp);
    }

    private static MetaCompEntity comp(CompType type) {
        return MetaCompEntity.builder().name(type + " 덱").tier(Tier.A).compType(type)
                .coreUnits(List.of("드레이븐")).recommendedItems(List.of()).build();
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
