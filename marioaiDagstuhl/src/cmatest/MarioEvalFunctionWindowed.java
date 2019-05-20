package cmatest;

import ch.idsia.mario.engine.level.Level;
import ch.idsia.mario.engine.level.LevelParser;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.tools.EvaluationInfo;
import communication.GANProcess;
import communication.MarioProcess;
import fr.inria.optimization.cmaes.fitness.IObjectiveFunction;
import reader.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static reader.JsonReader.JsonToDoubleArray;

public class MarioEvalFunctionWindowed implements IObjectiveFunction {

    // This is the distance that Mario traverses when he beats the short levels
    // that we are generating. It would need to be changed if we train on larger
    // levels or in any way change the level length.
    public static final int LEVEL_LENGTH = 704;

    private GANProcess ganProcess;
    private MarioProcess marioProcess;
    private MarioProcess simulationProcess;

    // changing floor will change the reason for termination
    // (in conjunction with the target value)
    // see cma.options.stopFitness
    static double floor = 0.0;

    public MarioEvalFunctionWindowed() throws IOException {
        // set up process for GAN
        ganProcess = new GANProcess();
        ganProcess.start();

        setupMarioProcesses();
        setupConsumptionOfNonDataResponses();
    }

    public MarioEvalFunctionWindowed(String GANPath, String GANDim) throws IOException {
        // set up process for GAN
        ganProcess = new GANProcess(GANPath, GANDim);
        ganProcess.start();

        setupMarioProcesses();
        setupConsumptionOfNonDataResponses();
    }

    private void setupMarioProcesses()
    {
        // set up mario game
        marioProcess = new MarioProcess();
        marioProcess.start();
        // set up a process for simulating input moves in order to see if a level can be completed in a particular way
        simulationProcess = new MarioProcess();
        simulationProcess.start();
    }

    private void setupConsumptionOfNonDataResponses()
    {
        // consume all start-up messages that are not data responses
        String response = "";
        while(!response.equals("READY")) {
            response = ganProcess.commRecv();
        }
    }

    /**
     * Takes a json String representing several levels
     * and returns an array of all of those Mario levels.
     * In order to convert a single level, it needs to be put into
     * a json array by adding extra square brackets [ ] around it.
     * @param json Json String representation of multiple Mario levels
     * @return Array of those levels
     */
    public static Level[] marioLevelsFromJson(String json) {
        List<List<List<Integer>>> allLevels = JsonReader.JsonToInt(json);
        Level[] result = new Level[allLevels.size()];
        int index = 0;
        for(List<List<Integer>> listRepresentation : allLevels) {
            result[index++] = LevelParser.createLevelJson(listRepresentation);
        }
        return result;
    }

    public void exit() throws IOException{
        ganProcess.commSend("0");
    }

    /**
     * Helper method to get the Mario Level from the latent vector
     * @param x Latent vector
     * @return Mario Level
     * @throws IOException Problems communicating with Python GAN process
     */
    public Level levelFromLatentVector(double[] x) throws IOException {
        x = mapArrayToOne(x);
        // Interpret x to a level
        // Brackets required since generator.py expects of list of multiple levels, though only one is being sent here
        ganProcess.commSend("[" + Arrays.toString(x) + "]");
        String levelString = ganProcess.commRecv(); // Response to command just sent
        Level[] levels = marioLevelsFromJson("[" +levelString + "]"); // Really only one level in this array
        Level level = levels[0];
        return level;
    }

    /**
     * Directly send a string to the GAN (Should be array of arrays of doubles in Json format).
     *
     * Note: A bit redundant: This could be called from the method above.
     *
     * @param input
     * @return
     * @throws IOException
     */
    public String stringToFromGAN(String input) throws IOException {
        double[] x = JsonToDoubleArray(input);
        x = mapArrayToOne(x);
        ganProcess.commSend(Arrays.toString(x));
        String levelString = ganProcess.commRecv(); // Response to com	mand just sent
        return levelString;
    }

