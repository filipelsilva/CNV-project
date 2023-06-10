package pt.ulisboa.tecnico.cnv.lbas;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

public class LoadBalancer {

        private String AWS_REGION = "us-east-1";
        private AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build();

        private ConcurrentHashMap<Instance, Double> instanceUsage;
        private AtomicInteger instanceCount;
        private AtomicInteger instanceAvailableCount;

        public LoadBalancer(ConcurrentHashMap<Instance, Double> instances, AtomicInteger instanceCount, AtomicInteger instanceAvailableCount) {
                this.instanceUsage = instances;
                this.instanceCount = instanceCount;
                this.instanceAvailableCount = instanceAvailableCount;

                createTable("FoxesAndRabbits", "world");
                createTable("ImageCompression", "format");
                createTable("InsectWars", "instructionsPerRoundPerSizeTimesRatio");
                System.out.println("Creating tables...");
                waitForTable("FoxesAndRabbits");
                waitForTable("ImageCompression");
                waitForTable("InsectWars");
                System.out.println("Tables ready to go!");
        }

        public void waitForTable(String tableName) {
                try {
                        // wait for the table to move into ACTIVE state
                        TableUtils.waitUntilActive(dynamoDBClient, tableName);
                } catch (InterruptedException e) {
                        e.printStackTrace();
                }
        }

        public void createTable(String tableName, String primaryKey) {
                try {
                        ScalarAttributeType primary;
                        if (primaryKey.equals("format")) {
                                primary = ScalarAttributeType.S;
                        } else {
                                primary = ScalarAttributeType.N;
                        }

                        // Create a table with a primary hash key named 'type', which holds a string
                        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                                .withKeySchema(new KeySchemaElement().withAttributeName(primaryKey).withKeyType(KeyType.HASH))
                                .withAttributeDefinitions(new AttributeDefinition().withAttributeName(primaryKey).withAttributeType(primary))
                                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

                        // Create table if it does not exist yet
                        TableUtils.createTableIfNotExists(dynamoDBClient, createTableRequest);

                } catch (AmazonServiceException ase) {
                        System.out.println("Caught an AmazonServiceException, which means your request made it "
                                        + "to AWS, but was rejected with an error response for some reason.");
                        System.out.println("Error Message:    " + ase.getMessage());
                        System.out.println("HTTP Status Code: " + ase.getStatusCode());
                        System.out.println("AWS Error Code:   " + ase.getErrorCode());
                        System.out.println("Error Type:       " + ase.getErrorType());
                        System.out.println("Request ID:       " + ase.getRequestId());
                } catch (AmazonClientException ace) {
                        System.out.println("Caught an AmazonClientException, which means the client encountered "
                                        + "a serious internal problem while trying to communicate with AWS, "
                                        + "such as not being able to access the network.");
                        System.out.println("Error Message: " + ace.getMessage());
                }
        }

        public Instance selectRandomInstance() {
                return instanceUsage.keySet().stream().findAny().get();
        }

        public void serveForever() {
                HttpServer server;
                try {
                        server = HttpServer.create(new InetSocketAddress(8000), 0);
                        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
                        server.createContext("/test", new TestHandler());
                        server.createContext("/simulate", new GetHandler("FoxesAndRabbits"));
                        server.createContext("/compressimage", new PostHandler("ImageCompression"));
                        server.createContext("/insectwar", new GetHandler("InsectWars"));
                        server.start();
                } catch (IOException e) {
                        e.printStackTrace();
                }
        }

        public Map<String, String> queryToMap(String query) {
                if (query == null) {
                        return null;
                }
                Map<String, String> result = new HashMap<>();
                for (String param : query.split("&")) {
                        String[] entry = param.split("=");
                        if (entry.length > 1) {
                                result.put(entry[0], entry[1]);
                        } else {
                                result.put(entry[0], "");
                        }
                }
                return result;
        }

        public Instance getInstanceWithLowestUtilization() {
                return instanceUsage.entrySet()
                        .stream()
                        .max(ConcurrentHashMap.Entry.comparingByValue())
                        .map(ConcurrentHashMap.Entry::getKey)
                        .orElse(null);
        }

