package com.familya.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cấu hình cấp dịch vụ được đọc từ {@code application.yml} dưới prefix {@code familya}.
 *
 * <p>Lớp này sử dụng {@link ConfigurationProperties} để Spring Boot tự động
 * bind các thuộc tính cấu hình vào các trường tương ứng, giúp việc inject cấu
 * hình vào các bean khác trở nên an toàn về kiểu (type-safe).</p>
 *
 * <p>Cấu trúc cây cấu hình:</p>
 * <ul>
 *   <li>{@code familya.outbox.relay} — cấu hình relay cho outbox.</li>
 *   <li>{@code familya.grpc.server} — cấu hình máy chủ gRPC.</li>
 *   <li>{@code familya.kafka} — cấu hình chung cho Kafka (partition, replication).</li>
 *   <li>{@code familya.security.bridge} — cấu hình Bridge token.</li>
 * </ul>
 *
 * <p>Mỗi nhóm cấu hình được đóng gói trong một lớp tĩnh lồng nhau, cho phép
 * các dịch vụ dễ dàng chỉ inject phần cấu hình mà chúng cần thông qua các
 * annotation {@code @ConfigurationProperties} riêng nếu muốn.</p>
 *
 * @author Family Tree Platform Team
 */
@ConfigurationProperties(prefix = "familya")
public class PlatformProperties {

    /** Cấu hình liên quan đến outbox. */
    private Outbox outbox = new Outbox();

    /** Cấu hình liên quan đến gRPC. */
    private Grpc grpc = new Grpc();

    /** Cấu hình liên quan đến Kafka. */
    private Kafka kafka = new Kafka();

    /** Cấu hình liên quan đến bảo mật. */
    private Security security = new Security();

    /** @return cấu hình outbox hiện tại */
    public Outbox getOutbox() { return outbox; }

    /** @param outbox cấu hình outbox mới */
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    /** @return cấu hình gRPC hiện tại */
    public Grpc getGrpc() { return grpc; }

    /** @param grpc cấu hình gRPC mới */
    public void setGrpc(Grpc grpc) { this.grpc = grpc; }

    /** @return cấu hình Kafka hiện tại */
    public Kafka getKafka() { return kafka; }

    /** @param kafka cấu hình Kafka mới */
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    /** @return cấu hình bảo mật hiện tại */
    public Security getSecurity() { return security; }

    /** @param security cấu hình bảo mật mới */
    public void setSecurity(Security security) { this.security = security; }

    /** Nhóm cấu hình outbox. */
    public static class Outbox {
        /** Cấu hình relay. */
        private Relay relay = new Relay();
        /** @return cấu hình relay */
        public Relay getRelay() { return relay; }
        /** @param relay cấu hình relay mới */
        public void setRelay(Relay relay) { this.relay = relay; }
    }

    /** Cấu hình relay cho outbox. */
    public static class Relay {
        /** Bật/tắt relay (mặc định bật). */
        private boolean enabled = true;
        /** Chu kỳ poll của relay, tính bằng mili-giây (mặc định 500ms). */
        private long intervalMs = 500L;

        /** @return relay có đang bật hay không */
        public boolean isEnabled() { return enabled; }
        /** @param enabled bật/tắt relay */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        /** @return chu kỳ poll hiện tại */
        public long getIntervalMs() { return intervalMs; }
        /** @param intervalMs chu kỳ poll mới (mili-giây) */
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }

    /** Nhóm cấu hình gRPC. */
    public static class Grpc {
        /** Cấu hình máy chủ gRPC. */
        private Server server = new Server();
        /** @return cấu hình máy chủ */
        public Server getServer() { return server; }
        /** @param server cấu hình máy chủ mới */
        public void setServer(Server server) { this.server = server; }
    }

    /** Cấu hình máy chủ gRPC. */
    public static class Server {
        /** Bật/tắt máy chủ gRPC (mặc định tắt). */
        private boolean enabled = false;
        /** Cổng lắng nghe (mặc định 9090). */
        private int port = 9090;

        /** @return trạng thái bật/tắt */
        public boolean isEnabled() { return enabled; }
        /** @param enabled bật/tắt */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        /** @return cổng hiện tại */
        public int getPort() { return port; }
        /** @param port cổng mới */
        public void setPort(int port) { this.port = port; }
    }

    /** Cấu hình chung cho Kafka. */
    public static class Kafka {
        /** Số partition mặc định khi tạo topic (mặc định 12). */
        private int partitions = 12;
        /** Hệ số replication mặc định (mặc định 3). */
        private short replicationFactor = 3;

        /** @return số partition */
        public int getPartitions() { return partitions; }
        /** @param partitions số partition mới */
        public void setPartitions(int partitions) { this.partitions = partitions; }
        /** @return hệ số replication */
        public short getReplicationFactor() { return replicationFactor; }
        /** @param replicationFactor hệ số replication mới */
        public void setReplicationFactor(short replicationFactor) { this.replicationFactor = replicationFactor; }
    }

    /** Nhóm cấu hình bảo mật. */
    public static class Security {
        /** Cấu hình Bridge token. */
        private Bridge bridge = new Bridge();
        /** @return cấu hình bridge */
        public Bridge getBridge() { return bridge; }
        /** @param bridge cấu hình bridge mới */
        public void setBridge(Bridge bridge) { this.bridge = bridge; }
    }

    /** Cấu hình Bridge token (cầu nối NextAuth). */
    public static class Bridge {
        /** Audience mặc định của token (mặc định {@code "familya"}). */
        private String audience = "familya";
        /** Thời gian sống tối đa của token, tính bằng giây (mặc định 300s = 5 phút). */
        private long maxLifetimeSeconds = 300L;

        /** @return audience */
        public String getAudience() { return audience; }
        /** @param audience audience mới */
        public void setAudience(String audience) { this.audience = audience; }
        /** @return thời gian sống tối đa */
        public long getMaxLifetimeSeconds() { return maxLifetimeSeconds; }
        /** @param maxLifetimeSeconds thời gian sống tối đa mới (giây) */
        public void setMaxLifetimeSeconds(long maxLifetimeSeconds) { this.maxLifetimeSeconds = maxLifetimeSeconds; }
    }
}
