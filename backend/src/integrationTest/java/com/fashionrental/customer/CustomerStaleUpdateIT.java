package com.fashionrental.customer;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.StaleUpdateException;
import com.fashionrental.customer.model.request.UpdateCustomerRequest;
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
 * Proves optimistic locking on customers (#189) through the real HTTP layer and a real database.
 */
@AutoConfigureMockMvc
class CustomerStaleUpdateIT extends AbstractIntegrationTest {

    private static final String PHONE = "9877700011";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;

    @AfterEach
    void cleanUp() {
        customerRepository.deleteAll();
    }

    @Test
    void should_accept_consecutive_saves_that_echo_the_returned_version_and_reject_an_old_version() throws Exception {
        Customer customer = IntegrationFixtures.customer(customerRepository, "Ravi Kumar", PHONE);

        update(customer, "123 Main St", 0L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        update(customer, "456 New St", 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.address").value("456 New St"));

        update(customer, "789 Stale St", 0L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(StaleUpdateException.MESSAGE));
    }

    private ResultActions update(Customer customer, String address, long version) throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Ravi Kumar", PHONE, address, Customer.CustomerType.MISC, null, version);
        return mockMvc.perform(put("/api/customers/{id}", customer.getId())
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
