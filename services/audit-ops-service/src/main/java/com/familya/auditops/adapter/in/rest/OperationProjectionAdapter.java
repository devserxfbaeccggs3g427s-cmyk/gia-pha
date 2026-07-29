package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.usecase.OperationService;
import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side adapter for the shared platform {@code OperationController}.
 * Returns the operation envelope from the local
 * {@code operation_audit} projection.
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    private final OperationService service;

    public OperationProjectionAdapter(OperationService service) {
        this.service = service;
    }

    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        try {
            return Optional.of(service.find(operationId));
        } catch (com.familya.platform.error.NotFoundException e) {
            return Optional.empty();
        }
    }
}