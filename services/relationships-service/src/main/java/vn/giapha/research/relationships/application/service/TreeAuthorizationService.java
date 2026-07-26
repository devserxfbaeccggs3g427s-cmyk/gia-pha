package vn.giapha.research.relationships.application.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.giapha.research.relationships.support.RelationshipSupport.ForbiddenException;
import vn.giapha.research.relationships.support.RelationshipSupport.Principal;

@Service
public class TreeAuthorizationService {

    public enum Action { READ, WRITE }

    public Optional<String> authorize(long treeKey, Principal principal, Action action,
            boolean failClosed) {
        boolean authenticated = principal != null && principal.userId() != null;
        if (authenticated || action == Action.READ) {
            return Optional.of(action.name());
        }
        if (failClosed) {
            throw new ForbiddenException("Authentication is required for tree mutation");
        }
        return Optional.empty();
    }
}
