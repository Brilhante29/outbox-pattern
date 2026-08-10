package com.portfolio.outbox.application.port;

import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.Order;

public interface OrderTransaction {
    void persist(Order order, CommerceEvent event);
}
