package com.fashionrental.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.customer.model.request.CreateCustomerRequest;
import com.fashionrental.customer.model.response.CustomerResponse;
import com.fashionrental.customer.model.response.CustomerSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    // Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest
    @MockitoBean
    private com.fashionrental.config.JwtConfig jwtConfig;
    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private CustomerResponse sampleCustomerResponse(UUID id) {
        return new CustomerResponse(
                id, "Ravi Kumar", "9876543210", "123 Main St",
                Customer.CustomerType.MISC, null, true,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    private CustomerSummaryResponse sampleSummaryResponse(UUID id) {
        return new CustomerSummaryResponse(
                id, "Ravi Kumar", "9876543210", "123 Main St",
                Customer.CustomerType.MISC, null, 0
        );
    }

    @Test
    @WithMockUser
    void should_return_201_when_customer_created_successfully() throws Exception {
        UUID id = UUID.randomUUID();
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ravi Kumar", "9876543210", "123 Main St", Customer.CustomerType.MISC, null
        );
        when(customerService.createCustomer(any(CreateCustomerRequest.class))).thenReturn(sampleCustomerResponse(id));

        mockMvc.perform(post("/api/customers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$.data.phone").value("9876543210"));
    }

    @Test
    @WithMockUser
    void should_return_409_when_phone_already_exists() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ravi Kumar", "9876543210", null, Customer.CustomerType.MISC, null
        );
        when(customerService.createCustomer(any(CreateCustomerRequest.class)))
                .thenThrow(new ConflictException("A customer with phone 9876543210 already exists"));

        mockMvc.perform(post("/api/customers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_phone_format_invalid() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ravi Kumar", "12345", null, Customer.CustomerType.MISC, null
        );

        mockMvc.perform(post("/api/customers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_name_is_blank() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "", "9876543210", null, Customer.CustomerType.MISC, null
        );

        mockMvc.perform(post("/api/customers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_student_type_missing_org_name() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Priya Sharma", "8765432109", null, Customer.CustomerType.STUDENT, null
        );
        when(customerService.createCustomer(any(CreateCustomerRequest.class)))
                .thenThrow(new ValidationException("School name is required for student customers"));

        mockMvc.perform(post("/api/customers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_200_with_search_results() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.searchCustomers("987", null)).thenReturn(List.of(sampleSummaryResponse(id)));

        mockMvc.perform(get("/api/customers").param("phone", "987"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Ravi Kumar"));
    }

    @Test
    @WithMockUser
    void should_return_empty_list_when_search_params_missing() throws Exception {
        when(customerService.searchCustomers(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void should_return_401_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }
}
