package com.fashionrental.invoice;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.receipt.CheckoutService;
import com.fashionrental.receipt.ReceiptGivenService;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.IntegrationFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves return idempotency (#189) through the real HTTP layer: a retried return with the same
 * Idempotency-Key replays the first invoice, while a different key on an already-returned
 * receipt is still rejected.
 *
 * <p>Deliberately NOT @Transactional: each request needs its own real transaction.
 */
@AutoConfigureMockMvc
class IdempotentReturnIT extends AbstractIntegrationTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CheckoutService checkoutService;
    @Autowired private ReceiptGivenService receiptGivenService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    @AfterEach
    void cleanUp() {
        invoiceRepository.deleteAll();
        receiptRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_replay_the_first_invoice_when_a_return_is_retried_with_the_same_key() throws Exception {
        ReceiptResponse receipt = givenReceipt("9866600011");
        UUID key = UUID.randomUUID();

        String firstInvoiceId = returnWithKey(receipt, key);
        String replayedInvoiceId = returnWithKey(receipt, key);

        assertThat(replayedInvoiceId).isEqualTo(firstInvoiceId);
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void should_reject_a_different_key_on_an_already_returned_receipt_as_already_returned() throws Exception {
        ReceiptResponse receipt = givenReceipt("9866600022");
        returnWithKey(receipt, UUID.randomUUID());

        mockMvc.perform(post("/api/receipts/{id}/return", receipt.id())
                        .with(user("owner").roles("OWNER"))
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(receipt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("already been returned")));
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    private String returnWithKey(ReceiptResponse receipt, UUID key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/receipts/{id}/return", receipt.id())
                        .with(user("owner").roles("OWNER"))
                        .header(IDEMPOTENCY_KEY_HEADER, key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(receipt)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String returnBody(ReceiptResponse receipt) throws Exception {
        return objectMapper.writeValueAsString(new ProcessReturnRequest(
                IntegrationFixtures.RENTAL_END,
                List.of(new ReturnLineItem(receipt.lineItems().get(0).id(), false, null, null)),
                "CASH", null, null, null));
    }

    private ReceiptResponse givenReceipt(String phone) throws Exception {
        Customer customer = IntegrationFixtures.customer(customerRepository, "Renter " + phone, phone);
        Item item = IntegrationFixtures.item(itemRepository, "Gown " + phone, 1);
        return IntegrationFixtures.asOwner(() -> {
            ReceiptResponse created = checkoutService.createReceipt(
                    IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, null));
            receiptGivenService.markGiven(created.id());
            return created;
        });
    }
}
