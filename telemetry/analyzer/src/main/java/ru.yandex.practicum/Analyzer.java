package ru.yandex.practicum;

import lombok.extern.slf4j.Slf4j;
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

        KafkaConsumer<?, ?> snapshotConsumer = snapshotProcessor.getConsumer(); // если нужен геттер
        KafkaConsumer<?, ?> hubEventConsumer = hubEventProcessor.getConsumer();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered. Waking up consumers...");
            if (snapshotConsumer != null) snapshotConsumer.wakeup();
            if (hubEventConsumer != null) hubEventConsumer.wakeup();
        }));

        // HubEventProcessor в отдельном потоке
        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        // SnapshotProcessor тоже в отдельном потоке (чтобы не блокировать main)
        Thread snapshotThread = new Thread(snapshotProcessor);
        snapshotThread.setName("SnapshotProcessorThread");
        snapshotThread.start();
    }
}
