package vn.giapha.research.tree.saga;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.giapha.research.tree.saga.core.JdbcSagaStateLog;
import vn.giapha.research.tree.saga.core.SagaOrchestrator;
import vn.giapha.research.tree.saga.core.SagaStep;

import java.util.List;

/**
 * Wires the sagas tree-service orchestrates. Per design.md §Sagas:
 *
 * <ul>
 *   <li>{@code DeleteMemberSaga}: relationships.delete -> events.unlink ->
 *       media.unlink -> audit.record (compensation: re-insert best-effort).</li>
 * </ul>
 *
 * <p>The participant steps reach other services via synchronous REST or
 * RabbitMQ commands. The orchestrator only knows the step order and
 * the compensation order; participants handle the actual work.
 */
@Configuration
public class DeleteMemberSagaConfig {

    @Bean
    public SagaOrchestrator deleteMemberSaga(JdbcSagaStateLog log) {
        List<SagaStep> ordered = List.of(
                DeleteMemberSteps.relationshipsDelete(),
                DeleteMemberSteps.eventsUnlink(),
                DeleteMemberSteps.mediaUnlink(),
                DeleteMemberSteps.auditRecord()
        );
        return new SagaOrchestrator("DeleteMemberSaga", ordered, log);
    }
}
