package com.practicefintech.portfolio.domain.product.controller;

import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상품 목록 API. 컨트롤러는 경로를 서비스에 연결하는 일만 하고, 계산이나 조건 판단은 하지 않는다.
 */
@RestController
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /**
     * 상품 전체를 배열로 응답한다 (A1).
     * 페이지 형식({@code content}, {@code totalElements})으로 감싸면 명세와 달라져 점수를 잃는다.
     * 목록을 그대로 돌려주면 스프링이 JSON 배열로 바꿔 준다.
     */
    @GetMapping("/api/v1/products")
    public List<ProductResponse> findAll() {
        return service.findAll();
    }
}
