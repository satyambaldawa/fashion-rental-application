package com.fashionrental.customer;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.customer.model.request.CreateCustomerRequest;
import com.fashionrental.customer.model.response.CustomerResponse;
import com.fashionrental.customer.model.response.CustomerSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer buildActiveCustomer(String name, String phone, Customer.CustomerType type) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(type);
        customer.setIsActive(true);
        setTimestamps(customer);
        return customer;
    }

    private void setTimestamps(Customer customer) {
        try {
            java.lang.reflect.Field createdAt = Customer.class.getDeclaredField("createdAt");
            java.lang.reflect.Field updatedAt = Customer.class.getDeclaredField("updatedAt");
            createdAt.setAccessible(true);
            updatedAt.setAccessible(true);
            createdAt.set(customer, OffsetDateTime.now());
            updatedAt.set(customer, OffsetDateTime.now());
        } catch (Exception ignored) {}
    }

    @Test
    void should_create_customer_successfully() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ravi Kumar", "9876543210", "123 Main St", Customer.CustomerType.MISC, null
        );
        when(customerRepository.existsByPhone("9876543210")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            setTimestamps(c);
            return c;
        });

        CustomerResponse result = customerService.createCustomer(request);

        assertThat(result.name()).isEqualTo("Ravi Kumar");
        assertThat(result.phone()).isEqualTo("9876543210");
        assertThat(result.customerType()).isEqualTo(Customer.CustomerType.MISC);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void should_throw_conflict_when_phone_already_exists() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ravi Kumar", "9876543210", null, Customer.CustomerType.MISC, null
        );
        when(customerRepository.existsByPhone("9876543210")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void should_throw_validation_when_student_has_no_school_name() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Priya Sharma", "8765432109", null, Customer.CustomerType.STUDENT, null
        );
        when(customerRepository.existsByPhone("8765432109")).thenReturn(false);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("School name is required");
    }

    @Test
    void should_throw_validation_when_professional_has_no_org_name() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Amit Joshi", "7654321098", null, Customer.CustomerType.PROFESSIONAL, "  "
        );
        when(customerRepository.existsByPhone("7654321098")).thenReturn(false);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Organization name is required");
    }

    @Test
    void should_allow_misc_without_org_name() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Suresh Patel", "6543210987", null, Customer.CustomerType.MISC, null
        );
        when(customerRepository.existsByPhone("6543210987")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            setTimestamps(c);
            return c;
        });

        CustomerResponse result = customerService.createCustomer(request);

        assertThat(result.customerType()).isEqualTo(Customer.CustomerType.MISC);
        assertThat(result.organizationName()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_empty_list_when_no_search_params() {
        List<CustomerSummaryResponse> result = customerService.searchCustomers(null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_empty_list_when_search_params_are_blank() {
        List<CustomerSummaryResponse> result = customerService.searchCustomers("  ", "");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_matching_customers_when_searching_by_phone() {
        Customer customer = buildActiveCustomer("Ravi Kumar", "9876543210", Customer.CustomerType.MISC);
        when(customerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        List<CustomerSummaryResponse> result = customerService.searchCustomers("987", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).phone()).isEqualTo("9876543210");
        assertThat(result.get(0).activeRentalsCount()).isZero();
    }

    @Test
    void should_throw_not_found_when_customer_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_throw_not_found_when_customer_is_inactive() {
        UUID id = UUID.randomUUID();
        Customer inactive = buildActiveCustomer("Old Customer", "9999999999", Customer.CustomerType.MISC);
        inactive.setIsActive(false);
        when(customerRepository.findById(id)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> customerService.getCustomer(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
