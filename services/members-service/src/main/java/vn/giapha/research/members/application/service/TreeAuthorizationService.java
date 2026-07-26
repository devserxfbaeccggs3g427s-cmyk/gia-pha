package vn.giapha.research.members.application.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.giapha.research.members.support.MemberSupport.ForbiddenException;
import vn.giapha.research.members.support.MemberSupport.Principal;

@Service
public class TreeAuthorizationService {

    public enum Action { READ, WRITE, DELETE }

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
