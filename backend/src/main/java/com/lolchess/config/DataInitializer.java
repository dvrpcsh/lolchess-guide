package com.lolchess.config;

import com.lolchess.entity.MetaCompEntity;
import com.lolchess.entity.Tier;
import com.lolchess.repository.MetaCompRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * [역할] 서버 기동 시 meta_comp 테이블에 시즌 18 대표 메타 덱 초기 데이터를 넣어 주는 시더(Seeder).
 *   개발 초기에 DB가 비어 있어도 덱 추천 API가 바로 응답할 수 있도록 기본 데이터를 보장한다.
 *
 * [Data Flow]
 *   Spring Boot 기동 완료 --> ApplicationRunner.run() 호출
 *     --> MetaCompRepository.count() 로 데이터 존재 여부 확인
 *     --> 0건이면 기본 덱 3개를 MetaCompEntity로 생성
 *     --> saveAll() --> meta_comp / meta_comp_core_units / meta_comp_recommended_items INSERT
 *   이미 데이터가 있으면 아무 것도 하지 않으므로 재기동해도 중복 삽입되지 않는다.
 *
 * ※ @PostConstruct 대신 ApplicationRunner를 사용한 이유:
 *   @PostConstruct 시점에는 트랜잭션 프록시가 적용되지 않을 수 있지만,
 *   ApplicationRunner는 컨텍스트가 완전히 준비된 후 실행되어 @Transactional이 정상 동작한다.
 *
 * ※ 유닛/아이템 구성은 개발용 샘플 데이터이며, 실제 시즌 18 메타 데이터로 교체해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final MetaCompRepository metaCompRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (metaCompRepository.count() > 0) {
            log.info("[DataInitializer] meta_comp 데이터가 이미 존재하여 초기 데이터 삽입을 건너뜁니다.");
            return;
        }

        List<MetaCompEntity> defaultComps = List.of(
                MetaCompEntity.builder()
                        .name("지옥불 드레이븐 고밸류")
                        .tier(Tier.S)
                        .coreUnits(List.of("드레이븐", "브랜드", "사이온", "세트", "애니"))
                        .recommendedItems(List.of(
                                "무한의 대검", "최후의 속삭임", "피바라기",
                                "B.F. 대검", "곡궁", "쇠사슬 조끼"))
                        .description("""
                                드레이븐에게 공격 아이템을 몰아주는 고밸류 덱.
                                초반에는 연승/연패 중 하나를 확실히 유지하며 골드를 모으고,
                                8레벨에서 리롤해 드레이븐 3성 또는 4코스트 핵심 유닛 2성을 노린다.
                                아이템: 드레이븐 무한의 대검 > 최후의 속삭임 > 피바라기 순으로 우선 완성.""")
                        .build(),
                MetaCompEntity.builder()
                        .name("속사포 아펠리오스")
                        .tier(Tier.A)
                        .coreUnits(List.of("아펠리오스", "케이틀린", "트위치", "자르반 4세", "레오나"))
                        .recommendedItems(List.of(
                                "구인수의 격노검", "거인 학살자", "무한의 대검",
                                "곡궁", "B.F. 대검", "여신의 눈물"))
                        .description("""
                                공격 속도를 누적하는 아펠리오스를 메인 딜러로 하는 덱.
                                초반 곡궁을 확보하면 방향을 잡기 좋으며, 7레벨에서 안정화 후 8레벨로 올린다.
                                아이템: 아펠리오스 구인수의 격노검 > 거인 학살자 > 무한의 대검.
                                탱커(레오나, 자르반 4세)에게는 방어 아이템을 배분한다.""")
                        .build(),
                MetaCompEntity.builder()
                        .name("감시자 아리 모르가나")
                        .tier(Tier.B)
                        .coreUnits(List.of("아리", "모르가나", "럭스", "갈리오", "쉔"))
                        .recommendedItems(List.of(
                                "푸른 파수꾼", "보석 건틀릿", "라바돈의 죽음모자",
                                "쓸데없이 큰 지팡이", "여신의 눈물", "음전자 망토"))
                        .description("""
                                감시자 시너지로 아군을 보호하며 아리와 모르가나가 광역 마법 피해를 넣는 덱.
                                초반 여신의 눈물/쓸데없이 큰 지팡이를 확보하면 진입하기 좋다.
                                아이템: 아리 푸른 파수꾼 > 보석 건틀릿 > 라바돈의 죽음모자,
                                모르가나에게는 생존용 방어 아이템을 우선 배분한다.""")
                        .build()
        );

        metaCompRepository.saveAll(defaultComps);
        log.info("[DataInitializer] 기본 메타 덱 {}건을 meta_comp 테이블에 저장했습니다.", defaultComps.size());
    }
}
