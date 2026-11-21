package com.practicefintech.portfolio.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 상품 데이터 파일 설정. {@code application.yml}의 {@code products} 아래 값을 받는다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "products")
public class ProductsProperties {

    /**
     * 상품 CSV 경로. 서버를 실행한 디렉터리 기준의 상대 경로이고 기본값은 {@code data/products.csv}다.
     * 이 파일은 수정하면 치명 오류 F1에 해당하므로 읽기만 한다.
     */
    private String csvPath;
}
