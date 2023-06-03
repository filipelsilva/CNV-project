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

    private AmazonDynamoDB dynamoDB;

    public void createTable(String tableName) {
        try {
            dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build();

            // Create a table with a primary hash key named 'name', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchemaElement().withAttributeName("name").withKeyType(KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition().withAttributeName("name").withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);

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
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void describeTable(String tableName) {
        DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
        TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
        System.out.println("Table Description: " + tableDescription);
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

    public void getItem(String tableName, Map<String, Condition> scanFilter) {
        try {
            // Scan items for movies with a year attribute greater than 1985
            // HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
            // Condition condition = new Condition()
            //     .withComparisonOperator(ComparisonOperator.GT.toString())
            //     .withAttributeValueList(new AttributeValue().withN("1985"));
            // scanFilter.put("year", condition);
            ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            System.out.println("Result: " + scanResult);

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

    public Map<String, AttributeValue> newItemImageCompression(Long instructions, int width, int height, String format, float compression) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("type", new AttributeValue("ImageCompression"));
        item.put("instructions", new AttributeValue().withN(Long.toString(instructions)));
        item.put("width", new AttributeValue().withN(Long.toString(width)));
        item.put("height", new AttributeValue().withN(Long.toString(height)));
        item.put("format", new AttributeValue(format));
        item.put("compression", new AttributeValue().withN(Float.toString(compression)));
        return item;
    }

    public Map<String, AttributeValue> newItemFoxesAndRabbits(Long instructions, int world, int generations) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("type", new AttributeValue("FoxesAndRabbits"));
        item.put("instructions", new AttributeValue().withN(Long.toString(instructions)));
        item.put("world", new AttributeValue().withN(Long.toString(world)));
        item.put("generations", new AttributeValue().withN(Long.toString(generations)));
        return item;
    }

    public Map<String, AttributeValue> newItemInsectWars(Long instructions, int max, int sz1, int sz2) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("type", new AttributeValue("InsectWars"));
        item.put("instructions", new AttributeValue().withN(Long.toString(instructions)));
        item.put("max", new AttributeValue().withN(Long.toString(max)));
        item.put("sz1", new AttributeValue().withN(Long.toString(sz1)));
        item.put("sz2", new AttributeValue().withN(Long.toString(sz2)));
        return item;
    }
}
