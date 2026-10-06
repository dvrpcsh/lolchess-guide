package com.lolchess.entity.converter;

import jakarta.persistence.AttributeConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [역할] Map 형태의 Entity 필드를 DB의 TEXT 컬럼에 JSON 문자열로 저장/복원하는 공통 JPA 컨버터.
 *   "레벨 -> 기물 목록", "기물 -> 아이템 목록"처럼 중첩된 컬렉션은 @ElementCollection으로 표현하기 어려워
 *   JSON 한 컬럼으로 저장한다. (조회·검색 조건으로 쓰지 않고 통째로 읽어 보여 주기만 하는 데이터)
 *
 * [Data Flow]
 *   (저장) Entity Map 필드 --convertToDatabaseColumn--> JSON 문자열 --> meta_comp TEXT 컬럼
 *   (조회) TEXT 컬럼 --convertToEntityAttribute--> LinkedHashMap (입력 순서 유지, NULL/빈 값이면 빈 Map)
 *
 * @param <K> Map 키 타입 (Integer 레벨 / String 기물 이름)
 * @param <V> Map 값 타입
 */
public abstract class JsonMapConverter<K, V> implements AttributeConverter<Map<K, V>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TypeReference<LinkedHashMap<K, V>> typeReference;

    protected JsonMapConverter(TypeReference<LinkedHashMap<K, V>> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public String convertToDatabaseColumn(Map<K, V> attribute) {
        return JSON.writeValueAsString(attribute == null ? Map.of() : attribute);
    }

    @Override
    public Map<K, V> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        return JSON.readValue(dbData, typeReference);
    }
}
