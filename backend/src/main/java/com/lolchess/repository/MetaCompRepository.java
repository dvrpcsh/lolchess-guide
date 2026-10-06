package com.lolchess.repository;

import com.lolchess.entity.MetaCompEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [역할] meta_comp 테이블(및 하위 컬렉션 테이블)에 대한 DB 접근 계층.
 *
 * [Data Flow]
 *   Service / DataInitializer --> MetaCompRepository (Spring Data JPA가 구현체 자동 생성)
 *     --> Hibernate가 SQL 생성 --> MySQL tft_db.meta_comp
 *   기본 CRUD(save, findAll, findById, count 등)는 JpaRepository가 제공한다.
 */
public interface MetaCompRepository extends JpaRepository<MetaCompEntity, Long> {
}
