package com.practicefintech.portfolio.domain.price.service;

import com.practicefintech.portfolio.common.config.PriceClientProperties;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPriceQuote;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPricesResponse;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalTokenRequest;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalTokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 가상 시세 서버 클라이언트. 1단계에서는 부를 때마다 토큰을 새로 발급받고 시세를 받아 온다.
 *
 * <p>연동 자체를 돌려 보는 것이 1단계의 목표다. 토큰을 받고, 헤더에 붙여 시세를 조회하고,
 * 받은 배열을 코드별 Map으로 바꾸는 흐름까지 만든다. 값은 맞게 들어온다.
 *
 * <p>하지 않은 것이 세 가지다. 토큰을 보관하지 않고, 제한 시간을 걸지 않고, 실패를 다루지 않는다.
 * 이 메서드를 부르는 쪽이 조회 API의 처리 흐름이라, 세 가지가 모두 조회 결과에 그대로 드러난다.
 * 시세 서버가 503을 내면 조회가 500이 되고(치명 오류 F2), 조회 횟수만큼 토큰이 발급된다(요구사항 A6).
 *
 * <p>2단계에서도 이 클래스는 그대로다. 3단계에서 토큰 보관과 401 재시도, 제한 시간이 들어오고
 * 부르는 쪽이 주기 갱신 스케줄러 하나로 바뀐다.
 */
@Component
public class PriceClient {

    private final PriceClientProperties properties;
    private final RestClient restClient;

    public PriceClient(PriceClientProperties properties) {
        this.properties = properties;
        // 주소만 지정하고 제한 시간은 걸지 않았다. 시세 서버가 응답을 끝내지 않으면
        // 요청을 처리하던 스레드가 그만큼 묶인다.
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * 시세를 받아 코드별 Map으로 돌려준다. 외부 요청이 두 번(토큰 발급과 시세 조회) 나간다.
     * 실패하면 예외가 그대로 부른 쪽으로 올라가 조회 API가 500으로 응답한다.
     */
    public Map<String, BigDecimal> fetchPrices() {
        // 토큰을 새로 발급받는다. 직전에 받은 토큰이 아직 살아 있어도 보관하지 않으므로 다시 받는다.
        String token = restClient.post()
                .uri("/auth/token")
                .body(new ExternalTokenRequest(properties.getClientId(), properties.getClientSecret()))
                .retrieve()
                .body(ExternalTokenResponse.class)
                .getAccessToken();

        // 받은 토큰을 Authorization 헤더에 Bearer로 붙여 시세를 조회한다.
        // 토큰을 매번 새로 받으므로 만료로 401이 날 일이 없고, 그래서 401 처리 코드도 없다.
        ExternalPricesResponse response = restClient.get()
                .uri("/v1/prices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ExternalPricesResponse.class);

        // 시세 서버는 배열로 주는데 계산할 때는 코드로 바로 찾아야 해서 Map으로 바꿔 돌려준다.
        // 응답의 asOf는 쓰지 않고 버린다. 받아 둔 시세가 없어 언제 것인지 알릴 자리도 없기 때문이다.
        Map<String, BigDecimal> byCode = new HashMap<>();
        for (ExternalPriceQuote quote : response.getPrices()) {
            byCode.put(quote.getCode(), quote.getPrice());
        }
        return byCode;
    }
}
