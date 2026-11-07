# 주문은 됐는데 배송이 사라졌다

Java 21, Gradle 8.14.3, Spring Boot 3.5.16, MySQL 8.0, Apache ActiveMQ Artemis를 사용합니다. 주문·배송용 MySQL DB와 계정을 각각 준비해 상대 DB 권한을 부여하지 마세요. 브로커는 별도 터미널에서 `./gradlew.bat runBroker`로 실행합니다. 기본 TCP 포트는 61616이고 메시지는 `build/broker-data`에 보관됩니다. 주문과 배송은 각각 환경변수를 설정하고 `./gradlew.bat bootRun`으로 실행합니다.

| 변수 | 주문 프로세스 | 배송 프로세스 |
| --- | --- | --- |
| MISSION_ROLE | order | shipping |
| MISSION_PORT | 8093 | 8094 |
| MISSION_DB_URL | 주문 DB JDBC URL | 배송 DB JDBC URL |
| MISSION_DB_USERNAME, MISSION_DB_PASSWORD | 주문 전용 계정 | 배송 전용 계정 |
| MISSION_BROKER_URL | 기본값 tcp://127.0.0.1:61616 | 기본값 tcp://127.0.0.1:61616 |

`POST /orders`는 202와 `orderId`를 반환합니다. 발행기는 1초마다 최대 32건을 조회해 영속 큐 `orders.created`에 보냅니다. 이벤트는 UUID 문자열 `eventId`와 양의 정수 `orderId`를 포함합니다. 브로커가 송신을 확인할 때 발행을 완료하고, 배송은 DB 커밋 뒤에 수신을 확정합니다. 미확인 이벤트는 같은 ID로 재시도합니다. 브로커는 로컬 실습용으로 루프백에만 열립니다. 서로 다른 내용이 같은 이벤트 ID로 전달되는 경우는 실습 범위 밖입니다.

`src/main/java/darex/distributed/Exercise.java`를 수정하세요. 주문·발행 대기 기록, 배송·처리 완료 기록을 각각 원자적으로 저장해야 합니다. 처리 전 예외·수신 응답 유실·서비스 중단과 재시작 뒤에도 접수된 주문당 배송이 정확히 1건이어야 합니다. 두 서비스 사이에서 상대 DB를 직접 접근하거나 메모리에만 처리 여부를 저장하지 마세요. API·테스트 장애 지점·스키마 계약을 유지하세요.

테스트에는 `DISTRIBUTED_ORDER_TEST_DB_URL`, `DISTRIBUTED_SHIPPING_TEST_DB_URL`와 실행 계정과 별도로 `DISTRIBUTED_ORDER_TEST_DB_USERNAME`, `DISTRIBUTED_SHIPPING_TEST_DB_USERNAME`, 각 계정의 `_PASSWORD`를 설정합니다. 빈 테스트 DB 두 개에서 `./gradlew.bat test`로 정상 전달·중복·저장 실패·응답 유실·중단 후 복구를 검사합니다. 테스트는 테스트 DB에 예시 행을 작성합니다.

수정한 코드를 공개 GitHub 저장소에 올리고 저장소 URL과 커밋 SHA를 제출하세요.
