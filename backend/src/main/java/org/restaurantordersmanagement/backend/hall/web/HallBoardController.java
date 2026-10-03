package org.restaurantordersmanagement.backend.hall.web;

import org.restaurantordersmanagement.backend.hall.service.HallBoardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, no @PreAuthorize - the board is a wall screen in the dining area
 * with no login, and SecurityConfig's anyRequest().permitAll() already allows
 * it. That is only safe because the response is order and table numbers and nothing else.
 */
@RestController
@RequestMapping("/api/hall/orders")
public class HallBoardController {

    private final HallBoardService hallBoardService;

    public HallBoardController(HallBoardService hallBoardService) {
        this.hallBoardService = hallBoardService;
    }

    @GetMapping
    public HallBoardResponse getBoard() {
        return hallBoardService.getBoard();
    }

}
