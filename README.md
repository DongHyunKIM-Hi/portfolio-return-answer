# 가상 포트폴리오 수익률 서비스 모범 답안

> 가상 포트폴리오 수익률 서비스 과제의 **모범 답안**입니다.
> 정답은 하나가 아니며, 이 코드는 좋은 구현의 한 예시입니다. 과제를 풀어 본 뒤 설계와 비교해 보거나, 평가에 참고할 수 있습니다.
> 평가 기준은 `평가기준표.md`에 있습니다. (문서 버전 v2.0, 2026-11-21)

지원자용 과제 저장소: [portfolio-return-assignment](https://github.com/DongHyunKIM-Hi/portfolio-return-assignment)

| | |
|---|---|
| 평가 역량 | 자율 구현과 외부 서버 연동 |
| 주요 과제 | 토큰 만료와 장애가 있는 외부 시세 연동, 주기 갱신, 매입가 고정과 수익률 계산 |
| 제공 가상 서버 | 시세 서버 `ghcr.io/donghyunkim-hi/portfolio-price-server:1.2.0` (포트 9091) |
| 실행에 필요한 것 | JDK 17, Docker (시세 서버) |

## 구성

정답 코드는 저장소 루트의 Gradle 프로젝트 하나입니다.

- 지원자에게 제공한 빈 시작 프로젝트(`starter-project/`)에 도메인 코드를 모두 새로 작성한 참고 구현입니다.
- 정답은 하나가 아닙니다. 특히 명세에 없는 빈틈(G1)은 다른 결정이라도 근거가 있으면 인정합니다. (평가 기준표 B 영역)

해설 영상용 **단계별 구현은 커밋으로** 구분되어 있습니다.

| 단계 | 태그 | 내용 |
|---|---|---|
| 1 | `step1` | 설계한 흐름이 도는지만 확인합니다. 입력 검증도 예외 처리도 하지 않고, 시세는 요청이 올 때마다 받아 옵니다. |
| 2 | `step2` | 입력 검증, 에러 응답, 매입가 고정, 수익률 반올림을 채웁니다. 아직 시세는 요청마다 받아 옵니다. |
| 3 | `step3` | 외부 호출을 요청 경로 밖으로 빼냅니다. 주기 갱신, 토큰 재사용, 장애 대비가 한 번에 풀립니다. (최종, `main`과 같음) |

단계마다 무엇이 추가되는지는 `git diff step1 step2`, `git diff step2 step3`로 바로 볼 수 있습니다.
단계별 설명과 실측 결과는 `docs/stages.md`에 있습니다.

| 파일 | 내용 |
|---|---|
| `평가기준표.md` | 배점, 수준별 기준, 확인 방법, 치명 오류, 기록 양식 (100점 만점) |
| `docs/stages.md` | 단계별 구현 설명과 단계마다 실제 실행해 측정한 결과 |
| `check-scenarios.sh` | 시나리오 S1~S8을 한 번에 보내 통과/실패를 보여 주는 스크립트 |
| `docs/assumptions.md` | 명세에 없는 상황에 대한 가정 문서 |

## 실행

```bash
./gradlew bootRun     # 포트 8080
./gradlew test        # 정답 코드 테스트 3개 (계산 검증)
bash check-scenarios.sh # 시나리오 자동 확인 (가상 시세 서버가 떠 있어야 함, 별도 터미널에서)
```

### 가상 서버 실행 (관리자 기능으로 시나리오를 직접 고정하고 싶을 때)

```bash
docker run -p 9091:9091 -e ADMIN_ENABLED=true -e ADMIN_TOKEN=admin-secret ghcr.io/donghyunkim-hi/portfolio-price-server:1.2.0
```

관리자 요청에는 헤더 `X-Admin-Token: admin-secret`을 붙입니다.

> `POST /admin/reset`은 **일시 장애율을 기본값 5%로 되돌립니다.** 초기화한 뒤에는 `POST /admin/failure` 본문 `{"mode":"NONE","transientFailureRate":0}`을 다시 보내세요. 끄지 않으면 정상 제출물도 A5, A6에서 운에 따라 떨어집니다.

> 지원자에게 제공한 환경에서는 관리자 기능이 꺼져 있습니다.

## 테스트

```bash
./gradlew test
```

`PortfolioCalculationTest`에서 매입가 고정, 수익률 반올림, 수정 시 매입가 재설정을 검증합니다.

## 주요 설계 결정

| 주제 | 결정 |
|---|---|
| 상품 데이터 적재 | 제공한 CSV는 코드가 겹치지 않고 표기도 통일돼 있으므로, 정리하지 않고 적힌 값을 그대로 저장합니다. |
| 상품 조회 | 전체를 코드 오름차순 배열로 돌려줍니다. 검색, 정렬, 필터, 페이지 처리는 명세에 없습니다. |
| 시세 연동 | 토큰을 보관해 재사용하고, 401을 받으면 한 번 재발급한 뒤 다시 요청합니다. |
| 시세 갱신 | 30초 주기 스케줄러를 사용합니다. 갱신에 실패하면 마지막으로 성공한 값을 유지하고, `priceStale: true`로 오래된 값임을 표시합니다. 이 필드는 명세에 없는 추가 필드라 `docs/assumptions.md`에 기록했습니다. |
| 포트폴리오 계산 | 매입 시점의 가격을 보유 종목에 저장해 고정합니다. 금액은 모두 정수이고, 수익률만 HALF_UP으로 소수 둘째 자리까지 반올림합니다. |
| 수정 API | 보유 상품을 전체 교체하고 매입가를 모두 수정 시점 시세로 다시 정합니다. 명세에 적힌 규칙이라 가정이 아닙니다. |

빈틈 G1에 대한 결정과 이유는 `docs/assumptions.md`에 있습니다. 평가 기준표 B 영역에서 상 수준에 해당하는 예시입니다.

## 검증 결과

가상 시세 서버(관리자 API 포함)와 정답 서버를 함께 실행하고 `check-scenarios.sh`와 curl로 확인했습니다. 아래는 2026-11-21에 측정한 값입니다.

| 시나리오 | 결과 |
|---|---|
| S1 상품 목록 | 배열로 80개, 코드 오름차순. 현재가는 모두 정수 |
| S2 포트폴리오 CRUD와 검증 | 생성 201, 조회 200, 삭제 204. 빈 이름, 수량 0, 코드 중복은 400 `INVALID_REQUEST`, 없는 코드는 400 `UNKNOWN_PRODUCT`, 없는 ID는 404 `PORTFOLIO_NOT_FOUND` |
| S3 포트폴리오 목록 | 배열로 응답하고 각 항목에 `id`, `name`, `totalValue`, `returnRate` |
| S4 매입가 고정 | 가격을 10,000에서 11,000으로 바꿔도 매입가 10,000과 원가 100,000은 그대로. 현재가와 평가금액만 바뀜 |
| S5 평가 계산 | 10주 보유 시 `totalCost` 100,000 / `totalValue` 110,000 / `profit` 10,000 / `returnRate` 10.00 |
| S6 시세 주기 갱신 | 가격을 바꾼 뒤 30초 안에 평가금액에 반영 (3회 측정: 24초, 28초, 30초) |
| S7 토큰 만료 | 관리자 API로 강제 만료시킨 뒤 28초에 갱신 재개. 토큰 발급 2회 대비 시세 조회 4~5회로 재사용 확인 |
| S8 시세 서버 장애 | `ALWAYS_503` 상태에서 상품 조회, 목록 조회, 상세 조회가 모두 200. `priceStale: true` 표시 |

`check-scenarios.sh` 전체 실행 결과는 통과 8 / 실패 0입니다.

## 패키지 구조

```
com.practicefintech.portfolio
├── PortfolioApplication.java
├── common/
│   ├── entity/        # Product, Portfolio, Holding (JPA 엔티티)
│   ├── enums/          # ProductType
│   ├── exception/      # ApiException, ErrorResponse, GlobalExceptionHandler
│   └── config/         # PriceClientProperties, ProductsProperties
└── domain/
    ├── product/
    │   ├── controller/
    │   ├── service/      # ProductService, ProductCsvLoader
    │   ├── repository/
    │   └── model/response/
    ├── portfolio/
    │   ├── controller/
    │   ├── service/      # PortfolioService (계산, 가정 G1 반영)
    │   ├── repository/
    │   └── model/
    │       ├── request/
    │       └── response/
    └── price/
        ├── service/      # PriceClient(토큰 보관), PriceRefreshScheduler, PriceSnapshotStore
        └── model/dto/     # 가상 시세 서버 응답 형식(External*)
```

DTO는 record 대신 Lombok(`@Getter` 등)을 사용한 일반 클래스로 작성했습니다.

---

## 평가자 참고

이 아래는 평가에 참고하는 내용입니다.

- **정답은 하나가 아닙니다.** 평가 기준표는 결과뿐 아니라 설계를 선택한 근거도 평가합니다.
- 판정이 애매하면 낮은 점수를 주지 말고 **판정 보류**로 기록한 뒤 이유를 남깁니다.
- 기능 확인은 지원자 서버를 직접 실행하고 요청을 보내는 방식으로 진행합니다. `bash check-scenarios.sh`로 시나리오를 한 번에 확인할 수 있습니다. 기대 동작이 헷갈리면 이 정답 코드를 실행해 같은 요청을 보내 보세요.
- 지원자가 바뀔 때마다 지원자 서버를 **새로 실행**합니다. 재시작하지 않으면 이전 확인의 데이터가 남습니다.
