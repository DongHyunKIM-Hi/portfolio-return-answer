package com.practicefintech.portfolio.domain.product.repository;

import com.practicefintech.portfolio.common.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 저장소. 두 번째 타입 파라미터가 {@code String}인 이유는 상품의 기본 키가 숫자 id가 아니라 상품 코드이기 때문이다.
 * 덕분에 보유 종목 검증을 {@code existsById(code)} 한 번으로 끝낼 수 있다.
 */
public interface ProductRepository extends JpaRepository<Product, String> {
}
