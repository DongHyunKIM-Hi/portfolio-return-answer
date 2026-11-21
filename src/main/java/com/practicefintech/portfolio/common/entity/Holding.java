package com.practicefintech.portfolio.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 포트폴리오가 보유한 종목 하나.
 *
 * <p>{@code purchasePrice} 필드가 이 과제의 요구사항 하나를 그대로 떠받친다.
 * 담은 시점의 가격을 여기에 적어 두기 때문에, 이후 시세가 올라가도 매입가와 매입금액이 변하지 않는다
 * (README 6장, 요구사항 A3, 시나리오 S4). 1단계에는 이 필드가 없어서 조회할 때마다 현재가로 매입가를 다시 구했고,
 * 그래서 매입금액과 평가금액이 늘 같아 수익률이 영원히 0이었다.
 *
 * <p>가격을 저장한다는 말이 시세를 DB에 캐시한다는 뜻은 아니다. 저장하는 것은 이 종목을 산 가격 한 건이고,
 * 시세는 계속 바뀌는 값이라 메모리 스냅샷에 둔다.
 */
@Entity
@Getter
@Table(name = "holdings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소유한 포트폴리오. 보유 종목만 따로 조회하는 API가 없어 지연 로딩으로 두었다.
     * 외래 키를 들고 있는 쪽이 이쪽이므로, 저장 전에 이 참조가 채워져 있어야 한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    /** 상품 코드. 상품 엔티티를 연관으로 걸지 않고 코드만 둔다. 담은 뒤에 상품 정보가 필요하지 않다. */
    private String code;

    private long quantity;

    /** 담은 시점의 가격. 한 번 정해지면 바꾸는 메서드가 없어, 수정 API는 종목을 새로 만들어 교체한다. */
    private BigDecimal purchasePrice;

    public Holding(String code, long quantity, BigDecimal purchasePrice) {
        this.code = code;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
    }

    /** 양쪽 참조를 맞춘다. {@code Portfolio.replaceHoldings}만 부르므로 package-private으로 두었다. */
    void assignTo(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    /**
     * 매입 원가. 담은 시점의 가격으로 고정된다.
     * 계산을 서비스가 아니라 이 클래스에 둔 이유는, 매입가와 수량을 함께 들고 있는 쪽이 여기이기 때문이다.
     */
    public BigDecimal cost() {
        return purchasePrice.multiply(BigDecimal.valueOf(quantity));
    }
}
