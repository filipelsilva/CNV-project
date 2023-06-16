package pt.ulisboa.tecnico.cnv.lbas;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
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

  private LambdaConnector lambdaConnector = new LambdaConnector();
  private DynamoDBGetter dynamoDBGetter = new DynamoDBGetter();

  private ConcurrentHashMap<Instance, Double> instanceUsage;
  private ConcurrentHashMap<Instance, Double> instanceInstructionsCount;
  private AtomicInteger instanceCount;
  private AtomicInteger instanceAvailableCount;
  private int counter = -1;
  private int lastSize = 0;

  private int dynamoDBCounter = -1;
  private static Map<Integer, Float> FoxesAndRabbitsCache = new HashMap<>();
  private static Map<String, Float> ImageCompressionCache = new HashMap<>();
  private static Float InsectWarsCache = null;

  public LoadBalancer(
      ConcurrentHashMap<Instance, Double> instances,
      AtomicInteger instanceCount,
      AtomicInteger instanceAvailableCount) {
    this.instanceUsage = instances;
    this.instanceCount = instanceCount;
    this.instanceAvailableCount = instanceAvailableCount;
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

  public void log(String toPrint) {
    System.out.println(String.format("[%s] %s", this.getClass().getSimpleName(), toPrint));
  }

  private void updateDynamoDBCache() {
    log("Updating DynamoDB Cache");
    HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

    ScanResult result = dynamoDBGetter.getDynamoDB("FoxesAndRabbits", scanFilter);
    for (Map<String, AttributeValue> entry : result.getItems()) {
      FoxesAndRabbitsCache.put(
          Integer.parseInt(entry.get("world").getN()),
          Float.parseFloat(entry.get("instructionsPerGeneration").getN()));
    }

    log("FoxesAndRabbitsCache: " + FoxesAndRabbitsCache);

    result = dynamoDBGetter.getDynamoDB("InsectWars", scanFilter);
    for (Map<String, AttributeValue> entry : result.getItems()) {
      InsectWarsCache = Float.parseFloat(entry.get("instructionsPerRoundPerSizeTimesRatio").getN());
    }

    log("InsectWarsCache: " + InsectWarsCache);

    result = dynamoDBGetter.getDynamoDB("ImageCompression", scanFilter);
    for (Map<String, AttributeValue> entry : result.getItems()) {
      ImageCompressionCache.put(
          entry.get("format").getS(),
          Float.parseFloat(entry.get("instructionsPerImageSizePerCompressionFactor").getN()));
    }
    
    log("ImageCompressionCache: " + ImageCompressionCache);
  }

  public String getInstanceURL(String type, Map<String, String> parameters) {
    dynamoDBCounter++;
    log(String.format("Updated dynamoDBCounter: (%s/5)", dynamoDBCounter));
    if (dynamoDBCounter == 5) {
      dynamoDBCounter = 0;
      updateDynamoDBCache();
    }

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

    if (instanceAvailableCountLocal < instanceCountLocal || instanceAvailableCountLocal == 0) {
      return "lambda";
    }

    for (Instance instance : instanceUsage.keySet()) {
      instanceInstructionsCount.putIfAbsent(instance, -1d);
    }
    for (Instance instance : instanceInstructionsCount.keySet()) {
      if (!instanceUsage.containsKey(instance)) {
        instanceInstructionsCount.remove(instance);
      }
    }

    for (Instance instance : instanceInstructionsCount.keySet()) {
      if (!instanceUsage.containsKey(instance)) {
        instanceInstructionsCount.remove(instance);
      }
    }

    if (instructions == -1) {

      // Do a round robin on the instances, sorted by cpu usage so that the first ones
      // are the ones with the lowest usage

      int size = instanceUsage.size();
      List<Instance> instancesSorted = new ArrayList<>(instanceUsage.keySet());
      Collections.sort(instancesSorted, Comparator.comparingDouble(instanceUsage::get));
      if (lastSize != size) {
        counter = 0;
        lastSize = size;
      } else {
        counter++;
        counter %= size;
      }
      return instancesSorted.get(counter).getPublicIpAddress();

    } else {

      // Idea: sort the instances by number of instructions ran
      // If a workload "fits" in an instance, use it
      // This will compact the number of instances as much as possible

      List<Instance> instancesSorted = new ArrayList<>(instanceInstructionsCount.keySet());
      Collections.sort(instancesSorted, Comparator.comparingDouble(instanceInstructionsCount::get));
      Collections.reverse(instancesSorted);

      for (Instance instance : instancesSorted) {
        double count = instanceInstructionsCount.get(instance);
        double usage = instanceUsage.get(instance);

        double estimation = instructions * usage / count;

        if (estimation + usage < 100) {
          instanceInstructionsCount.compute(instance, (k, v) -> v + estimation);
          return instance.getPublicIpAddress();
        }
      }

      // Unoptimal fallback
      return instancesSorted.get(0).getPublicIpAddress();
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

  public Integer getInstructionsFoxesAndRabbits(Map<String, String> parameters) {
    int n_generations = Integer.parseInt(parameters.get("generations"));
    int world = Integer.parseInt(parameters.get("world"));
    // int n_scenario = Integer.parseInt(parameters.get("scenario"));

    Float instructionsPerGeneration = FoxesAndRabbitsCache.get(world);
    if (instructionsPerGeneration == null) {
      return -1;
    }

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

    Float instructionsPerImageSizePerCompressionFactor = ImageCompressionCache.get(format);
    if (instructionsPerImageSizePerCompressionFactor == null) {
      return -1;
    }

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

    Float instructionsPerRoundPerSizeTimesRatio = InsectWarsCache;
    if (instructionsPerRoundPerSizeTimesRatio == null) {
      return -1;
    }

    Integer instructions = Math.round(instructionsPerRoundPerSizeTimesRatio * max * (army1 + army2));
    log("Instructions (estimate): " + instructions);

    return instructions;
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
      int responseCode;
      String response;
      String server = getInstanceURL(whereFrom, parameters);
      // server = "aaaa";
      if (server == null) {
        return;

      } else if (server.equals("lambda")) {
        log("Running lambda");
        String json = lambdaConnector.payloadGenerator(parameters);
        responseCode = 200;
        response = lambdaConnector.invokeFunction("CNV-" + whereFrom, json);

      } else {
        String url = "http://" + server + ":8000" + requestedUri;
        // String url = "http://localhost:8001" + requestedUri;
        log("URL: " + url);
        URL requestUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
        connection.setRequestMethod("GET");

        // Create response to client from response to server
        responseCode = connection.getResponseCode();
        log("Response Code: " + responseCode);

        BufferedReader reader =
            new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String line;
        StringBuilder responseBuilder = new StringBuilder();

        while ((line = reader.readLine()) != null) {
          responseBuilder.append(line);
        }
        reader.close();

        response = responseBuilder.toString();

        connection.disconnect();
      }
      // log("Response Body:\n" + response);

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
        int responseCode;
        String response;
        String server = getInstanceURL(whereFrom, parameters);
        // server = "aaaa";
        if (server == null) {
          return;

        } else if (server.equals("lambda")) {
          System.out.println("Running lambda");
          String json = lambdaConnector.payloadGenerator(parameters);
          responseCode = 200;
          response = lambdaConnector.invokeFunction("CNV-" + whereFrom, json);

        } else {
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
          responseCode = connection.getResponseCode();
          log("Response Code: " + responseCode);

          // Read response
          StringBuilder responseBuilder = new StringBuilder();
          try (BufferedReader reader =
              new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
              responseBuilder.append(line);
            }
          }
          response = responseBuilder.toString();

          connection.disconnect();
        }
        // log("Response Body:\n" + response);

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
