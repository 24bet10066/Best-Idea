package com.partlinq.core.config;

import com.partlinq.core.model.entity.*;
import com.partlinq.core.model.enums.*;
import com.partlinq.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Data seeder for PartLinQ platform.
 * Runs on application startup in dev profile and seeds realistic demo data.
 * Creates parts shops, technicians, spare parts, inventory, and trust relationships.
 */
@Component
@Profile("dev")
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final TechnicianRepository technicianRepository;
    private final PartsShopRepository partsShopRepository;
    private final SparePartRepository sparePartRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TrustEndorsementRepository trustEndorsementRepository;
    private final CustomerFeedbackRepository customerFeedbackRepository;
    private final TrustEventRepository trustEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentLedgerRepository paymentLedgerRepository;

    @Override
    public void run(String... args) throws Exception {
        if (technicianRepository.count() > 0) {
            log.info("Database already seeded, skipping data initialization");
            return;
        }

        log.info("Starting PartLinQ data seeding...");

        List<PartsShop> shops = seedShops();
        List<Technician> technicians = seedTechnicians();
        List<SparePart> parts = seedSpareParts();
        seedInventory(shops, parts);
        seedTrustEndorsements(technicians);
        seedCustomerFeedbacks(technicians);
        seedOrdersAndUdhaar(shops, technicians, parts);

        log.info("Data seeding completed successfully!");
    }

    private List<PartsShop> seedShops() {
        log.info("Seeding parts shops...");

        List<PartsShop> shops = new ArrayList<>();

        // Raj Spare Parts - Lucknow (Founder's shop)
        PartsShop shop1 = PartsShop.builder()
            .shopName("Raj Spare Parts")
            .ownerName("Rajesh Kumar Sharma")
            .phone("+91-9876543210")
            .email("raj.sparepartsl@gmail.com")
            .address("123 Hazratganj, Lucknow")
            .city("Lucknow")
            .pincode("226001")
            .gstNumber("09AABCT1234E1Z0")
            .isVerified(true)
            .rating(4.8)
            .totalOrdersServed(250)
            .build();

        // Krishna Electronics & Parts - Kanpur
        PartsShop shop2 = PartsShop.builder()
            .shopName("Krishna Electronics & Parts")
            .ownerName("Krishna Verma")
            .phone("+91-9123456789")
            .email("krishna.electronics@gmail.com")
            .address("456 Nana Rao Park, Kanpur")
            .city("Kanpur")
            .pincode("208001")
            .gstNumber("09AABCU5678E1Z1")
            .isVerified(true)
            .rating(4.5)
            .totalOrdersServed(180)
            .build();

        // Sharma Appliance Center - Varanasi
        PartsShop shop3 = PartsShop.builder()
            .shopName("Sharma Appliance Center")
            .ownerName("Amit Sharma")
            .phone("+91-8765432109")
            .email("sharma.appliances@gmail.com")
            .address("789 Assi Ghat Road, Varanasi")
            .city("Varanasi")
            .pincode("221001")
            .gstNumber("09AABCV9012E1Z2")
            .isVerified(false)
            .rating(3.9)
            .totalOrdersServed(120)
            .build();

        shops.add(partsShopRepository.save(shop1));
        shops.add(partsShopRepository.save(shop2));
        shops.add(partsShopRepository.save(shop3));

        log.info("Seeded {} parts shops", shops.size());
        return shops;
    }

    private List<Technician> seedTechnicians() {
        log.info("Seeding technicians...");

        List<Technician> technicians = new ArrayList<>();
        String[] firstNames = {"Rajesh", "Amit", "Vikram", "Deepak", "Sanjay", "Pradeep", "Rohit", "Arun", "Manoj", "Suresh", "Harish", "Anand", "Vinod", "Pankaj", "Nitin"};
        String[] lastNames = {"Sharma", "Verma", "Singh", "Patel", "Gupta", "Kumar", "Rao", "Reddy", "Mishra", "Saxena"};

        int[] cities = {0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2};
        String[] cityNames = {"Lucknow", "Kanpur", "Varanasi"};
        String[] pincodes = {"226001", "208001", "221001"};

        ApplianceType[][] specializations = {
            {ApplianceType.AC},
            {ApplianceType.AC, ApplianceType.REFRIGERATOR},
            {ApplianceType.WASHING_MACHINE},
            {ApplianceType.GEYSER, ApplianceType.WATER_PURIFIER},
            {ApplianceType.AC, ApplianceType.REFRIGERATOR, ApplianceType.WASHING_MACHINE},
            {ApplianceType.WATER_PURIFIER},
            {ApplianceType.AC, ApplianceType.DISHWASHER},
            {ApplianceType.WASHING_MACHINE, ApplianceType.GEYSER},
            {ApplianceType.REFRIGERATOR, ApplianceType.MICROWAVE},
            {ApplianceType.AC, ApplianceType.REFRIGERATOR, ApplianceType.GEYSER, ApplianceType.WATER_PURIFIER},
            {ApplianceType.CHIMNEY},
            {ApplianceType.AC, ApplianceType.TELEVISION},
            {ApplianceType.WASHING_MACHINE, ApplianceType.DISHWASHER},
            {ApplianceType.WATER_PURIFIER, ApplianceType.GEYSER},
            {ApplianceType.AC, ApplianceType.REFRIGERATOR, ApplianceType.WASHING_MACHINE, ApplianceType.WATER_PURIFIER}
        };

        double[] trustScores = {75.5, 45.2, 82.0, 38.5, 91.3, 52.0, 68.4, 41.0, 78.9, 85.5, 48.3, 62.7, 70.1, 39.5, 88.0};
        int[] creditLimits = {25000, 8000, 45000, 5000, 50000, 15000, 30000, 10000, 35000, 48000, 12000, 22000, 28000, 6000, 50000};

        for (int i = 0; i < 15; i++) {
            String fullName = firstNames[i] + " " + lastNames[i % lastNames.length];
            String email = fullName.toLowerCase().replace(" ", ".") + "@technician.partlinq.com";
            String phone = "+91-" + (9000000000L + i * 100);

            Technician tech = Technician.builder()
                .fullName(fullName)
                .phone(String.valueOf(phone))
                .email(email)
                .city(cityNames[cities[i]])
                .pincode(pincodes[cities[i]])
                .specializations(new HashSet<>(Arrays.asList(specializations[i])))
                .trustScore(trustScores[i])
                .creditLimit(BigDecimal.valueOf(creditLimits[i]))
                .totalTransactions(10 + (i * 3) % 50)
                .avgPaymentDays(12.5 + (i % 5) * 2)
                .isVerified(i % 3 != 0)
                .referredById(i > 0 && i % 4 == 0 ? technicians.get(i / 4).getId() : null)
                .build();

            technicians.add(technicianRepository.save(tech));
        }

        log.info("Seeded {} technicians", technicians.size());
        return technicians;
    }

    private List<SparePart> seedSpareParts() {
        log.info("Seeding spare parts...");

        List<SparePart> parts = new ArrayList<>();

        // AC Parts
        parts.add(createPart("AC-COMP-VOL-001", "Voltas AC Compressor", "Heavy-duty compressor for Voltas AC units", PartCategory.COMPRESSOR, ApplianceType.AC, "Voltas", "5 Ton, 3 Phase", 8500, true));
        parts.add(createPart("AC-COMP-BS-002", "Blue Star AC Compressor", "Efficient compressor for Blue Star ACs", PartCategory.COMPRESSOR, ApplianceType.AC, "Blue Star", "1.5 Ton, 1 Phase", 5200, true));
        parts.add(createPart("AC-COMP-DAI-003", "Daikin AC Compressor", "Premium Daikin rotary compressor", PartCategory.COMPRESSOR, ApplianceType.AC, "Daikin", "2 Ton", 9800, true));
        parts.add(createPart("AC-PCB-001", "AC Control PCB Board", "Main control board for AC units", PartCategory.PCB_BOARD, ApplianceType.AC, "Generic", "Universal", 1850, false));
        parts.add(createPart("AC-FAN-001", "AC Fan Motor", "Cooling fan motor for AC condenser", PartCategory.MOTOR, ApplianceType.AC, "Generic", "0.5 HP", 1200, true));
        parts.add(createPart("AC-CAP-001", "AC Run Capacitor", "Run capacitor for AC compressor", PartCategory.CAPACITOR, ApplianceType.AC, "Generic", "25uF 450V", 280, true));
        parts.add(createPart("AC-THERMO-001", "AC Thermostat", "Temperature sensing thermostat", PartCategory.THERMOSTAT, ApplianceType.AC, "Generic", "18-32 Celsius", 450, true));
        parts.add(createPart("AC-FILTER-001", "AC Air Filter", "Dust filter for AC indoor unit", PartCategory.FILTER, ApplianceType.AC, "Generic", "24x12 inches", 320, false));

        // Refrigerator Parts
        parts.add(createPart("REF-COMP-001", "Refrigerator Compressor", "Hermetic compressor for refrigerators", PartCategory.COMPRESSOR, ApplianceType.REFRIGERATOR, "Embraco", "0.5 HP", 4500, true));
        parts.add(createPart("REF-THERMO-001", "Refrigerator Thermostat", "Temperature control thermostat", PartCategory.THERMOSTAT, ApplianceType.REFRIGERATOR, "Generic", "-10 to 10C", 380, true));
        parts.add(createPart("REF-GASKET-001", "Door Gasket Seal", "Rubber seal for refrigerator door", PartCategory.GASKET, ApplianceType.REFRIGERATOR, "Generic", "Universal", 550, false));
        parts.add(createPart("REF-FAN-001", "Evaporator Fan Motor", "Fan motor for evaporator coil", PartCategory.MOTOR, ApplianceType.REFRIGERATOR, "Generic", "220V", 890, true));
        parts.add(createPart("REF-TIMER-001", "Defrost Timer", "Automatic defrost timer mechanism", PartCategory.TIMER, ApplianceType.REFRIGERATOR, "Generic", "6-8 hours", 620, true));
        parts.add(createPart("REF-RELAY-001", "Refrigerator Relay", "Compressor start relay", PartCategory.RELAY, ApplianceType.REFRIGERATOR, "Generic", "Universal", 420, true));

        // Washing Machine Parts
        parts.add(createPart("WM-MOTOR-001", "Washing Machine Motor", "Main drive motor for washing machine", PartCategory.MOTOR, ApplianceType.WASHING_MACHINE, "Generic", "0.75 HP, 220V", 1950, true));
        parts.add(createPart("WM-PUMP-001", "Drain Pump", "Water drain pump assembly", PartCategory.PUMP, ApplianceType.WASHING_MACHINE, "Generic", "220V AC", 650, true));
        parts.add(createPart("WM-BELT-001", "Drive Belt", "Rubber drive belt for drum rotation", PartCategory.BELT, ApplianceType.WASHING_MACHINE, "Generic", "Universal", 420, false));
        parts.add(createPart("WM-LOCK-001", "Door Lock Solenoid", "Electronic door lock mechanism", PartCategory.DOOR_LOCK, ApplianceType.WASHING_MACHINE, "Generic", "220V", 780, true));
        parts.add(createPart("WM-PCB-001", "Washing Machine PCB", "Main control circuit board", PartCategory.PCB_BOARD, ApplianceType.WASHING_MACHINE, "Generic", "Universal", 2100, false));
        parts.add(createPart("WM-SENSOR-001", "Water Level Sensor", "Pressure switch water level sensor", PartCategory.SENSOR, ApplianceType.WASHING_MACHINE, "Generic", "Universal", 340, true));
        parts.add(createPart("WM-HOSE-001", "Inlet Hose Assembly", "Water inlet hose with filter", PartCategory.HOSE, ApplianceType.WASHING_MACHINE, "Generic", "Universal", 290, false));

        // Water Purifier Parts
        parts.add(createPart("WP-FILTER-RO", "RO Membrane Filter", "High-capacity RO membrane filter", PartCategory.FILTER, ApplianceType.WATER_PURIFIER, "Generic", "50 GPD", 950, true));
        parts.add(createPart("WP-FILTER-SED", "Sediment Filter", "Sediment pre-filter for RO purifiers", PartCategory.FILTER, ApplianceType.WATER_PURIFIER, "Generic", "1 Micron", 320, true));
        parts.add(createPart("WP-FILTER-CARB", "Carbon Filter Cartridge", "Activated carbon filter cartridge", PartCategory.FILTER, ApplianceType.WATER_PURIFIER, "Generic", "Universal", 280, false));
        parts.add(createPart("WP-PUMP-001", "RO Booster Pump", "High-pressure booster pump for RO", PartCategory.PUMP, ApplianceType.WATER_PURIFIER, "Generic", "36V DC", 1850, true));
        parts.add(createPart("WP-VALVE-001", "Solenoid Valve", "Water inlet solenoid valve", PartCategory.VALVE, ApplianceType.WATER_PURIFIER, "Generic", "220V", 520, true));

        // Geyser Parts
        parts.add(createPart("GEY-HEATER-001", "Heating Element", "Electric heating element for geyser", PartCategory.HEATING_ELEMENT, ApplianceType.GEYSER, "Generic", "1500W, 3000W", 620, true));
        parts.add(createPart("GEY-THERMO-001", "Geyser Thermostat", "Temperature control thermostat", PartCategory.THERMOSTAT, ApplianceType.GEYSER, "Generic", "30-70C", 480, true));
        parts.add(createPart("GEY-GASKET-001", "Tank Gasket Seal", "Rubber gasket for tank connection", PartCategory.GASKET, ApplianceType.GEYSER, "Generic", "1 inch", 380, false));
        parts.add(createPart("GEY-VALVE-001", "Safety Relief Valve", "Pressure relief valve for geyser", PartCategory.VALVE, ApplianceType.GEYSER, "Generic", "6 kg/cm2", 750, true));

        // Dishwasher Parts
        parts.add(createPart("DW-MOTOR-001", "Dishwasher Spray Motor", "Spray arm motor for dishwasher", PartCategory.MOTOR, ApplianceType.DISHWASHER, "Generic", "220V", 1450, true));
        parts.add(createPart("DW-PUMP-001", "Dishwasher Pump", "Water circulation pump", PartCategory.PUMP, ApplianceType.DISHWASHER, "Generic", "220V", 980, true));

        // Chimney Parts
        parts.add(createPart("CH-MOTOR-001", "Chimney Exhaust Motor", "High-speed exhaust fan motor", PartCategory.MOTOR, ApplianceType.CHIMNEY, "Generic", "220V, 300 RPM", 1650, true));
        parts.add(createPart("CH-FILTER-001", "Chimney Baffle Filter", "Aluminum baffle filter for chimney", PartCategory.FILTER, ApplianceType.CHIMNEY, "Generic", "60x30 cm", 890, false));

        // Additional universal parts
        parts.add(createPart("UNI-BEARING-001", "Ball Bearing", "General purpose ball bearing", PartCategory.BEARING, ApplianceType.OTHER, "Generic", "6204", 210, true));
        parts.add(createPart("UNI-SWITCH-001", "Push Button Switch", "Electrical push button switch", PartCategory.SWITCH, ApplianceType.OTHER, "Generic", "Universal", 150, false));

        log.info("Seeded {} spare parts", parts.size());
        return parts;
    }

    private SparePart createPart(String partNumber, String name, String description, PartCategory category, ApplianceType applianceType, String brand, String modelCompatibility, int mrp, boolean isOem) {
        SparePart part = SparePart.builder()
            .partNumber(partNumber)
            .name(name)
            .description(description)
            .category(category)
            .applianceType(applianceType)
            .brand(brand)
            .modelCompatibility(modelCompatibility)
            .mrp(BigDecimal.valueOf(mrp))
            .isOem(isOem)
            .build();
        return sparePartRepository.save(part);
    }

    private void seedInventory(List<PartsShop> shops, List<SparePart> parts) {
        log.info("Seeding inventory...");

        Random rand = new Random(42); // Deterministic seed
        int inventoryCount = 0;

        for (PartsShop shop : shops) {
            // Each shop carries 25-35 parts
            int partsToCarry = 25 + rand.nextInt(11);
            List<SparePart> shopParts = new ArrayList<>(parts);
            Collections.shuffle(shopParts, rand);
            shopParts = shopParts.subList(0, Math.min(partsToCarry, shopParts.size()));

            for (SparePart part : shopParts) {
                int quantity = 5 + rand.nextInt(96);
                int minStock = 3 + rand.nextInt(8);
                BigDecimal sellingPrice = part.getMrp().multiply(BigDecimal.valueOf(0.85 + rand.nextDouble() * 0.10));

                InventoryItem item = InventoryItem.builder()
                    .sparePart(part)
                    .shop(shop)
                    .quantity(quantity)
                    .sellingPrice(sellingPrice)
                    .minStockLevel(minStock)
                    .isAvailable(true)
                    .build();

                inventoryItemRepository.save(item);
                inventoryCount++;
            }
        }

        log.info("Seeded {} inventory items", inventoryCount);
    }

    private void seedTrustEndorsements(List<Technician> technicians) {
        log.info("Seeding trust endorsements...");

        // Create a trust network: A->B, B->C, C->A (cycle), D->B, E->D, F->E, G->B, H->C, I->A, J->D
        int[][] endorsements = {
            {0, 1}, // Technician 0 endorses 1
            {1, 2}, // Technician 1 endorses 2
            {2, 0}, // Technician 2 endorses 0 (creates cycle)
            {3, 1}, // Technician 3 endorses 1
            {4, 3}, // Technician 4 endorses 3
            {5, 4}, // Technician 5 endorses 4
            {6, 1}, // Technician 6 endorses 1
            {7, 2}, // Technician 7 endorses 2
            {8, 0}, // Technician 8 endorses 0
            {9, 3}  // Technician 9 endorses 3
        };

        int endorsementCount = 0;
        for (int[] pair : endorsements) {
            if (pair[0] < technicians.size() && pair[1] < technicians.size()) {
                TrustEndorsement endorsement = TrustEndorsement.builder()
                    .endorser(technicians.get(pair[0]))
                    .endorsee(technicians.get(pair[1]))
                    .weight(0.6 + Math.random() * 0.4)
                    .message("Great technician, very reliable and professional")
                    .isActive(true)
                    .build();

                trustEndorsementRepository.save(endorsement);
                endorsementCount++;
            }
        }

        log.info("Seeded {} trust endorsements", endorsementCount);
    }

    private void seedCustomerFeedbacks(List<Technician> technicians) {
        log.info("Seeding customer feedbacks...");

        String[] feedbackComments = {
            "Excellent service, very professional and punctual",
            "Good repair work, reasonable pricing",
            "Quick turnaround time, highly recommended",
            "Average service, could be better",
            "Poor communication, but work was okay",
            "Outstanding expertise, fixed the issue permanently",
            "Satisfactory service overall",
            "Not happy with the outcome, will look for others",
            "Fantastic work, exceeded expectations",
            "Decent service but slow response time",
            "Very knowledgeable technician",
            "Reasonable rates and good quality",
            "Work quality was below expectations",
            "Highly skilled and trustworthy",
            "Would definitely hire again"
        };

        String[] customerNames = {
            "Ramesh Kumar", "Priya Singh", "Anil Verma", "Sneha Patel", "Rahul Gupta",
            "Divya Sharma", "Arjun Reddy", "Neha Mishra", "Sanjay Rao", "Anita Saxena"
        };

        Random rand = new Random(123);
        int feedbackCount = 0;

        for (int i = 0; i < 20; i++) {
            Technician tech = technicians.get(rand.nextInt(technicians.size()));
            ApplianceType serviceType = ApplianceType.values()[rand.nextInt(ApplianceType.values().length)];
            int rating = 1 + rand.nextInt(5);
            String comment = feedbackComments[rand.nextInt(feedbackComments.length)];
            String customerName = customerNames[rand.nextInt(customerNames.length)];
            String customerPhone = "+91-" + (8000000000L + rand.nextInt(100000000));

            CustomerFeedback feedback = CustomerFeedback.builder()
                .technician(tech)
                .customerName(customerName)
                .customerPhone(String.valueOf(customerPhone))
                .rating(rating)
                .comment(comment)
                .serviceType(serviceType)
                .isVerified(rand.nextBoolean())
                .build();

            customerFeedbackRepository.save(feedback);
            feedbackCount++;
        }

        log.info("Seeded {} customer feedbacks", feedbackCount);
    }

    /**
     * Seed realistic orders and udhaar (credit/payment) data.
     *
     * Ground reality simulation:
     * - Some technicians are regular (2-3 orders/week), some occasional
     * - Most orders are on credit (udhaar) — cash payment is rare at parts shops
     * - Payment patterns vary: some pay weekly, some monthly, some are overdue
     * - This creates a realistic udhaar board for the shop owner
     */
    private void seedOrdersAndUdhaar(List<PartsShop> shops, List<Technician> technicians, List<SparePart> parts) {
        log.info("Seeding orders and udhaar data...");

        Random rand = new Random(777);
        PartsShop mainShop = shops.get(0); // Raj Spare Parts — Lucknow
        int orderCount = 0;
        int ledgerCount = 0;

        // Create 25 orders spread over the last 45 days
        for (int i = 0; i < 25; i++) {
            // Pick a technician from Lucknow (every 3rd tech is Lucknow-based)
            Technician tech = technicians.get((i * 3) % technicians.size());
            int daysAgo = 45 - (i * 2); // Spread orders over 45 days

            // Pick 1-3 random parts for this order
            int itemCount = 1 + rand.nextInt(3);
            List<SparePart> orderParts = new ArrayList<>();
            BigDecimal orderTotal = BigDecimal.ZERO;
            Set<OrderItem> orderItems = new HashSet<>();

            for (int j = 0; j < itemCount; j++) {
                SparePart part = parts.get(rand.nextInt(parts.size()));
                if (orderParts.contains(part)) continue;
                orderParts.add(part);

                int qty = 1 + rand.nextInt(3);
                BigDecimal unitPrice = part.getMrp().multiply(BigDecimal.valueOf(0.88));
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                orderTotal = orderTotal.add(lineTotal);

                OrderItem item = OrderItem.builder()
                    .sparePart(part)
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
                orderItems.add(item);
            }

            if (orderItems.isEmpty()) continue;

            // Determine order status based on age
            OrderStatus status;
            boolean creditUsed = rand.nextInt(10) < 8; // 80% orders on credit
            LocalDateTime createdAt = LocalDateTime.now().minusDays(daysAgo);
            LocalDateTime paidAt = null;

            if (daysAgo > 30) {
                status = OrderStatus.COMPLETED;
                if (creditUsed && rand.nextInt(10) < 6) {
                    paidAt = createdAt.plusDays(10 + rand.nextInt(15));
                }
            } else if (daysAgo > 14) {
                status = rand.nextInt(10) < 7 ? OrderStatus.COMPLETED : OrderStatus.PICKED_UP;
                if (status == OrderStatus.COMPLETED && creditUsed && rand.nextInt(10) < 4) {
                    paidAt = createdAt.plusDays(7 + rand.nextInt(10));
                }
            } else if (daysAgo > 3) {
                status = OrderStatus.PICKED_UP;
            } else {
                status = OrderStatus.PLACED;
            }

            String orderNumber = "PLQ-" + createdAt.toLocalDate().toString().replace("-", "") +
                "-" + String.format("%05d", 10000 + i);

            Order order = Order.builder()
                .orderNumber(orderNumber)
                .technician(tech)
                .shop(mainShop)
                .status(status)
                .totalAmount(orderTotal)
                .creditUsed(creditUsed)
                .paymentDueDate(creditUsed ? createdAt.plusDays(30) : null)
                .paidAt(paidAt)
                .notes(creditUsed ? "Udhaar — will settle next visit" : "Cash payment")
                .build();

            // Initialize orderItems if null (Lombok @Builder skips field initializers)
            if (order.getOrderItems() == null) {
                order.setOrderItems(new HashSet<>());
            }
            // Set order items BEFORE initial save — cascade ALL handles persist
            for (OrderItem item : orderItems) {
                item.setOrder(order);
            }
            order.getOrderItems().addAll(orderItems);

            Order savedOrder = orderRepository.save(order);
            orderCount++;

            // Create udhaar ledger entries for credit orders
            if (creditUsed) {
                // CREDIT entry when order was placed
                BigDecimal balanceBefore = getCurrentBalance(tech, mainShop);
                BigDecimal balanceAfterCredit = balanceBefore.add(orderTotal);

                PaymentLedger creditEntry = PaymentLedger.builder()
                    .technician(tech)
                    .shop(mainShop)
                    .order(savedOrder)
                    .entryType(LedgerEntryType.CREDIT)
                    .amount(orderTotal)
                    .balanceAfter(balanceAfterCredit)
                    .notes("Order " + orderNumber + " on credit")
                    .recordedBy("system")
                    .build();
                paymentLedgerRepository.save(creditEntry);
                ledgerCount++;

                // If paid, add PAYMENT entry
                if (paidAt != null) {
                    BigDecimal paymentAmount;
                    if (rand.nextInt(10) < 6) {
                        paymentAmount = orderTotal; // Full payment
                    } else {
                        paymentAmount = orderTotal.multiply(BigDecimal.valueOf(0.5 + rand.nextDouble() * 0.3));
                    }

                    BigDecimal balanceAfterPayment = balanceAfterCredit.subtract(paymentAmount);
                    PaymentMode mode = rand.nextInt(10) < 5 ? PaymentMode.CASH :
                        (rand.nextInt(10) < 7 ? PaymentMode.UPI : PaymentMode.BANK_TRANSFER);

                    PaymentLedger paymentEntry = PaymentLedger.builder()
                        .technician(tech)
                        .shop(mainShop)
                        .entryType(LedgerEntryType.PAYMENT)
                        .amount(paymentAmount)
                        .balanceAfter(balanceAfterPayment)
                        .paymentMode(mode)
                        .referenceNumber(mode == PaymentMode.UPI ? "UPI" + (100000 + rand.nextInt(900000)) : null)
                        .notes("Payment via " + mode.name().toLowerCase())
                        .recordedBy(mainShop.getOwnerName())
                        .build();
                    paymentLedgerRepository.save(paymentEntry);
                    ledgerCount++;
                }
            }
        }

        log.info("Seeded {} orders and {} ledger entries", orderCount, ledgerCount);
    }

    /**
     * Helper to compute current balance from existing ledger entries.
     * Used only during seeding — in production, UdhaarService handles this.
     */
    private BigDecimal getCurrentBalance(Technician tech, PartsShop shop) {
        return paymentLedgerRepository
            .findFirstByTechnicianIdAndShopIdOrderByCreatedAtDesc(tech.getId(), shop.getId())
            .map(PaymentLedger::getBalanceAfter)
            .orElse(BigDecimal.ZERO);
    }
}
