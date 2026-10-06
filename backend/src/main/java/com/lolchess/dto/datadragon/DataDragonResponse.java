package com.lolchess.dto.datadragon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * [역할] 라이엇 Data Dragon의 tft-champion.json / tft-item.json 응답을 그대로 받는 외부 API 전용 DTO.
 *   우리 DB 구조(Entity)와 분리하여, 라이엇 응답 형식이 바뀌어도 이 클래스와 변환 로직만 수정하면 된다.
 *
 * [Data Flow]
 *   Data Dragon JSON --RestTemplate(Jackson 역직렬화)--> DataDragonResponse
 *     --> RiotDataDragonService에서 필터링 후 ChampionEntity / ItemEntity로 변환
 *
 * [응답 예시]
 *   { "version": "16.19.1",
 *     "data": { "Maps/Shipping/Map22/Sets/TFTSet18/Shop/DA_Draven18":
 *               { "id": "DA_Draven18", "name": "드레이븐", "cost": 5,
 *                 "image": { "full": "TFT18_Draven_splash_centered_5.TFT_Set18.png" } } } }
 *
 * @param version 데이터의 패치 버전
 * @param data    리소스 경로(key) -> 챔피언/아이템 정보
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataDragonResponse(String version, Map<String, Entry> data) {

    /**
     * @param cost 챔피언 코스트 (아이템 응답에는 없으므로 null)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String id, String name, Integer cost, Image image) {
    }

    /**
     * @param full   아이콘 이미지 파일명 (/cdn/{version}/img/{tft-champion|tft-item}/{full})
     * @param sprite 48x48 썸네일이 모여 있는 스프라이트 시트 파일명 (/cdn/{version}/img/sprite/{sprite})
     * @param x      스프라이트 시트 안에서 썸네일의 x 좌표(px)
     * @param y      스프라이트 시트 안에서 썸네일의 y 좌표(px)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String full, String sprite, Integer x, Integer y) {
    }
}