        public ScanResult getDynamoDB(String tableName, Map<String, Condition> scanFilter) {
                try {
                        ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
                        ScanResult scanResult = dynamoDBClient.scan(scanRequest);
                        System.out.println("Result: " + scanResult);
                        return scanResult;

                } catch (AmazonServiceException ase) {
                        System.out.println("Caught an AmazonServiceException, which means your request made it "
                                        + "to AWS, but was rejected with an error response for some reason.");
                        System.out.println("Error Message:    " + ase.getMessage());
                        System.out.println("HTTP Status Code: " + ase.getStatusCode());
                        System.out.println("AWS Error Code:   " + ase.getErrorCode());
                        System.out.println("Error Type:       " + ase.getErrorType());
                        System.out.println("Request ID:       " + ase.getRequestId());
                } catch (AmazonClientException ace) {
                        System.out.println("Caught an AmazonClientException, which means the client encountered "
                                        + "a serious internal problem while trying to communicate with AWS, "
                                        + "such as not being able to access the network.");
                        System.out.println("Error Message: " + ace.getMessage());
                }
                return null;
        }

        public Integer getInstructionsFoxesAndRabbits(Map<String, String> parameters) {
                int n_generations = Integer.parseInt(parameters.get("generations"));
                int world = Integer.parseInt(parameters.get("world"));
                int n_scenario = Integer.parseInt(parameters.get("scenario"));

                // Get data from the db
                HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

                scanFilter.put("world", new Condition()
                                .withComparisonOperator(ComparisonOperator.EQ.toString())
                                .withAttributeValueList(new AttributeValue().withN(Integer.toString(world))));

                ScanResult result = getDynamoDB("FoxesAndRabbits", scanFilter);
                System.out.println("Result: " + result);

                return 0;
        }

        public Integer getInstructionsImageCompression(Map<String, String> parameters) {
                String inputEncoded = parameters.get("image");
                String format = parameters.get("format");
                Float compressionFactor = Float.parseFloat(parameters.get("compressionFactor"));

                byte[] decoded = Base64.getDecoder().decode(inputEncoded);
                BufferedImage bi = null;

                try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
                        bi = ImageIO.read(bais);

                } catch (IOException e) {
                        e.printStackTrace();
                }

                float width = bi.getWidth();
                float height = bi.getHeight();

                // Get data from the db
                HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

                scanFilter.put("format", new Condition()
                                .withComparisonOperator(ComparisonOperator.EQ.toString())
                                .withAttributeValueList(new AttributeValue(format)));

                ScanResult result = getDynamoDB("ImageCompression", scanFilter);
                System.out.println("Result: " + result);

