package com.lolchess.service;

import com.lolchess.dto.ChampionResponse;
import com.lolchess.dto.ItemListResponse;
import com.lolchess.dto.ItemResponse;
import com.lolchess.dto.datadragon.DataDragonResponse;
import com.lolchess.entity.ChampionEntity;
import com.lolchess.entity.ItemEntity;
import com.lolchess.repository.ChampionRepository;
import com.lolchess.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * [역할] 라이엇 공식 정적 데이터(Data Dragon)에서 TFT 챔피언/아이템 정보를 받아 DB와 동기화하고,
 *   동기화된 데이터를 프론트엔드용 DTO로 제공하는 서비스.
 *
 * [동기화 Data Flow]
 *   RiotDataSyncRunner(서버 기동) --> syncIfOutdated()
 *     1) GET /api/versions.json --> 최신 패치 버전 (예: 16.19.1)
 *     2) DB에 저장된 patchVersion과 비교 --> 같으면 종료
 *     3) GET /cdn/{version}/data/ko_KR/tft-champion.json, tft-item.json --> DataDragonResponse
 *     4) 최신 시즌 챔피언 / 기본 아이템만 골라 Entity로 변환
 *     5) 하나의 트랜잭션에서 기존 champion/item 행 삭제 후 새 데이터 저장
 *   ※ "latest" 경로(/cdn/latest/...)는 Data Dragon이 403을 반환하므로 versions.json으로 버전을 구한다.
 *
 * [조회 Data Flow]
 *   RiotDataController --> getChampions() / getItems() --> Repository --> Response DTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiotDataDragonService {

    private static final String BASE_URL = "https://ddragon.leagueoflegends.com";
    private static final String VERSIONS_URL = BASE_URL + "/api/versions.json";
    private static final String DATA_URL = BASE_URL + "/cdn/{version}/data/ko_KR/{file}";
    private static final String CHAMPION_ICON_URL = BASE_URL + "/cdn/%s/img/tft-champion/%s";
    private static final String ITEM_ICON_URL = BASE_URL + "/cdn/%s/img/tft-item/%s";

    // 챔피언 key 예: Maps/Shipping/Map22/Sets/TFTSet18/Shop/DA_Draven18 -> 시즌 번호 18 추출
    private static final Pattern SET_SHOP_KEY = Pattern.compile("/Sets/TFTSet(\\d+)/Shop/");

    // 기본 아이템 id 접두사. 증강/유물/시즌 한정 아이템 등은 다른 접두사를 사용하므로 제외된다.
    private static final String STANDARD_ITEM_PREFIX = "TFT_Item_";
    // TFT_Item_ 중 실제 게임 아이템이 아닌 것 (유물, 테스트용, 빈 슬롯 등)
    private static final List<String> EXCLUDED_ITEM_KEYWORDS =
            List.of("Artifact_", "Debug", "UnusableSlot", "EmptyBag", "Choncc");

    // Data Dragon에는 조합법 정보가 없으므로 재료 아이템은 id로 직접 지정한다.
    private static final Set<String> COMPONENT_ITEM_IDS = Set.of(
            "TFT_Item_BFSword", "TFT_Item_RecurveBow", "TFT_Item_ChainVest", "TFT_Item_NegatronCloak",
            "TFT_Item_NeedlesslyLargeRod", "TFT_Item_TearOfTheGoddess", "TFT_Item_GiantsBelt",
            "TFT_Item_SparringGloves", "TFT_Item_Spatula", "TFT_Item_FryingPan");

    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ChampionRepository championRepository;
    private final ItemRepository itemRepository;

    /**
     * DB 데이터가 비어 있거나 최신 패치 버전이 아니면 Data Dragon에서 다시 받아 저장한다.
     * 외부 HTTP 호출은 트랜잭션 밖에서 수행하여, 응답을 기다리는 동안 DB 커넥션을 붙잡지 않는다.
     */
    public void syncIfOutdated() {
        String latestVersion = fetchLatestVersion();
        if (isUpToDate(latestVersion)) {
            log.info("[DataDragon] 챔피언/아이템 데이터가 최신 버전({})입니다. 동기화를 건너뜁니다.", latestVersion);
            return;
        }

        List<ChampionEntity> champions = toChampionEntities(fetchData(latestVersion, "tft-champion.json"), latestVersion);
        List<ItemEntity> items = toItemEntities(fetchData(latestVersion, "tft-item.json"), latestVersion);

        // 삭제와 저장을 한 트랜잭션으로 묶어, 중간에 실패해도 기존 데이터가 그대로 남도록 한다.
        transactionTemplate.executeWithoutResult(status -> {
            championRepository.deleteAllInBatch();
            itemRepository.deleteAllInBatch();
            championRepository.saveAll(champions);
            itemRepository.saveAll(items);
        });
        log.info("[DataDragon] 패치 {} 동기화 완료: 챔피언 {}개, 아이템 {}개", latestVersion, champions.size(), items.size());
    }

    /**
     * GET /api/v1/champions - 코스트 순으로 정렬된 챔피언 목록
     */
    @Transactional(readOnly = true)
    public List<ChampionResponse> getChampions() {
        return championRepository.findAllByOrderByCostAscNameAsc().stream()
                .map(ChampionResponse::from)
                .toList();
    }

    /**
     * GET /api/v1/items - 재료 아이템과 조합 아이템을 나눈 목록
     */
    @Transactional(readOnly = true)
    public ItemListResponse getItems() {
        List<ItemEntity> items = itemRepository.findAllByOrderByNameAsc();
        return new ItemListResponse(
                items.stream().filter(ItemEntity::isComponent).map(ItemResponse::from).toList(),
                items.stream().filter(item -> !item.isComponent()).map(ItemResponse::from).toList()
        );
    }

    private String fetchLatestVersion() {
        String[] versions = restTemplate.getForObject(VERSIONS_URL, String[].class);
        if (versions == null || versions.length == 0) {
            throw new IllegalStateException("Data Dragon 버전 목록이 비어 있습니다.");
        }
        return versions[0]; // 최신 버전이 맨 앞에 온다
    }

    private boolean isUpToDate(String latestVersion) {
        return championRepository.count() > 0
                && itemRepository.count() > 0
                && !championRepository.existsByPatchVersionNot(latestVersion)
                && !itemRepository.existsByPatchVersionNot(latestVersion);
    }

    private DataDragonResponse fetchData(String version, String file) {
        DataDragonResponse response = restTemplate.getForObject(DATA_URL, DataDragonResponse.class, version, file);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Data Dragon " + file + " 응답이 비어 있습니다.");
        }
        return response;
    }

    /**
     * 챔피언 데이터에는 튜토리얼과 과거 시즌 챔피언이 모두 섞여 있으므로,
     * key 경로의 시즌 번호(TFTSet{n})가 가장 큰 시즌의 상점(Shop) 기물만 남긴다.
     */
    private List<ChampionEntity> toChampionEntities(DataDragonResponse response, String version) {
        int latestSet = response.data().keySet().stream()
                .map(SET_SHOP_KEY::matcher)
                .filter(Matcher::find)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElseThrow(() -> new IllegalStateException("챔피언 데이터에서 시즌 정보를 찾을 수 없습니다."));
        String latestSetPath = "/Sets/TFTSet" + latestSet + "/Shop/";

        // 같은 id가 여러 key로 존재할 수 있어 id 기준으로 중복 제거
        Map<String, ChampionEntity> champions = new LinkedHashMap<>();
        response.data().forEach((key, entry) -> {
            if (key.contains(latestSetPath) && entry.cost() != null && hasText(entry.name())) {
                champions.putIfAbsent(entry.id(), ChampionEntity.builder()
                        .championId(entry.id())
                        .name(entry.name())
                        .cost(entry.cost())
                        .iconUrl(iconUrl(CHAMPION_ICON_URL, version, entry))
                        .patchVersion(version)
                        .build());
            }
        });
        log.info("[DataDragon] 최신 시즌 TFTSet{} 챔피언 {}개 추출", latestSet, champions.size());
        return List.copyOf(champions.values());
    }

    /**
     * 기본 아이템은 key가 경로 없이 "TFT_Item_..." 으로 바로 시작한다.
     * (보상 상자/전리품 구 등은 "TFT_GenericAssistItems/TFT_Item_..." 처럼 폴더 경로가 붙어 있어 제외된다.)
     */
    private List<ItemEntity> toItemEntities(DataDragonResponse response, String version) {
        return response.data().entrySet().stream()
                .filter(e -> e.getKey().startsWith(STANDARD_ITEM_PREFIX) && !e.getKey().contains("/"))
                .map(Map.Entry::getValue)
                .filter(entry -> entry.id() != null && EXCLUDED_ITEM_KEYWORDS.stream().noneMatch(entry.id()::contains))
                .filter(entry -> hasText(entry.name()))
                .collect(Collectors.toMap(DataDragonResponse.Entry::id, entry -> entry, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(DataDragonResponse.Entry::id))
                .map(entry -> ItemEntity.builder()
                        .itemId(entry.id())
                        .name(entry.name())
                        .isComponent(COMPONENT_ITEM_IDS.contains(entry.id()))
                        .iconUrl(iconUrl(ITEM_ICON_URL, version, entry))
                        .patchVersion(version)
                        .build())
                .toList();
    }

    private String iconUrl(String format, String version, DataDragonResponse.Entry entry) {
        return entry.image() != null && hasText(entry.image().full())
                ? String.format(format, version, entry.image().full())
                : null;
    }

    private boolean hasText(String value) {
        return Objects.nonNull(value) && !value.isBlank();
    }
}
