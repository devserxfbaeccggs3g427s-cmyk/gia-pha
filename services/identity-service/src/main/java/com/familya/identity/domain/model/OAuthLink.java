package com.familya.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class OAuthLink {
    private final UUID id;
    private final UUID userId;
    private final String provider;
    private final String providerSubject;
    private final String normalizedEmail;

    public OAuthLink(UUID id, UUID userId, String provider, String providerSubject, String normalizedEmail) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.provider = Objects.requireNonNull(provider);
        this.providerSubject = Objects.requireNonNull(providerSubject);
        this.normalizedEmail = normalizedEmail;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String provider() { return provider; }
    public String providerSubject() { return providerSubject; }
    public String normalizedEmail() { return normalizedEmail; }
}
