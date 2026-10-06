package com.lolchess.repository;

import com.lolchess.entity.MetaCompEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * [역할] meta_comp 테이블(및 하위 컬렉션 테이블)에 대한 DB 접근 계층.
 *
 * [Data Flow]
 *   Service / DataInitializer --> MetaCompRepository (Spring Data JPA가 구현체 자동 생성)
 *     --> Hibernate가 SQL 생성 --> MySQL tft_db.meta_comp
 *   기본 CRUD(save, findAll, findById, count 등)는 JpaRepository가 제공한다.
 */
public interface MetaCompRepository extends JpaRepository<MetaCompEntity, Long> {

    // 덱 이름(unique)으로 조회 - 초기 데이터를 이름 기준으로 추가/갱신할 때 사용
    Optional<MetaCompEntity> findByName(String name);

    // 이름이 바뀌어 더 이상 쓰지 않는 기존 초기 데이터 정리용
    void deleteByNameIn(Collection<String> names);
}
