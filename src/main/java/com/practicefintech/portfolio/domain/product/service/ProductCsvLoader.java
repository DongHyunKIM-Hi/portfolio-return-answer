package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.config.ProductsProperties;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.ProductType;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 서버 시작 시 {@code data/products.csv}를 읽어 저장한다.
 * 파일은 코드가 겹치지 않고 표기도 통일돼 있으므로, 정리하지 않고 적힌 값을 그대로 쓴다.
 *
 * <p>{@link ApplicationRunner}는 스프링이 모든 빈을 만든 뒤 한 번 실행한다.
 * 적재가 끝난 뒤에 요청을 받게 되므로, 첫 조회가 빈 목록을 보는 일이 없다.
 *
 * <p>CSV 파일은 읽기만 한다. 고치면 치명 오류 F1(파일 수정)에 해당하고, 해시로 확인한다.
 */
@Component
public class ProductCsvLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCsvLoader.class);

    private final ProductsProperties properties;
    private final ProductRepository repository;

    public ProductCsvLoader(ProductsProperties properties, ProductRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path path = Path.of(properties.getCsvPath());
        if (!Files.exists(path)) {
            // 파일이 없으면 서버를 죽이지 않고 경고만 남긴다. 실행한 디렉터리가 달라 경로가 어긋난 경우가 대부분이라,
            // 어느 절대 경로를 찾았는지 함께 찍어 두면 원인을 바로 알 수 있다.
            log.warn("상품 데이터 파일을 찾을 수 없습니다: {} (상품 조회 API가 빈 목록을 반환합니다)", path.toAbsolutePath());
            return;
        }

        List<Product> products = new ArrayList<>();
        // try-with-resources라 블록을 벗어날 때 파일이 닫힌다. UTF-8을 직접 지정해 한글 상품 이름이 깨지지 않게 한다.
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            // 첫 줄은 열 이름이므로 읽어서 버린다.
            String header = reader.readLine();
            if (header == null) {
                log.warn("상품 데이터 파일이 비어 있습니다: {}", path);
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                // 파일 끝의 빈 줄을 만나면 건너뛴다. 그냥 쪼개면 열이 하나뿐이라 아래에서 터진다.
                if (line.isBlank()) {
                    continue;
                }
                // 두 번째 인자 -1은 끝의 빈 칸도 버리지 않고 남기라는 뜻이다.
                // 값이 비어 있을 때 열 수가 줄어들면 엉뚱한 열을 읽게 된다.
                String[] cols = line.split(",", -1);
                // 열 세 개는 순서대로 코드, 이름, 종류다. 상품 이름에 쉼표가 없는 데이터라 따옴표 처리는 하지 않았다.
                products.add(new Product(cols[0].trim(), cols[1].trim(), ProductType.valueOf(cols[2].trim())));
            }
        }

        // 한 줄마다 저장하지 않고 모아서 한 번에 저장한다.
        repository.saveAll(products);
        log.info("상품 데이터 로드 완료: {}개", products.size());
    }
}
