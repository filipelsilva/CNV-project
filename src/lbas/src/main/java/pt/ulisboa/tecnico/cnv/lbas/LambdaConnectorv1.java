package pt.ulisboa.tecnico.cnv.lbas;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LambdaConnectorv1 {

  public static String payloadGenerator(Map<String, String> parameters) {
    String ret = "{";

    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      ret += "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\",";
    }
    ret = ret.substring(0, ret.length() - 1) + "}";

    System.out.println(ret);
    return ret;
  }

  public static String invokeFunction(AWSLambda awsLambda, String functionName, String json) {
    String value = null;
    try {
      InvokeRequest request = new InvokeRequest().withFunctionName(functionName).withPayload(json);

      InvokeResult res = awsLambda.invoke(request);
      value = new String(res.getPayload().array(), StandardCharsets.UTF_8);
      System.out.println(value);

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }

    return value;
  }

  public static void main(String[] args) {
    AWSLambda awsLambda =
        AWSLambdaClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion(Regions.US_EAST_1)
            .build();

    invokeFunction(awsLambda, "CNV-InsectWars", "{\"max\":\"2\",\"army1\":\"10\",\"army2\":\"9\"}");
  }
}
