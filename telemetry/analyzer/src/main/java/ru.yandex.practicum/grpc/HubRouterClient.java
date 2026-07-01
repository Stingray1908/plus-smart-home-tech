package ru.yandex.practicum.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

import java.time.Instant;

@Service
public class HubRouterClient {
    private static final Logger log = LoggerFactory.getLogger(HubRouterClient.class);

    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub stub;

    public HubRouterClient(@GrpcClient("hub-router") HubRouterControllerGrpc.HubRouterControllerBlockingStub stub) {
        this.stub = stub;
    }

    public void sendAction(String hubId, String scenarioName, DeviceActionProto action) {
        Instant now = Instant.now();
        com.google.protobuf.Timestamp ts = com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();

        var request = DeviceActionRequest.newBuilder()
                .setHubId(hubId)
                .setScenarioName(scenarioName)
                .setAction(action)
                .setTimestamp(ts)
                .build();

        log.debug("Sending gRPC command to Hub Router: hubId={}, scenario={}", hubId, scenarioName);

        try {
            stub.handleDeviceAction(request);
            log.info("gRPC command sent successfully: hubId={}, scenario={}", hubId, scenarioName);
        } catch (StatusRuntimeException e) {
            // Это самая важная часть: теперь ты увидишь реальную ошибку
            log.error("gRPC call failed to Hub Router (hubId={}, scenario={}): status={}, message={}",
                    hubId, scenarioName, e.getStatus().getCode(), e.getStatus(), e);
            throw e; // пробрасываем дальше, чтобы тест/консьюмер увидел проблему
        } catch (Exception e) {
            log.error("Unexpected error while sending gRPC command (hubId={}, scenario={})",
                    hubId, scenarioName, e);
            throw e;
        }
    }
}
