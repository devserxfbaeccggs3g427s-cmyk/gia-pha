package vn.giapha.research.tree.saga.core;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcSagaStateLog implements SagaStateLog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcSagaStateLog(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public String start(String sagaName, CommandContext context) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO saga_log(saga_id, saga_name, actor_user_key, tree_key, "
                        + "correlation_id, state, started_at, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, sagaName, context.actorUserKey(), context.treeKey(), context.correlationId(),
                SagaState.STARTED.name(), Timestamp.from(Instant.now()),
                context.idempotencyKey().toString());
        return id;
    }

    @Override
    @Transactional
    public void transition(String sagaId, SagaState state) {
        jdbc.update("UPDATE saga_log SET state = ? WHERE saga_id = ?", state.name(), sagaId);
    }

    @Override
    @Transactional
    public void recordStep(String sagaId, String stepName, SagaStepResult result) {
        writeResult(sagaId, stepName, result, false);
    }

    @Override
    @Transactional
    public void recordCompensation(String sagaId, String stepName, SagaStepResult result) {
        writeResult(sagaId, stepName, result, true);
    }

    private void writeResult(String sagaId, String stepName, SagaStepResult result,
            boolean compensation) {
        try {
            if (compensation) {
                jdbc.update("UPDATE saga_step_log SET compensate_result = ? "
                                + "WHERE saga_id = ? AND step_name = ?",
                        mapper.writeValueAsString(result), sagaId, stepName);
            } else {
                jdbc.update("INSERT INTO saga_step_log(saga_id, step_name, sequence, forward_result) "
                                + "VALUES (?, ?, COALESCE((SELECT MAX(sequence)+1 FROM saga_step_log s "
                                + "WHERE s.saga_id = ?), 1), ?)",
                        sagaId, stepName, sagaId, mapper.writeValueAsString(result));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("cannot serialise saga result", exception);
        }
    }

    @Override
    public SagaStepResult lastResultFor(String sagaId, String stepName) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT forward_result FROM saga_step_log WHERE saga_id = ? AND step_name = ?",
                sagaId, stepName);
        if (rows.isEmpty()) {
            return null;
        }
        try {
            return mapper.readValue((String) rows.getFirst().get("forward_result"),
                    SagaStepResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot deserialise saga result", exception);
        }
    }
}
