/**
 * Serverless Apparel Price Tracker - AWS Cloud Handlers
 * Author: Nikita

 * * Description: 
 * Consolidated Lambda backend architecture. Instead of deploying three separate 
 * repositories, this single class manages the core cloud interfaces:
 * - handleS3Upload: Event-driven SES email alerts based on S3 updates.
 * - handleApiGet: REST API endpoint bridging the S3 data to the frontend.
 * - handleApiPost: REST API endpoint appending frontend user inputs to S3.
 */

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApparelTrackerBackend {

    private static final String BUCKET_NAME = "apparel-tracker-data";
    private static final String SENDER_EMAIL = "example@gmail.com";
    private static final String RECEIVER_EMAIL = "example@gmail.com";
    
    private final S3Client s3Client = S3Client.builder().region(Region.AP_SOUTH_1).build();
    private final SesClient sesClient = SesClient.builder().region(Region.AP_SOUTH_1).build();
    private final Gson gson = new Gson();

    static class TrackedItem {
        String itemName; 
        double currentPrice; 
        double targetPrice; 
        String amazonUrl; 
        String myntraUrl; 
        String flipkartUrl;
    }

    // --- AWS Lambda Handler 1: Event-Driven S3 Trigger ---
    public String handleS3Upload(S3Event s3event, Context context) {
        try {
            S3EventNotification.S3EventNotificationRecord record = s3event.getRecords().get(0);
            String bucket = record.getS3().getBucket().getName();
            String fileKey = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8.name());

            if (!fileKey.equals("latest_prices.json")) return "Ignored historical file upload.";

            String jsonContent = fetchFileFromS3(bucket, fileKey);
            List<TrackedItem> items = gson.fromJson(jsonContent, new TypeToken<List<TrackedItem>>(){}.getType());

            for (TrackedItem item : items) {
                if (item.currentPrice > 0 && item.currentPrice <= item.targetPrice) {
                    sendEmailAlert(item);
                }
            }
            return "Alert logic execution complete.";
        } catch (Exception e) {
            context.getLogger().log("S3 processing error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // --- AWS Lambda Handler 2: API Gateway GET Endpoint ---
    public APIGatewayProxyResponseEvent handleApiGet(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(getCorsHeaders("GET, OPTIONS"));

        try {
            response.setStatusCode(200);
            response.setBody(fetchFileFromS3(BUCKET_NAME, "latest_prices.json"));
        } catch (Exception e) {
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Cloud fetch failure.\"}");
        }
        return response;
    }

    // --- AWS Lambda Handler 3: API Gateway POST Endpoint ---
    public APIGatewayProxyResponseEvent handleApiPost(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(getCorsHeaders("POST, OPTIONS"));

        try {
            List<TrackedItem> trackingList = new ArrayList<>();
            try {
                String rawJson = fetchFileFromS3(BUCKET_NAME, "latest_prices.json");
                trackingList = gson.fromJson(rawJson, new TypeToken<ArrayList<TrackedItem>>() {}.getType());
            } catch (Exception ex) {
                context.getLogger().log("Initializing fresh cloud array.");
            }

            trackingList.add(gson.fromJson(request.getBody(), TrackedItem.class));
            s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key("latest_prices.json").contentType("application/json").build(),
                    RequestBody.fromString(gson.toJson(trackingList)));

            response.setStatusCode(200);
            response.setBody("{\"message\": \"Item safely stored in S3 payload.\"}");
        } catch (Exception e) {
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Failed to persist incoming data.\"}");
        }
        return response;
    }

    // --- Internal Helpers ---
    private String fetchFileFromS3(String bucket, String key) {
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
        return new String(bytes.asByteArray(), StandardCharsets.UTF_8);
    }

    private Map<String, String> getCorsHeaders(String methods) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", methods);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private void sendEmailAlert(TrackedItem item) {
        String subject = "Price Drop Alert: " + item.itemName;
        String body = String.format("The tracked price for '%s' dropped to ₹%.2f. Target: ₹%.2f", item.itemName, item.currentPrice, item.targetPrice);
        sesClient.sendEmail(SendEmailRequest.builder()
                .source(SENDER_EMAIL).destination(Destination.builder().toAddresses(RECEIVER_EMAIL).build())
                .message(Message.builder().subject(Content.builder().data(subject).build())
                .body(Body.builder().text(Content.builder().data(body).build()).build()).build()).build());
    }
}