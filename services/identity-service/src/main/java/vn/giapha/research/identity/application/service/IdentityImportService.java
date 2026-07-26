package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import vn.giapha.research.identity.application.port.out.OAuthAccountRepository;
import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.application.port.out.VerificationTokenRepository;
import vn.giapha.research.identity.domain.model.LegacyUserImport;
import vn.giapha.research.identity.domain.model.OAuthAccountLink;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.domain.model.VerificationToken;

/**
 * Idempotent identity bulk load (Task 17.3). Every user imports in its own
 * transaction so one bad record cannot roll back the batch; re-running the
 * load converges: rows that already exist (matched by unique constraint, not
 * by pre-check) are counted as {@code skippedExisting}. A duplicate that maps
 * to a <em>different</em> external ID (same email claimed by two source
 * records) is a conflict, never silently merged.
 */
@Service
public class IdentityImportService {

    public record ImportResult(int usersInserted, int usersSkippedExisting,
            int usersUpdated,
            int oauthLinksInserted, int oauthLinksSkippedExisting,
            int verificationTokensInserted, int verificationTokensSkippedExisting,
            List<Conflict> conflicts) {}

    public record Conflict(String externalId, String reason) {}

    public enum Mode {
        /** Fail-fast load: existing user rows are skipped; counts and conflicts
         *  are reported. Suitable for the very first users.json migration. */
        INITIAL,
        /** Final-delta load: existing user rows are updated in place so the
         *  post-freeze source export converges with the relational state
         *  (Task 20.1). Updates are CAS'd on {@code version}. */
        FINAL_DELTA
    }

    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final VerificationTokenRepository verificationTokens;
    private final TransactionTemplate transaction;

    public IdentityImportService(UserRepository users, OAuthAccountRepository oauthAccounts,
            VerificationTokenRepository verificationTokens, TransactionTemplate transaction) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.verificationTokens = verificationTokens;
        this.transaction = transaction;
    }

    public ImportResult importBatch(List<LegacyUserImport> batch) {
        return importBatch(batch, Mode.INITIAL);
    }

    public ImportResult importBatch(List<LegacyUserImport> batch, Mode mode) {
        int usersInserted = 0;
        int usersSkipped = 0;
        int usersUpdated = 0;
        int linksInserted = 0;
        int linksSkipped = 0;
        int tokensInserted = 0;
        int tokensSkipped = 0;
        List<Conflict> conflicts = new ArrayList<>();

        for (LegacyUserImport imported : batch) {
            Outcome outcome;
            try {
                // Programmatic per-user transaction (self-invocation would
                // bypass an @Transactional proxy).
                outcome = transaction.execute(status -> importOne(imported, mode));
            } catch (RuntimeException failure) {
                conflicts.add(new Conflict(imported.user().externalId(),
                        failure.getClass().getSimpleName()));
                continue;
            }
            if (outcome.conflictReason() != null) {
                conflicts.add(new Conflict(imported.user().externalId(),
                        outcome.conflictReason()));
                continue;
            }
            if (outcome.userInserted()) {
                usersInserted++;
            } else if (outcome.userUpdated()) {
                usersUpdated++;
            } else {
                usersSkipped++;
            }
            linksInserted += outcome.linksInserted();
            linksSkipped += outcome.linksSkipped();
            tokensInserted += outcome.tokenInserted() ? 1 : 0;
            tokensSkipped += outcome.tokenSkipped() ? 1 : 0;
        }
        return new ImportResult(usersInserted, usersSkipped, usersUpdated, linksInserted,
                linksSkipped, tokensInserted, tokensSkipped, List.copyOf(conflicts));
    }

    private record Outcome(boolean userInserted, boolean userUpdated, int linksInserted,
            int linksSkipped, boolean tokenInserted, boolean tokenSkipped,
            String conflictReason) {}

    private Outcome importOne(LegacyUserImport imported, Mode mode) {
        boolean userInserted;
        boolean userUpdated = false;
        long userKey;
        try {
            userKey = users.insert(imported.user());
            userInserted = true;
        } catch (DuplicateKeyException duplicate) {
            Optional<User> existing = users.findByExternalId(imported.user().externalId());
            if (existing.isEmpty()) {
                // Email already owned by a different external ID: a source-side
                // duplicate the migration must surface, not merge (Task 17.4).
                return new Outcome(false, false, 0, 0, false, false,
                        "EMAIL_CLAIMED_BY_OTHER_USER");
            }
            User existingUser = existing.get();
            userKey = existingUser.userKey();
            userInserted = false;
            if (mode == Mode.FINAL_DELTA) {
                userUpdated = applyDelta(existingUser, imported);
            }
        }

        int linksInserted = 0;
        int linksSkipped = 0;
        int linkConflicts = 0;
        for (LegacyUserImport.OAuthLinkImport link : imported.oauthAccounts()) {
            Optional<OAuthAccountLink> existingLink =
                    oauthAccounts.findByProviderAccount(link.provider(), link.providerAccountId());
            if (existingLink.isPresent() && existingLink.get().userKey() != userKey) {
                // (provider, providerAccountId) already linked to a *different*
                // user — a real conflict, never silently merged (Task 17.4).
                linkConflicts++;
                continue;
            }
            if (existingLink.isPresent()) {
                linksSkipped++;
                continue;
            }
            try {
                oauthAccounts.link(userKey, link.provider(), link.providerAccountId(),
                        imported.user().createdAt());
                linksInserted++;
            } catch (DuplicateKeyException duplicate) {
                linksSkipped++;
            }
        }
        if (linkConflicts > 0) {
            return new Outcome(userInserted, userUpdated, linksInserted, linksSkipped,
                    false, false, "OAUTH_ACCOUNT_OWNED_BY_OTHER_USER");
        }

        boolean tokenInserted = false;
        boolean tokenSkipped = false;
        LegacyUserImport.PendingVerification verification = imported.verification();
        if (verification != null) {
            try {
                verificationTokens.insert(userKey,
                        VerificationToken.PURPOSE_EMAIL_VERIFICATION,
                        verification.tokenHash(), verification.expiresAt(),
                        imported.user().updatedAt() != null
                                ? imported.user().updatedAt()
                                : Instant.now());
                tokenInserted = true;
            } catch (DuplicateKeyException duplicate) {
                tokenSkipped = true;
            }
        }
        return new Outcome(userInserted, userUpdated, linksInserted, linksSkipped,
                tokenInserted, tokenSkipped, null);
    }

    /**
     * Apply the post-freeze delta on an existing user. Limited to fields the
     * migration is allowed to refresh: {@code name}, {@code imageUrl} and
     * {@code updatedAt}. Password hashes, email verification, lockout, and
     * provider are managed by the Spring authentication flow and never
     * overwritten by the importer (Task 20.1).
     */
    private boolean applyDelta(User existing, LegacyUserImport imported) {
        if (existing.name().equals(imported.user().name())
                && java.util.Objects.equals(existing.imageUrl(), imported.user().imageUrl())) {
            return false;
        }
        Instant updatedAt = imported.user().updatedAt() != null
                ? imported.user().updatedAt() : Instant.now();
        return users.applyProfileDelta(existing.userKey(),
                imported.user().name(),
                imported.user().imageUrl(),
                existing.version(),
                updatedAt);
    }

    /**
     * Lookup helper used by the golden-corpus replay driver to verify that an
     * imported {@code users.password_hash} authenticates a known plaintext.
     * Intentionally not part of the regular import pipeline.
     */
    public Optional<String> lookupStoredHash(String externalId) {
        return users.findByExternalId(externalId).map(User::passwordHash);
    }
}
