package com.fashionrental.customer;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.customer.model.request.CreateCustomerRequest;
import com.fashionrental.customer.model.request.UpdateCustomerRequest;
import com.fashionrental.customer.model.response.CustomerDetailResponse;
import com.fashionrental.customer.model.response.CustomerResponse;
import com.fashionrental.customer.model.response.CustomerSummaryResponse;
import com.fashionrental.reporting.CustomerHistoryData;
import com.fashionrental.reporting.CustomerHistoryService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerHistoryService customerHistoryService;

    public CustomerService(CustomerRepository customerRepository, CustomerHistoryService customerHistoryService) {
        this.customerRepository = customerRepository;
        this.customerHistoryService = customerHistoryService;
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        if (customerRepository.existsByPhone(request.phone())) {
            throw new ConflictException("A customer with phone " + request.phone() + " already exists");
        }

        validateOrganizationName(request.customerType(), request.organizationName());

        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setCustomerType(request.customerType());
        customer.setOrganizationName(resolveOrganizationName(request.customerType(), request.organizationName()));

        Customer saved = customerRepository.save(customer);
        return toResponse(saved);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));

        customerRepository.findByPhone(request.phone()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("A customer with phone " + request.phone() + " already exists");
            }
        });

        validateOrganizationName(request.customerType(), request.organizationName());

        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setCustomerType(request.customerType());
        customer.setOrganizationName(resolveOrganizationName(request.customerType(), request.organizationName()));
        // Forces the entity dirty even when no other field changed, so @PreUpdate
        // (which sets the authoritative timestamp) always fires on save.
        customer.setUpdatedAt(OffsetDateTime.now());

        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerSummaryResponse> searchCustomers(String phone, String name) {
        boolean phoneBlank = phone == null || phone.isBlank();
        boolean nameBlank = name == null || name.isBlank();

        if (phoneBlank && nameBlank) {
            return List.of();
        }

        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("isActive")));
            if (!phoneBlank) {
                predicates.add(cb.like(root.get("phone"), phone + "%"));
            }
            if (!nameBlank) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return customerRepository.findAll(spec, PageRequest.of(0, 20))
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));

        if (!customer.getIsActive()) {
            throw new ResourceNotFoundException("Customer not found: " + id);
        }

        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerHistory(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));

        if (!customer.getIsActive()) {
            throw new ResourceNotFoundException("Customer not found: " + id);
        }

        CustomerHistoryData history = customerHistoryService.getHistory(id);

        return new CustomerDetailResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getCustomerType(),
                customer.getOrganizationName(),
                history.outstandingDeposit(),
                history.receipts()
        );
    }

    private void validateOrganizationName(Customer.CustomerType customerType, String organizationName) {
        if (customerType == Customer.CustomerType.STUDENT && (organizationName == null || organizationName.isBlank())) {
            throw new ValidationException("School name is required for student customers");
        }
        if (customerType == Customer.CustomerType.PROFESSIONAL && (organizationName == null || organizationName.isBlank())) {
            throw new ValidationException("Organization name is required for professional customers");
        }
    }

    private String resolveOrganizationName(Customer.CustomerType customerType, String organizationName) {
        return customerType == Customer.CustomerType.MISC ? null : organizationName;
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getCustomerType(),
                customer.getOrganizationName(),
                customer.getIsActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private CustomerSummaryResponse toSummaryResponse(Customer customer) {
        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getCustomerType(),
                customer.getOrganizationName(),
                0
        );
    }
}
