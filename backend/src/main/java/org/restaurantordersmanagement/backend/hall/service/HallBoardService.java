package org.restaurantordersmanagement.backend.hall.service;

import java.util.List;
import org.restaurantordersmanagement.backend.hall.web.HallBoardResponse;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dining-area board's view of orders: PREPARING and READY order numbers.
 * A submitted order the kitchen has not started yet, and anything served or
 * cancelled, is not on the board - so a number leaves it the moment it is served.
 */
@Service
public class HallBoardService {

    private final OrderRepository orderRepository;

    public HallBoardService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public HallBoardResponse getBoard() {
        return new HallBoardResponse(numbersWith(OrderStatus.PREPARING), numbersWith(OrderStatus.READY));
    }

    private List<Integer> numbersWith(OrderStatus status) {
        return orderRepository.findByStatusOrderByOrderNumberAsc(status).stream()
                .map(OrderRepository.OrderNumberOnly::getOrderNumber)
                .toList();
    }

}
