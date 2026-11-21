package com.practicefintech.portfolio.domain.product.model.response;

import com.practicefintech.portfolio.common.enums.ProductType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 상품 목록의 한 항목. 필드 네 개는 명세가 정한 것이고, 이름을 바꾸거나 더 넣으면 A1에서 걸린다.
 *
 * <p>{@code code}, {@code name}, {@code type}은 CSV에서 읽어 저장한 값이고
 * {@code currentPrice}는 시세 서버에서 온 값이다. 저장된 데이터와 외부에서 받은 값이 이 응답에서 합쳐진다.
 */
@Getter
@AllArgsConstructor
public class ProductResponse {
    private String code;
    private String name;
    private ProductType type;

    /** 현재가. 가격은 정수이지만 계산 도중 소수가 생기지 않게 {@code BigDecimal}로 다룬다. */
    private BigDecimal currentPrice;
}
