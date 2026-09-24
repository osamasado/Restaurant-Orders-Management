package org.restaurantordersmanagement.backend.guest.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Not @Transactional - see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableRepository tableRepository;

    private final List<Long> createdTableIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdTableIds.forEach(tableRepository::deleteById);
    }

    private Table createPairedTable(String code) {
        Table table = new Table();
        table.setTableNumber("D-" + UUID.randomUUID());
        table.setRoom("Main room");
        table.setSeats(2);
        table.setPairedDeviceId(code);
        table = tableRepository.saveAndFlush(table);
        createdTableIds.add(table.getId());
        return table;
    }

    private String claimBody(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    @Test
    void claimReturnsTheTableAndMarksTheDeviceSeen() throws Exception {
        Table table = createPairedTable("QX7K2M");

        mockMvc.perform(post("/api/guest/device/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody("QX7K2M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(table.getId()))
                .andExpect(jsonPath("$.tableNumber").value(table.getTableNumber()))
                .andExpect(jsonPath("$.room").value("Main room"))
                .andExpect(jsonPath("$.pairedDeviceId").doesNotExist());

        assertNotNull(tableRepository.findById(table.getId()).orElseThrow().getLastSeenAt());
    }

    @Test
    void claimIgnoresCaseAndSurroundingWhitespace() throws Exception {
        Table table = createPairedTable("HW4RT9");

        mockMvc.perform(post("/api/guest/device/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody("  hw4rt9 ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(table.getId()));
    }

    @Test
    void unknownCodeIsNotFound() throws Exception {
        mockMvc.perform(post("/api/guest/device/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody("NOPE00")))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankCodeIsNotFound() throws Exception {
        mockMvc.perform(post("/api/guest/device/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(" ")))
                .andExpect(status().isNotFound());
    }

}
