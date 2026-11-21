package com.practicefintech.portfolio.domain.portfolio.service;

import com.practicefintech.portfolio.common.entity.Holding;
import com.practicefintech.portfolio.common.entity.Portfolio;
import com.practicefintech.portfolio.domain.portfolio.model.request.HoldingRequest;
import com.practicefintech.portfolio.domain.portfolio.model.request.PortfolioRequest;
import com.practicefintech.portfolio.domain.portfolio.model.response.HoldingView;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioDetailResponse;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioListItem;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioSummary;
import com.practicefintech.portfolio.domain.portfolio.repository.PortfolioRepository;
import com.practicefintech.portfolio.domain.price.service.PriceClient;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 생성, 조회, 수정, 삭제. 1단계에서는 설계한 흐름이 도는지만 확인한다.
 *
 * <p>일부러 하지 않는 것이 많다. 입력 검증도, 에러 응답 형식도, 매입가 고정도, 수익률 반올림도 없다.
 * 요청을 받아 저장하고, 조회하면 시세를 받아 와 금액을 더해 돌려주는 것까지만 만든다.
 * 서버가 뜨고 201과 200이 돌아오는 것을 먼저 확인하려는 단계다.
 *
 * <p>그래서 이 코드는 응답이 와도 요구사항을 만족하지 않는다. 실제로 실행하면 이렇게 된다.
 * <ul>
 *   <li>빈 이름, 수량 0, 같은 코드 두 번이 모두 201로 저장된다. 400이어야 한다.</li>
 *   <li>없는 상품 코드나 없는 포트폴리오 id는 500이 난다. 400과 404여야 한다.</li>
 *   <li>시세가 10,000에서 11,000으로 바뀌면 매입가도 11,000으로 따라 올라가고 매입금액도 같이 바뀐다.
 *       수익률은 언제 조회해도 0이다.</li>
 * </ul>
 * 수익률 서비스인데 수익률이 나오지 않는다는 것이 2단계로 넘어가는 이유다.
 * 시나리오 8개 중 통과는 3개이고, 그중 하나(S6, 시세 반영)는 주기 갱신을 만들어서가 아니라
 * 조회할 때마다 외부를 불러 늘 최신이기 때문에 통과한다. 통과 표시가 구현의 증거는 아니다.
 *
 * <p>상품 저장소를 주입받지 않는 것도 눈여겨볼 지점이다. 상품 코드를 확인하지 않으므로 필요가 없다.
 * 2단계에서 검증을 넣을 때 생긴다.
 */
