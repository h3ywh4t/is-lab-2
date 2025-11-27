package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.HumanBeingDTO;
import org.example.entity.WeaponType;
import org.example.service.HumanBeingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/human-beings")
@CrossOrigin(origins = "*")
public class HumanBeingController {

    @Autowired
    private HumanBeingService humanBeingService;

    @Autowired
    private WebSocketNotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<HumanBeingDTO>> getAllHumanBeings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String filterBy,
            @RequestParam(required = false) String filterValue) {

        Sort sort = Sort.unsorted();
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction direction = sortDir != null && sortDir.equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            sort = Sort.by(direction, sortBy);
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<HumanBeingDTO> result = humanBeingService.getAllHumanBeings(pageable, filterBy, filterValue);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HumanBeingDTO> getHumanBeingById(@PathVariable Long id) {
        try {
            HumanBeingDTO dto = humanBeingService.getHumanBeingById(id);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/cars")
    public ResponseEntity<List<org.example.entity.Car>> getAllCars() {
        return ResponseEntity.ok(humanBeingService.getAllCars());
    }

    @GetMapping("/coordinates")
    public ResponseEntity<List<org.example.entity.Coordinates>> getAllCoordinates() {
        return ResponseEntity.ok(humanBeingService.getAllCoordinates());
    }
}