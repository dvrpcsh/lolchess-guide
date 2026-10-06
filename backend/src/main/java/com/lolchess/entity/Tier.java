package com.lolchess.entity;

/**
 * [역할] 메타 덱의 티어 등급.
 *
 * [Data Flow]
 *   MetaCompEntity.tier 필드에 사용되며, @Enumerated(EnumType.STRING)으로
 *   meta_comp.tier 컬럼에 "S" / "A" / "B" 문자열 그대로 저장된다.
 *   (ORDINAL 저장 시 enum 순서 변경에 따라 데이터가 깨지므로 STRING 사용)
 */
public enum Tier {
    S, A, B
}