@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    /** 시세 서버 클라이언트. 조회를 처리하는 도중에 부른다. */
    private final PriceClient priceClient;

    public PortfolioService(PortfolioRepository portfolioRepository, PriceClient priceClient) {
        this.portfolioRepository = portfolioRepository;
        this.priceClient = priceClient;
    }

    /** 포트폴리오를 만든다. 요청에 적힌 값을 그대로 저장하고, 매입가는 저장하지 않는다. */
    public PortfolioDetailResponse create(PortfolioRequest request) {
        Portfolio portfolio = new Portfolio(request.getName());
        portfolio.replaceHoldings(toHoldings(request));
        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public PortfolioDetailResponse getDetail(Object rawId) {
        return toDetail(find(rawId));
    }

    /**
     * 포트폴리오 전체를 만든 순서(id 오름차순)로 돌려준다.
     * 수익률 자리에는 0을 넣는다. 매입가가 없어 계산할 방법이 없다.
     */
    public List<PortfolioListItem> list() {
        Map<String, BigDecimal> prices = priceClient.fetchPrices();
        return portfolioRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                .map(p -> {
                    BigDecimal value = totalValue(p, prices);
                    return new PortfolioListItem(p.getId(), p.getName(), value, BigDecimal.ZERO);
                })
                .toList();
    }

    /** 수정. 이름을 바꾸고 보유 종목을 전체 교체한다. */
    public PortfolioDetailResponse update(Object rawId, PortfolioRequest request) {
        Portfolio portfolio = find(rawId);
        portfolio.setName(request.getName());
        portfolio.replaceHoldings(toHoldings(request));
        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public void delete(Object rawId) {
        portfolioRepository.delete(find(rawId));
    }

    // ---- 내부 계산 ----

    /**
     * 요청을 보유 종목으로 옮긴다. 검사하는 것이 하나도 없다.
     *
     * <p>2단계에서 여기에 세 가지가 들어온다. 같은 코드가 두 번 들어왔는지, 있는 상품 코드인지,
     * 그리고 담는 시점의 시세를 매입가로 함께 저장하는 일이다.
     * 지금은 코드와 수량만 저장하므로, 없는 상품 코드도 그대로 들어가고 조회할 때 터진다.
     */
    private List<Holding> toHoldings(PortfolioRequest request) {
        List<Holding> holdings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            holdings.add(new Holding(h.getCode(), h.getQuantity()));
        }
        return holdings;
    }

    /**
     * 평가금액 합계. 현재가 곱하기 수량을 더한다.
     *
     * <p>{@code prices.get}이 null을 돌려주면 바로 다음 곱셈에서 터진다.
     * 없는 상품 코드로 만든 포트폴리오를 조회할 때 500이 나는 자리가 여기다.
     */
    private BigDecimal totalValue(Portfolio portfolio, Map<String, BigDecimal> prices) {
        BigDecimal total = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            total = total.add(prices.get(h.getCode()).multiply(BigDecimal.valueOf(h.getQuantity())));
        }
        return total;
    }

    /**
     * 상세 응답을 만든다. 1단계의 문제가 모두 이 메서드에 모여 있다.
     *
     * <p>매입가를 저장하지 않았으니 응답에 넣을 매입가가 없다. 그래서 현재가를 매입가 자리에도 넣는데,
     * 그 결과 매입금액과 평가금액이 같은 값이 되고 수익금과 수익률이 0으로 고정된다.
     * 시세가 올라가면 두 금액이 함께 올라가므로, 조회 시점에 따라 매입금액까지 달라진다.
     */
    private PortfolioDetailResponse toDetail(Portfolio portfolio) {
        // 조회할 때마다 시세 서버를 부른다. 이 호출 안에서 토큰 발급까지 일어나므로 외부 요청이 두 번이다.
        // 시세 서버가 503을 내면 이 줄에서 예외가 올라가 조회가 500으로 깨진다.
        Map<String, BigDecimal> prices = priceClient.fetchPrices();

        List<HoldingView> views = portfolio.getHoldings().stream()
                .map(h -> {
                    BigDecimal current = prices.get(h.getCode());
                    // 매입가를 저장하지 않으므로 현재가를 매입가 자리에 그대로 넣는다.
                    // 매입가와 현재가가 늘 같은 값으로 나가는 것이 응답에서 바로 보인다.
                    return new HoldingView(h.getCode(), h.getQuantity(), current, current);
                })
                .toList();

        BigDecimal value = totalValue(portfolio, prices);
        // 매입금액도 현재가로 다시 계산하므로 수익금은 항상 0이 된다.
        // 수익률도 계산하지 않고 0을 넣는다. asOf와 priceStale은 아직 쓸 값이 없어 null이다.
        PortfolioSummary summary = new PortfolioSummary(value, value, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(), views, summary);
    }

    /**
     * id로 포트폴리오를 찾는다.
     *
     * <p>{@code Optional.get()}을 조건 없이 부르고, 문자열을 숫자로 바꾸는 것도 확인하지 않는다.
     * 없는 id를 조회하면 {@code NoSuchElementException}이, 숫자가 아닌 id는 {@code NumberFormatException}이
     * 올라가 둘 다 500이 된다. 2단계에서 404 응답으로 바꾼다.
     */
    private Portfolio find(Object rawId) {
        return portfolioRepository.findById(Long.valueOf(String.valueOf(rawId))).get();
    }
}
