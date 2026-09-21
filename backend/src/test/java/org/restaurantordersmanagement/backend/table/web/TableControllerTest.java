package org.restaurantordersmanagement.backend.table.web;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Not @Transactional - see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private OrderRepository orderRepository;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long orderId : createdOrderIds) {
            orderRepository.findById(orderId).ifPresent(orderRepository::delete);
        }
        for (Long tableId : createdTableIds) {
            tableRepository.findById(tableId).ifPresent(tableRepository::delete);
        }
    }

    private String tableRequestJson(String tableNumber) {
        return """
                {"tableNumber": "%s", "room": "Front room", "seats": 4}
                """.formatted(tableNumber);
    }

    @Test
    void listRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/tables").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRejectsBlankTableNumber() throws Exception {
        mockMvc.perform(post("/api/tables")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableNumber": "", "room": "Front room", "seats": 4}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createListUpdateAndDeleteRoundTrip() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tables")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableRequestJson("T-100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value("T-100"))
                .andExpect(jsonPath("$.deviceStatus").value("UNPAIRED"))
                .andReturn();

        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
        createdTableIds.add(id);

        mockMvc.perform(get("/api/tables").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(put("/api/tables/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableNumber": "T-100", "room": "Garden room", "seats": 6}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room").value("Garden room"))
                .andExpect(jsonPath("$.seats").value(6));

        mockMvc.perform(delete("/api/tables/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertTrue(tableRepository.findById(id).isEmpty());
    }

    @Test
    void createRejectsDuplicateTableNumber() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tables")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableRequestJson("T-200")))
                .andExpect(status().isOk())
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
        createdTableIds.add(id);

        mockMvc.perform(post("/api/tables")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableRequestJson("T-200")))
                .andExpect(status().isConflict());
    }

    @Test
    void pairAndUnpairTable() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tables")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableRequestJson("T-300")))
                .andExpect(status().isOk())
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
        createdTableIds.add(id);

        MvcResult pairResult = mockMvc.perform(post("/api/tables/" + id + "/pair").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceStatus").value("OFFLINE"))
                .andReturn();
        String pairingCode = JsonPath.read(pairResult.getResponse().getContentAsString(), "$.pairedDeviceId");
        assertNotEquals("", pairingCode);

        mockMvc.perform(delete("/api/tables/" + id + "/pair").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceStatus").value("UNPAIRED"))
                .andExpect(jsonPath("$.pairedDeviceId").doesNotExist());
    }

    @Test
    void deletingATableStillReferencedByAnOrderIsRejected() throws Exception {
        Table table = new Table();
        table.setTableNumber("T-400");
        table.setRoom("Front room");
        table.setSeats(2);
        Table savedTable = tableRepository.saveAndFlush(table);
        createdTableIds.add(savedTable.getId());

        Order order = new Order();
        order.setTable(savedTable);
        Long orderId = orderRepository.saveAndFlush(order).getId();
        createdOrderIds.add(orderId);

        mockMvc.perform(delete("/api/tables/" + savedTable.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());

        assertTrue(tableRepository.findById(savedTable.getId()).isPresent());
    }

}
