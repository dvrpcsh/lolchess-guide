# CLAUDE.md

이 파일은 Claude Code(AI 코딩 도구)가 이 저장소에서 작업할 때 따를 컨텍스트와 규칙을 담고 있습니다.

## 프로젝트 개요

**TFT 시즌 18 실시간 덱 추천 웹 애플리케이션**

- **Backend**: Spring Boot 4.1 (Java 17, Gradle), Spring Data JPA, Spring Web MVC, Lombok
- **Database**: MySQL (`mysql-connector-j`)
- **Frontend**: React (예정, `frontend/` 디렉터리에 추가 예정)

### 디렉터리 구조

```
lolchess/
├── backend/                 # Spring Boot 애플리케이션 (Gradle 루트)
│   ├── build.gradle
│   ├── settings.gradle      # rootProject.name = 'lolchess'
│   └── src/
│       ├── main/java/com/lolchess/   # 기본 패키지 (LolchessApplication)
│       ├── main/resources/           # application.properties
│       └── test/java/com/lolchess/
├── frontend/                # React 앱 (예정)
├── CLAUDE.md
└── 작업일지.md
```

### 백엔드 패키지 구조 (권장)

```
com.lolchess
├── controller/   # HTTP 요청/응답 처리 (REST API 엔드포인트)
├── service/      # 비즈니스 로직, 트랜잭션 경계
├── repository/   # Spring Data JPA Repository 인터페이스
├── entity/       # JPA Entity (DB 테이블 매핑)
├── dto/          # 요청/응답 DTO (request/, response/ 하위 분리 가능)
├── config/       # 설정 클래스 (CORS, WebClient 등)
└── exception/    # 커스텀 예외, 전역 예외 처리(@RestControllerAdvice)
```

## 코딩 규칙 (CRITICAL)

### 1. 주석은 "역할"과 "데이터 흐름(Data Flow)"을 설명할 것

단순히 구문을 설명하는 주석(`// 리스트를 순회한다`)은 작성하지 않는다. 대신 다음을 명확히 설명한다.

- 이 API/클래스/메서드가 **시스템 전체에서 맡는 역할**
- 데이터가 **어디서 들어와서 → 어떻게 가공되고 → 어디로 나가는지**

```java
/**
 * [역할] 현재 메타에서 승률이 높은 추천 덱 목록을 제공하는 API.
 *
 * [Data Flow]
 *   Client(React) --GET /api/decks/recommend?tier=...-->
 *   DeckController --> DeckService.getRecommendedDecks()
 *     --> DeckRepository(MySQL deck 테이블 조회)
 *     --> Entity -> DeckResponse DTO 변환
 *   --> JSON 응답 반환
 */
@GetMapping("/api/decks/recommend")
public ResponseEntity<List<DeckResponse>> recommend(@RequestParam String tier) { ... }
```

### 2. DTO / Entity / Controller / Service 역할 분리를 엄격히 준수

| 계층 | 책임 | 금지 사항 |
|------|------|-----------|
| **Controller** | 요청 파라미터 검증(`@Valid`), Service 호출, `ResponseEntity`로 응답 | 비즈니스 로직 작성, Repository 직접 호출, Entity 직접 반환 |
| **Service** | 비즈니스 로직, 트랜잭션(`@Transactional`), Entity ↔ DTO 변환 | HTTP 관련 객체(`HttpServletRequest` 등) 의존 |
| **Repository** | DB 접근 (Spring Data JPA) | 비즈니스 로직 |
| **Entity** | DB 테이블 매핑, 도메인 상태 변경 메서드 | API 응답으로 직접 노출, `@Setter` 남용 |
| **DTO** | 계층 간/외부와의 데이터 전달 | JPA 어노테이션 사용 |

- **Entity는 절대 Controller 밖으로(API 응답으로) 노출하지 않는다.** 반드시 Response DTO로 변환한다.
- 요청 DTO와 응답 DTO는 분리한다 (예: `DeckCreateRequest`, `DeckResponse`).
- DTO 변환은 DTO의 정적 팩토리 메서드(`DeckResponse.from(deck)`) 또는 Service에서 수행한다.
- 조회 전용 Service 메서드에는 `@Transactional(readOnly = true)`를 붙인다.

### 3. Lombok 및 Spring Data JPA 표기법 가이드

**Entity**
```java
@Entity
@Table(name = "deck")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부 생성 차단
public class Deck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY) // 연관관계는 항상 LAZY
    @JoinColumn(name = "season_id")
    private Season season;

    @Builder
    private Deck(String name, Season season) { ... }

    // 상태 변경은 @Setter 대신 의미 있는 메서드로
    public void updateName(String name) { this.name = name; }
}
```
- Entity에 `@Setter`, `@Data`, `@AllArgsConstructor`, `@ToString`(연관관계 포함 시) 사용 금지.
- `@Builder`는 클래스가 아닌 생성자에 붙인다.
- 연관관계는 `FetchType.LAZY`를 기본으로 하고, N+1 문제는 `fetch join` / `@EntityGraph`로 해결한다.
- 테이블/컬럼명은 `snake_case`, 필드명은 `camelCase`.

**DTO**
- 가능하면 Java `record`를 사용한다. (`public record DeckResponse(Long id, String name) { ... }`)
- class가 필요하면 `@Getter` + `@NoArgsConstructor` (+ 필요 시 `@Builder`) 조합을 사용한다.

**Service / Controller**
- 의존성 주입은 생성자 주입만 사용: `@RequiredArgsConstructor` + `private final` 필드. (`@Autowired` 필드 주입 금지)
- 로깅은 `@Slf4j`를 사용한다.

**Repository**
- `JpaRepository<Entity, Long>`을 상속한다.
- 메서드 이름 쿼리 규칙을 따른다: `findBy...`, `existsBy...`, `countBy...`, `deleteBy...`
- 복잡한 쿼리는 `@Query`(JPQL)로 작성하고, 메서드명이 지나치게 길어지지 않게 한다.

## 자주 쓰는 명령어

모든 Gradle 명령은 `backend/` 디렉터리에서 실행한다. (Windows PowerShell에서는 `.\gradlew.bat` 사용)

```bash
cd backend

./gradlew build          # 컴파일 + 테스트 + JAR 생성 (build/libs/lolchess-0.0.1-SNAPSHOT.jar)
./gradlew bootRun        # 애플리케이션 실행 (기본 포트 8080)
./gradlew test           # 전체 테스트 실행
./gradlew test --tests "com.lolchess.LolchessApplicationTests"   # 특정 테스트 클래스만 실행
./gradlew clean build    # 빌드 결과물 삭제 후 재빌드
./gradlew build -x test  # 테스트 생략하고 빌드
```

## 작업 규칙

- 의미 있는 작업 단위를 마치면 루트의 `작업일지.md`에 `- YYYY-MM-DD: 작업 내용` 형식으로 한 줄을 추가한다.
- DB 접속 정보 등 비밀 값은 커밋하지 않는다. `.env` 또는 `application-local.properties`(gitignore 처리됨)를 사용한다.
