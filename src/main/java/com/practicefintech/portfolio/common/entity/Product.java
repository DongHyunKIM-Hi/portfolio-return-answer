package com.practicefintech.portfolio.common.entity;

import com.practicefintech.portfolio.common.enums.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투자 상품. 서버가 시작될 때 {@code data/products.csv}를 읽어 그대로 저장한다.
 *
 * <p>가격이 필드에 없는 것이 이 엔티티의 성격을 말해 준다. 상품의 이름과 종류는 한 번 정해지면 바뀌지 않는 값이고,
 * 가격은 30초마다 바뀌는 값이라 DB가 아니라 메모리 스냅샷에 둔다. 두 종류의 데이터를 섞지 않으려고 나눠 두었다.
 */
@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    /** 상품 코드를 그대로 기본 키로 쓴다. 코드가 겹치지 않도록 제공된 데이터라 숫자 id를 따로 둘 이유가 없다. */
    @Id
    private String code;

    private String name;

    /**
     * {@code EnumType.STRING}이라 DB에 ETF, FUND, BOND 문자열로 저장된다.
     * 기본값인 {@code ORDINAL}로 두면 순서 번호가 저장되어, enum에 값을 끼워 넣는 순간 기존 데이터의 뜻이 바뀐다.
     */
    @Enumerated(EnumType.STRING)
    private ProductType type;

    public Product(String code, String name, ProductType type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }
}
