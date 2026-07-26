package vn.giapha.research.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IdentityGoldenCorpusTest {

    @Test
    void generated_hashes_round_trip_through_password_hasher() {
        PasswordHasher hasher = new PasswordHasher();
        var fixtures = IdentityGoldenCorpus.withGeneratedHashes(hasher);
        assertThat(fixtures).hasSize(6);
        assertThat(fixtures.get(IdentityGoldenCorpus.CREDENTIAL_USER_ID).plaintext())
                .isEqualTo(IdentityGoldenCorpus.CREDENTIAL_PLAINTEXT);
        assertThat(hasher.verify(IdentityGoldenCorpus.CREDENTIAL_PLAINTEXT,
                extractHash(fixtures.get(IdentityGoldenCorpus.CREDENTIAL_USER_ID).json())))
                .isTrue();
    }

    @Test
    void duplicate_external_ids_are_flagged_as_rejected() {
        PasswordHasher hasher = new PasswordHasher();
        LegacyUsersTransformer transformer = new LegacyUsersTransformer(JsonMapper.builder().build());
        var fixtures = IdentityGoldenCorpus.withGeneratedHashes(hasher);
        String json = "[" + fixtures.get(IdentityGoldenCorpus.CREDENTIAL_USER_ID).json()
                + "," + fixtures.get(IdentityGoldenCorpus.DUPLICATE_USER_ID).json() + "]";
        LegacyUsersTransformer.Batch batch = transformer.transform(json);
        assertThat(batch.accepted()).hasSize(1);
        assertThat(batch.rejected())
                .anyMatch(rejected -> "DUPLICATE_EXTERNAL_ID".equals(rejected.reason()));
    }

    private static String extractHash(String json) {
        int start = json.indexOf("\"passwordHash\": \"") + 17;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
