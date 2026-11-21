package com.practicefintech.portfolio.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포트폴리오가 보유한 종목 하나. 1단계에서는 상품 코드와 수량만 들고 있다.
 *
 * <p>담은 시점의 가격을 저장하지 않는 것이 1단계의 가장 큰 구멍이다.
 * 저장해 두지 않으면 조회할 때 매입가를 알 길이 없어, 현재가를 매입가 자리에 넣는 수밖에 없다.
 * 그 결과 매입금액과 평가금액이 같아져 수익률이 영원히 0이 된다.
 *
 * <p>2단계에서 {@code purchasePrice} 필드와 매입 원가를 구하는 {@code cost()}가 생긴다.
 * 필드 하나가 요구사항 하나(A3, 매입가 고정)를 떠받치는 구조다.
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

    /** 상품 코드. 있는 코드인지 확인하지 않고 받은 값을 그대로 저장한다. */
    private String code;

    private long quantity;

    public Holding(String code, long quantity) {
        this.code = code;
        this.quantity = quantity;
    }

    /** 양쪽 참조를 맞춘다. {@code Portfolio.replaceHoldings}만 부르므로 package-private으로 두었다. */
    void assignTo(Portfolio portfolio) {
        this.portfolio = portfolio;
    }
}
