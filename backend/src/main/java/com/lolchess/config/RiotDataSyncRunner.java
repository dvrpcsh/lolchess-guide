package com.lolchess.config;

import com.lolchess.service.RiotDataDragonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * [역할] 서버 기동 시 라이엇 Data Dragon의 챔피언/아이템 데이터를 DB와 자동 동기화하는 실행기.
 *
 * [Data Flow]
 *   Spring Boot 기동 완료 --> run() --> RiotDataDragonService.syncIfOutdated()
 *     --> DB가 비어 있거나 패치 버전이 다르면 Data Dragon에서 받아 champion / item 테이블 갱신
 *
 * 네트워크 오류 등으로 동기화에 실패해도 서버는 계속 기동한다.
 * (이전에 저장된 데이터가 있으면 그 데이터로 API가 응답하고, 다음 기동 때 다시 동기화를 시도한다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotDataSyncRunner implements ApplicationRunner {

    private final RiotDataDragonService riotDataDragonService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            riotDataDragonService.syncIfOutdated();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("[DataDragon] 챔피언/아이템 동기화에 실패했습니다. 기존 DB 데이터를 사용합니다. 원인: {}", e.getMessage());
        }
    }
}
