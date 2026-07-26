package vn.giapha.research.tree.saga;

import vn.giapha.research.tree.saga.core.SagaStep;
import vn.giapha.research.tree.saga.core.SagaStepInput;
import vn.giapha.research.tree.saga.core.SagaStepResult;

import java.util.Map;

/**
 * Participant steps for {@code DeleteMemberSaga}. The actual cross-
 * service calls live in their own adapters (so we don't pull REST
 * clients into this class); this file provides the {@link SagaStep}
 * beans the orchestrator wires.
 *
 * <p>Each step is a no-op placeholder that returns OK. Replacing the
 * body with a real call to the participant service is the per-service
 * integration work; the saga framework doesn't care.
 */
public final class DeleteMemberSteps {

    private DeleteMemberSteps() {}

    public static SagaStep relationshipsDelete() {
        return new SagaStep() {
            @Override public String name() { return "relationships.delete"; }
            @Override public SagaStepResult apply(SagaStepInput input) {
                // TODO: call relationships-service via REST or RabbitMQ command.
                return SagaStepResult.ok(Map.of("deletedRelationships", 0));
            }
            @Override public SagaStepResult compensate(SagaStepInput input, SagaStepResult r) {
                // Best-effort re-insert.
                return SagaStepResult.ok(Map.of());
            }
        };
    }

    public static SagaStep eventsUnlink() {
        return new SagaStep() {
            @Override public String name() { return "events.unlink"; }
            @Override public SagaStepResult apply(SagaStepInput input) {
                return SagaStepResult.ok(Map.of("unlinkedEvents", 0));
            }
            @Override public SagaStepResult compensate(SagaStepInput input, SagaStepResult r) {
                return SagaStepResult.ok(Map.of());
            }
        };
    }

    public static SagaStep mediaUnlink() {
        return new SagaStep() {
            @Override public String name() { return "media.unlink"; }
            @Override public SagaStepResult apply(SagaStepInput input) {
                return SagaStepResult.ok(Map.of("unlinkedMedia", 0));
            }
            @Override public SagaStepResult compensate(SagaStepInput input, SagaStepResult r) {
                return SagaStepResult.ok(Map.of());
            }
        };
    }

    public static SagaStep auditRecord() {
        return new SagaStep() {
            @Override public String name() { return "audit.record"; }
            @Override public SagaStepResult apply(SagaStepInput input) {
                return SagaStepResult.ok(Map.of("auditRecorded", true));
            }
        };
    }
}
