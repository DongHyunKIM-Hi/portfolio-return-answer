package com.practicefintech.portfolio.domain.portfolio.service;

import com.practicefintech.portfolio.common.entity.Holding;
import com.practicefintech.portfolio.common.entity.Portfolio;
import com.practicefintech.portfolio.common.exception.ApiException;
import com.practicefintech.portfolio.domain.portfolio.model.request.HoldingRequest;
import com.practicefintech.portfolio.domain.portfolio.model.request.PortfolioRequest;
import com.practicefintech.portfolio.domain.portfolio.model.response.HoldingView;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioDetailResponse;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioListItem;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioSummary;
import com.practicefintech.portfolio.domain.portfolio.repository.PortfolioRepository;
import com.practicefintech.portfolio.domain.price.service.PriceSnapshotStore;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import lombok.Getter;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 포트폴리오 생성, 조회, 수정, 삭제와 수익률 계산.
 *
 * <p>3단계에서 이 클래스에 생긴 변화는 하나다. 현재가를 {@code PriceClient}로 외부 서버에서 직접 받아 오지 않고,
 * {@link PriceSnapshotStore}에 이미 받아 둔 값을 읽는다. 계산 규칙은 2단계와 똑같은데도
 * 주기 갱신(A5), 토큰 재사용(A6), 장애 중 조회 생존(F2)이 한꺼번에 풀린다.
 * 요청 경로에서 외부 호출이 사라졌기 때문이다.
 *
 * <p>구현한 계산 규칙은 세 가지다 (README 6장).
 * <ul>
 *   <li>매입가는 담은 시점의 시세로 고정한다. 이후 시세가 움직여도 매입가와 매입금액은 그대로다 (A3).</li>
 *   <li>금액(매입금액, 평가금액, 수익금)은 모두 정수다. 가격과 수량이 정수라 소수가 생기는 자리가 없다.</li>
 *   <li>수익률만 소수 둘째 자리까지 HALF_UP으로 반올림한다 (A4).</li>
 * </ul>
 *
 * <p>명세에 없는 상황(시세를 아직 한 번도 받지 못했을 때)에 대한 결정은 가정 G1이고,
 * 이유와 고려한 다른 선택지는 {@code docs/assumptions.md}에 적어 두었다.
 */
