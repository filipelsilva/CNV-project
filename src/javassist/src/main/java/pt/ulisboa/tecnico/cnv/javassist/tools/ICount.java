package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javassist.CannotCompileException;
import javassist.CtBehavior;

public class ICount extends CodeDumper {

  private static int counter = 0;
  private static int BATCH_SIZE = 5;

  private static Map<Integer, Float> FoxesAndRabbitsCache = new HashMap<>();
  private static Map<String, Float> ImageCompressionCache = new HashMap<>();
  private static Float InsectWarsCache = null;

  private static int worldFoxesRabbits = 0;
  private static Map<Long, Long> ninstsPerThread = new HashMap<>();

  private static final Set<String> ignoredMethods =
      Set.of(
          "filter",
          "registerApplicationClasspathSpis",
          "unsetOrdering",
          "equals",
          "dispose",
          "readUTF",
          "length",
          "getRenderedImage",
          "hasRaster",
          "finalize",
          "getRaster");

  public ICount(List<String> packageNameList, String writeDestination) {
    super(packageNameList, writeDestination);
  }

  public static int getCounter() {
    return counter;
  }

    public static void incCounter() {
    counter += 1;
    System.out.println(
        String.format("[ICount] Incrementing counter: (%s/%s)...", counter, BATCH_SIZE));

    if (getCounter() == BATCH_SIZE) {
      // Reset counter
      counter = 0;

      // Update data on dynamoDB (indirectly; the report will explain this)
      System.out.println("[ICount] Updating estimations...");
      try {
          FileWriter fileWriter = new FileWriter("/tmp/dynamodb");

          System.out.println("[ICount] Updating FoxesAndRabbitsCache...");
          for (Map.Entry<Integer, Float> entry : FoxesAndRabbitsCache.entrySet()) {
              fileWriter.write(String.format("FoxesAndRabbits %s %s\n", entry.getKey(), entry.getValue()));
          }
      
          System.out.println("[ICount] Updating ImageCompressionCache...");
          for (Map.Entry<String, Float> entry : ImageCompressionCache.entrySet()) {
              fileWriter.write(String.format("ImageCompression %s %s\n", entry.getKey(), entry.getValue()));
          }

          System.out.println("[ICount] Updating InsectWarsCache...");
          if (InsectWarsCache != null) {
              fileWriter.write(String.format("InsectWars %s\n", InsectWarsCache));
          }

          System.out.println("[ICount] Updated estimations.");

          // Close the file writer
          fileWriter.close();

      } catch (IOException e) {
          System.out.println("An error occurred while writing to the file: " + e.getMessage());
      }
    }
  }

  public static Long getThreadInfo(Long threadID) {
    return ninstsPerThread.get(threadID);
  }

  public static void setThreadInfo(int position, int length) {
    Long threadID = Thread.currentThread().getId();
    ninstsPerThread.put(threadID, ninstsPerThread.getOrDefault(threadID, 0L) + length);
  }

  public static void clearThreadInfo(Long threadID) {
    ninstsPerThread.remove(threadID);
  }

  public static void setWorldFoxesRabbits(int newWorldFoxesRabbits) {
    worldFoxesRabbits = newWorldFoxesRabbits;
  }

  public static void treatImageCompression(
      BufferedImage bi, String targetFormat, float compressionQuality) {
    Long threadID = Thread.currentThread().getId();
    System.out.println(
        String.format(
            "[%s Image Compression] Image size is %sx%s",
            ICount.class.getSimpleName(), bi.getWidth(), bi.getHeight()));
    System.out.println(
        String.format(
            "[%s Image Compression] Target format is %s",
            ICount.class.getSimpleName(), targetFormat));
    System.out.println(
        String.format(
            "[%s Image Compression] Compression quality is %s",
            ICount.class.getSimpleName(), compressionQuality));
    System.out.println(
        String.format(
            "[%s Image Compression] ThreadID is %s", ICount.class.getSimpleName(), threadID));
    System.out.println(
        String.format(
            "[%s Image Compression] Number of instructions ran is %s",
            ICount.class.getSimpleName(), getThreadInfo(threadID)));

    if (compressionQuality == 0) {
      compressionQuality = 1;
    }
    Float instructionsPerImageSizePerCompressionFactor =
        (float) getThreadInfo(threadID)
            / ((float) (bi.getWidth() * bi.getHeight()) * compressionQuality);

    // Update local cache with info
    if (ImageCompressionCache.containsKey(targetFormat)) {
      ImageCompressionCache.put(
          targetFormat,
          (ImageCompressionCache.get(targetFormat) + instructionsPerImageSizePerCompressionFactor)
              / 2);
    } else {
      ImageCompressionCache.put(targetFormat, instructionsPerImageSizePerCompressionFactor);
    }

    // Every BATCH_SIZE requests, update the data on dynamoDB
    incCounter();

    // Reset the number of instructions per thread for this thread
    clearThreadInfo(threadID);
  }

