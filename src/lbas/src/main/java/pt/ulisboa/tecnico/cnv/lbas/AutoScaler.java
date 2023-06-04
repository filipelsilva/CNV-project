package pt.ulisboa.tecnico.cnv.lbas;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class AutoScaler {

    private static long OBS_TIME = 1000 * 60 * 20;
    private static String AWS_REGION = "us-east-1";
    private static String AMI_ID = "ami-0c3380fb1b339e040";
    private static String KEY_NAME = "awskeypair";
    private static String SEC_GROUP_ID = "sg-0bd30fee47aed5db8";
    private static Integer MAX_CPU_USAGE = 80;
    private static Integer MIN_CPU_USAGE = 20;

    private ConcurrentHashMap<Instance, Double> instances;

    private AmazonEC2 ec2;
    private AmazonCloudWatch cloudWatch;

    public AutoScaler(ConcurrentHashMap<Instance, Double> instances) {
        ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        this.instances = instances;
    }

    private Set<Instance> getInstances(AmazonEC2 ec2) throws Exception {
        Set<Instance> instances = new HashSet<Instance>();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            instances.addAll(reservation.getInstances());
        }
        return instances;
    }

    private void startNewInstance() {
        try {
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(KEY_NAME)
                .withSecurityGroupIds(SEC_GROUP_ID);
            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
            // TODO add this new instance to the load balancer
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private void stopInstance(String instanceId) {
        try {
            System.out.println("Stopping instance " + instanceId);
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(instanceId);
            ec2.terminateInstances(termInstanceReq);
            // TODO remove this instance from the load balancer
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    public void analyseInstances() {
        try {
            Double avgCPU = 0d;
            Integer instanceAvailableCount = 0;
            Integer instanceCount = 0;

            System.out.println("===========================================");
            System.out.println("Checking data...");
            System.out.println("===========================================");

            Set<Instance> instances = getInstances(ec2);
            System.out.println("total instances = " + instances.size());

            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);

            for (Instance instance : instances) {
                String iid = instance.getInstanceId();
                String state = instance.getState().getName();
                String amiid = instance.getImageId(); // TODO add check for image id later
                if (state.equals("running")) { // && amiid.equals(AMI_ID))
                    System.out.println("running instance id = " + iid);
                    instanceDimension.setValue(iid);
                    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                        .withStartTime(new Date(new Date().getTime() - OBS_TIME))
                        .withNamespace("AWS/EC2")
                        .withPeriod(60)
                        .withMetricName("CPUUtilization")
                        .withStatistics("Average")
                        .withDimensions(instanceDimension)
                        .withEndTime(new Date());

                    List<Datapoint> datapoints = cloudWatch.getMetricStatistics(request).getDatapoints();

                    // Because an instance may be running, but still be initializing
                    instanceCount++;
                    if (datapoints.size() != 0) {
                        instanceAvailableCount++;
                        avgCPU += datapoints.get(datapoints.size() - 1).getAverage();
                        System.out.println(" LAST CPU utilization for instance " + iid + " = " + datapoints.get(datapoints.size() - 1).getAverage());
                    }
                    for (Datapoint dp : datapoints) {
                        System.out.println(" CPU utilization for instance " + iid + " = " + dp.getAverage());
                    }
                } else {
                    System.out.println("instance id = " + iid);
                }
                System.out.println("Instance State : " + state +".");
            }

            System.out.println(String.format("Number of instances: %d", instanceCount));
            System.out.println(String.format("Number of ready instances: %d", instanceAvailableCount));
            if (instanceCount == 0) {
                System.out.println("Starting a new instance.");
                // startNewInstance();
                return;
            }

            avgCPU /= instanceAvailableCount;
            System.out.println("Average CPU utilization = " + avgCPU);

            if (avgCPU < MIN_CPU_USAGE) {
                System.out.println(String.format("Average CPU utilization is under %d%%", MIN_CPU_USAGE));
                System.out.println("Stopping an instance.");
                if (instanceCount == 1) {
                    System.out.println("Only one instance running. Cannot stop.");
                    return;
                }
                // stop the instance with the lowest CPU utilization
                // stopInstance(instances.get(instances.size() - 1).getInstanceId());
            } else if (avgCPU > 80) {
                System.out.println(String.format("Average CPU utilization is over %d%%", MAX_CPU_USAGE));
                System.out.println("Starting a new instance.");
                // startNewInstance();
            }

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
