package com.familya.search.application.port.out;

/**
 * Intentional no-op. The search service is a read-model only
 * service; it does not publish cross-service events. The interface
 * exists so the application layer can be wired symmetrically with
 * the other services and so future operational tooling (e.g.
 * rebuild alarms) has a sanctioned insertion point.
 */
public interface SearchChangePublisher {
    void noop();
}
