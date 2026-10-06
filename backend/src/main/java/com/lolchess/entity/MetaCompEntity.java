package com.lolchess.entity;

import jakarta.persistence.CollectionTable;
import com.lolchess.entity.converter.BuildUpGuideConverter;
import com.lolchess.entity.converter.UnitItemMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * [역할] TFT 메타 덱(추천 조합) 한 건을 표현하는 영속성 객체.
 *   덱 이름, 티어, 핵심 유닛, 추천 아이템, 운영 가이드를 보관하며
 *   덱 추천 API가 조회하는 데이터의 원천(Source of Truth)이다.
 *
 * [테이블 매핑]
 *   meta_comp                     : 덱 본문 (id, name, tier, description)
 *   meta_comp_core_units          : 덱별 핵심 유닛 목록 (meta_comp_id FK, 순서 보존)
 *   meta_comp_recommended_items   : 덱별 추천 재료/완제 아이템 목록 (meta_comp_id FK, 순서 보존)
 *   meta_comp.build_up_guide      : 레벨별 빌드업 기물 (JSON TEXT, 예: {"4": ["자야", ...]})
 *   meta_comp.unit_item_map       : 핵심 기물별 추천 완성 아이템 (JSON TEXT, 예: {"아펠리오스": ["무한의 대검", ...]})
 *
 * [Data Flow]
 *   (쓰기) DataInitializer / 향후 관리 API --> MetaCompRepository.save()
 *          --> meta_comp + 두 컬렉션 테이블에 INSERT
 *   (읽기) Service --> MetaCompRepository 조회 --> Entity를 Response DTO로 변환
 *          --> Controller가 JSON으로 응답 (Entity는 Controller 밖으로 노출하지 않음)
 */
@Entity
@Table(name = "meta_comp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 프록시/리플렉션용 기본 생성자, 외부 직접 생성 차단
public class MetaCompEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    // 덱 명칭은 추천 목록에서 덱을 식별하는 자연 키이므로 중복을 DB 레벨에서 차단
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Tier tier;

    // 운영 타입 (리롤 / Fast 8 / 9렙 밸류). 컬럼 추가 이전 행은 NULL일 수 있어 nullable, 기동 시 DataInitializer가 채운다
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CompType compType;

    // 덱의 핵심 유닛 이름 목록. 독립적인 생명주기가 없는 값 타입이므로 @ElementCollection으로
    // 별도 테이블에 저장하고, 덱 삭제 시 함께 삭제된다. @OrderColumn으로 입력 순서를 유지한다.
    @ElementCollection
    @CollectionTable(name = "meta_comp_core_units", joinColumns = @JoinColumn(name = "meta_comp_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "unit_name", nullable = false, length = 50)
    private List<String> coreUnits = new ArrayList<>();

    // 덱 운영에 필요한 추천 재료/완제 아이템 목록 (우선순위 순서 유지)
    @ElementCollection
    @CollectionTable(name = "meta_comp_recommended_items", joinColumns = @JoinColumn(name = "meta_comp_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "item_name", nullable = false, length = 100)
    private List<String> recommendedItems = new ArrayList<>();

    // 운영 방법 및 아이템 배분 가이드 (장문이므로 TEXT)
    @Column(columnDefinition = "TEXT")
    private String description;

    // 레벨(4~9) -> 그 레벨에서 필드에 올릴 추천 기물 목록. 상세 가이드 화면에 통째로 보여 주는 데이터라 JSON 한 컬럼에 저장
    @Convert(converter = BuildUpGuideConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<Integer, List<String>> buildUpGuide = new LinkedHashMap<>();

    // 핵심 기물 이름 -> 추천 완성 아이템 목록 (보통 3개)
    @Convert(converter = UnitItemMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, List<String>> unitItemMap = new LinkedHashMap<>();

    @Builder
    private MetaCompEntity(String name, Tier tier, CompType compType, List<String> coreUnits, List<String> recommendedItems,
                           String description, Map<Integer, List<String>> buildUpGuide,
                           Map<String, List<String>> unitItemMap) {
        this.name = name;
        this.tier = tier;
        this.compType = compType;
        // 외부 리스트 참조를 그대로 보관하지 않도록 방어적 복사 (불변 List.of() 전달 시에도 Hibernate가 수정 가능)
        this.coreUnits = coreUnits != null ? new ArrayList<>(coreUnits) : new ArrayList<>();
        this.recommendedItems = recommendedItems != null ? new ArrayList<>(recommendedItems) : new ArrayList<>();
        this.description = description;
        this.buildUpGuide = copyOf(buildUpGuide);
        this.unitItemMap = copyOf(unitItemMap);
    }

    /**
     * 덱 구성(티어, 운영 타입, 핵심 기물, 추천 아이템, 설명, 레벨별 빌드업, 기물별 추천 아이템)을 새 값으로 교체한다.
     * @ElementCollection 컬렉션은 Hibernate가 추적 중인 인스턴스를 유지한 채 내용만 바꿔야 변경 감지가 정상 동작하고,
     * JSON 컨버터 필드(Map)는 새 인스턴스로 교체해야 변경이 감지된다.
     */
    public void updateComposition(Tier tier, CompType compType, List<String> coreUnits, List<String> recommendedItems, String description,
                                  Map<Integer, List<String>> buildUpGuide, Map<String, List<String>> unitItemMap) {
        this.tier = tier;
        this.compType = compType;
        this.coreUnits.clear();
        this.coreUnits.addAll(coreUnits);
        this.recommendedItems.clear();
        this.recommendedItems.addAll(recommendedItems);
        this.description = description;
        this.buildUpGuide = copyOf(buildUpGuide);
        this.unitItemMap = copyOf(unitItemMap);
    }

    /**
     * 저장된 구성이 주어진 값과 완전히 같은지 비교한다. (같으면 불필요한 UPDATE를 생략하기 위함)
     */
    public boolean hasSameComposition(Tier tier, CompType compType, List<String> coreUnits, List<String> recommendedItems, String description,
                                      Map<Integer, List<String>> buildUpGuide, Map<String, List<String>> unitItemMap) {
        return this.tier == tier
                && this.compType == compType
                && this.coreUnits.equals(coreUnits)
                && this.recommendedItems.equals(recommendedItems)
                && Objects.equals(this.description, description)
                && this.buildUpGuide.equals(copyOf(buildUpGuide))
                && this.unitItemMap.equals(copyOf(unitItemMap));
    }

    private static <K> Map<K, List<String>> copyOf(Map<K, List<String>> source) {
        return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
    }
}
