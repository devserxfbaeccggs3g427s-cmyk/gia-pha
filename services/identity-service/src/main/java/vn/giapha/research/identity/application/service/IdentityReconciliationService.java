package vn.giapha.research.identity.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import vn.giapha.research.identity.application.port.out.OAuthAccountRepository;
import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.application.port.out.VerificationTokenRepository;
import vn.giapha.research.identity.domain.model.LegacyUserImport;
import vn.giapha.research.identity.domain.model.OAuthAccountLink;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.domain.model.VerificationToken;

/**
 * Source-vs-MySQL identity reconciliation (Task 17.3). Both sides are reduced
 * to the {@link IdentityDigest} canonical form (normalized email, provider,
 * password hash, verification instant, lockout state, sorted OAuth keys, sorted
 * outstanding verification tokens) and compared. The report carries only
 * counts, digests and external IDs — no emails or hashes — so it can be logged
 * and attached to cutover evidence (Req 14, Req 20).
 */
@Service
public class IdentityReconciliationService {

    public record Report(
            long sourceUserCount,
            long targetUserCount,
            long sourceOauthLinkCount,
            long targetOauthLinkCount,
            long sourceVerificationTokenCount,
            long targetVerificationTokenCount,
            String sourceDigest,
            String targetDigest,
            List<String> mismatchedExternalIds,
            List<String> missingInTarget,
            List<String> unexpectedInTarget) {

        public boolean reconciled() {
            return sourceDigest.equals(targetDigest)
                    && sourceUserCount == targetUserCount
                    && sourceOauthLinkCount == targetOauthLinkCount
                    && sourceVerificationTokenCount == targetVerificationTokenCount
                    && mismatchedExternalIds.isEmpty()
                    && missingInTarget.isEmpty()
                    && unexpectedInTarget.isEmpty();
        }
    }

    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final VerificationTokenRepository verificationTokens;

    public IdentityReconciliationService(UserRepository users,
            OAuthAccountRepository oauthAccounts,
            VerificationTokenRepository verificationTokens) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.verificationTokens = verificationTokens;
    }

    public Report reconcile(List<LegacyUserImport> sourceBatch) {
        Map<String, String> sourceLines = new LinkedHashMap<>();
        long sourceOauthCount = 0;
        long sourceTokenCount = 0;
        for (LegacyUserImport imported : sourceBatch) {
            List<String> oauthKeys = imported.oauthAccounts().stream()
                    .map(link -> IdentityDigest.oauthKey(link.provider(),
                            link.providerAccountId()))
                    .sorted()
                    .toList();
            sourceOauthCount += oauthKeys.size();
            String tokenDigest = imported.verification() == null
                    ? "-"
                    : IdentityDigest.tokenDigest(imported.verification().tokenHash(),
                            imported.verification().expiresAt());
            if (imported.verification() != null) {
                sourceTokenCount++;
            }
            sourceLines.put(imported.user().externalId(), IdentityDigest.canonicalLine(
                    imported.user().externalId(),
                    imported.user().email(),
                    imported.user().name(),
                    imported.user().passwordHash(),
                    imported.user().provider(),
                    imported.user().emailVerifiedAt(),
                    imported.user().failedLoginAttempts(),
                    imported.user().lockedUntil(),
                    imported.user().createdAt(),
                    oauthKeys,
                    tokenDigest));
        }

        List<User> targetUsers = users.findAllActiveOrderedByExternalId();
        Map<String, Long> externalIdToUserKey = new LinkedHashMap<>();
        for (User user : targetUsers) {
            externalIdToUserKey.put(user.externalId(), user.userKey());
        }
        Map<Long, List<String>> oauthKeysByUserKey = new LinkedHashMap<>();
        long targetOauthCount = 0;
        for (OAuthAccountLink link : oauthAccounts.findAllOrdered()) {
            oauthKeysByUserKey.computeIfAbsent(link.userKey(), key -> new ArrayList<>())
                    .add(IdentityDigest.oauthKey(link.provider(), link.providerAccountId()));
            targetOauthCount++;
        }
        Map<Long, List<String>> tokenDigestsByUserKey = new LinkedHashMap<>();
        long targetTokenCount = 0;
        for (VerificationToken token : verificationTokens.findAllOrdered()) {
            // Consumed tokens cannot be re-derived from users.json; compare only
            // outstanding (unconsumed, unexpired) tokens.
            if (token.consumedAt() != null) {
                continue;
            }
            tokenDigestsByUserKey.computeIfAbsent(token.userKey(), key -> new ArrayList<>())
                    .add(IdentityDigest.tokenDigest(token.tokenHash(), token.expiresAt()));
            targetTokenCount++;
        }

        Map<String, String> targetLines = new LinkedHashMap<>();
        for (User user : targetUsers) {
            List<String> oauthKeys = oauthKeysByUserKey
                    .getOrDefault(user.userKey(), List.of())
                    .stream().sorted().toList();
            List<String> tokens = tokenDigestsByUserKey
                    .getOrDefault(user.userKey(), List.of())
                    .stream().sorted().toList();
            targetLines.put(user.externalId(), IdentityDigest.canonicalLine(
                    user.externalId(),
                    user.email(),
                    user.name(),
                    user.passwordHash(),
                    user.provider(),
                    user.emailVerifiedAt(),
                    user.failedLoginAttempts(),
                    user.lockedUntil(),
                    user.createdAt(),
                    oauthKeys,
                    String.join(",", tokens)));
        }

        List<String> mismatched = new ArrayList<>();
        List<String> missingInTarget = new ArrayList<>();
        for (Map.Entry<String, String> source : sourceLines.entrySet()) {
            String targetLine = targetLines.get(source.getKey());
            if (targetLine == null) {
                missingInTarget.add(source.getKey());
            } else if (!targetLine.equals(source.getValue())) {
                mismatched.add(source.getKey());
            }
        }
        List<String> unexpectedInTarget = targetLines.keySet().stream()
                .filter(externalId -> !sourceLines.containsKey(externalId))
                .toList();

        return new Report(
                sourceLines.size(),
                targetLines.size(),
                sourceOauthCount,
                targetOauthCount,
                sourceTokenCount,
                targetTokenCount,
                IdentityDigest.digestHex(List.copyOf(sourceLines.values())),
                IdentityDigest.digestHex(List.copyOf(targetLines.values())),
                List.copyOf(mismatched),
                List.copyOf(missingInTarget),
                List.copyOf(unexpectedInTarget));
    }
}
