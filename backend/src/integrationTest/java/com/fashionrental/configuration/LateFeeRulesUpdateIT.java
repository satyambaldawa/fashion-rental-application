package com.fashionrental.configuration;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.StaleUpdateException;
import com.fashionrental.configuration.model.LateFeeRuleItem;
import com.fashionrental.configuration.model.UpdateLateFeeRulesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves optimistic locking and entity-level deactivation of late fee rules (#189) against a
 * real database. Works on the seeded rules and re-activates them afterwards, because other
 * integration tests price late fees from the seeded tiers.
 */
@AutoConfigureMockMvc
class LateFeeRulesUpdateIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LateFeeRuleRepository lateFeeRuleRepository;

    @AfterEach
    void restoreSeededRulesToActive() {
        List<LateFeeRule> rules = lateFeeRuleRepository.findAll();
        rules.forEach(rule -> rule.setIsActive(true));
        lateFeeRuleRepository.saveAll(rules);
    }

    @Test
    void should_accept_consecutive_saves_that_echo_the_returned_versions_and_reject_old_versions() throws Exception {
        List<LateFeeRule> beforeFirstSave = currentRules();

        save(requestFor(beforeFirstSave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(beforeFirstSave.get(0).getVersion() + 1));
        List<LateFeeRule> afterFirstSave = currentRules();

        save(requestFor(afterFirstSave)).andExpect(status().isOk());

        save(requestFor(beforeFirstSave))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(StaleUpdateException.MESSAGE));
    }

    @Test
    void should_deactivate_a_rule_left_out_of_the_request_and_reactivate_it_when_sent_again_with_its_current_version()
            throws Exception {
        List<LateFeeRule> all = currentRules();
        LateFeeRule openEndedTier = all.get(all.size() - 1);

        save(requestFor(all.subList(0, all.size() - 1))).andExpect(status().isOk());

        assertThat(isActive(openEndedTier)).isFalse();
        assertThat(all.subList(0, all.size() - 1)).allSatisfy(rule -> assertThat(isActive(rule)).isTrue());

        save(requestFor(currentRules())).andExpect(status().isOk());

        assertThat(isActive(openEndedTier)).isTrue();
    }

    @Test
    void should_reject_re_sending_a_rule_that_someone_else_deactivated_with_its_old_version() throws Exception {
        List<LateFeeRule> all = currentRules();
        LateFeeRule openEndedTier = all.get(all.size() - 1);
        save(requestFor(all.subList(0, all.size() - 1))).andExpect(status().isOk());

        save(requestFor(all))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(StaleUpdateException.MESSAGE));

        assertThat(isActive(openEndedTier)).isFalse();
    }

    private List<LateFeeRule> currentRules() {
        return lateFeeRuleRepository.findAll().stream()
                .sorted(Comparator.comparingInt(LateFeeRule::getSortOrder))
                .toList();
    }

    private boolean isActive(LateFeeRule rule) {
        return lateFeeRuleRepository.findById(rule.getId()).orElseThrow().getIsActive();
    }

    private UpdateLateFeeRulesRequest requestFor(List<LateFeeRule> rules) {
        return new UpdateLateFeeRulesRequest(rules.stream()
                .map(rule -> new LateFeeRuleItem(
                        rule.getId(), rule.getDurationFromHours(), rule.getDurationToHours(),
                        rule.getPenaltyMultiplier(), rule.getSortOrder(), true, rule.getVersion()))
                .toList());
    }

    private ResultActions save(UpdateLateFeeRulesRequest request) throws Exception {
        return mockMvc.perform(put("/api/config/late-fee-rules")
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
