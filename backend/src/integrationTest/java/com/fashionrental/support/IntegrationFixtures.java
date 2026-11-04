package com.fashionrental.support;

import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponent;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Shared builders for the concurrency integration tests. Those tests run service calls on
 * worker threads, so every helper that touches the security context is explicit about it:
 * SecurityContextHolder is thread-local and is never inherited from the test's main thread.
 */
public final class IntegrationFixtures {

    public static final OffsetDateTime RENTAL_START = OffsetDateTime.parse("2026-05-01T10:00:00+05:30");
    public static final OffsetDateTime RENTAL_END = OffsetDateTime.parse("2026-05-03T10:00:00+05:30");

    private IntegrationFixtures() {}

    public static Customer customer(CustomerRepository repository, String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        return repository.save(customer);
    }

    public static Item item(ItemRepository repository, String name, int quantity) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(200);
        item.setDeposit(500);
        item.setQuantity(quantity);
        item.setIsActive(true);
        return repository.save(item);
    }

    public static Item packageOf(ItemRepository itemRepository, PackageComponentRepository componentRepository,
                                 String name, int quantity, Item component, int componentQuantity) {
        Item pkg = new Item();
        pkg.setName(name);
        pkg.setCategory(Item.Category.COSTUME);
        pkg.setItemType(Item.ItemType.PACKAGE);
        pkg.setRate(500);
        pkg.setDeposit(1000);
        pkg.setQuantity(quantity);
        pkg.setIsActive(true);
        pkg = itemRepository.save(pkg);

        PackageComponent link = new PackageComponent();
        link.setPackageItem(pkg);
        link.setComponentItem(component);
        link.setQuantity(componentQuantity);
        componentRepository.save(link);
        return pkg;
    }

    public static CheckoutRequest cashCheckout(UUID customerId, UUID itemId, int quantity, UUID idempotencyKey) {
        return new CheckoutRequest(
                customerId, RENTAL_START, RENTAL_END,
                List.of(new CheckoutLineItem(itemId, quantity)),
                List.of(), null, null, null, null, idempotencyKey);
    }

    public static <T> T asOwner(Callable<T> action) throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            return action.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
