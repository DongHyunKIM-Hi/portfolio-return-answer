package com.practicefintech.portfolio.domain.price.service;

import com.practicefintech.portfolio.common.config.PriceClientProperties;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPricesResponse;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalTokenRequest;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 가상 시세 서버 클라이언트. 토큰을 보관해 두었다가 만료 직전에만 새로 받고(A6, 시나리오 S7),
 * 401을 받으면 토큰을 버리고 한 번 재발급받아 다시 요청한다.
 *
 * <p>2단계와 달라진 점이 둘 있다.
 * 토큰을 {@link TokenCache}에 보관해 요청마다 발급하지 않는 것과,
 * 결과를 Map으로 가공하지 않고 응답 객체를 그대로 돌려주는 것이다.
 * 가공은 {@link PriceRefreshScheduler}가 맡는다. 받아 오는 일과 보관하는 일을 나눠 둔 것이다.
 *
 * <p>이 클래스를 부르는 쪽은 스케줄러 하나뿐이다. 조회 API는 이 클래스를 거치지 않고
 * {@link PriceSnapshotStore}를 읽는다. 외부 호출이 요청 경로 밖에 있다는 말이 코드에서는 이 구조를 뜻한다.
 */
@Component
public class PriceClient {

    private static final Logger log = LoggerFactory.getLogger(PriceClient.class);

    private final PriceClientProperties properties;
    private final RestClient restClient;

    /** 토큰 보관함. 이 클라이언트 하나당 하나이므로 필드에서 바로 만든다. */
    private final TokenCache tokenCache = new TokenCache();

    public PriceClient(PriceClientProperties properties) {
        this.properties = properties;
        // 연결과 읽기 제한 시간을 둘 다 3초(설정값 call-timeout-ms)로 잡는다.
        // 제한을 두지 않으면 시세 서버가 응답을 끝내지 않을 때 갱신 스레드가 영원히 묶여
        // 그 뒤 주기 갱신이 아예 돌지 않는다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getCallTimeoutMs());
        factory.setReadTimeout((int) properties.getCallTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** 시세 전체를 받아 온다. 응답을 그대로 돌려주고, 실패하면 예외가 그대로 호출한 쪽으로 올라간다. */
    public ExternalPricesResponse fetchPrices() {
        return callWithAuth(token -> restClient.get()
                .uri("/v1/prices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ExternalPricesResponse.class));
    }

    /**
     * 토큰을 붙여 호출하고, 토큰이 만료돼 401이 오면 한 번만 재발급해 다시 보낸다.
     *
     * <p>호출하는 코드를 함수로 받는 이유는 같은 요청을 두 번(처음과 재시도) 보내야 하기 때문이다.
     * 시세 조회가 하나뿐이어서 당장 이득은 없지만, 외부 API가 늘어나도 토큰 처리를 한 군데서 끝낼 수 있다.
     */
    private <T> T callWithAuth(java.util.function.Function<String, T> call) {
        String token = ensureToken();
        try {
            return call.apply(token);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                // 재시도는 딱 한 번이다. 조건 없이 반복하면 재발급-실패가 무한히 돈다.
                // 재발급한 토큰으로도 401이면 예외가 그대로 올라가고, 스케줄러가 받아 markStale을 켠다.
                log.info("시세 서버 토큰이 만료된 것으로 보여 재발급 후 재시도합니다.");
                tokenCache.invalidate();
                String freshToken = ensureToken();
                return call.apply(freshToken);
            }
            // 401이 아닌 응답(503 등)은 토큰 문제가 아니므로 재시도하지 않고 올려 보낸다.
            throw e;
        }
    }

    /**
     * 쓸 수 있는 토큰을 돌려준다. 남은 수명이 넉넉하면 보관하던 것을 그대로 쓰고, 아니면 새로 발급받는다.
     *
     * <p>{@code synchronized}인 이유는 토큰 발급이 겹치는 것을 막기 위해서다.
     * 지금은 스케줄러 한 스레드만 부르지만, 401 재시도와 주기 갱신이 맞물리면 두 흐름이 동시에 들어올 수 있고
     * 그때 발급이 두 번 일어나면 A6의 발급 횟수 기준에 걸린다.
     */
    private synchronized String ensureToken() {
        if (!tokenCache.isValid(properties.getTokenRefreshMarginSeconds())) {
            ExternalTokenResponse response = restClient.post()
                    .uri("/auth/token")
                    .body(new ExternalTokenRequest(properties.getClientId(), properties.getClientSecret()))
                    .retrieve()
                    .body(ExternalTokenResponse.class);
            tokenCache.set(response.getAccessToken(), response.getExpiresIn());
        }
        return tokenCache.get();
    }
}
