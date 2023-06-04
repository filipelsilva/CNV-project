package pt.ulisboa.tecnico.cnv.lbas;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.ec2.model.Instance;

public class LBAS {
    private static AutoScaler autoScaler;
    // private static LoadBalancer loadBalancer;

    public static void main(String[] args) {
        ConcurrentHashMap<Instance, Double> instances = new ConcurrentHashMap<>();
        autoScaler = new AutoScaler(instances);
        // loadBalancer = new LoadBalancer(instaces);

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        Runnable autoscalerTask = () -> {
            autoScaler.analyseInstances();
        };

        // Run the autoscaler task every 30 seconds
        executorService.scheduleAtFixedRate(autoscalerTask, 0, 5, TimeUnit.SECONDS);

        // Run the load balancer forever
        // loadBalancer.runForever();
    }
}
