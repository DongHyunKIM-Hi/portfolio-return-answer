package com.practicefintech.portfolio.domain.price.service;

import com.practicefintech.portfolio.domain.price.model.dto.ExternalPriceQuote;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPricesResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 30초마다(설정값) 가상 시세 서버에서 시세를 받아 {@link PriceSnapshotStore}를 갱신한다.
 * 3단계에서 새로 생긴 세 클래스 중 하나이고, 외부 호출이 일어나는 유일한 자리다.
 *
 * <p>요구사항은 30초 주기 갱신(A5)이고 시나리오는 가격을 바꾼 뒤 45초 안에 반영되는지 본다.
 * 1, 2단계도 S6을 통과하지만 그것은 주기 갱신을 만들어서가 아니라 요청마다 외부를 불러 늘 최신이었기 때문이고,
 * 그 대가로 토큰이 요청마다 발급되고 장애가 조회로 번졌다.
 *
 * <p>주기 실행이 동작하려면 {@code PortfolioApplication}에 {@code @EnableScheduling}이 붙어 있어야 한다.
 */
@Component
public class PriceRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshScheduler.class);

    private final PriceClient priceClient;
    private final PriceSnapshotStore store;

    public PriceRefreshScheduler(PriceClient priceClient, PriceSnapshotStore store) {
        this.priceClient = priceClient;
        this.store = store;
    }

    /**
     * 서버가 뜰 때 한 번 즉시 갱신한다. 이것이 없으면 첫 30초 동안 스냅샷이 비어 있어
     * 상품 조회의 현재가가 null로 나가고 포트폴리오 생성이 503으로 거절된다.
     */
    @PostConstruct
    void refreshOnStartup() {
        refresh();
    }

    /**
     * 주기 갱신. 간격은 {@code application.yml}의 {@code price.refresh-interval-ms}(30,000밀리초)에서 읽는다.
     *
     * <p>{@code fixedDelay}는 앞 작업이 끝난 시점부터 30초를 센다. {@code fixedRate}와 달리
     * 시세 서버 응답이 느려도 호출이 겹치지 않는다. 또 시작 지연을 같은 30초로 주어
     * 위의 기동 시 1회 갱신과 첫 주기 실행이 붙어서 두 번 호출되는 것을 막는다.
     */
    @Scheduled(fixedDelayString = "${price.refresh-interval-ms}", initialDelayString = "${price.refresh-interval-ms}")
    void refreshPeriodically() {
        refresh();
    }

    /** 시세를 받아 코드별 Map으로 만들고 스냅샷을 교체한다. 실패하면 직전 스냅샷을 유지한다. */
    private void refresh() {
        try {
            ExternalPricesResponse prices = priceClient.fetchPrices();
            // 시세 서버는 배열로 주는데 계산할 때는 코드로 바로 찾아야 해서 Map으로 바꿔 둔다.
            Map<String, BigDecimal> byCode = new HashMap<>();
            for (ExternalPriceQuote quote : prices.getPrices()) {
                byCode.put(quote.getCode(), quote.getPrice());
            }
            // asOf는 우리가 받은 시각이 아니라 시세 서버가 알려준 기준 시각을 그대로 쓴다.
            store.update(byCode, prices.getAsOf());
        } catch (Exception e) {
            // 실패는 여기서 끝낸다(예외를 위로 올리지 않는다). 여기서 올라가면 조회까지 함께 죽어서
            // 외부 호출을 요청 경로 밖으로 뺀 의미가 없어진다. 직전 스냅샷을 유지하고 오래된 값 표시만 켠다.
            // 한 번 실패해도 다음 30초에 다시 시도하므로, 재시도 코드를 따로 두지 않았다.
            log.warn("시세 갱신 실패, 마지막 스냅샷을 유지합니다: {}", e.toString());
            store.markStale();
        }
    }
}
