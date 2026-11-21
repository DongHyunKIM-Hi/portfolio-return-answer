package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.domain.price.service.PriceClient;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 상품 목록 조회 (요구사항 A1, 시나리오 S1).
 *
 * <p>2단계에서도 조회할 때마다 시세 서버를 호출한다. 응답은 늘 최신 시세여서 결과가 맞게 나오지만,
 * 그 대가가 둘 있다. 시세 서버가 503을 내는 동안 이 API도 500이 되고, 조회마다 토큰이 새로 발급된다.
 * 실측으로 응답 시간이 20회 평균 0.178초인데, 그중 대부분이 외부 서버를 기다리는 시간이다.
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final PriceClient priceClient;

    public ProductService(ProductRepository repository, PriceClient priceClient) {
        this.repository = repository;
        this.priceClient = priceClient;
    }

    /**
     * 상품 전체를 코드 오름차순으로 돌려준다.
     *
     * <p>시세는 반복문 밖에서 한 번만 받아 온다. 상품마다 받아 오면 80번을 부르게 된다.
     * 정렬은 DB에 맡긴다. 명세가 코드 오름차순 하나만 요구하므로 검색, 필터, 페이지 처리는 만들지 않았고
     * 응답도 배열 그대로다.
     */
    public List<ProductResponse> findAll() {
        Map<String, BigDecimal> prices = priceClient.fetchPrices();
        return repository.findAll(Sort.by(Sort.Order.asc("code"))).stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getType(), prices.get(p.getCode())))
                .toList();
    }
}
