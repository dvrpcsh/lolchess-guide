package com.lolchess.service;

import com.lolchess.dto.RecommendRequest;
import com.lolchess.dto.RecommendResponse;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.repository.MetaCompRepository;
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
        service = new RecommendationService(repository);
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
}