                return 0;
        }

        public Integer getInstructionsInsectWars(Map<String, String> parameters) {
                int max = Integer.parseInt(parameters.get("max"));
                int army1 = Integer.parseInt(parameters.get("army1"));
                int army2 = Integer.parseInt(parameters.get("army2"));

                // Get data from the db
                HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

                ScanResult result = getDynamoDB("InsectWars", scanFilter);
                result.getItems();
                return 0;
        }

        public String getInstanceURL(String type, Map<String, String> parameters) {
                Integer instructions = 0;
                switch (type) {
                        case "FoxesAndRabbits":
                                instructions = getInstructionsFoxesAndRabbits(parameters);
                                break;
                        case "ImageCompression":
                                instructions = getInstructionsImageCompression(parameters);
                                break;
                        case "InsectWars":
                                instructions = getInstructionsInsectWars(parameters);
                                break;
                        default:
                                return null;
                }
                return chooseInstance(instructions);
        }

        private String chooseInstance(Integer instructions) {
                int instanceCountLocal = instanceCount.get();
                int instanceAvailableCountLocal = instanceAvailableCount.get();
                return null;
        }

        protected class GetHandler implements HttpHandler {

                public String whereFrom;

                public GetHandler(String type) {
                        whereFrom = type;
                }

                @Override
                public void handle(HttpExchange t) throws IOException {
                        // Handling CORS
                        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

                        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                                t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                                t.sendResponseHeaders(204, -1);
                                return;
                        }

                        // Parsing request
                        URI requestedUri = t.getRequestURI();
                        System.out.println("Request URI: " + requestedUri);
                        String query = requestedUri.getRawQuery();
                        System.out.println("Query: " + query);
                        Map<String, String> parameters = queryToMap(query);
                        System.out.println("Parameters: " + parameters);

                        // Send request to (a) server
                        String url = "http://" + getInstanceURL(whereFrom, parameters) + ":8000/" + whereFrom + "?" + query;
                        System.out.println("URL: " + url);
                        URL requestUrl = new URL(url);
                        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
                        connection.setRequestMethod("GET");

                        // Create response to client from response to server
                        int responseCode = connection.getResponseCode();
                        System.out.println("Response Code: " + responseCode);

                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                        String line;
                        StringBuilder response = new StringBuilder();

                        while ((line = reader.readLine()) != null) {
                                response.append(line);
                        }
                        reader.close();

                        System.out.println("Response Body:\n" + response.toString());

                        connection.disconnect();

                        // Send response back to client
                        t.sendResponseHeaders(responseCode, response.length());
                        OutputStream os = t.getResponseBody();
                        os.write(response.toString().getBytes());
                        os.close();
                }
        }

        protected class PostHandler implements HttpHandler {

                public String whereFrom;

                public PostHandler(String type) {
                        whereFrom = type;
                }

                @Override
                public void handle(HttpExchange t) throws IOException {
                        if (t.getRequestHeaders().getFirst("Origin") != null) {
                                t.getResponseHeaders().add("Access-Control-Allow-Origin", t.getRequestHeaders().getFirst("Origin"));
                        }
                        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                                t.getResponseHeaders().add("Access-Control-Allow-Methods", "POST");
                                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,API-Key");
                                t.sendResponseHeaders(204, -1);
                        } else {
                                InputStream stream = t.getRequestBody();
                                // Result syntax:
                                // targetFormat:<targetFormat>;compressionFactor:<factor>;data:image/<currentFormat>;base64,<encoded
                                // image>
                                String result = new BufferedReader(new InputStreamReader(stream)).lines()
                                        .collect(Collectors.joining("\n"));
                                String[] resultSplits = result.split(",");

                                String targetFormat = resultSplits[0].split(":")[1].split(";")[0];
                                String compressionFactor = resultSplits[0].split(":")[2].split(";")[0];

                                Map<String, String> params = new HashMap<>();
                                params.put("targetFormat", targetFormat);
                                params.put("compressionFactor", compressionFactor);
                                params.put("image", resultSplits[1]);
                                getInstanceURL(whereFrom, params);

                                String output = String.format("data:image/%s;base64,%s", targetFormat,
                                                handleRequest(resultSplits[1], targetFormat, Float.parseFloat(compressionFactor)));
                                t.sendResponseHeaders(200, output.length());
                                OutputStream os = t.getResponseBody();
                                os.write(output.getBytes());
                                os.close();
                        }
                }

                private String handleRequest(String inputEncoded, String format, float compressionFactor) {
                        byte[] decoded = Base64.getDecoder().decode(inputEncoded);
                        // try {
                        // ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
                        // BufferedImage bi = ImageIO.read(bais);
                        // byte[] resultImage = process(bi, format, compressionFactor);
                        // byte[] outputEncoded = Base64.getEncoder().encode(resultImage);
                        // return new String(outputEncoded);
                        return "";
                        // } catch (IOException e) {
                        // return e.toString();
                        // }
                }
        }

        static class TestHandler implements HttpHandler {
                @Override
                public void handle(HttpExchange t) throws IOException {
                        String response = "This was the query: <" + t.getRequestURI().getQuery() + ">";
                        t.sendResponseHeaders(200, response.length());
                        OutputStream os = t.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                }
        }
}
