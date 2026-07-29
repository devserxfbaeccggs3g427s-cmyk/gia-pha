package com.familya.search.adapter.out.events;

import com.familya.search.application.port.out.SearchChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Search service is read-model only. The publisher is a
 * sanctioned no-op so the application layer can wire the port
 * symmetrically with the other services without ever emitting
 * cross-service events.
 */
@Component
public class NoopSearchChangePublisher implements SearchChangePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(NoopSearchChangePublisher.class);

    @Override
    public void noop() {
        LOG.trace("noop");
    }
}
