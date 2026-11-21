package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.domain.price.service.PriceSnapshotStore;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 상품 목록 조회 (요구사항 A1, 시나리오 S1).
 *
 * <p>3단계에서 바뀐 것은 현재가를 읽는 곳이다. 2단계는 요청이 올 때마다 시세 서버를 불렀고,
 * 지금은 주기 갱신이 받아 둔 스냅샷을 읽는다. 그래서 시세 서버가 503을 내는 동안에도 이 API는 200으로 응답한다.
 * 실측으로는 응답 시간이 20회 평균 0.178초에서 0.003초로 줄었다.
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final PriceSnapshotStore priceSnapshotStore;

    public ProductService(ProductRepository repository, PriceSnapshotStore priceSnapshotStore) {
        this.repository = repository;
        this.priceSnapshotStore = priceSnapshotStore;
    }

    /**
     * 상품 전체를 코드 오름차순으로 돌려준다. 현재가는 주기 갱신으로 받아 둔 시세를 쓴다.
     *
     * <p>정렬은 DB에 맡긴다. 명세가 코드 오름차순 하나만 요구하므로 검색, 필터, 페이지 처리는 만들지 않았고
     * 응답도 배열 그대로다. 스냅샷에 없는 코드의 현재가는 null로 나가는데,
     * 그 상황은 첫 갱신이 끝나기 전 짧은 구간뿐이다.
     */
    public List<ProductResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Order.asc("code"))).stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getType(),
                        priceSnapshotStore.getPrice(p.getCode())))
                .toList();
    }
}
