package cmatest;

import basicMap.Settings;
import ch.idsia.mario.engine.level.Level;
import ch.idsia.mario.engine.level.LevelParser;
import communication.MarioProcess;
import reader.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static reader.JsonReader.JsonToDoubleArray;

public class LevelStatistics {


    /* For testing purposes -- arg parsing copied from MarioLevelPlayer */
    public static void main(String[] args) throws IOException {
        Settings.setPythonProgram();
        // This is used because it contains code for communicating with the GAN
        MarioEvalFunctionUNC eval = new MarioEvalFunctionUNC();

        Level level;
        // Read input level
        String strLatentVector = "";
        if (args.length > 0) {
            StringBuilder builder = new StringBuilder();
            for (String str : args) {
                builder.append(str);
            }
            strLatentVector = builder.toString();
            Settings.printInfoMsg("Passed vector(s): " + strLatentVector);
            // If the input starts with two square brackets, then it must be an array of arrays,
            // and hence a series of several latent vectors rather than just one. In this case,
            // patch all of the levels together into one long level.
            if(strLatentVector.subSequence(0, 2).equals("[[")) {
                // remove opening/closing brackets
                strLatentVector = strLatentVector.substring(1,strLatentVector.length()-1);
                String levels = "";
                while(strLatentVector.length() > 0) {
                    int end = strLatentVector.indexOf("]")+1;
                    String oneVector = strLatentVector.substring(0,end);
                    System.out.println("ONE VECTOR: " + oneVector);
                    levels += eval.stringToFromGAN(oneVector); // Use the GAN
                    strLatentVector = strLatentVector.substring(end); // discard processed vector
                    if(strLatentVector.length() > 0) {
                        levels += ",";
                        strLatentVector = strLatentVector.substring(1); // discard leading comma
                    }
                }
                levels = "["+levels+"]"; // Put back in brackets
                System.out.println(levels);
                List<List<List<Integer>>> allLevels = JsonReader.JsonToInt(levels);
                // This list contains several separate levels. The following code
                // merges the levels by appending adjacent rows
                ArrayList<List<Integer>> oneLevel = new ArrayList<List<Integer>>();
                // Create the appropriate number of rows in the array
                for(List<Integer> row : allLevels.get(0)) { // Look at first level (assume all are same size)
                    oneLevel.add(new ArrayList<Integer>()); // Empty row
                }
                // Now fill up the rows, one level at a time
                for(List<List<Integer>> aLevel : allLevels) {
                    int index = 0;
                    for(List<Integer> row : aLevel) { // Loot at each row
                        oneLevel.get(index++).addAll(row);
                    }
                }
                // Now create the Mario level from the combined list representation
                level = LevelParser.createLevelJson(oneLevel);
            } else { // Otherwise, there must be a single latent vector, and thus a single level
                double[] latentVector = JsonToDoubleArray(strLatentVector);
                level = eval.levelFromLatentVector(latentVector);
            }
        } else {
            System.out.println("Generating level with default vector");
            level = eval.levelFromLatentVector(new double[] {0.9881835842209917, -0.9986077315374948, 0.9995512051242508, 0.9998643432807639, -0.9976165917284504, -0.9995247114230822, -0.9997001909358728, 0.9995694511739592, -0.9431036754879115, 0.9998155541290887, 0.9997863689962382, -0.8761392912669269, -0.999843833016589, 0.9993230720045649, 0.9995470247917402, -0.9998847606084427, -0.9998322053148382, 0.9997707200294411, -0.9998905141832997, -0.9999512510490688, -0.9533512808031753, 0.9997703088007039, -0.9992229823819915, 0.9953917828622341, 0.9973473366437476, 0.9943030781608361, 0.9995290290713732, -0.9994945079679955, 0.9997109900652238, -0.9988379572928884, 0.9995070647543864, 0.9994132207570211});
        }

        LevelStatistics levelStats = new LevelStatistics(level);
        System.out.println("Broken Pipe Tiles: " + levelStats.numBrokenPipeTiles);
        System.out.println(levelStats.containedInValidPipe(17, 12));


        eval.exit();
        System.exit(0);
    }

    /* Constants used by Level class to represent blocks */
    private static final int EMPTY_SPACE = 0;
    private static final int GROUND_ROCK = 9;
    private static final int TOP_LEFT_PIPE = 10;
    private static final int TOP_RIGHT_PIPE = 11;
    private static final int LEFT_PIPE = 26;
    private static final int RIGHT_PIPE = 27;

    private static final int MagicNumberUndef = -42;

    private Level level;

    /* Total number of pipe tiles */
    public int numPipeTiles = MagicNumberUndef;
    /* Number of pipe tiles in level that are part of a valid pipe */
    public int numValidPipeTiles = MagicNumberUndef;
    /* Number of pipe tiles in level that aren't part of a valid pipe */
    public int numBrokenPipeTiles = MagicNumberUndef;
    /* Total number of ground rocks (including those not on ground level) */
    public int numRocks = MagicNumberUndef;
    /* Number of rocks on ground level */
    public int numGroundRocks = MagicNumberUndef;
    /* Number of empty spaces on ground level */
    public int numGaps = MagicNumberUndef;

    public LevelStatistics(Level level) {
        this.level = level;
        numPipeTiles = 0;
        numBrokenPipeTiles = 0;
        numValidPipeTiles = 0;
        numRocks = 0;
        numGroundRocks = 0;
        numGaps = 0;
        processLevel();
    }

    /* Private helper functions */

    private boolean isPipeTile(int x, int y) {
        int tile = level.getBlock(x, y);
        return tile == LevelStatistics.TOP_LEFT_PIPE || tile == LevelStatistics.TOP_RIGHT_PIPE
                || tile == LevelStatistics.LEFT_PIPE || tile == LevelStatistics.RIGHT_PIPE;
    }

    private boolean isValidPipe(int xLeft, int yTop) {
        int tileTopLeft = level.getBlock(xLeft, yTop);
        int tileTopRight = level.getBlock(xLeft + 1, yTop);
        if (tileTopLeft != LevelStatistics.TOP_LEFT_PIPE || tileTopRight != LevelStatistics.TOP_RIGHT_PIPE)
            return false;

        int y = yTop + 1;
        while (level.getBlock(xLeft, y) == LevelStatistics.LEFT_PIPE
                && level.getBlock(xLeft + 1, y) == LevelStatistics.RIGHT_PIPE) y++;
        if (y == yTop + 1) return false;

        return level.getBlock(xLeft, y) == LevelStatistics.GROUND_ROCK
                && level.getBlock(xLeft + 1, y) == LevelStatistics.GROUND_ROCK;
    }

    private boolean containedInValidPipe(int x, int y) {
        for (int y_i = y; y_i >=0; y_i--) {
            if (isValidPipe(x - 1, y_i) || isValidPipe(x, y_i)) return true;
        }
        return false;
    }

    private void processTile(int x, int y) {
        if (isPipeTile(x, y)) {
            numPipeTiles++;
            if (containedInValidPipe(x, y)) numValidPipeTiles++;
            else numBrokenPipeTiles++;
        }
        if (level.getBlock(x, y) == LevelStatistics.GROUND_ROCK) {
            numRocks++;
            if (y == level.height - 1) numGroundRocks++;
        }
        if (y == level.height - 1 && level.getBlock(x, y) == LevelStatistics.EMPTY_SPACE)
            numGaps++;
    }

    private void processLevel() {
        for (int x = 0; x < level.width; x++) {
            for (int y = 0; y < level.height; y++) {
                processTile(x, y);
            }
        }

    }
}