    /**
     * Gets objective score for single latent vector.
     */
    @Override
    public double valueOf(double[] x) {
        try {
            Level level = levelFromLatentVector(x);
            return valueOf(level);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            return Double.NaN;
        }
    }

    /**
     * Gets objective score for a given level
     */
    public double valueOf(Level level) {
        EvaluationInfo info = this.marioProcess.simulateOneLevel(level);
        ArrayList<boolean[]> moves = info.marioMoves;

        double difficulty = 0.0;
        if (info.marioStatus == Mario.STATUS_WIN) {
            int numberOfValidStartEndPairs = 0;

            ArrayList<Integer> jumpStarts = new ArrayList<>();
            ArrayList<Integer> jumpEnds = new ArrayList<>();

            boolean jumpAlreadyPressed = false;
            for (int frame = 0; frame < moves.size(); frame++) {
                boolean jumpCurrentlyPressed = moves.get(frame)[Mario.KEY_JUMP];
                if (!jumpAlreadyPressed && jumpCurrentlyPressed) {
                    jumpStarts.add(frame);
                }
                if (jumpAlreadyPressed && !jumpCurrentlyPressed) {
                    jumpEnds.add(frame);
                }

                jumpAlreadyPressed = jumpCurrentlyPressed;
            }

            if (jumpStarts.isEmpty()) {
                /*
                 * If there are no jumps, the difficulty of the level is low, which corresponds to having a lot of
                 * valid start/end time windows for the 'jumps'
                 */
                numberOfValidStartEndPairs = Integer.MAX_VALUE;
            }
            else {
                if (jumpEnds.size() < jumpStarts.size())
                {
                    /*
                     * If there is no release of the jump key before the level is complete, add a sentinel jump
                     * release 1 frame after the last jump was started
                     */
                    int lastJumpStart = jumpStarts.get(jumpStarts.size() - 1);
                    jumpEnds.add(lastJumpStart + 1);
                }
                if (jumpStarts.size() != jumpEnds.size()) {
                    throw new RuntimeException("Mismatch between number of jump key presses and releases");
                }
                if (jumpStarts.size() != info.jumpActionsPerformed) {
                    throw new RuntimeException("Number of jumps incorrectly counted");
                }
                int numJumps = jumpStarts.size();
                /* Debug information */
                System.out.println("Jumps ");
                for (int i = 0; i < numJumps; i++)
                {
                    System.out.println("(" + jumpStarts.get(i) + ", " + jumpEnds.get(i) + ")");
                }

                numberOfValidStartEndPairs = numJumps;
            }

            difficulty = 1.0 / numberOfValidStartEndPairs;
        }
        else {
            double penaltyForMethodOfDeath;
            if (info.marioDiedToEnemy) {
                penaltyForMethodOfDeath = 5;
            }
            else if (info.marioDiedToFall) {
                penaltyForMethodOfDeath = 6;
            }
            else if (info.marioRanOutOfTime) {
                penaltyForMethodOfDeath = 7;
            }
            else {
                throw new RuntimeException("Invalid mario status collected from info: " + info.marioStatus);
            }
            difficulty = -penaltyForMethodOfDeath;
        }
        // Since the CMA-ES will minimize the function, we need to negate our fitness value to maximize difficulty
        return -difficulty;
    }

    @Override
    public boolean isFeasible(double[] x) {
        return true;
    }

    /**
     * Map the value in R to (-1, 1)
     * @param valueInR
     * @return
     */
    public static double mapToOne(double valueInR) {
        return ( valueInR / Math.sqrt(1+valueInR*valueInR) );
    }

    public static double[] mapArrayToOne(double[] arrayInR) {
        double[] newArray = new double[arrayInR.length];
        for(int i=0; i<newArray.length; i++) {
            double valueInR = arrayInR[i];
            newArray[i] = mapToOne(valueInR);
            //System.out.println(valueInR);
        }
        return newArray;
    }
}
