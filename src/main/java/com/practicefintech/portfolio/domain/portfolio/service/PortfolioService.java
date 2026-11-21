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
import com.practicefintech.portfolio.domain.price.service.PriceClient;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import lombok.Getter;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 포트폴리오 생성, 조회, 수정, 삭제와 수익률 계산.
 *
 * <p>2단계에서 채운 것은 네 가지다.
 * <ul>
 *   <li>입력 검증: 같은 코드가 두 번 들어왔는지, 있는 상품 코드인지 확인한다.
 *       이름 길이와 수량 범위는 {@code PortfolioRequest}의 애너테이션이 맡는다.</li>
 *   <li>에러 응답: 상황마다 {@link ApiException}을 던진다. 1단계에서 500으로 나갔던 것들이
 *       400, 404, 503으로 갈라진다.</li>
 *   <li>매입가 고정: 담은 시점의 시세를 보유 종목에 저장한다. 1단계는 이 값을 저장하지 않아
 *       조회할 때마다 현재가를 매입가 자리에 넣었고, 그래서 수익률이 영원히 0이었다.</li>
 *   <li>수익률 반올림: 소수 둘째 자리까지 HALF_UP으로 맞춘다.</li>
 * </ul>
 * 이 네 가지로 요구사항 A2, A3, A4와 시나리오 S2, S4, S5가 통과한다. 통과 수가 3에서 6으로 올라간다.
 *
 * <p>여전히 하지 않는 것은 외부 호출의 위치다. 시세는 요청이 들어올 때마다 시세 서버에서 받아 온다.
 * 아래 {@code priceClient.fetchPrices()} 호출이 세 군데(목록, 상세, 보유 종목 만들기)에 흩어져 있는데,
 * 이것이 남은 문제 두 개의 원인이다.
 * <ul>
 *   <li>시세 서버가 장애면 조회가 500으로 깨진다. 평가 기준표의 치명 오류 F2이고 시나리오 S8이 이것을 본다.</li>
 *   <li>요청마다 토큰을 새로 발급받는다. 요구사항 A6이 발급 횟수를 보므로 걸린다.</li>
 * </ul>
 * 계산은 맞는데 구조 때문에 떨어지는 상태다. 3단계에서 외부 호출을 요청 경로 밖으로 빼내면 둘이 함께 풀린다.
 */
