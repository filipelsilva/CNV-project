package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.lang.Thread;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class ICount extends CodeDumper {

    /**
     * Number of executed basic blocks.
     */
    private static long nblocks = 0;

    /**
     * Number of executed methods.
     */
    private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    /**
      * Map of executed instructions per thread.
      */
    private static Map<Long, Long> ninstsPerThread = new HashMap<>();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length, long threadID) {
        nblocks++;
        ninsts += length;
        ninstsPerThread.put(threadID, ninstsPerThread.getOrDefault(threadID, 0L) + length);
    }

    public static void incBehavior(String name) {
        nmethods++;
    }

    public static void printStatistics() {
        System.out.println(String.format("[%s] Number of executed methods: %s", ICount.class.getSimpleName(), nmethods));
        System.out.println(String.format("[%s] Number of executed basic blocks: %s", ICount.class.getSimpleName(), nblocks));
        System.out.println(String.format("[%s] Number of executed instructions: %s", ICount.class.getSimpleName(), ninsts));
        System.out.println(String.format("[%s] Number of executed instructions per thread: %s", ICount.class.getSimpleName(), ninstsPerThread));
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));

        if (behavior.getName().equals("main")) {
            behavior.insertAfter(String.format("%s.printStatistics();", ICount.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength(), Thread.currentThread().getId()));
    }

}
