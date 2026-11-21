package com.practicefintech.portfolio.common.enums;

/**
 * 상품 종류. CSV의 세 번째 열에 적힌 값과 이름이 같아야 한다.
 * 적재할 때 {@code ProductType.valueOf(...)}로 바로 변환하므로, 여기에 없는 값이 들어오면 서버가 뜨면서 실패한다.
 * 모르는 종류를 조용히 건너뛰는 것보다, 데이터와 코드가 어긋난 것을 시작할 때 알게 되는 편이 낫다고 보고 그대로 두었다.
 */
public enum ProductType {
    ETF, FUND, BOND
}
