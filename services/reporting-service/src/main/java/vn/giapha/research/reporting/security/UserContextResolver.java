package vn.giapha.research.reporting.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class UserContextResolver {

    private final JsonMapper mapper;

    public UserContextResolver(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public Principal resolve(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }
        try {
            UserContext context = mapper.readValue(value, UserContext.class);
            if (context.userKey() == null || context.userKey().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid user context");
            }
            return new Principal(context.userKey(),
                    context.userKey() + "@giapha.local", "Authenticated user");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid user context", exception);
        }
    }

    private record UserContext(String userKey, String roles, String csrf,
            String requestId, String signature) {
    }
}
