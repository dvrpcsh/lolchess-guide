package com.lolchess.config;

import com.lolchess.entity.ChampionEntity;
import com.lolchess.entity.CompType;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * [역할] 서버 기동 시 meta_comp 테이블의 시즌 18 기본 메타 덱 데이터를 이 클래스의 정의와 일치시키는 시더(Seeder).
 *   덱 추천 API가 점수를 계산하는 기준 데이터이므로, 기물/아이템 이름은 Data Dragon 한글명과 정확히 같아야 한다.
 *
 * [Data Flow]
 *   Spring Boot 기동 --> RiotDataSyncRunner(@Order(1))가 champion / item 테이블 동기화
 *     --> DataInitializer.run() (@Order(2))
 *     --> 0) 시드 덱 목록 = 코드에 정의한 DEFAULT_COMPS + classpath:data/meta-comps-lolchess.json
 *            (lolchess.gg 메타 가이드에서 수집한 덱. 기물/아이템 이름은 Data Dragon 이름으로 변환되어 있음)
 *     --> 1) 이름이 바뀌어 더 이상 쓰지 않는 예전 초기 덱(LEGACY_COMP_NAMES) 삭제
 *     --> 2) 시드 덱을 덱 이름 기준으로 조회
 *            - 없으면 INSERT
 *            - 있는데 티어/기물/아이템/설명이 다르면 UPDATE (meta_comp_core_units 등 컬렉션 테이블도 갱신)
 *            - 같으면 아무 것도 하지 않음 (재기동해도 중복 삽입/불필요한 UPDATE 없음)
 *     --> 3) 기물/아이템 이름이 champion / item 테이블에 없으면 경고 로그 (오타로 매칭 점수가 0이 되는 것을 조기 발견)
 *            핵심 기물·추천 아이템뿐 아니라 레벨별 빌드업 기물, 기물별 추천 완성 아이템까지 검사한다.
 *
 * ※ 빌드업 기물과 기물별 추천 완성 아이템은 Data Dragon 시즌 18 기물/아이템 이름으로 구성한 샘플 운영 가이드이며,
 *   실제 메타 통계로 검증된 값이 아니다.
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
                    CompType.VALUE_9,
                    List.of("드레이븐", "자야", "애쉬", "아무무", "마오카이"),
                    List.of("곡궁", "B.F. 대검", "쇠사슬 조끼", "연습용 장갑"),
                    "구인수 필수, 크라켄 및 최후의 속삭임/전역 갑주 조기 제작 추천. 9렙 고밸류 전환 용이.",
                    buildUp(
                            List.of("자야", "레오나", "바루스", "오른"),
                            List.of("자야", "레오나", "바루스", "오른", "세주아니"),
                            List.of("자야", "레오나", "바루스", "세주아니", "아무무", "케이틀린"),
                            List.of("자야", "바루스", "세주아니", "아무무", "케이틀린", "드레이븐", "말파이트"),
                            List.of("자야", "바루스", "세주아니", "아무무", "드레이븐", "말파이트", "애쉬", "마오카이"),
                            List.of("자야", "세주아니", "아무무", "드레이븐", "말파이트", "애쉬", "마오카이", "타릭", "아이번")),
                    unitItems(
                            Map.entry("드레이븐", List.of("무한의 대검", "최후의 속삭임", "거인 학살자")),
                            Map.entry("애쉬", List.of("구인수의 격노검", "크라켄의 분노", "거인 학살자")),
                            Map.entry("아무무", List.of("가고일 돌갑옷", "워모그의 갑옷", "태양불꽃 망토")))),
            new CompDefinition(
                    "속사포 아펠리오스",
                    Tier.S,
                    CompType.FAST_8,
                    List.of("아펠리오스", "바루스", "자야", "아무무"),
                    List.of("B.F. 대검", "연습용 장갑", "거인의 허리띠", "쇠사슬 조끼"),
                    "자체 공속 증가로 구인수 제작 금지. Pure AD 및 방템 위주 구성.",
                    buildUp(
                            List.of("바루스", "자야", "레오나", "케이틀린"),
                            List.of("바루스", "자야", "레오나", "케이틀린", "세주아니"),
                            List.of("바루스", "자야", "레오나", "케이틀린", "세주아니", "아무무"),
                            List.of("바루스", "자야", "케이틀린", "세주아니", "아무무", "아펠리오스", "시비르"),
                            List.of("바루스", "자야", "케이틀린", "세주아니", "아무무", "아펠리오스", "시비르", "말파이트"),
                            List.of("바루스", "자야", "세주아니", "아무무", "아펠리오스", "시비르", "말파이트", "애쉬", "타릭")),
                    unitItems(
                            Map.entry("아펠리오스", List.of("무한의 대검", "최후의 속삭임", "거인 학살자")),
                            Map.entry("바루스", List.of("죽음의 검", "피바라기", "스테락의 도전")),
                            Map.entry("아무무", List.of("가고일 돌갑옷", "용의 발톱", "워모그의 갑옷")))),
            new CompDefinition(
                    "감시자 아리 모르가나",
                    Tier.A,
                    CompType.FAST_8,
                    List.of("아리", "모르가나", "아무무", "알룬"),
                    List.of("쓸데없이 큰 지팡이", "여신의 눈물", "거인의 허리띠", "음전자 망토"),
                    "모르가나 역병의 보석 핵심. 감시자 암라인과 유연한 4주문술사 전환 가능.",
                    buildUp(
                            List.of("카르마", "베이가", "레오나", "쉔"),
                            List.of("카르마", "베이가", "레오나", "쉔", "르블랑"),
                            List.of("카르마", "베이가", "레오나", "쉔", "르블랑", "아무무"),
                            List.of("카르마", "레오나", "쉔", "르블랑", "아무무", "아리", "모르가나"),
                            List.of("카르마", "쉔", "르블랑", "아무무", "아리", "모르가나", "소라카", "말파이트"),
                            List.of("쉔", "아무무", "아리", "모르가나", "소라카", "말파이트", "알룬", "럭스", "타릭")),
                    unitItems(
                            Map.entry("아리", List.of("푸른 파수꾼", "보석 건틀릿", "라바돈의 죽음모자")),
                            Map.entry("모르가나", List.of("모렐로노미콘", "정령의 형상", "적응형 투구")),
                            Map.entry("알룬", List.of("대천사의 지팡이", "보석 건틀릿", "이온 충격기")))),
            // 3코스트 이하 주문술사 3성작 리롤 덱 (베이가는 시즌 18에서 1코스트, 카시오페아·피들스틱이 3코스트 주문술사)
            new CompDefinition(
                    "주문술사 베이가 리롤",
                    Tier.A,
                    CompType.REROLL,
                    List.of("베이가", "카르마", "르블랑", "카시오페아", "피들스틱", "오른", "알리스타", "렉사이"),
                    List.of("쓸데없이 큰 지팡이", "쓸데없이 큰 지팡이", "쓸데없이 큰 지팡이", "쓸데없이 큰 지팡이",
                            "여신의 눈물", "여신의 눈물", "여신의 눈물", "여신의 눈물",
                            "거인의 허리띠", "거인의 허리띠", "거인의 허리띠", "거인의 허리띠",
                            "B.F. 대검", "B.F. 대검", "쇠사슬 조끼", "쇠사슬 조끼", "연습용 장갑", "음전자 망토"),
                    "6렙에서 50원 이자를 유지하며 베이가·르블랑 3성, 7렙에서 카시오페아·피들스틱 3성을 완성. 베이가에게 마나 아이템 우선.",
                    buildUp(
                            List.of("베이가", "카르마", "르블랑", "오른"),
                            List.of("베이가", "카르마", "르블랑", "오른", "렉사이"),
                            List.of("베이가", "카르마", "르블랑", "오른", "렉사이", "카시오페아"),
                            List.of("베이가", "카르마", "르블랑", "오른", "렉사이", "카시오페아", "피들스틱"),
                            List.of("베이가", "카르마", "르블랑", "오른", "렉사이", "카시오페아", "피들스틱", "알리스타")),
                    unitItems(
                            Map.entry("베이가", List.of("푸른 파수꾼", "보석 건틀릿", "마법공학 총검")),
                            Map.entry("카시오페아", List.of("대천사의 지팡이", "모렐로노미콘", "쇼진의 창")),
                            Map.entry("오른", List.of("가고일 돌갑옷", "워모그의 갑옷", "태양불꽃 망토"))))
    );

    // 빌드업 가이드 레벨 범위: 4레벨부터 9레벨까지 순서대로
    private static final int FIRST_BUILD_UP_LEVEL = 4;

    private final MetaCompRepository metaCompRepository;
    private final ChampionRepository championRepository;
    private final ItemRepository itemRepository;

    // lolchess.gg 메타 가이드에서 수집한 시즌 18 덱 (수집 출처·버전은 파일 상단 source / sourceVersion 참고)
    private static final String LOLCHESS_SEED_PATH = "data/meta-comps-lolchess.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CompDefinition> seedComps = loadSeedComps();
        metaCompRepository.deleteByNameIn(LEGACY_COMP_NAMES);

        int inserted = 0;
        int updated = 0;
        for (CompDefinition def : seedComps) {
            MetaCompEntity existing = metaCompRepository.findByName(def.name()).orElse(null);
            if (existing == null) {
                metaCompRepository.save(def.toEntity());
                inserted++;
            } else if (!existing.hasSameComposition(def.tier(), def.compType(), def.coreUnits(), def.recommendedItems(), def.description(),
                    def.buildUpGuide(), def.unitItemMap())) {
                // 영속 상태 엔티티이므로 값만 바꾸면 트랜잭션 커밋 시 변경 감지로 UPDATE 된다.
                existing.updateComposition(def.tier(), def.compType(), def.coreUnits(), def.recommendedItems(), def.description(),
                        def.buildUpGuide(), def.unitItemMap());
                updated++;
            }
        }
        log.info("[DataInitializer] 기본 메타 덱 {}개 확인: 신규 {}개, 갱신 {}개", seedComps.size(), inserted, updated);

        warnUnknownNames(seedComps);
    }

    // 초기 데이터의 기물/아이템 이름이 Data Dragon 동기화 데이터에 존재하는지 검사
    private void warnUnknownNames(List<CompDefinition> seedComps) {
        Set<String> championNames = championRepository.findAll().stream()
                .map(ChampionEntity::getName).collect(Collectors.toSet());
        Set<String> itemNames = itemRepository.findAll().stream()
                .map(ItemEntity::getName).collect(Collectors.toSet());
        if (championNames.isEmpty() || itemNames.isEmpty()) {
            log.warn("[DataInitializer] champion / item 데이터가 비어 있어 메타 덱 이름 검증을 건너뜁니다.");
            return;
        }

        for (CompDefinition def : seedComps) {
            // 핵심 기물 + 빌드업 기물 + 기물별 아이템의 기물 이름 / 추천 아이템 + 기물별 추천 완성 아이템 이름을 모두 검사
            List<String> unknownUnits = Stream.of(def.coreUnits().stream(),
                            def.buildUpGuide().values().stream().flatMap(List::stream),
                            def.unitItemMap().keySet().stream())
                    .flatMap(Function.identity())
                    .filter(u -> !championNames.contains(u)).distinct().toList();
            List<String> unknownItems = Stream.concat(def.recommendedItems().stream(),
                            def.unitItemMap().values().stream().flatMap(List::stream))
                    .filter(i -> !itemNames.contains(i)).distinct().toList();
            if (!unknownUnits.isEmpty() || !unknownItems.isEmpty()) {
                log.warn("[DataInitializer] '{}' 덱에 Data Dragon에 없는 이름이 있습니다. 기물: {}, 아이템: {}",
                        def.name(), unknownUnits, unknownItems);
            }
        }
    }

    /**
     * 초기 메타 덱 한 개의 정의. Entity와 분리하여 "원하는 상태"를 선언적으로 표현한다.
     */
    /**
     * 코드 정의 덱 + lolchess.gg 수집 덱을 합친다. 같은 이름이 있으면 코드 정의가 우선한다.
     * JSON 파일을 읽지 못하면 경고만 남기고 코드 정의 덱만 사용한다.
     */
    private List<CompDefinition> loadSeedComps() {
        Map<String, CompDefinition> byName = new LinkedHashMap<>();
        DEFAULT_COMPS.forEach(def -> byName.put(def.name(), def));
        try (InputStream in = new ClassPathResource(LOLCHESS_SEED_PATH).getInputStream()) {
            SeedFile file = JSON.readValue(in, SeedFile.class);
            for (CompDefinition def : file.comps()) {
                if (byName.putIfAbsent(def.name(), def) != null) {
                    log.warn("[DataInitializer] '{}' 덱 이름이 코드 정의 덱과 겹쳐 lolchess.gg 데이터를 건너뜁니다.", def.name());
                }
            }
            log.info("[DataInitializer] {} ({}) 덱 {}개를 불러왔습니다.", file.source(), file.sourceVersion(), file.comps().size());
        } catch (IOException | JacksonException e) {
            log.warn("[DataInitializer] {} 을(를) 읽지 못해 코드 정의 덱만 사용합니다. 원인: {}", LOLCHESS_SEED_PATH, e.getMessage());
        }
        return List.copyOf(byName.values());
    }

    /**
     * classpath:data/meta-comps-lolchess.json 파일 구조
     */
    record SeedFile(String source, String sourceVersion, String collectedAt, List<CompDefinition> comps) {
    }

    record CompDefinition(String name, Tier tier, CompType compType, List<String> coreUnits, List<String> recommendedItems,
                                  String description, Map<Integer, List<String>> buildUpGuide,
                                  Map<String, List<String>> unitItemMap) {

        MetaCompEntity toEntity() {
            return MetaCompEntity.builder()
                    .name(name)
                    .tier(tier)
                    .compType(compType)
                    .coreUnits(coreUnits)
                    .recommendedItems(recommendedItems)
                    .description(description)
                    .buildUpGuide(buildUpGuide)
                    .unitItemMap(unitItemMap)
                    .build();
        }
    }

    /**
     * 4레벨부터 순서대로 레벨별 빌드업 기물 목록을 받아 { 4: [...], 5: [...], ... } 맵을 만든다 (순서 유지).
     */
    @SafeVarargs
    private static Map<Integer, List<String>> buildUp(List<String>... unitsByLevel) {
        Map<Integer, List<String>> guide = new LinkedHashMap<>();
        for (int i = 0; i < unitsByLevel.length; i++) {
            guide.put(FIRST_BUILD_UP_LEVEL + i, unitsByLevel[i]);
        }
        return guide;
    }

    /**
     * 핵심 기물 -> 추천 완성 아이템 맵 (덱에 적은 순서 유지)
     */
    @SafeVarargs
    private static Map<String, List<String>> unitItems(Map.Entry<String, List<String>>... entries) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
