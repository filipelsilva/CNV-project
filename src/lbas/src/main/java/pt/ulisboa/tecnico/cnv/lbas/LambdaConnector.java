package pt.ulisboa.tecnico.cnv.lbas;

import java.util.Map;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;

public class LambdaConnector {

  private String AWS_REGION = "us-east-1";
  private LambdaClient awsLambda =
      LambdaClient.builder()
          .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
          .region(Region.of(AWS_REGION))
          .build();

  public String payloadGenerator(Map<String, String> parameters) {
    String ret = "{";

    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      ret += "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\",";
    }
    ret = ret.substring(0, ret.length() - 1) + "}";

    System.out.println(ret);
    return ret;
  }

  public String invokeFunction(String functionName, String json) {
    try {
      SdkBytes payload = SdkBytes.fromUtf8String(json);

      InvokeRequest request =
          InvokeRequest.builder().functionName(functionName).payload(payload).build();

      InvokeResponse res = awsLambda.invoke(request);
      String value = res.payload().asUtf8String();
      return value;

    } catch (LambdaException e) {
      System.err.println(e.getMessage());
    }
    return null;
  }

  public void close() {
    awsLambda.close();
  }
}
