package com.lolchess.repository;

import com.lolchess.entity.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [역할] item 테이블에 대한 DB 접근 계층.
 *
 * [Data Flow]
 *   RiotDataDragonService --> ItemRepository --> MySQL tft_db.item
 */
public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    // 저장된 데이터 중 최신 패치 버전이 아닌 행이 하나라도 있으면 재동기화 필요
    boolean existsByPatchVersionNot(String patchVersion);

    List<ItemEntity> findAllByOrderByNameAsc();
}
