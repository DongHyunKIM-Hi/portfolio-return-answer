package com.practicefintech.portfolio.domain.price.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 가장 최근에 받아 온 시세 스냅샷을 보관한다. 3단계에서 새로 생긴 세 클래스 중 하나다.
 *
 * <p>조회 API가 외부 서버 대신 이 클래스를 읽기 때문에, 시세 서버가 장애 상태여도 조회는 마지막 성공 값으로 응답한다
 * (가정 G1, 시나리오 S8). 장애를 조회 경로에서 떼어 놓는 자리가 여기다.
 *
 * <p>쓰는 쪽은 {@link PriceRefreshScheduler} 하나이고 읽는 쪽은 요청을 처리하는 여러 스레드다.
 * 그래서 값을 하나씩 고치지 않고 {@link Snapshot}을 통째로 새로 만들어 참조만 바꾼다.
 * 80개를 하나씩 갈아 끼우면 그 사이에 읽은 요청이 절반은 새 값, 절반은 옛 값으로 계산된 금액을 받게 된다.
 */
@Component
public class PriceSnapshotStore {

    /**
     * 한 번의 갱신으로 받은 시세 전체와 그 시세의 기준 시각.
     * record라 만든 뒤 바꿀 수 없고, {@code update}에서 {@code Map.copyOf}로 복사해 넣으므로
     * 넘겨준 쪽이 나중에 Map을 고쳐도 보관된 스냅샷은 영향을 받지 않는다.
     */
    private record Snapshot(Map<String, BigDecimal> prices, Instant asOf) {
    }

    /**
     * volatile이라 갱신 스레드가 바꾼 참조를 요청 스레드가 즉시 본다.
     * 서버를 막 띄워 첫 갱신이 끝나기 전에는 null이다.
     */
    private volatile Snapshot snapshot;

    /** 마지막 갱신이 실패해 직전 스냅샷을 계속 쓰고 있는지. 응답의 {@code priceStale}이 이 값이다. */
    private volatile boolean stale = false;

    /** 갱신에 성공했을 때 호출한다. 스냅샷을 교체하고 오래된 값 표시를 내린다. */
    public void update(Map<String, BigDecimal> prices, Instant asOf) {
        this.snapshot = new Snapshot(Map.copyOf(prices), asOf);
        this.stale = false;
    }

    /**
     * 갱신에 실패했을 때 호출한다. 값은 그대로 두고 오래된 값이라는 표시만 남긴다.
     * 실패했다고 스냅샷을 비우면 장애 중 조회가 다시 깨지므로, 지우는 것이 아니라 표시만 켜는 것이 요점이다.
     */
    public void markStale() {
        this.stale = true;
    }

    /** 첫 갱신이 끝났는지. 아직 아니면 포트폴리오 생성을 503으로 거절한다. */
    public boolean isReady() {
        return snapshot != null;
    }

    public boolean isStale() {
        return stale;
    }

    /** 해당 코드의 현재가. 스냅샷이 없거나 그 코드가 스냅샷에 없으면 null을 돌려주고, 판단은 부르는 쪽이 한다. */
    public BigDecimal getPrice(String code) {
        return snapshot == null ? null : snapshot.prices().get(code);
    }

    public Instant getAsOf() {
        return snapshot == null ? null : snapshot.asOf();
    }
}
