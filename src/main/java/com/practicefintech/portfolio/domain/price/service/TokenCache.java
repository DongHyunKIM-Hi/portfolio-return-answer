package com.practicefintech.portfolio.domain.price.service;

import java.time.Instant;

/**
 * 발급받은 토큰과 만료 시각을 들고 있다. 3단계에서 새로 생긴 세 클래스 중 하나다.
 *
 * <p>가상 시세 서버의 토큰 수명은 120초다. 요청마다 새로 발급받아도 동작은 하지만,
 * 요구사항 A6은 시세 호출 횟수에 비해 토큰 발급이 절반 이하인지를 본다.
 * 만료 직전까지 재사용하는 자리가 이 클래스다 (시나리오 S7).
 *
 * <p>{@code PriceClient}만 쓰는 보조 클래스라 package-private으로 두고 다른 패키지에 노출하지 않았다.
 */
final class TokenCache {

    /** 발급받은 토큰 문자열. 아직 한 번도 받지 않았으면 null이다. */
    private volatile String token;

    /**
     * 토큰이 만료되는 시각. 초기값을 1970년으로 두면 첫 호출에서 자동으로 만료 상태가 되어
     * 토큰이 없는 경우를 따로 분기하지 않아도 새로 발급받는 길로 간다.
     */
    private volatile Instant expiresAt = Instant.EPOCH;

    /**
     * 지금 토큰을 그대로 써도 되는지 판단한다.
     *
     * <p>만료 시각에서 {@code marginSeconds}(설정값 10초)를 당겨 비교하는 것이 요점이다.
     * 만료 시각 자체와 비교하면, 검사에 통과한 직후 요청이 날아가는 사이에 토큰이 만료되어
     * 401을 받는 일이 생긴다. 남은 수명이 10초 이하면 미리 새 토큰을 받는다.
     */
    boolean isValid(long marginSeconds) {
        return token != null && Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
    }

    /** 새로 발급받은 토큰을 보관한다. 만료 시각은 시세 서버가 알려준 남은 수명(초)으로 계산한다. */
    void set(String token, long expiresInSeconds) {
        this.token = token;
        this.expiresAt = Instant.now().plusSeconds(expiresInSeconds);
    }

    String get() {
        return token;
    }

    /**
     * 토큰을 쓸 수 없는 상태로 만든다. 401을 받았을 때 호출한다.
     * 토큰 문자열을 지우지 않고 만료 시각만 과거로 돌려도 {@code isValid}가 false가 되므로 목적은 같다.
     */
    void invalidate() {
        this.expiresAt = Instant.EPOCH;
    }
}
