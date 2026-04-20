package com.fashionrental.configuration;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.model.LateFeeRuleItem;
import com.fashionrental.configuration.model.LateFeeRuleResponse;
import com.fashionrental.configuration.model.UpdateLateFeeRulesRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class ConfigService {

    private final LateFeeRuleRepository lateFeeRuleRepository;

    public ConfigService(LateFeeRuleRepository lateFeeRuleRepository) {
        this.lateFeeRuleRepository = lateFeeRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<LateFeeRuleResponse> getLateFeeRules() {
        return lateFeeRuleRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<LateFeeRuleResponse> updateLateFeeRules(UpdateLateFeeRulesRequest request) {
        validateRules(request.rules());

        lateFeeRuleRepository.deactivateAll();

        List<LateFeeRule> saved = request.rules().stream().map(item -> {
            LateFeeRule rule = item.id() != null
                    ? lateFeeRuleRepository.findById(item.id()).orElse(new LateFeeRule())
                    : new LateFeeRule();

            rule.setDurationFromHours(item.durationFromHours());
            rule.setDurationToHours(item.durationToHours());
            rule.setPenaltyMultiplier(item.penaltyMultiplier());
            rule.setSortOrder(item.sortOrder());
            rule.setIsActive(item.isActive());
            rule.setUpdatedAt(OffsetDateTime.now());
            if (rule.getCreatedAt() == null) rule.setCreatedAt(OffsetDateTime.now());

            return lateFeeRuleRepository.save(rule);
        }).toList();

        return saved.stream()
                .sorted(Comparator.comparingInt(LateFeeRule::getSortOrder))
                .map(this::toResponse)
                .toList();
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
                computeLabel(rule.getDurationFromHours(), rule.getDurationToHours())
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
