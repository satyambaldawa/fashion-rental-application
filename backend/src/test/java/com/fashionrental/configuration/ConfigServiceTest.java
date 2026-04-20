package com.fashionrental.configuration;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.model.LateFeeRuleItem;
import com.fashionrental.configuration.model.LateFeeRuleResponse;
import com.fashionrental.configuration.model.UpdateLateFeeRulesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private LateFeeRuleRepository lateFeeRuleRepository;

    @InjectMocks
    private ConfigService configService;

    private LateFeeRule ruleWithHours(int fromHours, Integer toHours, BigDecimal multiplier, int sortOrder) {
        LateFeeRule rule = new LateFeeRule();
        rule.setDurationFromHours(fromHours);
        rule.setDurationToHours(toHours);
        rule.setPenaltyMultiplier(multiplier);
        rule.setSortOrder(sortOrder);
        rule.setIsActive(true);
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());
        return rule;
    }

    private List<LateFeeRule> defaultRules() {
        return List.of(
            ruleWithHours(0,  3,    new BigDecimal("0.50"), 1),
            ruleWithHours(3,  6,    new BigDecimal("0.75"), 2),
            ruleWithHours(6,  24,   new BigDecimal("1.00"), 3),
            ruleWithHours(24, 48,   new BigDecimal("1.50"), 4),
            ruleWithHours(48, null, new BigDecimal("2.00"), 5)
        );
    }

    @Test
    void should_return_five_default_rules_ordered_by_sort_order() {
        when(lateFeeRuleRepository.findAllByOrderBySortOrderAsc()).thenReturn(defaultRules());

        List<LateFeeRuleResponse> result = configService.getLateFeeRules();

        assertThat(result).hasSize(5);
        assertThat(result.get(0).sortOrder()).isEqualTo(1);
        assertThat(result.get(4).sortOrder()).isEqualTo(5);
    }

    @Test
    void should_compute_correct_labels_for_default_rules() {
        when(lateFeeRuleRepository.findAllByOrderBySortOrderAsc()).thenReturn(defaultRules());

        List<LateFeeRuleResponse> result = configService.getLateFeeRules();

        assertThat(result.get(0).label()).isEqualTo("0 hrs–3 hrs");
        assertThat(result.get(1).label()).isEqualTo("3 hrs–6 hrs");
        assertThat(result.get(2).label()).isEqualTo("6 hrs–1 day");
        assertThat(result.get(3).label()).isEqualTo("1 day–2 days");
        assertThat(result.get(4).label()).isEqualTo("2 days+");
    }

    @Test
    void should_save_updated_multiplier_for_existing_rule() {
        UUID existingId = UUID.randomUUID();
        LateFeeRule existing = ruleWithHours(0, 3, new BigDecimal("0.50"), 1);

        when(lateFeeRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lateFeeRuleRepository.findById(existingId)).thenReturn(Optional.of(existing));

        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(existingId, 0, 3, new BigDecimal("0.75"), 1, true)
        ));

        List<LateFeeRuleResponse> result = configService.updateLateFeeRules(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).penaltyMultiplier()).isEqualByComparingTo("0.75");
        verify(lateFeeRuleRepository).deactivateAll();
        verify(lateFeeRuleRepository).save(existing);
    }

    @Test
    void should_throw_when_all_rules_are_removed() {
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(null, 0, 3, new BigDecimal("0.50"), 1, false)
        ));

        assertThatThrownBy(() -> configService.updateLateFeeRules(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("At least one late fee rule must be active.");
    }

    @Test
    void should_throw_when_ranges_overlap() {
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(null, 0, 6,  new BigDecimal("0.50"), 1, true),
            new LateFeeRuleItem(null, 3, 12, new BigDecimal("0.75"), 2, true)
        ));

        assertThatThrownBy(() -> configService.updateLateFeeRules(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("ranges must not overlap");
    }

    @Test
    void should_allow_contiguous_non_overlapping_ranges() {
        when(lateFeeRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(null, 0,  6,    new BigDecimal("0.50"), 1, true),
            new LateFeeRuleItem(null, 6,  24,   new BigDecimal("1.00"), 2, true),
            new LateFeeRuleItem(null, 24, null, new BigDecimal("2.00"), 3, true)
        ));

        List<LateFeeRuleResponse> result = configService.updateLateFeeRules(request);

        assertThat(result).hasSize(3);
    }
}
