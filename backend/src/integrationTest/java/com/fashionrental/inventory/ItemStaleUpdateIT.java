package com.fashionrental.inventory;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.StaleUpdateException;
import com.fashionrental.inventory.model.request.UpdateItemRequest;
import com.fashionrental.support.IntegrationFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves optimistic locking on items (#189) through the real HTTP layer and a real database:
 * the response carries the post-update version, consecutive saves that echo it succeed, and a
 * save made against an old version is rejected with a 409.
 */
@AutoConfigureMockMvc
class ItemStaleUpdateIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ItemRepository itemRepository;

    @AfterEach
    void cleanUp() {
        itemRepository.deleteAll();
    }

    @Test
    void should_accept_consecutive_saves_that_echo_the_returned_version_and_reject_an_old_version() throws Exception {
        Item item = IntegrationFixtures.item(itemRepository, "Royal Sherwani", 3);

        update(item, "Renamed Sherwani", 3, 0L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        update(item, "Renamed Sherwani", 4, 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.quantity").value(4));

        update(item, "Overwritten By Stale Tab", 3, 0L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(StaleUpdateException.MESSAGE));
    }

    private ResultActions update(Item item, String name, int quantity, long version) throws Exception {
        UpdateItemRequest request = new UpdateItemRequest(
                name, Item.Category.COSTUME, null, null, 200, 500, quantity, null, null, null, null, version);
        return mockMvc.perform(put("/api/items/{id}", item.getId())
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
