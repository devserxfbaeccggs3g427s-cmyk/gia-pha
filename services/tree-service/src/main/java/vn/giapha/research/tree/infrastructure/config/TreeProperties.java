package vn.giapha.research.tree.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "giapha.research")
public record TreeProperties(Routing routing) {
    public TreeProperties {
        routing = routing == null ? new Routing(false) : routing;
    }

    public record Routing(boolean ownerOnlyFinalDeletion) {}
}
