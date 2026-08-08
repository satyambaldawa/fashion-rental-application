package com.fashionrental.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.customer.model.request.CreateCustomerRequest;
import com.fashionrental.customer.model.request.UpdateCustomerRequest;
import com.fashionrental.customer.model.response.CustomerDetailResponse;
import com.fashionrental.customer.model.response.CustomerReceiptItemResponse;
import com.fashionrental.customer.model.response.CustomerReceiptResponse;
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

    private CustomerDetailResponse sampleDetailResponse(UUID id) {
        CustomerReceiptResponse receipt = new CustomerReceiptResponse(
                UUID.randomUUID(), "R-20260418-003", "GIVEN",
                OffsetDateTime.parse("2026-04-18T10:00:00+05:30"),
                OffsetDateTime.parse("2026-04-20T10:00:00+05:30"),
                400, 1500, 1900,
                List.of(new CustomerReceiptItemResponse("Blue Sherwani", 2)),
                null
        );
        return new CustomerDetailResponse(
                id, "Ravi Kumar", "9876543210", "123 Main St",
                Customer.CustomerType.MISC, null,
                1500, List.of(receipt)
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

    @Test
    @WithMockUser
    void should_return_200_when_customer_updated_successfully() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Ravi Kumar", "9876543210", "456 New St", Customer.CustomerType.MISC, null
        );
        when(customerService.updateCustomer(any(UUID.class), any(UpdateCustomerRequest.class)))
                .thenReturn(sampleCustomerResponse(id));

        mockMvc.perform(put("/api/customers/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ravi Kumar"));
    }

    @Test
    @WithMockUser
    void should_return_404_when_updating_missing_customer() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Ravi Kumar", "9876543210", null, Customer.CustomerType.MISC, null
        );
        when(customerService.updateCustomer(any(UUID.class), any(UpdateCustomerRequest.class)))
                .thenThrow(new ResourceNotFoundException("Customer not found: " + id));

        mockMvc.perform(put("/api/customers/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_409_when_updated_phone_belongs_to_another_customer() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Ravi Kumar", "8765432109", null, Customer.CustomerType.MISC, null
        );
        when(customerService.updateCustomer(any(UUID.class), any(UpdateCustomerRequest.class)))
                .thenThrow(new ConflictException("A customer with phone 8765432109 already exists"));

        mockMvc.perform(put("/api/customers/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_updated_phone_format_invalid() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Ravi Kumar", "12345", null, Customer.CustomerType.MISC, null
        );

        mockMvc.perform(put("/api/customers/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_updated_professional_missing_org_name() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Ravi Kumar", "9876543210", null, Customer.CustomerType.PROFESSIONAL, null
        );
        when(customerService.updateCustomer(any(UUID.class), any(UpdateCustomerRequest.class)))
                .thenThrow(new ValidationException("Organization name is required for professional customers"));

        mockMvc.perform(put("/api/customers/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_200_with_customer_details_on_get_by_id() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.getCustomer(id)).thenReturn(sampleCustomerResponse(id));

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$.data.phone").value("9876543210"));
    }

    @Test
    @WithMockUser
    void should_return_404_when_getting_missing_customer_by_id() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.getCustomer(id))
                .thenThrow(new ResourceNotFoundException("Customer not found: " + id));

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_200_with_customer_detail_and_outstanding_deposit() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.getCustomerHistory(id)).thenReturn(sampleDetailResponse(id));

        mockMvc.perform(get("/api/customers/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$.data.outstandingDeposit").value(1500))
                .andExpect(jsonPath("$.data.receipts", hasSize(1)))
                .andExpect(jsonPath("$.data.receipts[0].receiptNumber").value("R-20260418-003"))
                .andExpect(jsonPath("$.data.receipts[0].status").value("GIVEN"))
                .andExpect(jsonPath("$.data.receipts[0].items", hasSize(1)))
                .andExpect(jsonPath("$.data.receipts[0].items[0].itemName").value("Blue Sherwani"))
                .andExpect(jsonPath("$.data.receipts[0].invoice").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser
    void should_return_404_when_getting_history_for_missing_customer() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.getCustomerHistory(id))
                .thenThrow(new ResourceNotFoundException("Customer not found: " + id));

        mockMvc.perform(get("/api/customers/{id}/history", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
