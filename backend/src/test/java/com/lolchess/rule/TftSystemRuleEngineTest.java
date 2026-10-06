package com.lolchess.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TftSystemRuleEngine 시스템 규칙 계산 단위 테스트.
 */
class TftSystemRuleEngineTest {

    private final TftSystemRuleEngine engine = new TftSystemRuleEngine();

    @Test
    void 이자는_10원당_1골드이며_최대_5골드다() {
        assertThat(engine.calculateInterest(0)).isZero();
        assertThat(engine.calculateInterest(9)).isZero();
        assertThat(engine.calculateInterest(10)).isEqualTo(1);
        assertThat(engine.calculateInterest(39)).isEqualTo(3);
        assertThat(engine.calculateInterest(50)).isEqualTo(5);
        assertThat(engine.calculateInterest(87)).isEqualTo(5);
    }

    @Test
    void 구매_후_이자_구간이_내려가면_이자가_깨진다() {
        assertThat(engine.willBreakInterest(52, 3)).isTrue();   // 49원 -> 이자 4
        assertThat(engine.willBreakInterest(53, 3)).isFalse();  // 50원 -> 이자 5 유지
        assertThat(engine.willBreakInterest(60, 5)).isFalse();  // 55원 -> 최대 이자 유지
        assertThat(engine.willBreakInterest(30, 1)).isTrue();   // 29원 -> 이자 2
        assertThat(engine.willBreakInterest(5, 1)).isFalse();   // 원래 이자 0
    }

    @Test
    void 레벨별_상점_확률을_반환하고_각_레벨의_합은_100이다() {
        assertThat(engine.getShopProbability(7, 1)).isEqualTo(19);
        assertThat(engine.getShopProbability(7, 3)).isEqualTo(40);
        assertThat(engine.getShopProbability(9, 5)).isEqualTo(15);
        assertThat(engine.getShopProbability(7, 5)).isEqualTo(1);
        for (int level = 1; level <= 10; level++) {
            int sum = 0;
            for (int cost = 1; cost <= 5; cost++) {
                sum += engine.getShopProbability(level, cost);
            }
            assertThat(sum).as("%d레벨 확률 합", level).isEqualTo(100);
        }
    }

    @Test
    void 범위를_벗어난_레벨이나_코스트는_예외가_발생한다() {
        assertThatThrownBy(() -> engine.getShopProbability(11, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.getShopProbability(5, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 크립_라운드를_판별한다() {
        assertThat(engine.isPveRound("1-1")).isTrue();
        assertThat(engine.isPveRound("1-4")).isTrue();
        assertThat(engine.isPveRound("2-7")).isTrue();
        assertThat(engine.isPveRound("5-7")).isTrue();
        assertThat(engine.isPveRound("3-2")).isFalse();
        assertThat(engine.isPveRound("6-7")).isFalse();
        assertThat(engine.isPveRound("abc")).isFalse();
        assertThat(engine.isPveRound(null)).isFalse();
    }

    @Test
    void 도달한_가장_최근_레벨업_타이밍을_찾는다() {
        assertThat(engine.latestLevelTiming("1-3")).isEmpty();
        assertThat(engine.latestLevelTiming("2-1")).contains(new TftSystemRuleEngine.LevelTiming("2-1", 4));
        assertThat(engine.latestLevelTiming("3-5")).contains(new TftSystemRuleEngine.LevelTiming("3-2", 6));
        assertThat(engine.latestLevelTiming("4-2")).contains(new TftSystemRuleEngine.LevelTiming("4-2", 8));
        assertThat(engine.latestLevelTiming("6-3")).contains(new TftSystemRuleEngine.LevelTiming("5-1", 9));
    }

    @Test
    void 다음_라운드를_계산한다() {
        assertThat(engine.nextRound("3-2")).contains("3-3");
        assertThat(engine.nextRound("1-4")).contains("2-1");
        assertThat(engine.nextRound("3-7")).contains("4-1");
        assertThat(engine.nextRound("1-5")).isEmpty();
        assertThat(engine.nextRound("")).isEmpty();
    }
}
