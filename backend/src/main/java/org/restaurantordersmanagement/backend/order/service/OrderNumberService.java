package org.restaurantordersmanagement.backend.order.service;

import org.restaurantordersmanagement.backend.order.model.OrderNumberCounter;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderNumberService {

    /**
     * The two numbers an order gets when it is submitted: the internal one, unique for ever, and the displayed one,
     * which restarts when an admin resets the series.
     */
    public record AssignedNumbers(int orderNumber, int displayNumber) {
    }

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
     * Both numbers come from the same locked row, and a reset of the displayed
     * series takes the same lock (see OrderNumberResetService), so it can
     * never interleave with an assignment.
     */
    @Transactional
    public AssignedNumbers assignNextOrderNumber() {
        OrderNumberCounter counter = counterRepository.lockById(OrderNumberCounter.ROW_ID).orElseThrow();
        int orderNumber = counter.getNextValue();
        int displayNumber = counter.getNextDisplayValue();
        counter.setNextValue(orderNumber + 1);
        counter.setNextDisplayValue(displayNumber + 1);
        return new AssignedNumbers(orderNumber, displayNumber);
    }

}
