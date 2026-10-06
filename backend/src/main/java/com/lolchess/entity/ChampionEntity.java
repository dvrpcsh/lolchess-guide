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
 * [역할] 현재 TFT 시즌의 챔피언(기물) 정보를 보관하는 영속성 객체.
 *   라이엇 Data Dragon에서 동기화한 데이터이며, 프론트엔드 기물 선택 UI의 원천 데이터가 된다.
 *
 * [Data Flow]
 *   (쓰기) RiotDataSyncRunner --> RiotDataDragonService.syncIfOutdated()
 *          --> Data Dragon tft-champion.json --> ChampionEntity --> champion 테이블
 *   (읽기) GET /api/v1/champions --> RiotDataDragonService.getChampions()
 *          --> ChampionRepository --> ChampionResponse DTO --> Client
 */
@Entity
@Table(name = "champion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChampionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 라이엇 내부 식별자 (예: DA_Draven18). 패치가 바뀌어도 같은 챔피언을 식별하는 키
    @Column(nullable = false, unique = true, length = 100)
    private String championId;

    // 한글 챔피언명 (ko_KR 데이터)
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int cost;

    @Column(length = 500)
    private String iconUrl;

    // 이 데이터를 가져온 Data Dragon 패치 버전. 최신 버전과 다르면 재동기화 대상
    @Column(nullable = false, length = 20)
    private String patchVersion;

    @Builder
    private ChampionEntity(String championId, String name, int cost, String iconUrl, String patchVersion) {
        this.championId = championId;
        this.name = name;
        this.cost = cost;
        this.iconUrl = iconUrl;
        this.patchVersion = patchVersion;
    }
}
