package vn.giapha.research.identity.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.research.identity.infrastructure.clients.IdentityQueries;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal endpoint used by the gateway to resolve a session id to a
 * user key + roles + csrf. Not exposed publicly; in production this
 * would be gated by mTLS + an internal-only ACL on the gateway.
 */
@RestController
@RequestMapping("/identity/internal")
public class InternalSessionController {

    private final IdentityQueries identity;

    public InternalSessionController(IdentityQueries identity) {
        this.identity = identity;
    }

    @GetMapping("/resolve-session")
    public Map<String, String> resolveSession(@RequestParam String sessionId) {
        String userKey = identity.resolveSession(sessionId);
        Map<String, String> out = new HashMap<>();
        if (userKey == null) return out;
        out.put("userKey", userKey);
        out.put("roles", "USER");
        out.put("csrf", "");
        return out;
    }

    @GetMapping("/users/{externalId}")
    public IdentityQueries.IdentityUser findUser(@PathVariable String externalId) {
        IdentityQueries.IdentityUser user = identity.findUser(externalId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return user;
    }
}
