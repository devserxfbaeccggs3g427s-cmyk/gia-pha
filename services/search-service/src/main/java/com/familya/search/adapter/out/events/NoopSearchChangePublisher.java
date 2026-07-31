package com.familya.search.adapter.out.events;

import com.familya.search.application.port.out.SearchChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Triển khai cố ý không-op của {@link SearchChangePublisher}.
 *
 * <p>Search service chỉ chứa read-model, không phát sinh sự kiện liên
 * service. Việc triển khai này tồn tại để tầng application có thể nối dây
 * đối xứng với các service khác mà không phát ra sự kiện thật.</p>
 */
@Component
public class NoopSearchChangePublisher implements SearchChangePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(NoopSearchChangePublisher.class);

    /**
     * Không làm gì cả, chỉ ghi log ở mức trace để không gây nhiễu log.
     */
    @Override
    public void noop() {
        LOG.trace("noop");
    }
}
