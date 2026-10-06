package com.lolchess.entity;

/**
 * [역할] 메타 덱의 운영 타입. 같은 체력/골드 상황이라도 덱 운영 방식에 따라 해야 할 행동이 달라진다.
 *
 * [Data Flow]
 *   MetaCompEntity.compType (meta_comp.comp_type, 문자열 저장)
 *     --> ActionableGuideService가 체력(playerHp)과 결합해 피관리 브리핑 생성
 *     --> RecommendResponse.compType --> 프론트엔드 덱 타입 뱃지
 */
public enum CompType {
    /** 낮은 레벨(6/7렙)에서 50원 이자를 유지하며 저코스트 핵심 기물 3성을 노리는 덱 */
    REROLL,
    /** 골드를 모아 빠르게 8레벨을 찍고 4코스트 핵심 기물 2성으로 완성하는 덱 */
    FAST_8,
    /** 8렙에서 버티고 9레벨에서 5코스트 고밸류 기물로 완성하는 덱 */
    VALUE_9
}
