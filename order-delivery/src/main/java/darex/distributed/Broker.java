package darex.distributed;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

public final class Broker {
    static final String QUEUE = "orders.created";

    // 로컬 실습 브로커는 독립 JVM과 영속 저널을 사용한다.
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("MISSION_BROKER_PORT", "61616"));
        var data = Path.of(System.getenv().getOrDefault("MISSION_BROKER_DATA", "build/broker-data")).toAbsolutePath();
        var configuration = new ConfigurationImpl();
        configuration.setPersistenceEnabled(true).setSecurityEnabled(false).setJournalType(JournalType.NIO)
                .setBindingsDirectory(data.resolve("bindings").toString())
                .setJournalDirectory(data.resolve("journal").toString())
                .setPagingDirectory(data.resolve("paging").toString())
                .setLargeMessagesDirectory(data.resolve("large").toString());
        configuration.addAcceptorConfiguration("local", "tcp://127.0.0.1:" + port);
        configuration.addQueueConfiguration(QueueConfiguration.of(QUEUE).setAddress(QUEUE)
                .setRoutingType(RoutingType.ANYCAST).setDurable(true));
        configuration.addAddressSetting(QUEUE, new AddressSettings().setRedeliveryDelay(1000).setMaxDeliveryAttempts(-1));
        var broker = new EmbeddedActiveMQ().setConfiguration(configuration);
        broker.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { broker.stop(); }
            catch (Exception exception) { System.err.println("브로커 종료 실패: " + exception.getClass().getSimpleName()); }
        }));
        new CountDownLatch(1).await();
    }
}
