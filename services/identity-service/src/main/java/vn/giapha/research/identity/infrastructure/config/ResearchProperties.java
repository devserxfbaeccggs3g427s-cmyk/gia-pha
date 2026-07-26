package vn.giapha.research.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "giapha.research")
public record ResearchProperties(Auth auth, Routing routing, Limits limits) {
    public ResearchProperties {
        auth = auth == null ? new Auth("giapha-nextauth", "giapha-identity", true) : auth;
        routing = routing == null ? new Routing(false) : routing;
        limits = limits == null ? new Limits(10_485_760L) : limits;
    }

    public record Auth(String bridgeIssuer, String bridgeAudience,
                       boolean requireEmailVerification) {}

    public record Routing(boolean ownerOnlyFinalDeletion) {}

    public record Limits(long importMaxBytes) {}
}
