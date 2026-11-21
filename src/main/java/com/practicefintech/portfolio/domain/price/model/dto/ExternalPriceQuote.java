package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 가상 시세 서버가 주는 시세 한 건. */
@Getter
@NoArgsConstructor
public class ExternalPriceQuote {

    /** 상품 코드. CSV의 상품 코드와 같은 값이라 이것으로 짝을 맞춘다. */
    private String code;

    private BigDecimal price;

    /**
     * 통화. 시세 서버가 보내 주지만 쓰지 않는다. 상품이 모두 원화라 환산할 일이 없다.
     * 받는 필드를 지워도 동작은 같지만, 외부 응답에 무엇이 들어오는지 알 수 있게 남겨 두었다.
     */
    private String currency;
}
