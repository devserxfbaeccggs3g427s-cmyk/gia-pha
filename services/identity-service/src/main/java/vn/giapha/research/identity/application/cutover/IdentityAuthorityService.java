package vn.giapha.research.identity.application.cutover;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.application.port.out.IdentityAuthorityRepository;
import vn.giapha.research.identity.domain.cutover.IdentityAuthority;
import vn.giapha.research.identity.domain.cutover.IdentityWriter;
import vn.giapha.research.identity.domain.cutover.IdentityWriterForbiddenException;

/**
 * Identity authority service (Task 20.3, ADR-009). Guards every identity
 * write from the Spring side and is the only legal surface for
 * cutover/rollback. The {@link IdentityWriter} enum keeps the call site
 * explicit about which side is asking.
 */
@Service
public class IdentityAuthorityService {

    private final IdentityAuthorityRepository repository;

    public IdentityAuthorityService(IdentityAuthorityRepository repository) {
        this.repository = repository;
    }

    /** Read-only inspection used by ops endpoints and the bridge filter. */
    public Optional<IdentityAuthority> current() {
        return repository.findCurrent();
    }

    /**
     * Enforce the {@link IdentityWriter#SPRING} write rule. Throws
     * {@link IdentityWriterForbiddenException} when the current state forbids
     * Spring writes (i.e. the cutover has not flipped yet).
     */
    public void assertSpringCanWrite() {
        IdentityAuthority authority = current().orElseThrow(() ->
                new IdentityWriterForbiddenException("Identity authority row is missing"));
        if (!authority.canWrite(IdentityWriter.SPRING)) {
            throw new IdentityWriterForbiddenException(
                    "Spring identity writes are currently forbidden");
        }
    }

    /** Same rule for the legacy writer (used by the Next.js compatibility adapter). */
    public void assertLegacyCanWrite() {
        IdentityAuthority authority = current().orElseThrow(() ->
                new IdentityWriterForbiddenException("Identity authority row is missing"));
        if (!authority.canWrite(IdentityWriter.LEGACY)) {
            throw new IdentityWriterForbiddenException(
                    "Legacy identity writes are currently forbidden");
        }
    }

    @Transactional
    public IdentityAuthority freeze(String reason) {
        IdentityAuthority current = requireCurrent();
        return repository.compareAndSwitch(current.version(),
                current.writer(), true,
                current.legacyReadsAllowed(), false,
                current.springReadsAllowed(), true,
                reason).orElseThrow(() ->
                new IdentityWriterForbiddenException(
                        "Concurrent authority change aborted freeze"));
    }

    @Transactional
    public IdentityAuthority unfreeze(String reason) {
        IdentityAuthority current = requireCurrent();
        return repository.compareAndSwitch(current.version(),
                current.writer(), false,
                current.legacyReadsAllowed(), current.legacyWritesAllowed(),
                current.springReadsAllowed(), current.springWritesAllowed(),
                reason).orElseThrow(() ->
                new IdentityWriterForbiddenException(
                        "Concurrent authority change aborted unfreeze"));
    }

    @Transactional
    public IdentityAuthority switchToSpring(String reason) {
        IdentityAuthority current = requireCurrent();
        return repository.compareAndSwitch(current.version(),
                IdentityWriter.SPRING, current.freeze(),
                false, false,
                true, true,
                reason).orElseThrow(() ->
                new IdentityWriterForbiddenException(
                        "Concurrent authority change aborted Spring switch"));
    }

    @Transactional
    public IdentityAuthority rollbackToLegacy(String reason) {
        IdentityAuthority current = requireCurrent();
        return repository.compareAndSwitch(current.version(),
                IdentityWriter.LEGACY, current.freeze(),
                true, true,
                true, false,
                reason).orElseThrow(() ->
                new IdentityWriterForbiddenException(
                        "Concurrent authority change aborted rollback"));
    }

    private IdentityAuthority requireCurrent() {
        return repository.findCurrent().orElseThrow(() ->
                new IdentityWriterForbiddenException("Identity authority row is missing"));
    }
}
