package pt.ulisboa.tecnico.cnv.lbas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
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
    private AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withCredentials(new EnvironmentVariableCredentialsProvider())
        .withRegion(AWS_REGION)
        .build();

    private ConcurrentHashMap<Instance, Double> instanceUsage;

    public LoadBalancer(ConcurrentHashMap<Instance, Double> instances) {
        this.instanceUsage = instances;
    }

    public void serveForever() {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.createContext("/test", new TestHandler());
            server.createContext("/simulate", new RootHandler("FoxesAndRabbits"));
            server.createContext("/compressimage", new RootHandler("ImageCompression"));
            server.createContext("/insectwar", new RootHandler("InsectWars"));
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, String> queryToMap(String query) {
        if(query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for(String param : query.split("&")) {
            String[] entry = param.split("=");
            if(entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
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
            ScanResult scanResult = dynamoDB.scan(scanRequest);
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

    public Integer processFoxesAndRabbits(Map<String, String> parameters) {
        int n_generations = Integer.parseInt(parameters.get("generations"));
        int world = Integer.parseInt(parameters.get("world"));
        int n_scenario = Integer.parseInt(parameters.get("scenario"));

        // Get data from the db
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        
        scanFilter.put("type", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue("FoxesAndRabbits")));

        scanFilter.put("world", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withN(Integer.toString(world))));

        ScanResult result = getDynamoDB("info", scanFilter);
        return 0;
    }

    public Integer processImageCompression(Map<String, String> parameters) {
        // function executeCompress(e) {
        //     var iimage = document.getElementById("inputImageCompress").src;
        //     var host = document.getElementById("targetAddressCompress").value;
        //     var port = document.getElementById("targetPortCompress").value;
        //     var operation = document.getElementById("operation").value;
        //     var compressionFactor = document.getElementById("compressionFactor").value;
        //     iimage = 'targetFormat:' + operation + ';compressionFactor:' + compressionFactor + ';' + iimage;
        //     fetch('http://' + host + ':' + port + '/compressimage', { method: 'POST', body: iimage })
        //         .then(response => response.blob())
        //         .then(myBlob => {
        //             var reader = new FileReader() ;
        //             reader.readAsBinaryString(myBlob) ;
        //             reader.onload = function(ee) {
        //                 var result = ee.target.result
        //                     var image = new Image();
        //                 image.src = result;
        //                 image.onload = function () {
        //                     document.getElementById("outputImageCompress").src=image.src;
        //                 };
        //             };
        //         });
        // }

        // Get data from the db
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        
        scanFilter.put("type", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue("ImageCompression")));

        // scanFilter.put("format", new Condition()
        //         .withComparisonOperator(ComparisonOperator.EQ.toString())
        //         .withAttributeValueList(new AttributeValue(format)));

        ScanResult result = getDynamoDB("info", scanFilter);
        return 0;
    }

    public Integer processInsectWars(Map<String, String> parameters) {
        int max = Integer.parseInt(parameters.get("max"));
        int army1 = Integer.parseInt(parameters.get("army1"));
        int army2 = Integer.parseInt(parameters.get("army2"));

        // Get data from the db
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        
        scanFilter.put("type", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue("InsectWars")));

        // scanFilter.put("format", new Condition()
        //         .withComparisonOperator(ComparisonOperator.EQ.toString())
        //         .withAttributeValueList(new AttributeValue(format)));

        ScanResult result = getDynamoDB("info", scanFilter);
        return 0;
    }

    public String getInstanceURL(String type, Map<String, String> parameters) {
        Integer instructions = 0;
        switch (type) {
            case "FoxesAndRabbits":
                 instructions = processFoxesAndRabbits(parameters);
            case "ImageCompression":
                 instructions = processImageCompression(parameters);
            case "InsectWars":
                 instructions = processInsectWars(parameters);
            default:
                return null;
        }
    }

    protected class RootHandler implements HttpHandler {

        public String whereFrom;

        public RootHandler(String type) {
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
