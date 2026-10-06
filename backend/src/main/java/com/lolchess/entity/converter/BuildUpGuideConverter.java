package com.lolchess.entity.converter;

import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * [역할] MetaCompEntity.buildUpGuide (레벨 -> 그 레벨에서 올릴 기물 목록)를 JSON TEXT로 변환한다.
 *   예) {"4": ["자야", "레오나"], "5": [...]}
 */
@Converter
public class BuildUpGuideConverter extends JsonMapConverter<Integer, List<String>> {

    public BuildUpGuideConverter() {
        super(new TypeReference<LinkedHashMap<Integer, List<String>>>() {
        });
    }
}
