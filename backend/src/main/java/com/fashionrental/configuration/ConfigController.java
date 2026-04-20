package com.fashionrental.configuration;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.configuration.model.LateFeeRuleResponse;
import com.fashionrental.configuration.model.UpdateLateFeeRulesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Configuration", description = "Late fee rule configuration")
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary = "Get all late fee rules")
    @GetMapping("/late-fee-rules")
    public ResponseEntity<ApiResponse<List<LateFeeRuleResponse>>> getLateFeeRules() {
        return ResponseEntity.ok(ApiResponse.ok(configService.getLateFeeRules()));
    }

    @Operation(summary = "Replace all late fee rules")
    @PutMapping("/late-fee-rules")
    public ResponseEntity<ApiResponse<List<LateFeeRuleResponse>>> updateLateFeeRules(
            @Valid @RequestBody UpdateLateFeeRulesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(configService.updateLateFeeRules(request)));
    }
}
