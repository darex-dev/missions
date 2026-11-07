# 한정 수량 판매를 지켜라

Java 21, Gradle 8.14.3, Spring Boot 3.5.16, MySQL 8.0을 사용합니다. 실습 DB와 테스트 DB를 별도로 만들고 해당 DB만 접근하는 계정을 준비하세요.

| 환경변수 | 역할 |
| --- | --- |
| MISSION_DB_URL | 실행용 JDBC URL |
| CONCURRENCY_TEST_DB_URL | 테스트 전용 JDBC URL |
| MISSION_DB_USERNAME, MISSION_DB_PASSWORD | DB 접속 계정 |
| MISSION_PORT | 기본 포트 8091 |

Windows에서는 `./gradlew.bat bootRun`으로 실행하고 `./gradlew.bat test`로 공개 테스트를 실행하세요. 초기 상품은 실습 DB에서 `INSERT INTO products(stock) VALUES (10);`으로 추가합니다. 테스트는 별도 상품을 만들어 기존 데이터를 수정하지 않습니다.

`POST /products/{id}/orders`는 1개 주문을 처리합니다. 성공은 201/ORDERED, 재고 부족은 409/SOLD_OUT, 상품 없음은 404/NOT_FOUND입니다.

`src/main/java/darex/concurrency/Exercise.java`의 주문 로직을 수정하세요. 재고는 음수가 될 수 없고 성공 응답 수, 신규 주문 수, 차감량이 일치해야 합니다. 같은 MySQL을 공유하는 여러 인스턴스에서도 성립해야 합니다. API·스키마·테스트 계약을 유지하세요.

시작 코드는 순차 테스트가 통과하고 동시 요청 테스트는 실패합니다. 재고 10개/요청 30건과 재고 30개/요청 10건을 공개 테스트로 확인합니다. 서버 검증은 마지막 재고 경쟁, 여러 상품·반복 요청, 별도 JVM 인스턴스를 검사합니다.

수정한 코드를 공개 GitHub 저장소에 올리고 저장소 URL과 커밋 SHA를 제출하세요.
