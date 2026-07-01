package ru.yandex.practicum;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.yandex.practicum.kafka.HubEventProcessor;
import ru.yandex.practicum.kafka.SnapshotProcessor;

@Slf4j
@SpringBootApplication
public class Analyzer {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Analyzer.class, args);

        HubEventProcessor hubEventProcessor = context.getBean(HubEventProcessor.class);
        SnapshotProcessor snapshotProcessor = context.getBean(SnapshotProcessor.class);

        Consumer<?, ?> snapshotConsumer = snapshotProcessor.getConsumer();
        Consumer<?, ?> hubEventConsumer = hubEventProcessor.getConsumer();

        log.info("Consumers initialized: snapshot={}, hubEvent={}", snapshotConsumer != null, hubEventConsumer != null);
        if (snapshotConsumer != null) {
            log.info("SnapshotConsumer subscribed topics: {}", snapshotConsumer.subscription());
        }
        if (hubEventConsumer != null) {
            log.info("HubEventConsumer subscribed topics: {}", hubEventConsumer.subscription());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered. Waking up consumers...");
            if (snapshotConsumer != null) snapshotConsumer.wakeup();
            if (hubEventConsumer != null) hubEventConsumer.wakeup();
        }));

        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        Thread snapshotThread = new Thread(snapshotProcessor);
        snapshotThread.setName("SnapshotProcessorThread");
        snapshotThread.start();
    }
}
