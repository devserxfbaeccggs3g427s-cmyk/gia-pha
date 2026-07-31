package com.familya.platform.outbox;

/**
 * Domain port for writing to the local outbox table. Implementations
 * are bound to the current transaction and MUST be invoked inside a
 * {@code @Transactional} boundary that also writes the domain
 * aggregate. The relay reads committed rows after the transaction
 * commits.
 */
public interface OutboxWriter {

    /**
     * Stage a record. The record is persisted to {@code outbox_record}
     * in the current transaction.
     */
    void stage(OutboxRecord record);
}
