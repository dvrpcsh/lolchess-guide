package com.lolchess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [역할] TFT 아이템(재료 아이템 + 조합 아이템) 정보를 보관하는 영속성 객체.
 *   라이엇 Data Dragon에서 동기화한 데이터이며, 프론트엔드 아이템 선택 UI의 원천 데이터가 된다.
 *
 * [Data Flow]
 *   (쓰기) RiotDataSyncRunner --> RiotDataDragonService.syncIfOutdated()
 *          --> Data Dragon tft-item.json --> 기본 아이템만 필터링 --> ItemEntity --> item 테이블
 *   (읽기) GET /api/v1/items --> RiotDataDragonService.getItems()
 *          --> ItemRepository --> 재료/조합으로 나눈 ItemListResponse DTO --> Client
 */
@Entity
@Table(name = "item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 라이엇 내부 식별자 (예: TFT_Item_BFSword)
    @Column(nullable = false, unique = true, length = 100)
    private String itemId;

    // 한글 아이템명 (ko_KR 데이터)
    @Column(nullable = false, length = 100)
    private String name;

    // 재료 아이템이면 true, 재료 2개로 만드는 조합 아이템이면 false
    @Column(name = "is_component", nullable = false)
    private boolean isComponent;

    @Column(length = 500)
    private String iconUrl;

    // 이 데이터를 가져온 Data Dragon 패치 버전. 최신 버전과 다르면 재동기화 대상
    @Column(nullable = false, length = 20)
    private String patchVersion;

    @Builder
    private ItemEntity(String itemId, String name, boolean isComponent, String iconUrl, String patchVersion) {
        this.itemId = itemId;
        this.name = name;
        this.isComponent = isComponent;
        this.iconUrl = iconUrl;
        this.patchVersion = patchVersion;
    }
}
