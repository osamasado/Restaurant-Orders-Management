package org.restaurantordersmanagement.backend.order.service;

import org.restaurantordersmanagement.backend.order.model.OrderNumberCounter;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderNumberService {

    private final OrderNumberCounterRepository counterRepository;

    public OrderNumberService(OrderNumberCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * Locks the single counter row (SELECT ... FOR UPDATE, see
     * {@link OrderNumberCounterRepository#lockById}) before reading and
     * incrementing it, so concurrent callers serialize on that row instead of
     * racing an in-memory counter - the row lock is what guarantees no two
     * callers ever receive the same number, even under simultaneous commits.
     */
    @Transactional
    public int assignNextOrderNumber() {
        OrderNumberCounter counter = counterRepository.lockById(1L).orElseThrow();
        int assigned = counter.getNextValue();
        counter.setNextValue(assigned + 1);
        return assigned;
    }

}
