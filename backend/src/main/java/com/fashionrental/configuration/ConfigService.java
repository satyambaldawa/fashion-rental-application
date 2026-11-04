package com.fashionrental.configuration;

import com.fashionrental.common.exception.StaleUpdateException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.model.LateFeeRuleItem;
import com.fashionrental.configuration.model.LateFeeRuleResponse;
import com.fashionrental.configuration.model.UpdateLateFeeRulesRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConfigService {

    private final LateFeeRuleRepository lateFeeRuleRepository;

    public ConfigService(LateFeeRuleRepository lateFeeRuleRepository) {
        this.lateFeeRuleRepository = lateFeeRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<LateFeeRuleResponse> getLateFeeRules() {
        return lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<LateFeeRuleResponse> updateLateFeeRules(UpdateLateFeeRulesRequest request) {
        validateRules(request.rules());

        List<LateFeeRule> current = lateFeeRuleRepository.findAllForUpdate();
        Map<UUID, LateFeeRule> currentById = current.stream()
                .collect(Collectors.toMap(LateFeeRule::getId, Function.identity()));
        requireNoStaleRules(request.rules(), currentById);

        // Entity-level deactivation (not a bulk UPDATE) so @Version and the persistence context
        // stay consistent with the rewrite below.
        current.forEach(rule -> rule.setIsActive(false));

        List<LateFeeRule> rules = request.rules().stream().map(item -> {
            LateFeeRule rule = item.id() != null ? currentById.get(item.id()) : new LateFeeRule();

            rule.setDurationFromHours(item.durationFromHours());
            rule.setDurationToHours(item.durationToHours());
            rule.setPenaltyMultiplier(item.penaltyMultiplier());
            rule.setSortOrder(item.sortOrder());
            rule.setIsActive(item.isActive());
            rule.setUpdatedAt(OffsetDateTime.now());
            if (rule.getCreatedAt() == null) rule.setCreatedAt(OffsetDateTime.now());

            return rule;
        }).toList();

        List<LateFeeRule> saved = lateFeeRuleRepository.saveAllAndFlush(rules);

        return saved.stream()
                .sorted(Comparator.comparingInt(LateFeeRule::getSortOrder))
                .map(this::toResponse)
                .toList();
    }

    private void requireNoStaleRules(List<LateFeeRuleItem> items, Map<UUID, LateFeeRule> currentById) {
        for (LateFeeRuleItem item : items) {
            if (item.id() == null) {
                continue;
            }
            LateFeeRule existing = currentById.get(item.id());
            if (existing == null || !item.version().equals(existing.getVersion())) {
                throw new StaleUpdateException();
            }
        }
    }

    private void validateRules(List<LateFeeRuleItem> rules) {
        long activeCount = rules.stream().filter(LateFeeRuleItem::isActive).count();
        if (activeCount == 0) {
            throw new ValidationException("At least one late fee rule must be active.");
        }

        List<LateFeeRuleItem> active = rules.stream()
                .filter(LateFeeRuleItem::isActive)
                .sorted(Comparator.comparingInt(LateFeeRuleItem::durationFromHours))
                .toList();

        for (int i = 0; i < active.size() - 1; i++) {
            LateFeeRuleItem curr = active.get(i);
            LateFeeRuleItem next = active.get(i + 1);
            if (curr.durationToHours() == null || curr.durationToHours() > next.durationFromHours()) {
                throw new ValidationException("Late fee rule ranges must not overlap.");
            }
        }
    }

    private LateFeeRuleResponse toResponse(LateFeeRule rule) {
        return new LateFeeRuleResponse(
                rule.getId(),
                rule.getDurationFromHours(),
                rule.getDurationToHours(),
                rule.getPenaltyMultiplier(),
                rule.getSortOrder(),
                rule.getIsActive(),
                computeLabel(rule.getDurationFromHours(), rule.getDurationToHours()),
                rule.getVersion()
        );
    }

    private String computeLabel(int fromHours, Integer toHours) {
        String from = formatHours(fromHours);
        if (toHours == null) {
            return from + "+";
        }
        return from + "–" + formatHours(toHours);
    }

    private String formatHours(int hours) {
        if (hours == 0) return "0 hrs";
        if (hours < 24) return hours + " hrs";
        int days = hours / 24;
        return days + (days == 1 ? " day" : " days");
    }
}