@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    /** 보유 종목에 적힌 상품 코드가 실제로 있는 코드인지 확인하는 데만 쓴다. */
    private final ProductRepository productRepository;

    /**
     * 현재가를 읽는 곳. 외부 서버가 아니라 메모리에 보관된 스냅샷이다.
     * 2단계에서는 이 자리에 {@code PriceClient}가 있었고, 조회 한 번이 외부 호출 두 번이었다.
     */
    private final PriceSnapshotStore priceSnapshotStore;

    public PortfolioService(PortfolioRepository portfolioRepository, ProductRepository productRepository,
                             PriceSnapshotStore priceSnapshotStore) {
        this.portfolioRepository = portfolioRepository;
        this.productRepository = productRepository;
        this.priceSnapshotStore = priceSnapshotStore;
    }

    /**
     * 포트폴리오를 만든다. 이때의 시세를 매입가로 박아 저장하는 것이 A3의 출발점이다.
     * 저장 뒤 곧바로 상세 응답을 만들어 돌려주므로, 만든 직후에는 매입가와 현재가가 같고 수익률은 0.00이 된다.
     */
    public PortfolioDetailResponse create(PortfolioRequest request) {
        Portfolio portfolio = new Portfolio(request.getName());
        portfolio.replaceHoldings(buildHoldingsAtCurrentPrice(request));
        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public PortfolioDetailResponse getDetail(Object rawId) {
        return toDetail(findOrThrow(rawId));
    }

    /**
     * 포트폴리오 전체를 만든 순서(id 오름차순)로 돌려준다.
     * 목록도 상세와 같은 {@link #computeSummary} 하나를 쓴다. 계산식을 두 군데에 두면
     * 목록의 수익률과 상세의 수익률이 서로 달라지는 사고가 나기 때문이다.
     */
    public List<PortfolioListItem> list() {
        return portfolioRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                .map(p -> {
                    Summary s = computeSummary(p);
                    return new PortfolioListItem(p.getId(), p.getName(), s.getTotalValue(), s.getReturnRate());
                })
                .toList();
    }

    /**
     * 수정은 보유 상품을 전체 교체한다. 매입가는 남아 있던 종목까지 모두 수정 시점 시세로 다시 정한다.
     * 명세(README 6장)에 적힌 규칙이라 가정이 아니다.
     * 평균 단가를 다시 구하거나 기존 매입가를 살려 두는 구현이 아님에 주의한다.
     */
    public PortfolioDetailResponse update(Object rawId, PortfolioRequest request) {
        Portfolio portfolio = findOrThrow(rawId);
        portfolio.setName(request.getName());
        portfolio.replaceHoldings(buildHoldingsAtCurrentPrice(request));
        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public void delete(Object rawId) {
        portfolioRepository.delete(findOrThrow(rawId));
    }

    // ---- 내부 계산 ----

    /**
     * 요청을 검증한 뒤, 지금 받아 둔 시세를 매입가로 삼아 보유 종목을 만든다.
     * 생성과 수정이 같은 메서드를 쓰므로 검증 규칙과 매입가 결정 규칙이 두 API에서 어긋나지 않는다.
     *
     * <p>형식 검증(이름 길이, 수량 범위, 개수)은 {@code PortfolioRequest}의 애너테이션이 이미 끝냈다.
     * 여기서는 그것만으로 판단할 수 없는 세 가지를 본다.
     * 같은 코드가 두 번 들어왔는지, 있는 상품 코드인지, 그 코드의 시세를 지금 알고 있는지다.
     */
    private List<Holding> buildHoldingsAtCurrentPrice(PortfolioRequest request) {
        Set<String> seen = new HashSet<>();
        List<Holding> holdings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            // add가 false를 돌려주면 이미 들어 있던 코드다. 중복은 수량을 합치지 않고 요청 자체를 거절한다.
            if (!seen.add(h.getCode())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "같은 code를 두 번 쓸 수 없습니다: " + h.getCode());
            }
            // 형식은 맞지만 존재하지 않는 코드다. 형식 오류(INVALID_REQUEST)와 구분해 코드를 따로 준다.
            if (!productRepository.existsById(h.getCode())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRODUCT", "존재하지 않는 상품 코드입니다: " + h.getCode());
            }
            BigDecimal price = priceSnapshotStore.getPrice(h.getCode());
            if (price == null) {
                // 서버를 막 띄워 첫 갱신이 아직 끝나지 않은 구간이다. 매입가를 0이나 임의 값으로 박으면
                // 그 포트폴리오의 수익률이 영구히 틀어지므로, 저장하지 않고 503으로 거절하고 다시 요청하게 한다.
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_NOT_READY", "아직 시세를 받아오지 못했습니다.");
            }
            holdings.add(new Holding(h.getCode(), h.getQuantity(), price));
        }
        return holdings;
    }

    /** 상세 응답과 목록 응답이 함께 쓰는 계산 결과 묶음. 외부로 나가지 않는 내부 전용 타입이다. */
    @Getter
    private static class Summary {
        private final BigDecimal totalCost;   // 매입금액 합계. 저장된 매입가로 계산하므로 시세가 움직여도 그대로다
        private final BigDecimal totalValue;  // 평가금액 합계. 지금 스냅샷의 현재가로 계산한다
        private final BigDecimal profit;      // 평가금액에서 매입금액을 뺀 값. 손실이면 음수
        private final BigDecimal returnRate;  // 수익금을 매입금액으로 나눈 백분율, 소수 둘째 자리 반올림
        private final Instant asOf;           // 계산에 쓴 시세가 시세 서버에서 언제 만들어진 값인지
        private final boolean stale;          // 마지막 갱신이 실패해 직전 값을 계속 쓰고 있는 상태인지

        Summary(BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit, BigDecimal returnRate,
                Instant asOf, boolean stale) {
            this.totalCost = totalCost;
            this.totalValue = totalValue;
            this.profit = profit;
            this.returnRate = returnRate;
            this.asOf = asOf;
            this.stale = stale;
        }
    }

    /**
     * 매입금액, 평가금액, 수익금, 수익률을 한 번에 구한다.
     *
     * <p>매입금액은 저장된 매입가로, 평가금액은 지금 스냅샷의 현재가로 계산한다.
     * 두 금액이 서로 다른 가격을 쓰기 때문에 수익이 생긴다. 1단계는 둘 다 현재가로 계산해
     * 평가금액과 매입금액이 항상 같았고, 그래서 수익률이 영원히 0이었다.
     */
    private Summary computeSummary(Portfolio portfolio) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            totalCost = totalCost.add(h.cost());                 // 매입가 곱하기 수량, 담은 시점에 고정된 값
            totalValue = totalValue.add(currentPriceOf(h).multiply(BigDecimal.valueOf(h.getQuantity())));
        }
        // 상품 가격과 수량이 모두 정수라 금액도 정수가 된다. 소수 자리만 0으로 맞춘다.
        // 버리거나 반올림하는 것이 아니라 BigDecimal이 들고 있는 소수 자리 수를 정리하는 것이고,
        // 실제로 버릴 소수가 있으면 setScale(0)은 예외를 던지므로 금액이 정수라는 전제도 함께 확인된다.
        totalCost = totalCost.setScale(0);
        totalValue = totalValue.setScale(0);

        BigDecimal profit = totalValue.subtract(totalCost);
        // 수익률만 반올림(HALF_UP)으로 소수 둘째 자리까지 맞춘다.
        // 나눗셈을 먼저 소수 10자리까지 해 두고 100을 곱한 뒤 둘째 자리에서 반올림한다.
        // 자리를 넉넉히 잡지 않고 바로 두 자리로 나누면, 경계값에서 기대값과 1 차이가 난다.
        // 실측 예: 매입가 80,000에 현재가 80,092는 0.115가 되어 0.12로 올라가고,
        // 80,091은 0.11375가 되어 0.11로 내려간다. A4의 반올림 경계 벡터가 이 지점을 본다.
        BigDecimal returnRate = totalCost.signum() == 0
                // 매입금액이 0이면 나눌 수 없다. 수량 최솟값이 1이라 실제로는 오지 않는 경로지만,
                // 0으로 나누기 예외가 500으로 새어 나가지 않게 막아 둔다.
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : profit.divide(totalCost, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        // asOf와 stale은 계산 결과가 아니라 쓴 시세가 언제 것인지를 알리는 값이다. 스냅샷에서 그대로 가져온다.
        return new Summary(totalCost, totalValue, profit, returnRate,
                priceSnapshotStore.getAsOf(), priceSnapshotStore.isStale());
    }

    /**
     * 현재가. 시세를 한 번도 받지 못한 예외적인 경우에는 매입가로 대체해 계산을 이어간다 (가정 G1).
     * 그 종목의 수익률이 0으로 보이는 대신 조회 자체는 응답한다.
     * 여기서 예외를 던지면 시세 서버 장애가 조회 API의 5xx로 그대로 번지고, 그것이 치명 오류 F2다.
     */
    private BigDecimal currentPriceOf(Holding holding) {
        BigDecimal current = priceSnapshotStore.getPrice(holding.getCode());
        return current != null ? current : holding.getPurchasePrice();
    }

    /** 엔티티를 상세 응답 형태로 바꾼다. 응답 형식은 명세에 고정돼 있어 필드 이름을 바꿀 수 없다. */
    private PortfolioDetailResponse toDetail(Portfolio portfolio) {
        Summary s = computeSummary(portfolio);

        List<HoldingView> holdingViews = portfolio.getHoldings().stream()
                .map(h -> new HoldingView(h.getCode(), h.getQuantity(),
                        h.getPurchasePrice().setScale(0), currentPriceOf(h).setScale(0)))
                .toList();

        PortfolioSummary summary = new PortfolioSummary(
                s.getTotalCost(), s.getTotalValue(), s.getProfit(), s.getReturnRate(), s.getAsOf(),
                // 정상일 때는 false를 보내지 않고 필드 자체를 뺀다. 명세에 없는 추가 필드라
                // 평소 응답을 명세와 똑같이 유지하고 장애 때만 나타나게 했다 (가정 G1).
                s.isStale() ? Boolean.TRUE : null);

        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(), holdingViews, summary);
    }

    /**
     * 경로 변수로 받은 id로 포트폴리오를 찾고, 없으면 404로 끝낸다.
     *
     * <p>id를 {@code Long} 대신 문자열로 받아 여기서 변환하는 이유는 응답 코드 때문이다.
     * 컨트롤러 파라미터를 {@code Long}으로 두면 {@code /api/v1/portfolios/abc} 같은 요청이
     * 타입 변환 단계에서 400으로 끝나 버린다. 없는 포트폴리오를 가리키는 요청이므로 404가 맞다고 보고,
     * 숫자가 아닌 값도 못 찾은 것으로 처리한다.
     */
    private Portfolio findOrThrow(Object rawId) {
        Long id;
        try {
            id = rawId instanceof Long l ? l : Long.valueOf(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + rawId);
        }
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + id));
    }
}
