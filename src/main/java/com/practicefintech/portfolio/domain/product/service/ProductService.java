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
 * 상품 목록 조회 (요구사항 A1, 시나리오 S1). 조회할 때마다 시세 서버를 호출한다.
 *
 * <p>1단계에서 가장 먼저 동작하는 기능이다. CSV에서 읽어 저장한 상품 80개에
 * 시세 서버에서 받은 현재가를 붙여 배열로 돌려준다. 시나리오 S1이 1단계부터 통과하는 API다.
 *
 * <p>맞게 응답하지만 구조에 문제가 둘 있다. 시세 서버가 장애면 이 API도 500이 되고,
 * 조회마다 토큰이 새로 발급된다. 3단계에서 외부 호출을 요청 경로 밖으로 빼면서 함께 해결된다.
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
     * 정렬은 DB에 맡기고, 응답은 페이지 형식으로 감싸지 않고 배열 그대로 내보낸다.
     */
    public List<ProductResponse> findAll() {
        Map<String, BigDecimal> prices = priceClient.fetchPrices();
        return repository.findAll(Sort.by(Sort.Order.asc("code"))).stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getType(), prices.get(p.getCode())))
                .toList();
    }
}
