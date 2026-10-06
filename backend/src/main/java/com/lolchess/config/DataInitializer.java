package com.lolchess.config;

import com.lolchess.entity.ChampionEntity;
import com.lolchess.entity.ItemEntity;
import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.repository.ChampionRepository;
import com.lolchess.repository.ItemRepository;
import com.lolchess.repository.MetaCompRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [역할] 서버 기동 시 meta_comp 테이블의 시즌 18 기본 메타 덱 데이터를 이 클래스의 정의와 일치시키는 시더(Seeder).
 *   덱 추천 API가 점수를 계산하는 기준 데이터이므로, 기물/아이템 이름은 Data Dragon 한글명과 정확히 같아야 한다.
 *
 * [Data Flow]
 *   Spring Boot 기동 --> RiotDataSyncRunner(@Order(1))가 champion / item 테이블 동기화
 *     --> DataInitializer.run() (@Order(2))
 *     --> 1) 이름이 바뀌어 더 이상 쓰지 않는 예전 초기 덱(LEGACY_COMP_NAMES) 삭제
 *     --> 2) DEFAULT_COMPS를 덱 이름 기준으로 조회
 *            - 없으면 INSERT
 *            - 있는데 티어/기물/아이템/설명이 다르면 UPDATE (meta_comp_core_units 등 컬렉션 테이블도 갱신)
 *            - 같으면 아무 것도 하지 않음 (재기동해도 중복 삽입/불필요한 UPDATE 없음)
 *     --> 3) 기물/아이템 이름이 champion / item 테이블에 없으면 경고 로그 (오타로 매칭 점수가 0이 되는 것을 조기 발견)
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    // 이전 버전의 초기 데이터에서 사용하다가 이름이 바뀐 덱 (다른 덱으로 대체됨)
    private static final List<String> LEGACY_COMP_NAMES = List.of("지옥불 드레이븐 고밸류");

    private static final List<CompDefinition> DEFAULT_COMPS = List.of(
            new CompDefinition(
                    "나무정령 / 지옥불 드레이븐 고밸류",
                    Tier.S,
                    List.of("드레이븐", "자야", "애쉬", "아무무", "마오카이"),
                    List.of("곡궁", "B.F. 대검", "쇠사슬 조끼", "연습용 장갑"),
                    "구인수 필수, 크라켄 및 최후의 속삭임/전역 갑주 조기 제작 추천. 9렙 고밸류 전환 용이."),
            new CompDefinition(
                    "속사포 아펠리오스",
                    Tier.S,
                    List.of("아펠리오스", "바루스", "자야", "아무무"),
                    List.of("B.F. 대검", "연습용 장갑", "거인의 허리띠", "쇠사슬 조끼"),
                    "자체 공속 증가로 구인수 제작 금지. Pure AD 및 방템 위주 구성."),
            new CompDefinition(
                    "감시자 아리 모르가나",
                    Tier.A,
                    List.of("아리", "모르가나", "아무무", "알룬"),
                    List.of("쓸데없이 큰 지팡이", "여신의 눈물", "거인의 허리띠", "음전자 망토"),
                    "모르가나 역병의 보석 핵심. 감시자 암라인과 유연한 4주문술사 전환 가능.")
    );

    private final MetaCompRepository metaCompRepository;
    private final ChampionRepository championRepository;
    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        metaCompRepository.deleteByNameIn(LEGACY_COMP_NAMES);

        int inserted = 0;
        int updated = 0;
        for (CompDefinition def : DEFAULT_COMPS) {
            MetaCompEntity existing = metaCompRepository.findByName(def.name()).orElse(null);
            if (existing == null) {
                metaCompRepository.save(def.toEntity());
                inserted++;
            } else if (!existing.hasSameComposition(def.tier(), def.coreUnits(), def.recommendedItems(), def.description())) {
                // 영속 상태 엔티티이므로 값만 바꾸면 트랜잭션 커밋 시 변경 감지로 UPDATE 된다.
                existing.updateComposition(def.tier(), def.coreUnits(), def.recommendedItems(), def.description());
                updated++;
            }
        }
        log.info("[DataInitializer] 기본 메타 덱 {}개 확인: 신규 {}개, 갱신 {}개", DEFAULT_COMPS.size(), inserted, updated);

        warnUnknownNames();
    }

    // 초기 데이터의 기물/아이템 이름이 Data Dragon 동기화 데이터에 존재하는지 검사
    private void warnUnknownNames() {
        Set<String> championNames = championRepository.findAll().stream()
                .map(ChampionEntity::getName).collect(Collectors.toSet());
        Set<String> itemNames = itemRepository.findAll().stream()
                .map(ItemEntity::getName).collect(Collectors.toSet());
        if (championNames.isEmpty() || itemNames.isEmpty()) {
            log.warn("[DataInitializer] champion / item 데이터가 비어 있어 메타 덱 이름 검증을 건너뜁니다.");
            return;
        }

        for (CompDefinition def : DEFAULT_COMPS) {
            List<String> unknownUnits = def.coreUnits().stream().filter(u -> !championNames.contains(u)).toList();
            List<String> unknownItems = def.recommendedItems().stream().filter(i -> !itemNames.contains(i)).toList();
            if (!unknownUnits.isEmpty() || !unknownItems.isEmpty()) {
                log.warn("[DataInitializer] '{}' 덱에 Data Dragon에 없는 이름이 있습니다. 기물: {}, 아이템: {}",
                        def.name(), unknownUnits, unknownItems);
            }
        }
    }

    /**
     * 초기 메타 덱 한 개의 정의. Entity와 분리하여 "원하는 상태"를 선언적으로 표현한다.
     */
    private record CompDefinition(String name, Tier tier, List<String> coreUnits,
                                  List<String> recommendedItems, String description) {

        MetaCompEntity toEntity() {
            return MetaCompEntity.builder()
                    .name(name)
                    .tier(tier)
                    .coreUnits(coreUnits)
                    .recommendedItems(recommendedItems)
                    .description(description)
                    .build();
        }
    }
}
