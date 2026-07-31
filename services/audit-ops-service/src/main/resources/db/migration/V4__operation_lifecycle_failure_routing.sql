ALTER TABLE operation_lifecycle_projection
    ADD COLUMN failure_routing VARCHAR(32) NULL AFTER failure_message;
