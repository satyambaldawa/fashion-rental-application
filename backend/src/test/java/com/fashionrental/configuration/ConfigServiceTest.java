package com.fashionrental.configuration;

import com.fashionrental.common.exception.StaleUpdateException;
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

import static com.fashionrental.support.TestData.withVersion;
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

    private LateFeeRule persistedRule(UUID id, Long version, boolean active) {
        LateFeeRule rule = ruleWithHours(0, 3, new BigDecimal("0.50"), 1);
        rule.setIsActive(active);
        setField(rule, "id", id);
        return withVersion(rule, version);
    }

    private void setField(LateFeeRule rule, String name, Object value) {
        try {
            java.lang.reflect.Field field = LateFeeRule.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(rule, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(defaultRules());

        List<LateFeeRuleResponse> result = configService.getLateFeeRules();

        assertThat(result).hasSize(5);
        assertThat(result.get(0).sortOrder()).isEqualTo(1);
        assertThat(result.get(4).sortOrder()).isEqualTo(5);
    }

    @Test
    void should_exclude_deactivated_rules_from_get() {
        LateFeeRule active = ruleWithHours(0, 3, new BigDecimal("0.50"), 1);
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(active));

        List<LateFeeRuleResponse> result = configService.getLateFeeRules();

        assertThat(result).hasSize(1);
    }

    @Test
    void should_compute_correct_labels_for_default_rules() {
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(defaultRules());

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
        LateFeeRule existing = persistedRule(existingId, 0L, true);

        when(lateFeeRuleRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lateFeeRuleRepository.findAllForUpdate()).thenReturn(List.of(existing));

        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(existingId, 0, 3, new BigDecimal("0.75"), 1, true, 0L)
        ));

        List<LateFeeRuleResponse> result = configService.updateLateFeeRules(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).penaltyMultiplier()).isEqualByComparingTo("0.75");
        assertThat(existing.getIsActive()).isTrue();
        verify(lateFeeRuleRepository).saveAllAndFlush(List.of(existing));
    }

    @Test
    void should_deactivate_a_current_rule_that_is_left_out_of_the_request() {
        UUID keptId = UUID.randomUUID();
        UUID droppedId = UUID.randomUUID();
        LateFeeRule kept = persistedRule(keptId, 0L, true);
        LateFeeRule dropped = persistedRule(droppedId, 0L, true);
        when(lateFeeRuleRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lateFeeRuleRepository.findAllForUpdate()).thenReturn(List.of(kept, dropped));

        configService.updateLateFeeRules(new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(keptId, 0, 3, new BigDecimal("0.50"), 1, true, 0L)
        )));

        assertThat(kept.getIsActive()).isTrue();
        assertThat(dropped.getIsActive()).isFalse();
    }

    @Test
    void should_reject_a_rule_resent_with_an_old_version_after_someone_else_deactivated_it() {
        UUID ruleId = UUID.randomUUID();
        LateFeeRule deactivatedByOthers = persistedRule(ruleId, 5L, false);
        when(lateFeeRuleRepository.findAllForUpdate()).thenReturn(List.of(deactivatedByOthers));

        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(ruleId, 0, 3, new BigDecimal("0.50"), 1, true, 4L)
        ));

        assertThatThrownBy(() -> configService.updateLateFeeRules(request))
            .isInstanceOf(StaleUpdateException.class);
        assertThat(deactivatedByOthers.getIsActive()).isFalse();
    }

    @Test
    void should_reject_a_request_id_that_is_not_in_the_current_rule_set() {
        when(lateFeeRuleRepository.findAllForUpdate()).thenReturn(List.of());

        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(UUID.randomUUID(), 0, 3, new BigDecimal("0.50"), 1, true, 0L)
        ));

        assertThatThrownBy(() -> configService.updateLateFeeRules(request))
            .isInstanceOf(StaleUpdateException.class);
        verify(lateFeeRuleRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void should_accept_a_second_save_that_uses_the_versions_returned_by_the_first() {
        UUID ruleId = UUID.randomUUID();
        LateFeeRule rule = persistedRule(ruleId, 0L, true);
        when(lateFeeRuleRepository.findAllForUpdate()).thenReturn(List.of(rule));
        when(lateFeeRuleRepository.saveAllAndFlush(any())).thenAnswer(inv -> {
            List<LateFeeRule> saved = inv.getArgument(0);
            saved.forEach(saving -> withVersion(saving, saving.getVersion() + 1));
            return saved;
        });

        List<LateFeeRuleResponse> first = configService.updateLateFeeRules(new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(ruleId, 0, 3, new BigDecimal("0.60"), 1, true, 0L)
        )));
        List<LateFeeRuleResponse> second = configService.updateLateFeeRules(new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(ruleId, 0, 3, new BigDecimal("0.70"), 1, true, first.get(0).version())
        )));

        assertThat(first.get(0).version()).isEqualTo(1L);
        assertThat(second.get(0).version()).isEqualTo(2L);
        assertThat(second.get(0).penaltyMultiplier()).isEqualByComparingTo("0.70");
    }

    @Test
    void should_throw_when_all_rules_are_removed() {
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(null, 0, 3, new BigDecimal("0.50"), 1, false, null)
        ));

        assertThatThrownBy(() -> configService.updateLateFeeRules(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("At least one late fee rule must be active.");
    }

    @Test
    void should_throw_when_ranges_overlap() {
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(null, 0, 6,  new BigDecimal("0.50"), 1, true, null),
            new LateFeeRuleItem(null, 3, 12, new BigDecimal("0.75"), 2, true, null)
        ));

        assertThatThrownBy(() -> configService.updateLateFeeRules(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("ranges must not overlap");
    }

    @Test
    void should_allow_contiguous_non_overlapping_ranges() {
        when(lateFeeRuleRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lateFeeRuleRepository.findAllForUpdate()).thenReturn(List.of());

        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(
            new LateFeeRuleItem(null, 0,  6,    new BigDecimal("0.50"), 1, true, null),
            new LateFeeRuleItem(null, 6,  24,   new BigDecimal("1.00"), 2, true, null),
            new LateFeeRuleItem(null, 24, null, new BigDecimal("2.00"), 3, true, null)
        ));

        List<LateFeeRuleResponse> result = configService.updateLateFeeRules(request);

        assertThat(result).hasSize(3);
    }
}
