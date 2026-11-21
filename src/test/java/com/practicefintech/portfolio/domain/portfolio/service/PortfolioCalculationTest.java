package com.practicefintech.portfolio.domain.portfolio.service;

import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.ProductType;
import com.practicefintech.portfolio.domain.portfolio.model.request.HoldingRequest;
import com.practicefintech.portfolio.domain.portfolio.model.request.PortfolioRequest;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioDetailResponse;
import com.practicefintech.portfolio.domain.price.service.PriceSnapshotStore;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 매입가 고정, 수익률 반올림, 수정 시 매입가 재설정을 검증한다. 요구사항 A3, A4와 시나리오 S4, S5에 해당한다.
 *
 * <p>3단계 구조라서 이 테스트를 쓸 수 있다. 조회가 외부 서버를 부르지 않고 {@link PriceSnapshotStore}만 읽으므로,
 * 스냅샷을 원하는 값으로 채워 놓으면 시세 서버 없이도 계산을 확인할 수 있다.
 * 1, 2단계처럼 서비스가 직접 외부를 부르는 구조라면, 시세 서버를 띄우거나 가짜 클라이언트를 만들어야 하고
 * 가격을 바꿔 가며 확인하는 일도 쉽지 않다. 구조를 바꾼 덕이 테스트에서도 드러나는 지점이다.
 *
 * <p>{@code @Transactional}이 붙어 있어 각 테스트가 끝나면 저장한 데이터가 되돌려진다.
 * 테스트 순서에 따라 결과가 달라지지 않는다.
 */
@SpringBootTest
@Transactional
class PortfolioCalculationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PriceSnapshotStore priceSnapshotStore;
    @Autowired
    private PortfolioService portfolioService;

    /**
     * 상품 두 개를 넣고 시세를 채운다.
     *
     * <p>CSV에 있는 실제 코드(A001 등)를 쓰지 않고 T001, T002를 따로 만든 이유는,
     * CSV 데이터가 바뀌면 테스트가 함께 깨지기 때문이다. 검증하려는 것은 계산 규칙이지 특정 상품의 값이 아니다.
     * 가격도 계산하기 쉬운 값으로 잡았다.
     */
    @BeforeEach
    void setUp() {
        productRepository.save(new Product("T001", "테스트 상품 1", ProductType.ETF));
        productRepository.save(new Product("T002", "테스트 상품 2", ProductType.BOND));

        priceSnapshotStore.update(
                Map.of("T001", new BigDecimal("10000"), "T002", new BigDecimal("20000")),
                Instant.now());
    }

    /** 시세가 올라도 매입가와 매입금액은 그대로이고 현재가와 평가금액만 움직이는지 확인한다 (A3). */
    @Test
    void purchasePriceStaysFixedWhenMarketPriceChanges() {
        PortfolioDetailResponse created = portfolioService.create(
                new PortfolioRequest("테스트", List.of(new HoldingRequest("T001", 10L))));

        // 시세가 바뀌어도
        priceSnapshotStore.update(Map.of("T001", new BigDecimal("15000")), Instant.now());

        PortfolioDetailResponse after = portfolioService.getDetail(created.getId());
        assertEquals(new BigDecimal("10000"), after.getHoldings().get(0).getPurchasePrice()); // 매입가는 그대로
        assertEquals(new BigDecimal("15000"), after.getHoldings().get(0).getCurrentPrice()); // 현재가만 바뀜
        assertEquals(new BigDecimal("150000"), after.getSummary().getTotalValue()); // 10 * 15000
        assertEquals(new BigDecimal("100000"), after.getSummary().getTotalCost()); // 10 * 10000 (고정)
        assertEquals(new BigDecimal("50000"), after.getSummary().getProfit());
        assertEquals(new BigDecimal("50.00"), after.getSummary().getReturnRate());
    }

    /**
     * 수익률이 소수 둘째 자리에서 HALF_UP으로 반올림되는지 확인한다 (A4).
     * 나누어떨어지지 않는 값을 골라, 자리수를 넉넉히 두고 계산하는지까지 함께 걸린다.
     */
    @Test
    void returnRateRoundsHalfUpToTwoDecimals() {
        PortfolioDetailResponse created = portfolioService.create(
                new PortfolioRequest("반올림 테스트", List.of(new HoldingRequest("T002", 3L))));

        // 원가 60000, 평가금액 60099, 수익금 99라서 수익률은 0.165%가 되고 둘째 자리에서 올라 0.17이다.
        priceSnapshotStore.update(Map.of("T002", new BigDecimal("20033")), Instant.now());

        PortfolioDetailResponse after = portfolioService.getDetail(created.getId());
        assertEquals(new BigDecimal("60099"), after.getSummary().getTotalValue()); // 3 * 20033
        assertEquals(new BigDecimal("99"), after.getSummary().getProfit());
        assertEquals(new BigDecimal("0.17"), after.getSummary().getReturnRate()); // 99/60000*100 = 0.165
    }

    /**
     * 수정 API가 매입가를 수정 시점 시세로 다시 정하는지 확인한다.
     * 수량이 같아도 매입가가 바뀌는 것이 요점이다. 기존 매입가를 살려 두거나 평균 단가를 구하는 구현과 결과가 다르다.
     */
    @Test
    void updateRepricesEveryHoldingAtUpdateTime() {
        PortfolioDetailResponse created = portfolioService.create(
                new PortfolioRequest("수정 테스트", List.of(new HoldingRequest("T001", 10L))));
        assertEquals(new BigDecimal("100000"), created.getSummary().getTotalCost());

        // 시세가 오른 뒤 수정하면 매입가도 수정 시점 시세로 다시 정해진다
        priceSnapshotStore.update(Map.of("T001", new BigDecimal("12000")), Instant.now());
        PortfolioDetailResponse updated = portfolioService.update(created.getId(),
                new PortfolioRequest("수정 테스트", List.of(new HoldingRequest("T001", 10L))));

        assertEquals(new BigDecimal("12000"), updated.getHoldings().get(0).getPurchasePrice());
        assertEquals(new BigDecimal("120000"), updated.getSummary().getTotalCost());
        // 매입가와 현재가가 같아졌으므로 수익금은 0이다. 수정 직후에는 항상 이렇게 된다.
        assertEquals(new BigDecimal("0"), updated.getSummary().getProfit());
    }
}
