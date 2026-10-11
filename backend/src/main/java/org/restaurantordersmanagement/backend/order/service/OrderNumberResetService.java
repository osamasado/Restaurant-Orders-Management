package org.restaurantordersmanagement.backend.order.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.restaurantordersmanagement.backend.order.model.OrderNumberCounter;
import org.restaurantordersmanagement.backend.order.model.OrderNumberReset;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberCounterRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberResetRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.web.OrderNumberStatusResponse;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Restarts the displayed order number at 001, when an admin chooses (for example at the start of a day). Only the
 * displayed number restarts: the internal order number is never reset, so its unique index and every existing order
 * stay as they are.
 *
 * <p>The reset takes the same row lock as the assignment of a number (see {@link OrderNumberService}), so it can never
 * interleave with a submission: a submission in flight finishes first (and its order then counts as open and refuses
 * the reset), or the reset finishes first and the submission gets 001. It is refused while any order is submitted,
 * in preparation or ready, so two open orders can never show the same number on the hall board or in the kitchen.
 * Served and cancelled orders do not block it.
 */
@Service
public class OrderNumberResetService {

    /** The orders that are still being worked on. Drafts have no number yet, served and cancelled ones are done. */
    static final Set<OrderStatus> OPEN = EnumSet.of(OrderStatus.SUBMITTED, OrderStatus.PREPARING, OrderStatus.READY);

    private final OrderNumberCounterRepository counterRepository;
    private final OrderNumberResetRepository resetRepository;
    private final OrderRepository orderRepository;
    private final StaffAccountRepository staffAccountRepository;

    public OrderNumberResetService(
            OrderNumberCounterRepository counterRepository,
            OrderNumberResetRepository resetRepository,
            OrderRepository orderRepository,
            StaffAccountRepository staffAccountRepository) {
        this.counterRepository = counterRepository;
        this.resetRepository = resetRepository;
        this.orderRepository = orderRepository;
        this.staffAccountRepository = staffAccountRepository;
    }

    @Transactional(readOnly = true)
    public OrderNumberStatusResponse status() {
        OrderNumberCounter counter = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow();
        return statusOf(counter);
    }

    /**
     * @return the state after the reset. When no number has been handed out since the last reset (the next one is
     *         already 1) there is nothing to restart: nothing changes and nothing is recorded.
     * @throws ResponseStatusException 409 while any order is submitted, in preparation or ready
     */
    @Transactional
    public OrderNumberStatusResponse reset(Long staffAccountId) {
        OrderNumberCounter counter = counterRepository.lockById(OrderNumberCounter.ROW_ID).orElseThrow();

        long open = orderRepository.countByStatusIn(OPEN);
        if (open > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The order number cannot be reset while " + open + " order(s) are open");
        }

        if (counter.getNextDisplayValue() > 1) {
            OrderNumberReset reset = new OrderNumberReset();
            reset.setResetAt(Instant.now());
            reset.setResetBy(staffAccountRepository.getReferenceById(staffAccountId));
            reset.setPreviousNextDisplay(counter.getNextDisplayValue());
            resetRepository.save(reset);
            counter.setNextDisplayValue(1);
        }
        return statusOf(counter);
    }

    private OrderNumberStatusResponse statusOf(OrderNumberCounter counter) {
        OrderNumberStatusResponse.LastReset last = resetRepository.findFirstByOrderByResetAtDescIdDesc()
                .map(reset -> new OrderNumberStatusResponse.LastReset(reset.getResetAt(), reset.getResetBy().getName()))
                .orElse(null);
        return new OrderNumberStatusResponse(
                counter.getNextDisplayValue(), orderRepository.countByStatusIn(OPEN), last);
    }

}
