package co.in.thunderingherd.service;


import co.in.thunderingherd.model.Product;
import co.in.thunderingherd.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing demo data...");

        List<Product> products = new ArrayList<>();

        products.add(createProduct("DEMO-001", "Premium Headphones", "High-quality wireless headphones", "299.99", 100));
        products.add(createProduct("DEMO-002", "Gaming Keyboard", "Mechanical keyboard with RGB", "149.99", 50));
        products.add(createProduct("DEMO-003", "4K Monitor", "32-inch 4K display", "599.99", 25));
        products.add(createProduct("POPULAR-001", "iPhone 15", "Latest iPhone model", "999.99", 500));

        productRepository.saveAll(products);

        log.info("Initialized {} demo products", products.size());
    }

    private Product createProduct(String sku, String name, String description, String price, int inventory) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setInventory(inventory);
        return product;
    }
}