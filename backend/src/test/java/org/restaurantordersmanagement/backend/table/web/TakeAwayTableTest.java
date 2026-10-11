package org.restaurantordersmanagement.backend.table.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reserved take-away table (migration V17): it exists once, and the admin's Tables & devices can neither see nor
 * change it. Rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TakeAwayTableTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStateMachineService stateMachine;

    private StaffAccount admin;
    private Table takeAway;

    @BeforeEach
    void setUp() {
        admin = new StaffAccount();
        admin.setName("Table Admin " + UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        admin = staffAccountRepository.saveAndFlush(admin);
        takeAway = tableRepository.findByTakeAwayTrue().orElseThrow();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin() {
        return user(new StaffPrincipal(admin));
    }

    @Test
    void thereIsExactlyOneTakeAwayTableAndNoOtherTableIsOne() {
        assertEquals(1, tableRepository.findAll().stream().filter(Table::isTakeAway).count());
        assertTrue(takeAway.getPairedDeviceId() == null && takeAway.getSeats() == 0);
    }

    @Test
    void aSecondTakeAwayTableIsImpossible() {
        Table second = new Table();
        second.setTableNumber("another-" + UUID.randomUUID());
        second.setRoom("Anywhere");
        second.setSeats(1);
        second.setTakeAway(true);

        assertThrows(DataIntegrityViolationException.class, () -> tableRepository.saveAndFlush(second));
    }

    @Test
    void theTablesListOfTheAdminDoesNotShowIt() throws Exception {
        mockMvc.perform(get("/api/tables").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + takeAway.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.tableNumber == '__TAKE_AWAY__')]").isEmpty());
    }

    @Test
    void itCannotBeEditedPairedUnpairedOrDeleted() throws Exception {
        long id = takeAway.getId();
        String body = "{\"tableNumber\":\"Renamed\",\"room\":\"Room\",\"seats\":4}";

        mockMvc.perform(put("/api/tables/" + id).with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tables/" + id + "/pair").with(asAdmin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tables/" + id + "/pair").with(asAdmin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tables/" + id).with(asAdmin())).andExpect(status().isNotFound());

        Table reloaded = tableRepository.findById(id).orElseThrow();
        assertEquals("__TAKE_AWAY__", reloaded.getTableNumber());
        assertEquals(null, reloaded.getPairedDeviceId());
        assertTrue(reloaded.isTakeAway());
    }

    @Test
    void aRealTableCannotTakeItsReservedNumber() throws Exception {
        String body = "{\"tableNumber\":\"__TAKE_AWAY__\",\"room\":\"Room\",\"seats\":4}";

        mockMvc.perform(post("/api/tables").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void noPairingCodeEverClaimsIt() throws Exception {
        // Even a code written into its row by hand (it cannot be done through the API) opens no guest device.
        takeAway.setPairedDeviceId("TAK3AW");
        tableRepository.saveAndFlush(takeAway);

        mockMvc.perform(post("/api/guest/device/claim").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"TAK3AW\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theHallBoardMarksATakeAwayOrder() throws Exception {
        Order order = new Order();
        order.setTable(takeAway);
        order = orderRepository.saveAndFlush(order);
        order = stateMachine.transition(order, OrderStatus.SUBMITTED, null);
        stateMachine.transition(order, OrderStatus.PREPARING, admin);

        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preparing[?(@.orderNumber == " + order.getDisplayNumber() + " && @.takeAway == true)]").isNotEmpty());
    }

}