  public static void treatFoxesAndRabbits(int n_generations) {
    Long threadID = Thread.currentThread().getId();
    System.out.println(
        String.format(
            "[%s Foxes And Rabbits] World is %s", ICount.class.getSimpleName(), worldFoxesRabbits));
    System.out.println(
        String.format(
            "[%s Foxes And Rabbits] Number of generations is %s",
            ICount.class.getSimpleName(), n_generations));
    System.out.println(
        String.format(
            "[%s Foxes And Rabbits] ThreadID is %s", ICount.class.getSimpleName(), threadID));
    System.out.println(
        String.format(
            "[%s Foxes And Rabbits] Number of instructions ran is %s",
            ICount.class.getSimpleName(), getThreadInfo(threadID)));

    if (n_generations == 0) {
      n_generations = 1;
    }
    Float instructionsPerGeneration = (float) getThreadInfo(threadID) / (float) n_generations;

    // Update local cache map with info
    if (FoxesAndRabbitsCache.containsKey(worldFoxesRabbits)) {
      FoxesAndRabbitsCache.put(
          worldFoxesRabbits,
          (FoxesAndRabbitsCache.get(worldFoxesRabbits) + instructionsPerGeneration) / 2);
    } else {
      FoxesAndRabbitsCache.put(worldFoxesRabbits, instructionsPerGeneration);
    }

    // Every BATCH_SIZE requests, update the data on dynamoDB
    incCounter();

    // Reset the number of instructions per thread for this thread
    clearThreadInfo(threadID);
  }

  public static void treatInsectWars(int max, int sz1, int sz2) {
    Long threadID = Thread.currentThread().getId();
    System.out.println(
        String.format(
            "[%s Insect Wars] Max simulation rounds is %s", ICount.class.getSimpleName(), max));
    System.out.println(
        String.format("[%s Insect Wars] Army 1 size is %s", ICount.class.getSimpleName(), sz1));
    System.out.println(
        String.format("[%s Insect Wars] Army 2 size is %s", ICount.class.getSimpleName(), sz2));
    System.out.println(
        String.format("[%s Insect Wars] ThreadID is %s", ICount.class.getSimpleName(), threadID));
    System.out.println(
        String.format(
            "[%s Insect Wars] Number of instructions ran is %s",
            ICount.class.getSimpleName(), getThreadInfo(threadID)));

    float ratio = (float) sz1 / (float) sz2;
    if (sz1 > sz2) {
      ratio = (float) sz2 / (float) sz1;
    }
    if (ratio == 0) {
      ratio = 0.1f;
    }
    Float instructionsPerRoundPerSizeTimesRatio =
        5_000 * (float) getThreadInfo(threadID) / ((float) max * (float) (sz1 + sz2) * ratio);

    // Update local cache map with info
    if (InsectWarsCache == null) {
      InsectWarsCache = instructionsPerRoundPerSizeTimesRatio;
    } else {
      InsectWarsCache += instructionsPerRoundPerSizeTimesRatio;
      InsectWarsCache /= 2;
    }

    // Every BATCH_SIZE requests, update the data on dynamoDB
    incCounter();

    // Reset the number of instructions per thread for this thread
    clearThreadInfo(threadID);
  }

  @Override
  protected void transform(CtBehavior behavior) throws Exception {
    if (!ignoredMethods.contains(behavior.getName())) {
      super.transform(behavior);
    }
    switch (behavior.getName()) {
      case "process":
        behavior.insertAfter(
            String.format("%s.treatImageCompression($1, $2, $3);", ICount.class.getName()));
        break;
      case "populate":
        behavior.insertAfter(String.format("%s.setWorldFoxesRabbits($1);", ICount.class.getName()));
        break;
      case "runSimulation":
        behavior.insertAfter(String.format("%s.treatFoxesAndRabbits($1);", ICount.class.getName()));
        break;
      case "war":
        behavior.insertAfter(
            String.format("%s.treatInsectWars($1, $2, $3);", ICount.class.getName()));
        break;
    }
  }

  @Override
  protected void transform(BasicBlock block) throws CannotCompileException {
    super.transform(block);
    block.behavior.insertAt(
        block.line,
        String.format(
            "%s.setThreadInfo(%s, %s);",
            ICount.class.getName(), block.getPosition(), block.getLength()));
  }
}
