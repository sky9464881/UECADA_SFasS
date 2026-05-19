package com.example.phm.sensor.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.phm.config.XDasOpcUaProperties;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class XDasOpcUaSubscriber implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(XDasOpcUaSubscriber.class);
    private static final NodeId SERVER_STATUS_NODE_ID = new NodeId(0, 2256);

    private final XDasOpcUaProperties properties;
    private final SensorBufferRegistry registry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong clientHandle = new AtomicLong(1);

    private volatile ExecutorService executor;
    private volatile OpcUaClient client;
    private volatile Map<String, XDasOpcUaNodeMapping> mappingsByNodeId = Map.of();

    public XDasOpcUaSubscriber(XDasOpcUaProperties properties, SensorBufferRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public void start() {
        if (!properties.enabled()) {
            log.info("X_DAS OPC UA subscription is disabled");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "x-das-opcua-subscriber");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::runSubscriptionLoop);
    }

    @Override
    public void stop() {
        running.set(false);
        disconnectClient();

        ExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void runSubscriptionLoop() {
        while (running.get()) {
            try {
                subscribeUntilStopped();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                if (running.get()) {
                    log.warn(
                            "X_DAS OPC UA subscription failed. endpoint={}, retryInMs={}, reason={}",
                            properties.endpointUrl(),
                            properties.reconnectDelayMs(),
                            exception.getMessage()
                    );
                    try {
                        sleepBeforeRetry();
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void subscribeUntilStopped() throws Exception {
        List<XDasOpcUaNodeMapping> mappings =
                XDasOpcUaNodeMappings.defaults(properties.includeLine01AliasBuffers());
        mappingsByNodeId = mappings.stream()
                .collect(Collectors.toUnmodifiableMap(XDasOpcUaNodeMapping::nodeId, Function.identity()));

        OpcUaClient currentClient = OpcUaClient.create(properties.endpointUrl());
        client = currentClient;
        try {
            currentClient.connect().get(10, TimeUnit.SECONDS);

            UaSubscription subscription = currentClient
                    .getSubscriptionManager()
                    .createSubscription(properties.publishingIntervalMs())
                    .get(10, TimeUnit.SECONDS);

            List<MonitoredItemCreateRequest> requests = mappings.stream()
                    .map(this::createRequest)
                    .toList();

            List<UaMonitoredItem> monitoredItems = subscription.createMonitoredItems(
                    TimestampsToReturn.Both,
                    requests,
                    (item, index) -> item.setValueConsumer(this::recordValue)
            ).get(10, TimeUnit.SECONDS);

            long goodItems = monitoredItems.stream()
                    .filter(item -> item.getStatusCode() != null && item.getStatusCode().isGood())
                    .count();
            if (goodItems == 0) {
                throw new IllegalStateException("No X_DAS OPC UA nodes were accepted by the server yet");
            }
            if (goodItems < monitoredItems.size()) {
                log.warn(
                        "X_DAS OPC UA accepted only some monitored items. accepted={}, requested={}",
                        goodItems,
                        monitoredItems.size()
                );
            }

            log.info(
                    "Subscribed to X_DAS OPC UA endpoint. endpoint={}, nodes={}, buffers={}",
                    properties.endpointUrl(),
                    mappings.size(),
                    mappings.stream().mapToInt(mapping -> mapping.bufferKeys().size()).sum()
            );

            while (running.get()) {
                currentClient.readValue(0.0, TimestampsToReturn.Both, SERVER_STATUS_NODE_ID)
                        .get(5, TimeUnit.SECONDS);
                Thread.sleep(5000L);
            }
        } finally {
            disconnectClient();
        }
    }

    private MonitoredItemCreateRequest createRequest(XDasOpcUaNodeMapping mapping) {
        int handle = (int) clientHandle.getAndIncrement();

        ReadValueId readValueId = new ReadValueId(
                NodeId.parse(mapping.nodeId()),
                AttributeId.Value.uid(),
                null,
                QualifiedName.NULL_VALUE
        );
        MonitoringParameters parameters = new MonitoringParameters(
                uint(handle),
                properties.publishingIntervalMs(),
                null,
                uint(properties.queueSize()),
                true
        );
        return new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters);
    }

    private void recordValue(UaMonitoredItem item, DataValue dataValue) {
        StatusCode statusCode = dataValue.getStatusCode();
        if (statusCode != null && !statusCode.isGood()) {
            log.debug("Skipping bad X_DAS OPC UA value. nodeId={}, status={}", item.getReadValueId().getNodeId(), statusCode);
            return;
        }

        Object rawValue = dataValue.getValue() == null ? null : dataValue.getValue().getValue();
        Double numericValue = numericValue(rawValue);
        if (numericValue == null) {
            log.debug("Skipping non-numeric X_DAS OPC UA value. nodeId={}, value={}", item.getReadValueId().getNodeId(), rawValue);
            return;
        }

        String nodeId = item.getReadValueId().getNodeId().toParseableString();
        XDasOpcUaNodeMapping mapping = mappingsByNodeId.get(nodeId);
        if (mapping == null) {
            log.debug("Skipping unmapped X_DAS OPC UA value. nodeId={}", nodeId);
            return;
        }

        SensorFrame frame = new SensorFrame(System.currentTimeMillis(), numericValue);
        mapping.bufferKeys().forEach(bufferKey -> registry.push(bufferKey, frame));
    }

    private Double numericValue(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        return null;
    }

    private void disconnectClient() {
        OpcUaClient currentClient = client;
        client = null;
        if (currentClient != null) {
            try {
                currentClient.disconnect().get(3, TimeUnit.SECONDS);
            } catch (Exception exception) {
                log.debug("Failed to disconnect X_DAS OPC UA client cleanly: {}", exception.getMessage());
            }
        }
    }

    private void sleepBeforeRetry() throws InterruptedException {
        Thread.sleep(properties.reconnectDelayMs());
    }
}
