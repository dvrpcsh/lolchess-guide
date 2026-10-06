package com.lolchess.repository;

import com.lolchess.entity.ChampionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [역할] champion 테이블에 대한 DB 접근 계층.
 *
 * [Data Flow]
 *   RiotDataDragonService --> ChampionRepository --> MySQL tft_db.champion
 */
public interface ChampionRepository extends JpaRepository<ChampionEntity, Long> {

    // 저장된 데이터 중 최신 패치 버전이 아닌 행이 하나라도 있으면 재동기화 필요
    boolean existsByPatchVersionNot(String patchVersion);

    // 프론트엔드 표시 순서: 코스트 오름차순 -> 이름 가나다순
    List<ChampionEntity> findAllByOrderByCostAscNameAsc();
}
