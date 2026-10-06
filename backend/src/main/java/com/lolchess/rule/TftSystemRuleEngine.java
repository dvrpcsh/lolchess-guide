package com.lolchess.rule;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [역할] TFT 게임의 고정 시스템 규칙(이자, 레벨별 상점 확률, 라운드 구성)을 계산하는 규칙 엔진.
 *   DB나 외부 API에 의존하지 않는 순수 계산 로직이며, 상태가 없으므로 싱글톤 빈으로 공유한다.
 *
 * [Data Flow]
 *   RecommendationService --> TftSystemRuleEngine (골드/레벨/스테이지 + 기물 코스트 입력)
 *     --> 이자 손실 여부, 등장 확률, 크립 라운드 여부 반환
 *     --> RecommendationService가 이 결과로 추천 카드의 피드백 메시지를 만든다.
 *
 * ※ 상점 확률표는 패치마다 바뀔 수 있으므로 SHOP_ODDS 상수만 갱신하면 된다.
 */
@Component
public class TftSystemRuleEngine {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;
    public static final int MIN_COST = 1;
    public static final int MAX_COST = 5;

    private static final int GOLD_PER_INTEREST = 10;
    private static final int MAX_INTEREST = 5;

    /**
     * 레벨별 기물 코스트 등장 확률(%). 행 = 레벨 1~10, 열 = 1~5코스트. 각 행의 합은 100.
     */
    private static final int[][] SHOP_ODDS = {
            {100, 0, 0, 0, 0},  // 1레벨
            {100, 0, 0, 0, 0},  // 2레벨
            {75, 25, 0, 0, 0},  // 3레벨
            {55, 30, 15, 0, 0}, // 4레벨
            {45, 33, 20, 2, 0}, // 5레벨
            {30, 40, 25, 5, 0}, // 6레벨
            {19, 30, 35, 15, 1}, // 7레벨
            {18, 25, 32, 22, 3}, // 8레벨
            {10, 20, 25, 35, 10}, // 9레벨
            {5, 10, 20, 40, 25}, // 10레벨
    };

    // 크립(PvE) 라운드: 1스테이지 전체, 이후 각 스테이지의 7번째 라운드
    private static final Set<String> PVE_ROUNDS = Set.of("1-1", "1-2", "1-3", "1-4", "2-7", "3-7", "4-7", "5-7");
    private static final Pattern STAGE_PATTERN = Pattern.compile("^\\s*(\\d+)-(\\d+)\\s*$");
    private static final int STAGE_ONE_ROUNDS = 4;
    private static final int ROUNDS_PER_STAGE = 7;

    /**
     * 라운드 종료 시 받는 이자. 보유 골드 10원당 1골드, 최대 5골드.
     */
    public int calculateInterest(int currentGold) {
        return Math.min(Math.max(currentGold, 0) / GOLD_PER_INTEREST, MAX_INTEREST);
    }

    /**
     * 기물을 산 뒤 이자 구간(10/20/30/40/50원)이 내려가는지 여부.
     * 예: 52원에서 3원짜리 구매 -> 49원, 이자 5 -> 4 이므로 true
     */
    public boolean willBreakInterest(int currentGold, int cost) {
        return calculateInterestLoss(currentGold, cost) > 0;
    }

    /**
     * 기물 구매로 잃게 되는 이자 골드 수. (willBreakInterest의 상세 값)
     */
    public int calculateInterestLoss(int currentGold, int cost) {
        return calculateInterest(currentGold) - calculateInterest(currentGold - cost);
    }

    /**
     * 레벨에서 해당 코스트 기물이 상점 한 칸에 등장할 확률(%).
     *
     * @throws IllegalArgumentException 레벨(1~10) 또는 코스트(1~5) 범위를 벗어난 경우
     */
    public int getShopProbability(int level, int unitCost) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("레벨은 1~10 사이여야 합니다: " + level);
        }
        if (unitCost < MIN_COST || unitCost > MAX_COST) {
            throw new IllegalArgumentException("기물 코스트는 1~5 사이여야 합니다: " + unitCost);
        }
        return SHOP_ODDS[level - 1][unitCost - 1];
    }

    /**
     * 크립(PvE) 라운드 여부. 형식이 잘못된 스테이지 문자열은 false.
     */
    public boolean isPveRound(String stage) {
        return parseStage(stage)
                .map(s -> PVE_ROUNDS.contains(s.stage() + "-" + s.round()))
                .orElse(false);
    }

    /**
     * 현재 스테이지의 다음 라운드. (1-4 -> 2-1, 3-7 -> 4-1, 3-2 -> 3-3)
     * 형식이 잘못되었거나 존재하지 않는 라운드(예: 1-5, 3-8)면 빈 값.
     */
    public Optional<String> nextRound(String stage) {
        return parseStage(stage).flatMap(s -> {
            int lastRound = s.stage() == 1 ? STAGE_ONE_ROUNDS : ROUNDS_PER_STAGE;
            if (s.round() < 1 || s.round() > lastRound) {
                return Optional.empty();
            }
            return Optional.of(s.round() == lastRound
                    ? (s.stage() + 1) + "-1"
                    : s.stage() + "-" + (s.round() + 1));
        });
    }

    private Optional<StageRound> parseStage(String stage) {
        if (stage == null) {
            return Optional.empty();
        }
        Matcher matcher = STAGE_PATTERN.matcher(stage);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new StageRound(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
    }

    private record StageRound(int stage, int round) {
    }
}
