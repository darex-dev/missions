# 결제가 실패했는데 포인트가 사라졌다

Java 21, Gradle 8.14.3, Spring Boot 3.5.16, MySQL 8.0을 사용합니다. 실습용 DB와 별도의 테스트용 DB를 준비하세요. 실행에는 `MISSION_DB_URL`, 테스트에는 `TRANSACTION_TEST_DB_URL`, 공통으로 `MISSION_DB_USERNAME`, `MISSION_DB_PASSWORD`를 설정합니다.

`./gradlew.bat bootRun`으로 실행합니다. 기본 포트는 8092입니다. 실습 DB에 `INSERT INTO wallets(balance) VALUES (10000);`과 `INSERT INTO products(price) VALUES (3000);`을 실행해 사용할 ID를 확인하세요. `./gradlew.bat test`는 테스트 DB에 별도 지갑과 상품을 생성합니다.

`POST /wallets/{walletId}/purchases/{productId}`는 저장된 가격으로 1개를 구매합니다. 201 PURCHASED, 409 INSUFFICIENT_POINTS, 404 NOT_FOUND, 500 FAILED를 반환합니다. 실습은 순차 요청을 대상으로 합니다.

`src/main/java/darex/transaction/Exercise.java`를 수정하세요. 차감·구매·사용 내역은 한 트랜잭션으로 커밋 또는 롤백돼야 합니다. 실패한 구매의 시도 기록은 보존하고 기존 구매·다른 지갑은 변경하지 마세요. 보상 UPDATE·DELETE는 사용하지 않습니다. PurchaseStore와 API·스키마·장애 주입 계약은 유지하세요.

공개 테스트는 정상, 정확한 잔액, 잔액 부족, 각 쓰기 단계의 체크/런타임 예외를 확인합니다. 초기 코드는 실패 경로에서 데이터를 일부 저장합니다. 서버 검증은 예외 종류 조합과 기존 데이터 보존, 실패 후 재시도를 확인합니다. 시도 기록 저장 자체의 장애와 프로세스 중단은 이 실습의 범위 밖입니다.

수정한 코드를 공개 GitHub 저장소에 올리고 저장소 URL과 커밋 SHA를 제출하세요.
