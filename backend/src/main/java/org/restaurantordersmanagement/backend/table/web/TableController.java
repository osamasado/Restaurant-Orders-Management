package org.restaurantordersmanagement.backend.table.web;

import java.util.List;
import org.restaurantordersmanagement.backend.table.service.TableService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<TableResponse> list() {
        return tableService.findAll().stream().map(TableResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse create(@RequestBody TableRequest request) {
        validate(request);
        try {
            return TableResponse.from(tableService.create(request));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table number already exists");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse update(@PathVariable Long id, @RequestBody TableRequest request) {
        validate(request);
        try {
            return TableResponse.from(tableService.update(id, request));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table number already exists");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            tableService.delete(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table still has orders");
        }
    }

    @PostMapping("/{id}/pair")
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse pair(@PathVariable Long id) {
        return TableResponse.from(tableService.pair(id));
    }

    @DeleteMapping("/{id}/pair")
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse unpair(@PathVariable Long id) {
        return TableResponse.from(tableService.unpair(id));
    }

    private void validate(TableRequest request) {
        if (request.tableNumber() == null || request.tableNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tableNumber is required");
        }
        if (request.room() == null || request.room().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "room is required");
        }
        if (request.seats() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seats must be positive");
        }
    }

}
