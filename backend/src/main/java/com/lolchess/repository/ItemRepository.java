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

    List<ItemEntity> findAllByOrderByNameAsc();
}
