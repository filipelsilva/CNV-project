package pt.ulisboa.tecnico.cnv.javassist;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import java.util.HashMap;
import java.util.Map;

public class AmazonDynamoDBConnector {

  private String AWS_REGION = "us-east-1";

  private AmazonDynamoDB dynamoDB =
      AmazonDynamoDBClientBuilder.standard()
          .withCredentials(new EnvironmentVariableCredentialsProvider())
          .withRegion(AWS_REGION)
          .build();

  public void putItem(String tableName, Map<String, AttributeValue> item) {
    try {
      dynamoDB.putItem(new PutItemRequest(tableName, item));

    } catch (AmazonServiceException ase) {
      System.out.println(
          "Caught an AmazonServiceException, which means your request made it "
              + "to AWS, but was rejected with an error response for some reason.");
      System.out.println("Error Message:    " + ase.getMessage());
      System.out.println("HTTP Status Code: " + ase.getStatusCode());
      System.out.println("AWS Error Code:   " + ase.getErrorCode());
      System.out.println("Error Type:       " + ase.getErrorType());
      System.out.println("Request ID:       " + ase.getRequestId());
    } catch (AmazonClientException ace) {
      System.out.println(
          "Caught an AmazonClientException, which means the client encountered "
              + "a serious internal problem while trying to communicate with AWS, "
              + "such as not being able to access the network.");
      System.out.println("Error Message: " + ace.getMessage());
    }
  }

  public Map<String, AttributeValue> newItemImageCompression(
      Float instructionsPerImageSizePerCompressionFactor, String format) {
    Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
    item.put(
        "instructionsPerImageSizePerCompressionFactor",
        new AttributeValue().withN(Float.toString(instructionsPerImageSizePerCompressionFactor)));
    item.put("format", new AttributeValue(format));
    return item;
  }

  public Map<String, AttributeValue> newItemFoxesAndRabbits(
      Float instructionsPerGeneration, int world) {
    Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
    item.put(
        "instructionsPerGeneration",
        new AttributeValue().withN(Float.toString(instructionsPerGeneration)));
    item.put("world", new AttributeValue().withN(Integer.toString(world)));
    return item;
  }

  public Map<String, AttributeValue> newItemInsectWars(
      Float instructionsPerRoundPerSizeTimesRatio) {
    Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
    item.put(
        "instructionsPerRoundPerSizeTimesRatio",
        new AttributeValue().withN(Float.toString(instructionsPerRoundPerSizeTimesRatio)));
    return item;
  }
}
