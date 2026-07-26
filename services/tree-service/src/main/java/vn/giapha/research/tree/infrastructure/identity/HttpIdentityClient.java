package vn.giapha.research.tree.infrastructure.identity;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpIdentityClient implements IdentityPort {
    private final RestClient client;

    public HttpIdentityClient(RestClient client) {
        this.client = client;
    }

    @Override
    public Optional<IdentityUser> findUser(String externalId) {
        try {
            IdentityUser user = client.get()
                    .uri("/identity/internal/users/{externalId}", externalId)
                    .retrieve()
                    .body(IdentityUser.class);
            return Optional.ofNullable(user);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        }
    }
}