@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    /** 보유 종목에 적힌 상품 코드가 실제로 있는 코드인지 확인하는 데만 쓴다. */
    private final ProductRepository productRepository;

    /**
     * 시세 서버 클라이언트. 요청을 처리하는 도중에 이것을 부르기 때문에 외부 서버의 상태가 조회 결과에 그대로 드러난다.
     * 3단계에서는 이 자리가 메모리 스냅샷으로 바뀐다.
     */
    private final PriceClient priceClient;

    public PortfolioService(PortfolioRepository portfolioRepository, ProductRepository productRepository,
                             PriceClient priceClient) {
        this.portfolioRepository = portfolioRepository;
        this.productRepository = productRepository;
        this.priceClient = priceClient;
    }

    /**
     * 포트폴리오를 만든다. 이때의 시세를 매입가로 박아 저장하는 것이 A3의 출발점이다.
     *
     * <p>이 메서드 한 번에 시세 서버를 두 번 부른다. 매입가를 정할 때 한 번({@code buildHoldingsAtCurrentPrice}),
     * 응답을 만들 때 또 한 번({@code toDetail})이다. 토큰 발급까지 세면 외부 호출이 네 번이다.
     * 지금은 값이 맞게 나오므로 문제가 보이지 않지만, 3단계에서 호출 횟수를 셀 때 이 구조가 드러난다.
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
     *
     * <p>시세를 반복문 안이 아니라 앞에서 한 번만 받아 와 모든 포트폴리오가 같은 값을 쓴다.
     * 포트폴리오마다 받아 오면 호출이 개수만큼 늘고, 목록 안에서 서로 다른 시점의 시세로 계산된 금액이 섞인다.
     */
    public List<PortfolioListItem> list() {
        Map<String, BigDecimal> prices = priceClient.fetchPrices();
        return portfolioRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                .map(p -> {
                    Summary s = computeSummary(p, prices);
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
     * 요청을 검증한 뒤, 지금 받아 온 시세를 매입가로 삼아 보유 종목을 만든다.
     * 생성과 수정이 같은 메서드를 쓰므로 검증 규칙과 매입가 결정 규칙이 두 API에서 어긋나지 않는다.
     *
     * <p>형식 검증(이름 길이, 수량 범위, 개수)은 {@code PortfolioRequest}의 애너테이션이 이미 끝냈다.
     * 여기서는 그것만으로 판단할 수 없는 세 가지를 본다.
     * 같은 코드가 두 번 들어왔는지, 있는 상품 코드인지, 그 코드의 시세를 지금 알고 있는지다.
     */
    private List<Holding> buildHoldingsAtCurrentPrice(PortfolioRequest request) {
        // 매입가로 쓸 시세를 받아 온다. 종목마다 받아 오지 않고 한 번만 받아 와,
        // 한 요청으로 담은 종목들이 모두 같은 시점의 시세를 매입가로 갖게 한다.
        Map<String, BigDecimal> prices = priceClient.fetchPrices();
        Set<String> seen = new HashSet<>();
        List<Holding> holdings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            // add가 false를 돌려주면 이미 들어 있던 코드다. 중복은 수량을 합치지 않고 요청 자체를 거절한다.
            if (!seen.add(h.getCode())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "같은 code를 두 번 쓸 수 없습니다: " + h.getCode());
            }
            // 형식은 맞지만 존재하지 않는 코드다. 형식 오류(INVALID_REQUEST)와 구분해 코드를 따로 준다.
            // 1단계에서는 이 검사가 없어 없는 코드가 그대로 저장되고 계산 단계에서 500이 났다.
            if (!productRepository.existsById(h.getCode())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRODUCT", "존재하지 않는 상품 코드입니다: " + h.getCode());
            }
            BigDecimal price = prices.get(h.getCode());
            if (price == null) {
                // 상품 목록에는 있는데 시세 응답에는 없는 경우다. 매입가를 0이나 임의 값으로 박으면
                // 그 포트폴리오의 수익률이 영구히 틀어지므로, 저장하지 않고 503으로 거절하고 다시 요청하게 한다.
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_NOT_READY", "아직 시세를 받아오지 못했습니다.");
            }
            // 여기서 넘기는 price가 매입가로 저장되어 이후 조회에서 바뀌지 않는다. 매입가 고정이 일어나는 자리다.
            holdings.add(new Holding(h.getCode(), h.getQuantity(), price));
        }
        return holdings;
    }

    /** 상세 응답과 목록 응답이 함께 쓰는 계산 결과 묶음. 외부로 나가지 않는 내부 전용 타입이다. */
    @Getter
    private static class Summary {
        private final BigDecimal totalCost;   // 매입금액 합계. 저장된 매입가로 계산하므로 시세가 움직여도 그대로다
        private final BigDecimal totalValue;  // 평가금액 합계. 방금 받아 온 현재가로 계산한다
        private final BigDecimal profit;      // 평가금액에서 매입금액을 뺀 값. 손실이면 음수
        private final BigDecimal returnRate;  // 수익금을 매입금액으로 나눈 백분율, 소수 둘째 자리 반올림

        Summary(BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit, BigDecimal returnRate) {
            this.totalCost = totalCost;
            this.totalValue = totalValue;
            this.profit = profit;
            this.returnRate = returnRate;
        }
    }

    /**
     * 매입금액, 평가금액, 수익금, 수익률을 한 번에 구한다.
     *
     * <p>매입금액은 저장된 매입가로, 평가금액은 받아 온 현재가로 계산한다.
     * 두 금액이 서로 다른 가격을 쓰기 때문에 수익이 생긴다. 1단계는 둘 다 현재가로 계산해
     * 평가금액과 매입금액이 항상 같았고, 그래서 수익률이 영원히 0이었다.
     *
     * <p>시세를 파라미터로 받는다. 이 메서드 안에서 다시 받아 오면 목록 조회가 포트폴리오 수만큼 외부를 부르고,
     * 같은 응답 안의 항목들이 서로 다른 시점의 시세로 계산된다.
     */
    private Summary computeSummary(Portfolio portfolio, Map<String, BigDecimal> prices) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            totalCost = totalCost.add(h.cost());                 // 매입가 곱하기 수량, 담은 시점에 고정된 값
            // 시세 응답에 없는 코드는 매입가로 대신한다. 그 종목의 수익률이 0으로 보이는 대신 조회는 응답한다.
            BigDecimal current = prices.getOrDefault(h.getCode(), h.getPurchasePrice());
            totalValue = totalValue.add(current.multiply(BigDecimal.valueOf(h.getQuantity())));
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

        return new Summary(totalCost, totalValue, profit, returnRate);
    }

    /**
     * 엔티티를 상세 응답 형태로 바꾼다. 응답 형식은 명세에 고정돼 있어 필드 이름을 바꿀 수 없다.
     *
     * <p>첫 줄의 외부 호출이 2단계에 남은 문제다. 조회 한 번이 외부 호출 두 번(토큰과 시세)이 되고,
     * 시세 서버가 503을 내면 여기서 예외가 올라가 조회 API가 500으로 응답한다.
     * 가져오는 일과 보여 주는 일이 한 줄에 붙어 있어서, 가져오기가 실패하면 보여 주기도 함께 실패한다.
     */
    private PortfolioDetailResponse toDetail(Portfolio portfolio) {
        Map<String, BigDecimal> prices = priceClient.fetchPrices();
        Summary s = computeSummary(portfolio, prices);

        List<HoldingView> views = portfolio.getHoldings().stream()
                .map(h -> new HoldingView(h.getCode(), h.getQuantity(),
                        // 매입가는 저장된 값, 현재가는 방금 받아 온 값이다. 이 두 값이 다른 것이 수익의 근거다.
                        h.getPurchasePrice().setScale(0),
                        prices.getOrDefault(h.getCode(), h.getPurchasePrice()).setScale(0)))
                .toList();

        // asOf와 priceStale은 아직 채우지 않아 null로 두고, @JsonInclude(NON_NULL) 설정에 따라 응답에서 빠진다.
        // 둘 다 받아 둔 시세가 언제 것인지를 알리는 값이라, 시세를 보관하기 시작하는 3단계에 가서야 의미가 생긴다.
        PortfolioSummary summary = new PortfolioSummary(
                s.getTotalCost(), s.getTotalValue(), s.getProfit(), s.getReturnRate(), null, null);
        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(), views, summary);
    }

    /**
     * 경로 변수로 받은 id로 포트폴리오를 찾고, 없으면 404로 끝낸다.
     *
     * <p>1단계의 {@code find}는 {@code Optional.get()}을 그대로 불러 없는 id에 500을 냈다.
     * 여기서는 못 찾은 경우를 404 {@code PORTFOLIO_NOT_FOUND}로 바꾼다.
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
