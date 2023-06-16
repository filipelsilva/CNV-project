package pt.ulisboa.tecnico.cnv.webserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;

public class WebServer {
  public static void main(String[] args) throws Exception {
    int DELAY_DYNAMODB = 10;

    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    AmazonDynamoDBConnector dynamoDBConnector = new AmazonDynamoDBConnector();

    Runnable autoscalerTask =
        () -> {
          Map<Integer, Float> FoxesAndRabbitsCache = new HashMap<>();
          Map<String, Float> ImageCompressionCache = new HashMap<>();
          Float InsectWarsCache = null;

          try {
            FileReader fileReader = new FileReader("/tmp/dynamodb");
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
              String[] lineSplit = line.split(" ");
              switch (lineSplit[0]) {
                case "FoxesAndRabbits":
                  FoxesAndRabbitsCache.put(
                      Integer.parseInt(lineSplit[1]), Float.parseFloat(lineSplit[2]));
                  break;
                case "ImageCompression":
                  ImageCompressionCache.put(lineSplit[1], Float.parseFloat(lineSplit[2]));
                  break;
                case "InsectWars":
                  InsectWarsCache = Float.parseFloat(lineSplit[1]);
                  break;
              }
            }

            // Close the readers
            bufferedReader.close();
            fileReader.close();

          } catch (IOException e) {
            System.out.println("An error occurred while reading the file: " + e.getMessage());
          }

          System.out.println("[WebServer] New data:");
          System.out.println("[WebServer] FoxesAndRabbitsCache: " + FoxesAndRabbitsCache);
          System.out.println("[WebServer] ImageCompressionCache: " + ImageCompressionCache);
          System.out.println("[WebServer] InsectWarsCache: " + InsectWarsCache);

          // Update data on dynamoDB
          System.out.println("[WebServer] Updating DynamoDB...");

          System.out.println("[WebServer] Updating DynamoDB FoxesAndRabbitsCache...");
          for (Map.Entry<Integer, Float> entry : FoxesAndRabbitsCache.entrySet()) {
            dynamoDBConnector.putItem(
                "FoxesAndRabbits",
                dynamoDBConnector.newItemFoxesAndRabbits(entry.getValue(), entry.getKey()));
          }

          System.out.println("[WebServer] Updating DynamoDB ImageCompressionCache...");
          for (Map.Entry<String, Float> entry : ImageCompressionCache.entrySet()) {
            dynamoDBConnector.putItem(
                "ImageCompression",
                dynamoDBConnector.newItemImageCompression(entry.getValue(), entry.getKey()));
          }

          System.out.println("[WebServer] Updating DynamoDB InsectWarsCache...");
          if (InsectWarsCache != null) {
            dynamoDBConnector.putItem(
                "InsectWars", dynamoDBConnector.newItemInsectWars(InsectWarsCache));
          }
        };

    // Run the autoscaler task every 10 seconds
    executorService.scheduleAtFixedRate(autoscalerTask, 0, DELAY_DYNAMODB, TimeUnit.SECONDS);

    HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    // server.createContext("/", new RootHandler());
    server.createContext("/test", new TestHandler());
    server.createContext("/simulate", new SimulationHandler());
    server.createContext("/compressimage", new CompressImageHandlerImpl());
    server.createContext("/insectwar", new WarSimulationHandler());
    server.start();
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
