package pt.ulisboa.tecnico.cnv.lbas;

import com.amazonaws.services.ec2.model.Instance;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LBAS {
  private static AutoScaler autoScaler;
  private static LoadBalancer loadBalancer;

  public static void main(String[] args) {
    int DELAY_AUTOSCALER = 10;

    ConcurrentHashMap<Instance, Double> instances = new ConcurrentHashMap<>();
    AtomicInteger instanceCount = new AtomicInteger(0);
    AtomicInteger instanceAvailableCount = new AtomicInteger(0);
    autoScaler = new AutoScaler(instances, instanceCount, instanceAvailableCount);
    loadBalancer = new LoadBalancer(instances, instanceCount, instanceAvailableCount);

    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    Runnable autoscalerTask =
        () -> {
          autoScaler.analyseInstances();
        };

    // Run the autoscaler task every 30 seconds
    executorService.scheduleAtFixedRate(autoscalerTask, 0, DELAY_AUTOSCALER, TimeUnit.SECONDS);

    // Run the load balancer forever
    loadBalancer.serveForever();
  }
}
