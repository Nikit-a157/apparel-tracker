/**
 * Serverless Apparel Price Tracker - Scraper Engine
 * Author: Nikita 
 * * DESIGN ARCHITECTURE: Local-First, Cloud-Ready.
 * To activate live AWS integrations, flip IS_CLOUD_MODE to true and 
 * uncomment the designated AWS blocks.
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/* ====================================================================
   STEP 1: AWS CLOUD IMPORTS (Uncomment these to awake AWS)  or (comment for local )
   ==================================================================== */
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;
import java.time.format.DateTimeFormatter; // Uncomment for AWS S3 timestamping

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scraper {

    // --- GLOBAL CONFIGURATION TOGGLE ---
    // Set to true to switch data routing and alerts from Local to AWS Cloud
    private static final boolean IS_CLOUD_MODE = true; 
    private static final Scanner scanner = new Scanner(System.in);
    private static final String CONFIG_FILE = "tracked_items.json";
    private static final String FRONTEND_DATA_DIR = "frontend/data/";
    private static int currentCycleCount = 0; 
    private static final String VERIFIED_MY_EMAIL ="example@gmail.com";

    /* ====================================================================
       STEP 2: AWS CLIENT INFRASTRUCTURE (Uncomment these to awake AWS)
       ==================================================================== */
    private static final String BUCKET_NAME = "apparel-tracker-data"; 
    private static final S3Client s3Client = S3Client.builder()
            .region(Region.AP_SOUTH_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    public static class TrackedItem {
        private String itemName; 
        double targetPrice; 
        String amazonUrl; 
        String myntraUrl; 
        String flipkartUrl;
        public double currentPrice; 
        public String timestamp;
        
        public TrackedItem(String itemName, double targetPrice, String amazonUrl, String myntraUrl, String flipkartUrl) {
            this.itemName = itemName; 
            this.targetPrice = targetPrice; 
            this.amazonUrl = amazonUrl;
            this.myntraUrl = myntraUrl; 
            this.flipkartUrl = flipkartUrl;
        }

        public String getItemName() { return itemName; }
        public double getTargetPrice() { return targetPrice; }
        public String getAmazonUrl() { return amazonUrl; }
        public String getMyntraUrl() { return myntraUrl; }
        public String getFlipkartUrl() { return flipkartUrl; }
        public void setCurrentPrice(double currentPrice) { 
            this.currentPrice = currentPrice; 
        }
        public void setTimestamp(String timestamp) { 
            this.timestamp = timestamp; 
        }
    }

    public static class ConfigManager {
        private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

        public static List<TrackedItem> loadConfig() {
            File file = new File(CONFIG_FILE);
            if (!file.exists()) return new ArrayList<>();
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<ArrayList<TrackedItem>>() {}.getType();
                List<TrackedItem> items = gson.fromJson(reader, listType);
                return items != null ? items : new ArrayList<>();
            } catch (Exception e) { 
                return new ArrayList<>(); 
            }
        }
        
        public static void saveConfig(List<TrackedItem> items) {
            try (Writer writer = new FileWriter(CONFIG_FILE)) {
                List<java.util.Map<String, Object>> cleanConfig = new ArrayList<>();
                for (TrackedItem item : items) {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("itemName", item.getItemName());
                    map.put("targetPrice", item.getTargetPrice());
                    map.put("amazonUrl", item.getAmazonUrl());
                    map.put("myntraUrl", item.getMyntraUrl());
                    map.put("flipkartUrl", item.getFlipkartUrl());
                    cleanConfig.add(map);
                }
                gson.toJson(cleanConfig, writer);
            } catch (IOException e) { System.err.println("Save error: " + e.getMessage()); }
        }
    }

    public static void main(String[] args) {
        new File(FRONTEND_DATA_DIR).mkdirs(); 

        System.out.println("\n=== Price Tracker Initialization ===");
        System.out.println("Current Environment: " + (IS_CLOUD_MODE ? "AWS CLOUD PRODUCTION" : "LOCAL DEVELOPMENT"));
        System.out.print("Would you like to add a new item to track? (y/n): ");
        String addChoice = scanner.nextLine().trim().toLowerCase();

        if (addChoice.equals("y") || addChoice.equals("yes") || addChoice.equals("YES") || addChoice.equals("Yes")) {
            addNewItem();
        }

        System.out.println("\n=== Select Execution Mode ===");
        System.out.println("1. Run Scraper Manually (Once)");
        System.out.println("2. Start Automatic Scheduler (Background Monitoring)");
        System.out.println("3. Exit Application");
        System.out.print("Select an option (1-3): ");

        int choice = scanner.nextInt();
        scanner.nextLine(); 

        if (choice == 3) {
            System.out.println("Shutting down tracker.");
            System.exit(0);
        }

        if (choice == 1) {
            runScraperCycle(); 
            System.exit(0); 
        } else if (choice == 2) {
            String userEmail = "";
            System.out.print("\nDo you want to receive an email alert if a price drops below target? (y/n): ");
            String alertChoice = scanner.nextLine().trim().toLowerCase();
            if (alertChoice.equals("y") || alertChoice.equals("yes")) {
                System.out.print("Enter your email address: ");
                userEmail = scanner.nextLine().trim();
            }

            System.out.print("How many 12-hour cycles should the scheduler run before automatically stopping? (Enter 0 to run indefinitely): ");
            int maxCycles = scanner.nextInt();
            scanner.nextLine();

            startScheduler(userEmail, maxCycles);
        }
    }

    private static void addNewItem() {
        System.out.println("\n--- Add New Target ---");
        System.out.print("Item Name: "); String name = scanner.nextLine();
        System.out.print("Target Price (₹): "); double target = scanner.nextDouble(); scanner.nextLine();
        System.out.print("Amazon URL (optional): "); String amazon = scanner.nextLine();
        System.out.print("Myntra URL (optional): "); String myntra = scanner.nextLine();
        System.out.print("Flipkart URL (optional): "); String flipkart = scanner.nextLine();

        List<TrackedItem> items = ConfigManager.loadConfig();
        if (items == null) items = new ArrayList<>();
        
        items.add(new TrackedItem(name, target, amazon, myntra, flipkart));
        ConfigManager.saveConfig(items);
        System.out.println("Target acquired and saved to configuration.");
    }

    private static void startScheduler(String userEmail, int maxCycles) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        System.out.println("\n[SYSTEM] Background scheduler active. Scanning every 12 hours.");
        if (!userEmail.isEmpty()) {
            System.out.println("[SYSTEM] Deal alerts configured for: " + userEmail);
        }
        if (maxCycles > 0) {
            System.out.println("[SYSTEM] Auto-stop sequence armed: System kills after " + maxCycles + " cycles.");
        }

        Runnable scrapingTask = () -> {
            currentCycleCount++;
            System.out.println("\n--- Starting Cycle " + currentCycleCount + " ---");
            runScraperCycle();

            if (maxCycles > 0 && currentCycleCount >= maxCycles) {
                System.out.println("\n[SYSTEM] Maximum automatic cycles reached (" + maxCycles + "). Shutting down engine cleanly.");
                scheduler.shutdown();
                System.exit(0);
            }
        };

        scheduler.scheduleAtFixedRate(scrapingTask, 0, 12, TimeUnit.HOURS);

        System.out.println("\n>>> PRESS [ENTER] AT ANY TIME TO MANUALLY ABORT THE SCHEDULER AND EXIT <<<");
        scanner.nextLine();
        System.out.println("Manual override captured. Tearing down background threads.");
        scheduler.shutdown();
        System.exit(0);
    }

    private static void runScraperCycle() {
        List<TrackedItem> items = ConfigManager.loadConfig();
        if (items == null || items.isEmpty()) { 
            System.out.println("No items in tracking array. Exiting cycle."); 
            return; 
        }

        List<TrackedItem> oldPrices = new ArrayList<>();
        try (Reader reader = new FileReader(FRONTEND_DATA_DIR + "latest_prices.json")) {
            Type listType = new TypeToken<ArrayList<TrackedItem>>() {}.getType();
            oldPrices = new Gson().fromJson(reader, listType);
            if (oldPrices == null) oldPrices = new ArrayList<>();
        } catch (Exception e) { /* Cache completely empty on initial initialization */ }

        System.out.println("\nBooting Selenium WebDriver Environment...");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        WebDriver driver = new ChromeDriver(options);
        boolean priceChanged = false;

        try {
            for (TrackedItem item : items) {
                double lowestPrice = Double.MAX_VALUE;
                System.out.println("\nScanning price structures for: " + item.getItemName());

                if (item.getAmazonUrl() != null && !item.getAmazonUrl().trim().isEmpty()) {
                    double p = scrapeUrl(driver, item.getAmazonUrl(), "a-price-whole", "Amazon");
                    if (p > 0 && p < lowestPrice) lowestPrice = p;
                }
                if (item.getMyntraUrl() != null && !item.getMyntraUrl().trim().isEmpty()) {
                    double p = scrapeUrl(driver, item.getMyntraUrl(), "pdp-price", "Myntra");
                    if (p > 0 && p < lowestPrice) lowestPrice = p;
                }
                if (item.getFlipkartUrl() != null && !item.getFlipkartUrl().trim().isEmpty()) {
                    double p = scrapeUrl(driver, item.getFlipkartUrl(), "Nx9w7m", "Flipkart"); 
                    if (p > 0 && p < lowestPrice) lowestPrice = p;
                }

                double newPrice = (lowestPrice == Double.MAX_VALUE ? 0.0 : lowestPrice);
                item.setCurrentPrice(newPrice);
                item.setTimestamp(LocalDateTime.now().toString());

                // --- ALERT EVALUATION GATEWAY ---
                if (newPrice > 0 && newPrice <= item.getTargetPrice() ) {
                    // 1. Instant Terminal / Local Update
                    executeSimulatedEmailAlert(item); // Prints the nice alert block in the terminal
                
                    
                    // 2. AWS Cloud Email Update
                    try {
                        sendRealAWSEmailAlert(item, VERIFIED_MY_EMAIL);
                        System.out.println("AWS SES Alert Email successfully sent to: " +  VERIFIED_MY_EMAIL);
                    } catch (Exception e) {
                        System.out.println("Could not send AWS email (Check SES verification): " + e.getMessage());
                    }
                }

               // --- CHECK FOR PRICE CHANGES ---
                boolean isNewItem = true;
                for (TrackedItem oldItem : oldPrices) {
                    if (oldItem.getItemName().equals(item.getItemName())) {
                        isNewItem = false;
                        if (oldItem.currentPrice != newPrice) priceChanged = true;
                        break;
                    }
                }
                if (isNewItem) priceChanged = true;
            }
            
            ConfigManager.saveConfig(items);

            // --- DATA ROUTING GATEWAY (THE OMNI-CHANNEL SYNC) ---
            if (priceChanged) {
                System.out.println("\nPrice delta detected! Synchronizing pipelines...");
                
                // 1. Update the Local System (For terminal & local files)
                saveResultsLocally(items);    
                System.out.println("Local payload updated successfully.");

                // 2. Update the AWS Cloud (For live frontend webpage)
                try {
                    uploadResultsToS3(items);
                    System.out.println("AWS S3 Cloud payload updated successfully.");
                } catch (Exception e) {
                    System.out.println("AWS S3 Upload failed: " + e.getMessage());
                }
                
            } else {
                System.out.println("\n💤 No price updates detected across catalogs. Skipping data dumps.");
            }
            
            } finally {
                System.out.println("Tearing down running WebDriver thread instances.");
                if (driver != null) {
                    driver.quit(); 
                }
            }
    }

    private static double scrapeUrl(WebDriver driver, String url, String classSelector, String platformName) {
        try {
            driver.get(url);
            Thread.sleep(2000); 
            WebElement element = driver.findElement(By.className(classSelector));
            String text = element.getText().replaceAll("[^0-9]", "");
            double price = Double.parseDouble(text);
            System.out.println("  ✓ " + platformName + " Price: ₹" + price);
            return price;
        } catch (Exception e) { 
            System.err.println("  X Failed on " + platformName + ": " + e.getMessage().split("\n")[0]); 
            return 0.0; 
        } 
    }

    private static void saveResultsLocally(List<TrackedItem> items) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String payload = gson.toJson(items);
        try {
            File file = new File(FRONTEND_DATA_DIR + "latest_prices.json");
            try (FileWriter writer = new FileWriter(file)) { writer.write(payload); }
            System.out.println("Success! Local JSON store updated seamlessly.");
        } catch (IOException e) { 
            System.err.println("File IO error: " + e.getMessage()); 
        }
    }

    private static void executeSimulatedEmailAlert(TrackedItem item) {
        String subject = "DEAL ALERT: " + item.getItemName() + " hit your target!";
        String body = "Good news! " + item.getItemName() + " has dropped to ₹" + item.currentPrice + ".\n" +
                      "Your target threshold was set to ₹" + item.getTargetPrice() + ". Check your portal.";

        System.out.println("\n=================================================");
        System.out.println("         [LOCAL SIMULATION] SMTP OUTBOX          ");
        System.out.println("=================================================");
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
    }

    /* ====================================================================
       STEP 5: AWS CORE FUNCTIONAL METHODS (Uncomment to awake AWS entirely)
       ==================================================================== */
    
    private static void sendRealAWSEmailAlert(TrackedItem item, String userEmail) {
        String subject = "DEAL ALERT: " + item.getItemName() + " hit your target!";
        String body = "Good news! " + item.getItemName() + " has dropped to ₹" + item.currentPrice + ".\n" +
                      "Your target threshold was set to ₹" + item.getTargetPrice() + ".";
        try {
            SesClient sesClient = SesClient.builder()
                    .region(Region.AP_SOUTH_1)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            sesClient.sendEmail(SendEmailRequest.builder()
                    .source("example@gmail.com") // Replace with verified address
                    .destination(Destination.builder().toAddresses(userEmail).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).build())
                            .body(Body.builder().text(Content.builder().data(body).build()).build())
                            .build())
                    .build());
            System.out.println("\n[SUCCESS] AWS SES Cloud infrastructure dispatched real-time email to " + userEmail);
        } catch (Exception e) {
            System.err.println("\n[CRITICAL] AWS SES transactional email pipeline blocked: " + e.getMessage());
        }
    }

    private static void uploadResultsToS3(List<TrackedItem> items) {
        try {
            String payload = new Gson().toJson(items);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Log raw transactional payload historical records
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key("historical/price_data_" + timestamp + ".json")
                    .contentType("application/json")
                    .build(), RequestBody.fromString(payload));

            // Overwrite static cache asset consumed by the application frontend API layer
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key("latest_prices.json")
                    .contentType("application/json")
                    .build(), RequestBody.fromString(payload));

            System.out.println("[SUCCESS] AWS S3 pipeline synchronizations executed successfully. Live cloud models updated.");
        } catch (Exception e) {
            System.err.println("[CRITICAL] AWS Cloud S3 Storage ingestion failed: " + e.getMessage());
        }
    }
    
}