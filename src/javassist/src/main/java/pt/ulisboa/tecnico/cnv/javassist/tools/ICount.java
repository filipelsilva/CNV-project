package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.awt.image.BufferedImage;
import java.lang.Thread;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;

public class ICount extends CodeDumper {

    /**
     * Number of executed basic blocks.
     */
    private static long nblocks = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    /**
      * Map of executed instructions per thread.
      */
    private static Map<Integer, Long> ninstsPerThread = new HashMap<>();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length, int threadID) {
        nblocks++;
        ninsts += length;
        ninstsPerThread.put(threadID, ninstsPerThread.getOrDefault(threadID, 0L) + length);
    }

    public static void printStatistics(String name) {
        System.out.println(String.format("[%s %s] Number of executed basic blocks: %s", ICount.class.getSimpleName(), name, nblocks));
        System.out.println(String.format("[%s %s] Number of executed instructions: %s", ICount.class.getSimpleName(), name, ninsts));
        System.out.println(String.format("[%s %s] Number of executed instructions per thread: %s", ICount.class.getSimpleName(), name, ninstsPerThread));
    }

    public static void treatImageCompression(BufferedImage bi, String targetFormat, float compressionQuality) {
        System.out.println(String.format("[%s ImageCompression] Image size is %sx%s", ICount.class.getSimpleName(), bi.getWidth(), bi.getHeight()));
        System.out.println(String.format("[%s ImageCompression] Target format is %s", ICount.class.getSimpleName(), targetFormat));
        System.out.println(String.format("[%s ImageCompression] Compression quality is %s", ICount.class.getSimpleName(), compressionQuality));
    }

    public static void treatFoxesAndRabbits(int n_generations) {
        // TODO ir buscar world
        System.out.println(String.format("[%s Foxes And Rabbits] Number of generations is %s", ICount.class.getSimpleName(), n_generations));
    }

    public static void treatInsectWars(int max, int sz1, int sz2) {
        System.out.println(String.format("[%s Insect Wars] Max simulation rounds is %s", ICount.class.getSimpleName(), max));
        System.out.println(String.format("[%s Insect Wars] Army 1 size is %s", ICount.class.getSimpleName(), sz1));
        System.out.println(String.format("[%s Insect Wars] Army 2 size is %s", ICount.class.getSimpleName(), sz2));
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        switch (behavior.getName()) {
            case "process":
                behavior.insertAfter(String.format("%s.treatImageCompression($1, $2, $3);", ICount.class.getName()));
                break;
            case "runSimulation":
                behavior.insertAfter(String.format("%s.treatFoxesAndRabbits($1);", ICount.class.getName()));
                break;
            case "war":
                behavior.insertAfter(String.format("%s.treatInsectWars($1, $2, $3);", ICount.class.getName()));
                break;
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength(), Thread.currentThread().getId()));
    }
}
