package pt.ulisboa.tecnico.cnv.lbas;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoScaler {

  private static long OBS_TIME = 1000 * 60 * 20;
  private static Integer DELAY_KILL = 60;
  private static String AWS_REGION = "us-east-1";
  private static String AMI_ID;
  private static String KEY_NAME = "awskeypair";
  private static String SEC_GROUP_ID = System.getenv("AWS_SECURITY_GROUP");
  private static Integer MAX_CPU_USAGE = 80;
  private static Integer MIN_CPU_USAGE = 20;

  private ConcurrentHashMap<Instance, Double> instanceUsage;
  private AtomicInteger instanceCount;
  private AtomicInteger instanceAvailableCount;

  private AmazonEC2 ec2;
  private AmazonCloudWatch cloudWatch;

  public AutoScaler(
      ConcurrentHashMap<Instance, Double> instances,
      AtomicInteger instanceCount,
      AtomicInteger instanceAvailableCount) {
    ec2 =
        AmazonEC2ClientBuilder.standard()
            .withRegion(AWS_REGION)
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .build();
    cloudWatch =
        AmazonCloudWatchClientBuilder.standard()
            .withRegion(AWS_REGION)
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .build();
    this.instanceUsage = instances;
    this.instanceCount = instanceCount;
    this.instanceAvailableCount = instanceAvailableCount;

    DescribeImagesRequest request = new DescribeImagesRequest();
    request.withFilters(new Filter("name").withValues("CNV-Webserver"));
    DescribeImagesResult result = ec2.describeImages(request);
    AMI_ID = result.getImages().get(0).getImageId();
  }

  public void log(String toPrint) {
    System.out.println(String.format("[%s] %s", this.getClass().getSimpleName(), toPrint));
  }

  private Set<Instance> getInstances() throws Exception {
    Set<Instance> instances = new HashSet<Instance>();
    for (Reservation reservation : ec2.describeInstances().getReservations()) {
      instances.addAll(reservation.getInstances());
    }
    return instances;
  }

  private void startNewInstance() {
    try {
      log("Starting a new instance.");
      RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
      runInstancesRequest
          .withImageId(AMI_ID)
          .withInstanceType("t2.micro")
          .withMinCount(1)
          .withMaxCount(1)
          .withKeyName(KEY_NAME)
          .withSecurityGroupIds(SEC_GROUP_ID);

    } catch (AmazonServiceException ase) {
      log("Caught Exception: " + ase.getMessage());
      log("Reponse Status Code: " + ase.getStatusCode());
      log("Error Code: " + ase.getErrorCode());
      log("Request ID: " + ase.getRequestId());
    }
  }

  private void stopInstance(Instance instance) {
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    Runnable autoscalerTask =
        () -> {
          try {
            String instanceId = instance.getInstanceId();
            log("Stopping instance " + instanceId);
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(instanceId);
            ec2.terminateInstances(termInstanceReq);

          } catch (AmazonServiceException ase) {
            log("Caught Exception: " + ase.getMessage());
            log("Reponse Status Code: " + ase.getStatusCode());
            log("Error Code: " + ase.getErrorCode());
            log("Request ID: " + ase.getRequestId());
          }
        };

    // Remove this instance from the load balancer
    // This way, the load balancer will not choose it for further requests
    instanceUsage.remove(instance);

    // After DELAY_KILL, kill the instance. This is to give it time to return to someone
    executorService.scheduleAtFixedRate(autoscalerTask, DELAY_KILL, 0, TimeUnit.SECONDS);
  }

  public void analyseInstances() {
    try {
      Double avgCPU = 0d;

      // -1 to count for the LBAS instance
      // Totally unrelated fact: the first iteration of this program that worked
      // killed itself :D
      int instanceCountLocal = 0;
      int instanceAvailableCountLocal = 0;
      boolean pendingInstances = false;

      log("===========================================");
      log("Checking data...");
      log("===========================================");

      Set<Instance> instances = getInstances();

      Dimension instanceDimension = new Dimension();
      instanceDimension.setName("InstanceId");
      List<Dimension> dims = new ArrayList<Dimension>();
      dims.add(instanceDimension);

      for (Instance instance : instances) {
        String iid = instance.getInstanceId();
        String state = instance.getState().getName();
        String amiid = instance.getImageId();
        if (state.equals("pending")) {
          pendingInstances = true;

        } else if (state.equals("running") && amiid.equals(AMI_ID)) {
          log("running instance id = " + iid);

          instanceDimension.setValue(iid);
          GetMetricStatisticsRequest request =
              new GetMetricStatisticsRequest()
                  .withStartTime(new Date(new Date().getTime() - OBS_TIME))
                  .withNamespace("AWS/EC2")
                  .withPeriod(60)
                  .withMetricName("CPUUtilization")
                  .withStatistics("Average")
                  .withDimensions(instanceDimension)
                  .withEndTime(new Date());

          List<Datapoint> datapoints = cloudWatch.getMetricStatistics(request).getDatapoints();

          // Get status check
          DescribeInstanceStatusRequest statusRequest =
              new DescribeInstanceStatusRequest().withInstanceIds(iid);
          DescribeInstanceStatusResult statusResult = ec2.describeInstanceStatus(statusRequest);
          InstanceStatus instanceStatus = statusResult.getInstanceStatuses().get(0);
          // InstanceStatusDetails statusDetails =
          // instanceStatus.getInstanceStatus().getDetails().get(0);
          String systemStatus = instanceStatus.getSystemStatus().getStatus();
          // String instanceStatusCheck = statusDetails.getStatus();
          log("System Status Check: " + systemStatus);
          // log("Instance Status Check: " + instanceStatusCheck);

          // Because an instance may be running, but still be initializing
          instanceCountLocal++;
          if (systemStatus.equals("ok")) {
            instanceAvailableCountLocal++;
            instanceUsage.put(instance, -1d);

            if (datapoints.size() != 0) {
              Double cpuUtil = datapoints.get(datapoints.size() - 1).getAverage();
              avgCPU += cpuUtil;

              // Update instance usage
              // instanceUsage.put(instance, cpuUtil);
              instanceUsage.compute(instance, (k, v) -> cpuUtil);

              log(
                  "\tLAST CPU utilization for instance "
                  + iid
                  + " = "
                  + datapoints.get(datapoints.size() - 1).getAverage());
            }
            // for (Datapoint dp : datapoints) {
            //   log(" CPU utilization for instance " + iid + " = " + dp.getAverage());
            // }
          }
        }
      }

      instanceCount.set(instanceCountLocal);
      instanceAvailableCount.set(instanceAvailableCountLocal);

      log("-------------------------------------------");
      log(String.format("Number of instances: %d", instanceCountLocal));
      log(
          String.format("Number of ready instances: %d", instanceAvailableCountLocal));

      log("Usage of instances:");
      for (Map.Entry<Instance, Double> entry : instanceUsage.entrySet()) {
        log(
            String.format("Instance %s: %s%%", entry.getKey().getInstanceId(), entry.getValue()));
      }

      if (instanceCountLocal == 0) {
        if (pendingInstances) {
          log("There are pending instances. Waiting...");
        } else {
          startNewInstance();
        }
        return;
      }

      avgCPU /= (double)instanceAvailableCountLocal;
      log("Average CPU utilization = " + avgCPU);

      log("-------------------------------------------");

      if (avgCPU < MIN_CPU_USAGE) {
        log(String.format("Average CPU utilization is under %d%%", MIN_CPU_USAGE));
        if (instanceCountLocal == 1) {
          log("Only one instance running. Cannot stop.");
          return;
        }
        log("Stopping an instance.");

        // Stop the instance with the lowest CPU utilization
        Instance instanceWithMinUsage =
            instanceUsage.entrySet().stream()
                .min(ConcurrentHashMap.Entry.comparingByValue())
                .map(ConcurrentHashMap.Entry::getKey)
                .orElse(null);

        log("Stopping instance " + instanceWithMinUsage.getInstanceId());
        stopInstance(instanceWithMinUsage);

      } else if (avgCPU > 80) {
        log(String.format("Average CPU utilization is over %d%%", MAX_CPU_USAGE));
        startNewInstance();
      }

    } catch (AmazonServiceException ase) {
      log("Caught Exception: " + ase.getMessage());
      log("Reponse Status Code: " + ase.getStatusCode());
      log("Error Code: " + ase.getErrorCode());
      log("Request ID: " + ase.getRequestId());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
