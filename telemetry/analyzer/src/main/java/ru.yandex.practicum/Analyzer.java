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

        log.info("Starting Analyzer workers...");

        HubEventProcessor hubEventProcessor = context.getBean(HubEventProcessor.class);
        SnapshotProcessor snapshotProcessor = context.getBean(SnapshotProcessor.class);

        KafkaConsumer<?, ?> snapshotConsumer = snapshotProcessor.getConsumer();
        KafkaConsumer<?, ?> hubEventConsumer = hubEventProcessor.getConsumer();

        // Регистрируем хук остановки
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered. Waking up consumers...");
            if (snapshotConsumer != null) snapshotConsumer.wakeup();
            if (hubEventConsumer != null) hubEventConsumer.wakeup();
        }));

        // Запускаем HubEventProcessor в отдельном потоке
        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        // SnapshotProcessor запускаем в основном потоке
        snapshotProcessor.start();
    }
}
