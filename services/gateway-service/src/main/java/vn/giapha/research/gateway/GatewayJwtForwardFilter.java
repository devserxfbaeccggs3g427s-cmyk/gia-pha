package vn.giapha.research.gateway;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GatewayJwtForwardFilter implements WebFilter {

    private final WebClient identityClient;
    private final JsonMapper mapper;
    private final JwtSigner signer;

    public GatewayJwtForwardFilter(WebClient.Builder builder, JsonMapper mapper,
            JwtSigner signer) {
        this.identityClient = builder.baseUrl("http://identity-service:8081").build();
        this.mapper = mapper;
        this.signer = signer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String suppliedRequestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String requestId = suppliedRequestId == null ? UUID.randomUUID().toString()
                : suppliedRequestId;
        String sessionCookie = extractSessionCookie(exchange);
        if (sessionCookie == null) {
            return forward(exchange, chain, requestId, null);
        }

        return identityClient.get()
                .uri(uri -> uri.path("/identity/internal/resolve-session")
                        .queryParam("sessionId", sessionCookie).build())
                .retrieve()
                .onStatus(status -> status.value() == 404, response -> Mono.empty())
                .bodyToMono(Map.class)
                .flatMap(userMap -> {
                    String userKey = (String) userMap.get("userKey");
                    String roles = (String) userMap.getOrDefault("roles", "");
                    String csrf = (String) userMap.getOrDefault("csrf", "");
                    UserContext.Token unsigned = new UserContext.Token(
                            userKey, roles, csrf, requestId, "");
                    UserContext.Token signed = new UserContext.Token(
                            userKey, roles, csrf, requestId, signer.sign(unsigned));
                    return forward(exchange, chain, requestId,
                            mapper.writeValueAsString(signed));
                })
                .switchIfEmpty(Mono.defer(() -> forward(exchange, chain, requestId, null)));
    }

    private static Mono<Void> forward(ServerWebExchange exchange, WebFilterChain chain,
            String requestId, String userContext) {
        var request = exchange.getRequest().mutate().header("X-Request-Id", requestId);
        if (userContext != null) {
            request.header(UserContext.HEADER, userContext);
        }
        return chain.filter(exchange.mutate().request(request.build()).build());
    }

    private static String extractSessionCookie(ServerWebExchange exchange) {
        List<org.springframework.http.HttpCookie> cookies = exchange.getRequest().getCookies()
                .get("__Secure-next-auth.session-token");
        return cookies == null || cookies.isEmpty() ? null : cookies.getFirst().getValue();
    }
}
