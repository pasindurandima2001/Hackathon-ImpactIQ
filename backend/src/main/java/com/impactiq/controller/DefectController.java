package com.impactiq.controller;

import com.impactiq.dto.DefectRequest;
import com.impactiq.dto.DefectResponse;
import com.impactiq.service.DefectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    @PostMapping
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody DefectRequest request) {
        DefectResponse created = defectService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<DefectResponse> findAll() {
        return defectService.findAll();
    }

    @GetMapping("/{id}")
    public DefectResponse findById(@PathVariable Long id) {
        return defectService.findById(id);
    }

    @PutMapping("/{id}")
    public DefectResponse update(@PathVariable Long id, @Valid @RequestBody DefectRequest request) {
        return defectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        defectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
