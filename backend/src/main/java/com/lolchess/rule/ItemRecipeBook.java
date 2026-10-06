package com.lolchess.rule;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [역할] TFT 기본 재료 아이템 2개 -> 완성 아이템 조합표.
 *   Data Dragon(tft-item.json)에는 조합법 정보가 없으므로 아이템 id 기준으로 직접 정의한다.
 *   한글 이름은 패치/언어에 따라 바뀔 수 있어, 이름 변환은 동기화된 item 테이블을 통해 ItemRecipeService가 수행한다.
 *
 * [Data Flow]
 *   ItemRecipeService --> recipes() (id 조합표)
 *     --> item 테이블(id -> 한글 이름)로 변환
 *     --> GET /api/v1/items 응답의 recipes (프론트엔드 자동 합성 / 완성 아이템 도감)
 *     --> RecommendationService (완성 아이템을 재료로 분해해 덱 추천 아이템과 비교)
 *
 * ※ 재료 8종(대검·곡궁·지팡이·눈물·조끼·망토·허리띠·장갑)의 모든 조합 36가지만 정의한다.
 *   뒤집개/프라이팬 조합은 시즌마다 달라지는 상징(Emblem) 아이템이 대부분이라 포함하지 않는다.
 */
@Component
public class ItemRecipeBook {

    private static final String BF_SWORD = "TFT_Item_BFSword";
    private static final String RECURVE_BOW = "TFT_Item_RecurveBow";
    private static final String LARGE_ROD = "TFT_Item_NeedlesslyLargeRod";
    private static final String TEAR = "TFT_Item_TearOfTheGoddess";
    private static final String CHAIN_VEST = "TFT_Item_ChainVest";
    private static final String NEGATRON = "TFT_Item_NegatronCloak";
    private static final String GIANTS_BELT = "TFT_Item_GiantsBelt";
    private static final String GLOVES = "TFT_Item_SparringGloves";

    private static final List<Recipe> RECIPES = List.of(
            // B.F. 대검
            new Recipe(BF_SWORD, BF_SWORD, "TFT_Item_Deathblade"),
            new Recipe(BF_SWORD, RECURVE_BOW, "TFT_Item_MadredsBloodrazor"),
            new Recipe(BF_SWORD, LARGE_ROD, "TFT_Item_HextechGunblade"),
            new Recipe(BF_SWORD, TEAR, "TFT_Item_SpearOfShojin"),
            new Recipe(BF_SWORD, CHAIN_VEST, "TFT_Item_GuardianAngel"),
            new Recipe(BF_SWORD, NEGATRON, "TFT_Item_Bloodthirster"),
            new Recipe(BF_SWORD, GIANTS_BELT, "TFT_Item_SteraksGage"),
            new Recipe(BF_SWORD, GLOVES, "TFT_Item_InfinityEdge"),
            // 곡궁
            new Recipe(RECURVE_BOW, RECURVE_BOW, "TFT_Item_RapidFireCannon"),
            new Recipe(RECURVE_BOW, LARGE_ROD, "TFT_Item_GuinsoosRageblade"),
            new Recipe(RECURVE_BOW, TEAR, "TFT_Item_StatikkShiv"),
            new Recipe(RECURVE_BOW, CHAIN_VEST, "TFT_Item_TitansResolve"),
            new Recipe(RECURVE_BOW, NEGATRON, "TFT_Item_RunaansHurricane"),
            new Recipe(RECURVE_BOW, GIANTS_BELT, "TFT_Item_Leviathan"),
            new Recipe(RECURVE_BOW, GLOVES, "TFT_Item_LastWhisper"),
            // 쓸데없이 큰 지팡이
            new Recipe(LARGE_ROD, LARGE_ROD, "TFT_Item_RabadonsDeathcap"),
            new Recipe(LARGE_ROD, TEAR, "TFT_Item_ArchangelsStaff"),
            new Recipe(LARGE_ROD, CHAIN_VEST, "TFT_Item_Crownguard"),
            new Recipe(LARGE_ROD, NEGATRON, "TFT_Item_IonicSpark"),
            new Recipe(LARGE_ROD, GIANTS_BELT, "TFT_Item_Morellonomicon"),
            new Recipe(LARGE_ROD, GLOVES, "TFT_Item_JeweledGauntlet"),
            // 여신의 눈물
            new Recipe(TEAR, TEAR, "TFT_Item_BlueBuff"),
            new Recipe(TEAR, CHAIN_VEST, "TFT_Item_FrozenHeart"),
            new Recipe(TEAR, NEGATRON, "TFT_Item_AdaptiveHelm"),
            new Recipe(TEAR, GIANTS_BELT, "TFT_Item_Redemption"),
            new Recipe(TEAR, GLOVES, "TFT_Item_UnstableConcoction"),
            // 쇠사슬 조끼
            new Recipe(CHAIN_VEST, CHAIN_VEST, "TFT_Item_BrambleVest"),
            new Recipe(CHAIN_VEST, NEGATRON, "TFT_Item_GargoyleStoneplate"),
            new Recipe(CHAIN_VEST, GIANTS_BELT, "TFT_Item_RedBuff"),
            new Recipe(CHAIN_VEST, GLOVES, "TFT_Item_NightHarvester"),
            // 음전자 망토
            new Recipe(NEGATRON, NEGATRON, "TFT_Item_DragonsClaw"),
            new Recipe(NEGATRON, GIANTS_BELT, "TFT_Item_SpectralGauntlet"),
            new Recipe(NEGATRON, GLOVES, "TFT_Item_Quicksilver"),
            // 거인의 허리띠
            new Recipe(GIANTS_BELT, GIANTS_BELT, "TFT_Item_WarmogsArmor"),
            new Recipe(GIANTS_BELT, GLOVES, "TFT_Item_PowerGauntlet"),
            // 연습용 장갑
            new Recipe(GLOVES, GLOVES, "TFT_Item_ThiefsGloves")
    );

    public List<Recipe> recipes() {
        return RECIPES;
    }

    /**
     * 조합 한 개 (모두 Data Dragon 아이템 id)
     */
    public record Recipe(String componentA, String componentB, String result) {
    }
}
