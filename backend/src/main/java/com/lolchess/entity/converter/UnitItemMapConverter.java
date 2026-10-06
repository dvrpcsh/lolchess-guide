package com.lolchess.entity.converter;

import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * [역할] MetaCompEntity.unitItemMap (핵심 기물 -> 추천 완성 아이템 목록)을 JSON TEXT로 변환한다.
 *   예) {"아펠리오스": ["무한의 대검", "최후의 속삭임", "거인 학살자"]}
 */
@Converter
public class UnitItemMapConverter extends JsonMapConverter<String, List<String>> {

    public UnitItemMapConverter() {
        super(new TypeReference<LinkedHashMap<String, List<String>>>() {
        });
    }
}
