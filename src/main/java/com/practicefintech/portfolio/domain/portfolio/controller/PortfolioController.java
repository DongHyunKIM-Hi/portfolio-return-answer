package com.practicefintech.portfolio.domain.portfolio.controller;

import com.practicefintech.portfolio.domain.portfolio.model.request.PortfolioRequest;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioDetailResponse;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioListItem;
import com.practicefintech.portfolio.domain.portfolio.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 포트폴리오 API 다섯 개 (A2). 경로와 성공 상태 코드는 명세에 고정돼 있다.
 *
 * <p>1단계에서는 경로와 상태 코드만 맞춰 둔다. 요청 본문에 {@code @Valid}가 없어
 * 검증이 전혀 일어나지 않고, 실패 응답도 스프링 기본값에 맡긴다. 2단계에서 {@code @Valid}가 붙는다.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    /**
     * 생성. 성공하면 201이다.
     * {@code @Valid}가 없으므로 빈 이름이나 수량 0도 그대로 서비스로 넘어가 저장된다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDetailResponse create(@RequestBody PortfolioRequest request) {
        return service.create(request);
    }

    /**
     * 상세 조회.
     *
     * <p>{@code id}를 {@code Long}이 아니라 {@code String}으로 받는다. {@code Long}으로 두면
     * 숫자가 아닌 경로 변수가 변환 단계에서 400으로 끝나는데, 없는 포트폴리오를 가리키는 요청이라
     * 404로 응답하기로 정했다. 다만 1단계는 아직 404를 만들지 않아 500이 나간다.
     */
    @GetMapping("/{id}")
    public PortfolioDetailResponse get(@PathVariable String id) {
        return service.getDetail(id);
    }

    /** 목록 조회. 배열로 응답하며 페이지 형식으로 감싸지 않는다. */
    @GetMapping
    public List<PortfolioListItem> list() {
        return service.list();
    }

    /** 수정. 보유 종목을 전체 교체하는 의미라 PATCH가 아니라 PUT이다. 성공하면 200이다. */
    @PutMapping("/{id}")
    public PortfolioDetailResponse update(@PathVariable String id, @RequestBody PortfolioRequest request) {
        return service.update(id, request);
    }

    /** 삭제. 성공하면 본문 없이 204다. 반환형이 {@code void}라 본문이 비어 나간다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
