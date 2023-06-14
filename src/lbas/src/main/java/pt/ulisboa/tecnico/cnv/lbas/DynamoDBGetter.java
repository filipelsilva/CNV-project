package pt.ulisboa.tecnico.cnv.lbas;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import java.util.Map;

public class DynamoDBGetter {

  private String AWS_REGION = "us-east-1";
  private AmazonDynamoDB dynamoDBClient =
      AmazonDynamoDBClientBuilder.standard()
          .withCredentials(new EnvironmentVariableCredentialsProvider())
          .withRegion(AWS_REGION)
          .build();

  public DynamoDBGetter() {
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

  public ScanResult getDynamoDB(String tableName, Map<String, Condition> scanFilter) {
    try {
      ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
      ScanResult scanResult = dynamoDBClient.scan(scanRequest);
      log("Query result: " + scanResult);
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
}
