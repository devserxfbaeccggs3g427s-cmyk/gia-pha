package com.familya.platform.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-side adapter for the async operation envelope. Each service
 * implements the {@link OperationQuery} port with its local projection;
 * this controller is shared so the public route contract is identical
 * across services.
 */
@RestController
@RequestMapping("/api/v2/operations")
public class OperationController {

    private final OperationQuery query;

    public OperationController(OperationQuery query) {
        this.query = query;
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<AsyncOperation> get(@PathVariable UUID operationId) {
        return query.findById(operationId)
                .map(op -> ResponseEntity.status(HttpStatus.OK).body(op))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
