package pt.ulisboa.tecnico.cnv.lbas;

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
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class LoadBalancer {

  private String AWS_REGION = "us-east-1";
  private AmazonDynamoDB dynamoDBClient =
      AmazonDynamoDBClientBuilder.standard()
          .withCredentials(new EnvironmentVariableCredentialsProvider())
          .withRegion(AWS_REGION)
          .build();
  private LambdaConnector lambdaConnector = new LambdaConnector();

  private ConcurrentHashMap<Instance, Double> instanceUsage;
  private AtomicInteger instanceCount;
  private AtomicInteger instanceAvailableCount;
  private int counter = -1;
  private int lastSize = 0;

  public LoadBalancer(
      ConcurrentHashMap<Instance, Double> instances,
      AtomicInteger instanceCount,
      AtomicInteger instanceAvailableCount) {
    this.instanceUsage = instances;
    this.instanceCount = instanceCount;
    this.instanceAvailableCount = instanceAvailableCount;

    createTable("FoxesAndRabbits", "world");
    createTable("ImageCompression", "format");
    createTable("InsectWars", "instructionsPerRoundPerSizeTimesRatio");
    log("Creating tables...");
    waitForTable("FoxesAndRabbits");
    waitForTable("ImageCompression");
    waitForTable("InsectWars");
    log("Tables ready to go!");
  }

  public void log(String toPrint) {
    System.out.println(String.format("[%s] %s", this.getClass().getSimpleName(), toPrint));
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
      CreateTableRequest createTableRequest =
          new CreateTableRequest()
              .withTableName(tableName)
              .withKeySchema(
                  new KeySchemaElement().withAttributeName(primaryKey).withKeyType(KeyType.HASH))
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName(primaryKey)
                      .withAttributeType(primary))
              .withProvisionedThroughput(
                  new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

      // Create table if it does not exist yet
      TableUtils.createTableIfNotExists(dynamoDBClient, createTableRequest);

    } catch (AmazonServiceException ase) {
      log(
          "Caught an AmazonServiceException, which means your request made it "
              + "to AWS, but was rejected with an error response for some reason.");
      log("Error Message:    " + ase.getMessage());
      log("HTTP Status Code: " + ase.getStatusCode());
      log("AWS Error Code:   " + ase.getErrorCode());
      log("Error Type:       " + ase.getErrorType());
      log("Request ID:       " + ase.getRequestId());
    } catch (AmazonClientException ace) {
      log(
          "Caught an AmazonClientException, which means the client encountered "
              + "a serious internal problem while trying to communicate with AWS, "
              + "such as not being able to access the network.");
      log("Error Message: " + ace.getMessage());
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

  public ScanResult getDynamoDB(String tableName, Map<String, Condition> scanFilter) {
    try {
      ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
      ScanResult scanResult = dynamoDBClient.scan(scanRequest);
      log("Result: " + scanResult);
      return scanResult;

    } catch (AmazonServiceException ase) {
      log(
          "Caught an AmazonServiceException, which means your request made it "
              + "to AWS, but was rejected with an error response for some reason.");
      log("Error Message:    " + ase.getMessage());
      log("HTTP Status Code: " + ase.getStatusCode());
      log("AWS Error Code:   " + ase.getErrorCode());
      log("Error Type:       " + ase.getErrorType());
      log("Request ID:       " + ase.getRequestId());
    } catch (AmazonClientException ace) {
      log(
          "Caught an AmazonClientException, which means the client encountered "
              + "a serious internal problem while trying to communicate with AWS, "
              + "such as not being able to access the network.");
      log("Error Message: " + ace.getMessage());
    }
    return null;
  }

  public Integer getInstructionsFoxesAndRabbits(Map<String, String> parameters) {
    int n_generations = Integer.parseInt(parameters.get("generations"));
    int world = Integer.parseInt(parameters.get("world"));
    // int n_scenario = Integer.parseInt(parameters.get("scenario"));

    // Get data from the db
    HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

    scanFilter.put(
        "world",
        new Condition()
            .withComparisonOperator(ComparisonOperator.EQ.toString())
            .withAttributeValueList(new AttributeValue().withN(Integer.toString(world))));

    ScanResult result = getDynamoDB("FoxesAndRabbits", scanFilter);
    log("Result: " + result);

    Float instructionsPerGeneration =
        Float.parseFloat(result.getItems().get(0).get("instructionsPerGeneration").getN());

    Integer instructions = Math.round(instructionsPerGeneration * n_generations);
    log("Instructions (estimate): " + instructions);

    return instructions;
  }

  public Integer getInstructionsImageCompression(Map<String, String> parameters) {
    String inputEncoded = parameters.get("body");
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

    scanFilter.put(
        "format",
        new Condition()
            .withComparisonOperator(ComparisonOperator.EQ.toString())
            .withAttributeValueList(new AttributeValue(format)));

    ScanResult result = getDynamoDB("ImageCompression", scanFilter);
    log("Result: " + result);

    Float instructionsPerImageSizePerCompressionFactor =
        Float.parseFloat(
            result.getItems().get(0).get("instructionsPerImageSizePerCompressionFactor").getN());

    Integer instructions =
        Math.round(
            instructionsPerImageSizePerCompressionFactor * width * height * compressionFactor);
    log("Instructions (estimate): " + instructions);

    return instructions;
  }

  public Integer getInstructionsInsectWars(Map<String, String> parameters) {
    int max = Integer.parseInt(parameters.get("max"));
    int army1 = Integer.parseInt(parameters.get("army1"));
    int army2 = Integer.parseInt(parameters.get("army2"));

    // Get data from the db
    HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

    ScanResult result = getDynamoDB("InsectWars", scanFilter);
    log("Result: " + result);

    Float instructionsPerRoundPerSizeTimesRatio =
        Float.parseFloat(
            result.getItems().get(0).get("instructionsPerRoundPerSizeTimesRatio").getN());

    Integer instructions = Math.round(instructionsPerRoundPerSizeTimesRatio * max * army1 * army2);
    log("Instructions (estimate): " + instructions);

    return instructions;
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

    if (instanceAvailableCountLocal < instanceCountLocal) {
      // TODO run a lambda
    }

    List<Instance> instancesSorted = new ArrayList<>(instanceUsage.keySet());
    Collections.sort(instancesSorted, Comparator.comparingDouble(instanceUsage::get));

    // Do a round robin on the instances, sorted by cpu usage so that the first ones
    // are the ones with the lowest usage
    int size = instancesSorted.size();
    if (lastSize != size) {
      counter = 0;
      lastSize = size;
    } else {
      counter++;
      counter %= size;
    }

    return instancesSorted.get(counter).getPublicIpAddress();
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
      // log("Request URI: " + requestedUri);
      String query = requestedUri.getRawQuery();
      // log("Query: " + query);
      Map<String, String> parameters = queryToMap(query);
      log("Parameters: " + parameters);

      // Send request to (a) server
      String server = getInstanceURL(whereFrom, parameters);
      if (server == null) {
        return;
      }
      String url = "http://" + server + ":8000" + requestedUri;
      // String url = "http://localhost:8001" + requestedUri;
      log("URL: " + url);
      URL requestUrl = new URL(url);
      HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
      connection.setRequestMethod("GET");

      // Create response to client from response to server
      int responseCode = connection.getResponseCode();
      log("Response Code: " + responseCode);

      BufferedReader reader =
          new BufferedReader(new InputStreamReader(connection.getInputStream()));

      String line;
      StringBuilder response = new StringBuilder();

      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();

      log("Response Body:\n" + response.toString());

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
        t.getResponseHeaders()
            .add("Access-Control-Allow-Origin", t.getRequestHeaders().getFirst("Origin"));
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

        // Get the data and send it for analyzing (local)
        String result =
            new BufferedReader(new InputStreamReader(stream))
                .lines()
                .collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");

        String targetFormat = resultSplits[0].split(":")[1].split(";")[0];
        String compressionFactor = resultSplits[0].split(":")[2].split(";")[0];

        Map<String, String> parameters = new HashMap<>();
        parameters.put("targetFormat", targetFormat);
        parameters.put("compressionFactor", compressionFactor);
        parameters.put("body", resultSplits[1]);

        // Send request to (a) server
        String server = getInstanceURL(whereFrom, parameters);
        if (server == null) {
          return;
        }
        String url = "http://" + server + ":8000/compressimage";
        // String url = "http://localhost:8001/compressimage";
        log("URL: " + url);
        URL requestUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            outputStream.writeBytes(result);
            outputStream.flush();
        }

        // Create response to client from response to server
        int responseCode = connection.getResponseCode();
        log("Response Code: " + responseCode);

        // Read response
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }
        }

        t.sendResponseHeaders(responseCode, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.toString().getBytes());
        os.close();
      }
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
