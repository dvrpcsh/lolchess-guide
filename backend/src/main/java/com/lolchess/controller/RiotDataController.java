package com.lolchess.controller;

import com.lolchess.dto.ChampionResponse;
import com.lolchess.dto.ItemListResponse;
import com.lolchess.service.RiotDataDragonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [역할] 라이엇 Data Dragon에서 동기화한 챔피언/아이템 정적 데이터를 프론트엔드에 제공하는 REST API.
 *   (라이엇 서버를 매 요청마다 호출하지 않고, 기동 시 동기화해 둔 DB 데이터를 응답한다.)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RiotDataController {

    private final RiotDataDragonService riotDataDragonService;

    /**
     * [역할] 현재 시즌 전체 챔피언 목록 조회.
     *
     * [Data Flow]
     *   Client --GET /api/v1/champions--> RiotDataDragonService.getChampions()
     *     --> MySQL champion 테이블 (코스트, 이름 순 정렬) --> ChampionResponse 변환
     *   --> 200 OK JSON 배열 [{ championId, name, cost, iconUrl }]
     */
    @GetMapping("/champions")
    public ResponseEntity<List<ChampionResponse>> getChampions() {
        return ResponseEntity.ok(riotDataDragonService.getChampions());
    }

    /**
     * [역할] 재료 아이템 / 조합 아이템 목록 조회.
     *
     * [Data Flow]
     *   Client --GET /api/v1/items--> RiotDataDragonService.getItems()
     *     --> MySQL item 테이블 --> isComponent 기준으로 분리 --> ItemListResponse 변환
     *   --> 200 OK JSON { components: [...], combined: [...] }
     */
    @GetMapping("/items")
    public ResponseEntity<ItemListResponse> getItems() {
        return ResponseEntity.ok(riotDataDragonService.getItems());
    }
}
