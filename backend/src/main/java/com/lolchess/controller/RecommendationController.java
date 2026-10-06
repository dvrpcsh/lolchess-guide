package com.lolchess.controller;

import com.lolchess.dto.MetaCompResponse;
import com.lolchess.dto.RecommendRequest;
import com.lolchess.dto.RecommendResponse;
import com.lolchess.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [역할] 덱 추천 기능의 REST API 진입점.
 *   HTTP 요청을 받아 RecommendationService에 위임하고, 결과 DTO를 JSON으로 응답한다.
 *   (비즈니스 로직과 DB 접근은 이 계층에서 수행하지 않음)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * [역할] 현재 게임 상황에 맞는 메타 덱 추천.
     *
     * [Data Flow]
     *   Client --POST /api/v1/recommend (JSON: shopUnits, boardUnits, itemCounts)-->
     *   RecommendRequest 역직렬화 --> RecommendationService.recommend()
     *     --> MySQL meta_comp 조회 + 점수 계산/정렬
     *   --> List<RecommendResponse> --> 200 OK JSON (일치하는 덱이 없으면 빈 배열)
     */
    @PostMapping("/recommend")
    public ResponseEntity<List<RecommendResponse>> recommend(@RequestBody RecommendRequest request) {
        return ResponseEntity.ok(recommendationService.recommend(request));
    }

    /**
     * [역할] 등록된 메타 덱 전체 목록 조회.
     *
     * [Data Flow]
     *   Client --GET /api/v1/meta-comps--> RecommendationService.getAllMetaComps()
     *     --> MySQL meta_comp 전체 조회 --> MetaCompResponse 변환
     *   --> 200 OK JSON 배열
     */
    @GetMapping("/meta-comps")
    public ResponseEntity<List<MetaCompResponse>> getMetaComps() {
        return ResponseEntity.ok(recommendationService.getAllMetaComps());
    }
}
