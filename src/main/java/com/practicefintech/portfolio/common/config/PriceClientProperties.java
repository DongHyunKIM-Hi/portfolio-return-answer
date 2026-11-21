package com.practicefintech.portfolio.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가상 시세 서버 연동 설정. {@code application.yml}의 {@code price} 아래 값을 받는다.
 *
 * <p>prefix가 price이므로 환경변수 {@code PRICE_BASE_URL}이 {@link #getBaseUrl()}과 대응한다.
 * 환경변수 이름은 지원자 README 3장에 명시되어 있어, prefix를 바꾸면 그 이름이 더 이상 먹지 않는다.
 *
 * <p>주소와 제한 시간, 갱신 간격을 코드에 상수로 박지 않고 설정으로 뺀 이유는
 * 평가할 때 시세 서버를 다른 포트로 띄우거나 갱신 간격을 줄여 확인할 수 있어야 하기 때문이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "price")
public class PriceClientProperties {

    /** 가상 시세 서버 주소. 기본값은 로컬 9091번 포트다. */
    private String baseUrl;

    /** 토큰 발급에 쓰는 자격 정보. 시세 서버가 고정값으로 받아 준다. */
    private String clientId;
    private String clientSecret;

    /** 연결과 읽기 제한 시간(밀리초). 응답이 없는 시세 서버에 갱신 스레드가 묶이지 않게 한다. */
    private long callTimeoutMs;

    /** 시세 갱신 주기(밀리초). 요구사항이 30초라 30,000으로 둔다. */
    private long refreshIntervalMs;

    /** 토큰을 만료 몇 초 전에 미리 새로 받을지. 검사 직후 만료되어 401을 받는 것을 막는 여유 시간이다. */
    private long tokenRefreshMarginSeconds;
}
