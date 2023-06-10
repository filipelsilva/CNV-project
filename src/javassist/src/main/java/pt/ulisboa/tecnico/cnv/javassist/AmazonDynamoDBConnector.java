package pt.ulisboa.tecnico.cnv.javassist;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
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

public class AmazonDynamoDBConnector {

    private String AWS_REGION = "us-east-1";

    private AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withCredentials(new EnvironmentVariableCredentialsProvider())
        .withRegion(AWS_REGION)
        .build();

    public void waitForTable(String tableName) {
        try {
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);
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
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);

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

    public void putItem(String tableName, Map<String, AttributeValue> item) {
        try {
            dynamoDB.putItem(new PutItemRequest(tableName, item));

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

    public Map<String, AttributeValue> newItemImageCompression(Float instructionsPerImageSizePerCompressionFactor, String format) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("instructionsPerImageSizePerCompressionFactor", new AttributeValue().withN(Float.toString(instructionsPerImageSizePerCompressionFactor)));
        item.put("format", new AttributeValue(format));
        return item;
    }

    public Map<String, AttributeValue> newItemFoxesAndRabbits(Float instructionsPerGeneration, int world) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("instructionsPerGeneration", new AttributeValue().withN(Float.toString(instructionsPerGeneration)));
        item.put("world", new AttributeValue().withN(Integer.toString(world)));
        return item;
    }

    public Map<String, AttributeValue> newItemInsectWars(Float instructionsPerRoundPerSizeTimesRatio) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("instructionsPerRoundPerSizeTimesRatio", new AttributeValue().withN(Float.toString(instructionsPerRoundPerSizeTimesRatio)));
        return item;
    }
}
