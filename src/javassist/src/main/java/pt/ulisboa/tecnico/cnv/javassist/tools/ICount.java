package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.awt.image.BufferedImage;
import java.lang.Thread;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;
// import pt.ulisboa.tecnico.cnv.javassist.AmazonDynamoDBConnector;

public class ICount extends CodeDumper {

    private static int worldFoxesRabbits = 0;
    private static Map<Integer, Long> ninstsPerThread = new HashMap<>();
    // private static AmazonDynamoDBConnector dynamoDBConnector = new AmazonDynamoDBConnector();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static Long getThreadInfo(int threadID) {
        return ninstsPerThread.get(threadID);
    }

    public static void setThreadInfo(int position, int length, int threadID) {
        ninstsPerThread.put(threadID, ninstsPerThread.getOrDefault(threadID, 0L) + length);
    }

    public static void clearThreadInfo(int threadID) {
        ninstsPerThread.remove(threadID);
    }

    public static void setWorldFoxesRabbits(int newWorldFoxesRabbits) {
        worldFoxesRabbits = newWorldFoxesRabbits;
    }

    public static void treatImageCompression(BufferedImage bi, String targetFormat, float compressionQuality, int threadID) {
        System.out.println(String.format("[%s Image Compression] Image size is %sx%s", ICount.class.getSimpleName(), bi.getWidth(), bi.getHeight()));
        System.out.println(String.format("[%s Image Compression] Target format is %s", ICount.class.getSimpleName(), targetFormat));
        System.out.println(String.format("[%s Image Compression] Compression quality is %s", ICount.class.getSimpleName(), compressionQuality));
        System.out.println(String.format("[%s Image Compression] ThreadID is %s", ICount.class.getSimpleName(), threadID));
        System.out.println(String.format("[%s Image Compression] Number of instructions ran is %s", ICount.class.getSimpleName(), getThreadInfo(threadID)));

        // // Update data on dynamoDB
        // dynamoDBConnector.putItem(
        //         "info",
        //         dynamoDBConnector.newItemImageCompression(
        //             getThreadInfo(threadID),
        //             bi.getWidth(),
        //             bi.getHeight(),
        //             targetFormat,
        //             compressionQuality
        //             )
        //         );

        // Reset the number of instructions per thread for this thread
        clearThreadInfo(threadID);
    }

    public static void treatFoxesAndRabbits(int n_generations, int threadID) {
        System.out.println(String.format("[%s Foxes And Rabbits] World is %s", ICount.class.getSimpleName(), worldFoxesRabbits));
        System.out.println(String.format("[%s Foxes And Rabbits] Number of generations is %s", ICount.class.getSimpleName(), n_generations));
        System.out.println(String.format("[%s Foxes And Rabbits] ThreadID is %s", ICount.class.getSimpleName(), threadID));
        System.out.println(String.format("[%s Foxes And Rabbits] Number of instructions ran is %s", ICount.class.getSimpleName(), getThreadInfo(threadID)));

        // // Update data on dynamoDB
        // dynamoDBConnector.putItem(
        //         "info",
        //         dynamoDBConnector.newItemFoxesAndRabbits(
        //             getThreadInfo(threadID),
        //             worldFoxesRabbits,
        //             n_generations
        //             )
        //         );

        // Reset the number of instructions per thread for this thread
        clearThreadInfo(threadID);
    }

    public static void treatInsectWars(int max, int sz1, int sz2, int threadID) {
        System.out.println(String.format("[%s Insect Wars] Max simulation rounds is %s", ICount.class.getSimpleName(), max));
        System.out.println(String.format("[%s Insect Wars] Army 1 size is %s", ICount.class.getSimpleName(), sz1));
        System.out.println(String.format("[%s Insect Wars] Army 2 size is %s", ICount.class.getSimpleName(), sz2));
        System.out.println(String.format("[%s Insect Wars] ThreadID is %s", ICount.class.getSimpleName(), threadID));
        System.out.println(String.format("[%s Insect Wars] Number of instructions ran is %s", ICount.class.getSimpleName(), getThreadInfo(threadID)));

        // // Update data on dynamoDB
        // dynamoDBConnector.putItem(
        //         "info",
        //         dynamoDBConnector.newItemInsectWars(
        //             getThreadInfo(threadID),
        //             max,
        //             sz1,
        //             sz2
        //             )
        //         );

        // Reset the number of instructions per thread for this thread
        clearThreadInfo(threadID);
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        switch (behavior.getName()) {
            case "process":
                behavior.insertAfter(String.format("%s.treatImageCompression($1, $2, $3, %s);", ICount.class.getName(), Thread.currentThread().getId()));
                break;
            case "populate":
                behavior.insertAfter(String.format("%s.setWorldFoxesRabbits($1);", ICount.class.getName()));
                break;
            case "runSimulation":
                behavior.insertAfter(String.format("%s.treatFoxesAndRabbits($1, %s);", ICount.class.getName(), Thread.currentThread().getId()));
                break;
            case "war":
                behavior.insertAfter(String.format("%s.treatInsectWars($1, $2, $3, %s);", ICount.class.getName(), Thread.currentThread().getId()));
                break;
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.setThreadInfo(%s, %s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength(), Thread.currentThread().getId()));
    }
}
