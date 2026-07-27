package com.familya.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Service-level configuration. Properties are resolved from
 * {@code application.yml} under the {@code familya} prefix.
 */
@ConfigurationProperties(prefix = "familya")
public class PlatformProperties {

    private Outbox outbox = new Outbox();
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Security security = new Security();

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public Grpc getGrpc() { return grpc; }
    public void setGrpc(Grpc grpc) { this.grpc = grpc; }
    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public static class Outbox {
        private Relay relay = new Relay();
        public Relay getRelay() { return relay; }
        public void setRelay(Relay relay) { this.relay = relay; }
    }

    public static class Relay {
        private boolean enabled = true;
        private long intervalMs = 500L;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }

    public static class Grpc {
        private Server server = new Server();
        public Server getServer() { return server; }
        public void setServer(Server server) { this.server = server; }
    }

    public static class Server {
        private boolean enabled = false;
        private int port = 9090;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class Kafka {
        private int partitions = 12;
        private short replicationFactor = 3;
        public int getPartitions() { return partitions; }
        public void setPartitions(int partitions) { this.partitions = partitions; }
        public short getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(short replicationFactor) { this.replicationFactor = replicationFactor; }
    }

    public static class Security {
        private Bridge bridge = new Bridge();
        public Bridge getBridge() { return bridge; }
        public void setBridge(Bridge bridge) { this.bridge = bridge; }
    }

    public static class Bridge {
        private String audience = "familya";
        private long maxLifetimeSeconds = 300L;
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public long getMaxLifetimeSeconds() { return maxLifetimeSeconds; }
        public void setMaxLifetimeSeconds(long maxLifetimeSeconds) { this.maxLifetimeSeconds = maxLifetimeSeconds; }
    }
}
