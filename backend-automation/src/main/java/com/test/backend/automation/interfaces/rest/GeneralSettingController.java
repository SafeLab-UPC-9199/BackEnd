package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.infrastructure.persistence.jpa.repositories.GeneralSettingRepository;
import com.test.backend.automation.interfaces.rest.resources.GeneralSettingResource;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * General settings catalog shared by every tenant.
 *
 * <p>QAS-05: reads are open to any authenticated caller, mutations are
 * Administrator only (the same rule the monolith enforced through
 * {@code CurrentWorkspaceService.isCurrentUserAdmin()}; here the gateway's
 * verified roles arrive via {@link RequestIdentity}).</p>
 */
@Tag(name = "General Settings", description = "General Settings management API")
@RestController
@RequestMapping("/api/v1/general-settings")
public class GeneralSettingController {

    private final GeneralSettingRepository generalSettingRepository;

    public GeneralSettingController(GeneralSettingRepository generalSettingRepository) {
        this.generalSettingRepository = generalSettingRepository;
    }

    @GetMapping
    @Cacheable("generalSettings")
    @Operation(summary = "Get all general settings")
    public ResponseEntity<List<GeneralSettingResource>> getAllSettings() {
        var settings = generalSettingRepository.findAll();
        var resources = settings.stream()
                .map(s -> new GeneralSettingResource(
                        s.getId(),
                        s.getSettingKey(),
                        s.getValue(),
                        s.getCategory(),
                        s.getDescription()
                ))
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PutMapping("/{id}")
    @CacheEvict(cacheNames = "generalSettings", allEntries = true)
    @Operation(summary = "Update general setting")
    public ResponseEntity<GeneralSettingResource> updateSetting(@PathVariable Long id, @RequestBody GeneralSettingResource resource) {
        // QAS-05: global settings are shared by every tenant - mutations are Administrator only.
        if (!RequestIdentity.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var settingOpt = generalSettingRepository.findById(id);
        if (settingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var setting = settingOpt.get();
        if (resource.value() != null) {
            setting.setValue(resource.value());
        }
        if (resource.description() != null) {
            setting.setDescription(resource.description());
        }
        generalSettingRepository.save(setting);
        return ResponseEntity.ok(new GeneralSettingResource(
                setting.getId(),
                setting.getSettingKey(),
                setting.getValue(),
                setting.getCategory(),
                setting.getDescription()
        ));
    }
}
