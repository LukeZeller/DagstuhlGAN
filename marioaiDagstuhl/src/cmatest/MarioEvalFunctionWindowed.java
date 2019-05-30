package cmatest;

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.engine.level.Level;
import ch.idsia.mario.engine.level.LevelParser;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.tools.EvaluationInfo;
import communication.GANProcess;
import communication.MarioProcess;
import communication.MarioProcess.AgentType;
import competition.cig.spencerschumann.Action;
import competition.icegic.robin.AStarAgent;
import fr.inria.optimization.cmaes.fitness.IObjectiveFunction;
import javafx.util.Pair;
import reader.JsonReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cmatest.ActionArray.*;
import static reader.JsonReader.JsonToDoubleArray;

public class MarioEvalFunctionWindowed implements IObjectiveFunction {

    // This is the distance that Mario traverses when he beats the short levels
    // that we are generating. It would need to be changed if we train on larger
    // levels or in any way change the level length.
    public static final int LEVEL_LENGTH = 704;

    private GANProcess ganProcess;
    private MarioProcess marioProcess;

    // changing floor will change the reason for termination
    // (in conjunction with the target value)
    // see cma.options.stopFitness
    static double floor = 0.0;

    private static final int debugVolume = 0;

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

//        Agent agent = new AStarAgent();
//        marioProcess = new MarioProcess();
//        marioProcess.launchMario(new String[0], agent, true); // true means there is a human player


        marioProcess = new MarioProcess();
        marioProcess.launchMario(new String[0], AgentType.HUMAN_PLAYER);
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
        catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Gets objective score for a given level
     */
    public double valueOf(Level level) {
        EvaluationInfo info = this.marioProcess.simulateOneLevel(level.deepcopy());
        ArrayList<boolean[]> moves = info.marioMoves;

        if (debugVolume > 0) {
            /*  Debug info */
            System.out.println("Original jumps");
            System.out.println(viewJumps(moves));
            System.out.println("Evaluation info:");
            System.out.println(info.toStringQuiet());
            System.out.println("Check consistency");
            if (debugVolume > 5) {
                boolean humanCompleted = info.marioStatus == Mario.STATUS_WIN;
                checkConsistency(level.deepcopy(), moves, humanCompleted);
                checkLevelStable(level);
            }
        }
        double difficulty = 0.0;
        if (info.marioStatus == Mario.STATUS_WIN && canComplete(level.deepcopy(), moves, true)) {

            difficulty = calculateDifficultyForSuccess(level, moves);
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
            else if (info.marioStatus == Mario.STATUS_WIN) {
                // Replaying a winning set of moves failedd the level since the margin of error for the moves was too fine
                penaltyForMethodOfDeath = 0;
            }
            else {
                throw new RuntimeException("Invalid mario status collected from info: " + info.marioStatus);
            }
            difficulty = -penaltyForMethodOfDeath;
        }
        // Since the CMA-ES will minimize the function, we need to negate our fitness value to maximize difficulty
        return -difficulty;
    }

    private void checkLevelStable(Level level) {
        /* Check deepcopy */
        String checkLevel1 = level.toString();
        String checkLevel2 = level.deepcopy().toString();
        if (!checkLevel1.equals(checkLevel2)) {
            throw new RuntimeException("deepcopy failed to make an equivalent copy");
        }

        MarioProcess testProcess = new MarioProcess();
        testProcess.launchMario(new String[0], AgentType.HUMAN_PLAYER);

        String levelBeforePlaying = level.toString();
        testProcess.simulateOneLevel(level.deepcopy());
        String levelAfterPlaying = level.toString();

        if (!levelBeforePlaying.equals(levelAfterPlaying)) {
            throw new RuntimeException("Playing through a level changed the level");
        }
    }

    private double calculateDifficultyForSuccess(Level level, ArrayList<boolean[]> moves) {
        Pair<ArrayList<Integer>, ArrayList<Integer>> jumps = getJumps(moves);
        ArrayList<Integer> jumpStarts = jumps.getKey();
        ArrayList<Integer> jumpEnds = jumps.getValue();

        if (debugVolume > 5) {
            System.out.println("Before removing redundant jumps");
            simulateMoves(level.deepcopy(), moves, true);
        }
        moves = removeRedundantJumps(level, moves, jumpStarts, jumpEnds);
        if (debugVolume > 5) {
            System.out.println("After removing redundant jumps");
            simulateMoves(level.deepcopy(), moves, true);
        }

        int numberOfValidStartEndPairs = 0;
        int numberOfTotalStartEndPairs = 0;
        if (jumpStarts.isEmpty()) {
            /*
             * If there are no jumps, the difficulty of the level is low, which corresponds to having a lot of
             * valid start/end time windows for the 'jumps'
             */
            numberOfValidStartEndPairs = Integer.MAX_VALUE;
            numberOfTotalStartEndPairs = 1;
        }
        else {
            int numJumps = jumpStarts.size();
            if (debugVolume > 5) {
                checkSeparationOfJumps(numJumps, jumpStarts, jumpEnds);
            }

            /*
             * For each jump, determine the number of tuples (s, e) such that shifting the jump to start at frame s,
             * and end at frame e results in completing the level in the same amount of time as when the original
             * jump is done
             */
            for (int currentJump = 0; currentJump < numJumps; currentJump++)
            {
                int originalStart = jumpStarts.get(currentJump);
                int originalEnd = jumpEnds.get(currentJump);

                /*
                 * The start of this jump must come after the end of the previous jump
                 * If there is no prior jump, this jump can begin as early as frame 0
                 *
                 * Similarly, the end of this jump must come before the start of the next jump
                 * If there is no next jump, this jump can end as early as the last frame
                 * The number of frames is given by the number of actions in moves
                 */
                int lowerBound = currentJump == 0 ? 0 : jumpEnds.get(currentJump - 1) + 1;
                int upperBound = currentJump == numJumps - 1 ? moves.size() - 1 : jumpStarts.get(currentJump + 1) - 1;

                /*
                * Iterate through all pairs of valid (newStart, newEnd) pairs such that the current jump can be replaced
                * by a jump lasting from newStart to newEnd
                */

                final int maxFramesJumpHold = 25;
                for (int newStart = lowerBound; newStart <= upperBound; newStart++) {
                    for (int newEnd = newStart + 1; newEnd <= upperBound && newEnd <= newStart + maxFramesJumpHold; newEnd++) {
                        ArrayList<boolean[]> newMoves = new ArrayList<>(moves);
                        removeAllJumpsInRange(newMoves, originalStart, originalEnd);
                        addAllJumpsInRange(newMoves, newStart, newEnd);

                        // Check if this new move list can complete the level
                        boolean completable = canComplete(level.deepcopy(), newMoves, false);
                        if (completable) {
                            numberOfValidStartEndPairs++;
                        }
                        numberOfTotalStartEndPairs++;

                        /* Debug info */
                        if (debugVolume > 0) {
                            System.out.print("Old: " + originalStart + " " + originalEnd + " ");
                            System.out.print("Window: " + newStart + " " + newEnd + " ");
                            if (completable) {
                                System.out.println("Succeeded");
                            }
                            else {
                                System.out.println("Failed");
                                if (newStart == originalStart && newEnd == originalEnd) {
                                    System.out.println("Start error checking");
                                    System.out.println("Rerun");
                                    canComplete(level.deepcopy(), newMoves, true);
                                    System.out.println("View");
                                    viewMoves(level.deepcopy(), moves);

                                    System.out.println("They are same: " + areIdenticalMoves(moves, newMoves));
                                    System.out.println("Original moves: ");
                                    System.out.println(ActionArray.viewJumps(moves));
                                    System.out.println("Current moves: ");
                                    System.out.println(ActionArray.viewJumps(newMoves));
                                    throw new RuntimeException("Doing the exact same jump as in the original run-through " +
                                            "should not fail");
                                }
                            }
                        }
                    }
                }
            }
        }

        if (debugVolume > -1) {
            System.out.println("Number of valid pairs: " + numberOfValidStartEndPairs);
            System.out.println("Number of total pairs: " + numberOfTotalStartEndPairs);
        }
        return (double) numberOfValidStartEndPairs / numberOfTotalStartEndPairs;
    }

    private ArrayList<boolean[]> removeRedundantJumps(Level level,
                                      ArrayList<boolean[]> moves,
                                      ArrayList<Integer> jumpStarts,
                                      ArrayList<Integer> jumpEnds) {
        int indexOfJumpToRemove = 0;
        while (indexOfJumpToRemove < jumpStarts.size()) {
            ArrayList<boolean[]> movesWithoutJump = new ArrayList<>(moves);
            removeAllJumpsInRange(movesWithoutJump, jumpStarts.get(indexOfJumpToRemove), jumpEnds.get(indexOfJumpToRemove));
            if (debugVolume > 0) {
                System.out.println("Trying to remove jump from " + jumpStarts.get(indexOfJumpToRemove) + " to " + jumpEnds.get(indexOfJumpToRemove));
            }
            if (canComplete(level.deepcopy(), movesWithoutJump, debugVolume > 0)) {
                if (debugVolume > 0) {
                    System.out.println("Can remove");
                }
                // If you can complete the level without this jump, remove it from moves, jumpStarts, and jumpEnds
                moves = new ArrayList<>(movesWithoutJump);
                jumpStarts.remove(indexOfJumpToRemove);
                jumpEnds.remove(indexOfJumpToRemove);
            }
            else {
                if (debugVolume > 0) {
                    System.out.println("Cannot remove");
                }
                // Since this jump is necessary, check the next jump
                indexOfJumpToRemove++;
            }
        }
        return moves;
    }

    private void removeAllJumpsInRange(ArrayList<boolean[]> moves, int start, int end) {
        for (int currentFrame = start; currentFrame < end; currentFrame++){
            boolean [] currentAction = moves.get(currentFrame);
            boolean [] newAction = removedActions(currentAction, MarioAction.JUMP);
            moves.set(currentFrame, newAction);
        }
    }

    private void addAllJumpsInRange(ArrayList<boolean[]> moves, int start, int end) {
        for (int currentFrame = start; currentFrame < end; currentFrame++){
            boolean [] currentAction = moves.get(currentFrame);
            boolean [] newAction = addedActions(currentAction, MarioAction.JUMP);
            moves.set(currentFrame, newAction);
        }
    }

    private void checkSeparationOfJumps(int numJumps, ArrayList<Integer> jumpStarts, ArrayList<Integer> jumpEnds) {
        /*
         * Make sure that none of jump key presses coincide with a jump key release and that no two jumps start
         * at the same time nor end at the same time
         */
        for (int i = 0; i < numJumps; i++)
        {
            for (int j = 0; j < numJumps; j++)
            {
                boolean startAndEndAreSame = (int) jumpStarts.get(i) == jumpEnds.get(j);
                boolean startsAreSame = (int) jumpStarts.get(i) == jumpStarts.get(j);
                boolean endsAreSame = (int) jumpEnds.get(i) == jumpEnds.get(j);
                if (startAndEndAreSame)
                {
                    throw new RuntimeException( "There are jumps (not necessarily distinct) where " +
                            "the start of one jump coincides with the end of the other");
                }
                if ((i != j) && (startsAreSame || endsAreSame)) {
                    throw new RuntimeException("Two distinct jumps have the same start or the same end");
                }
            }
        }
    }

    private void checkConsistency(Level level, ArrayList<boolean[]> moves, boolean expectComplete) {
        System.out.println("Double check that we get same moves back");
        EvaluationInfo doubleCheckMoves = simulateMoves(level.deepcopy(), moves, true);
        if (!areIdenticalMoves(doubleCheckMoves.marioMoves, moves)) {
            System.out.println("Original Jumps");
            System.out.println(ActionArray.viewJumps(moves));
            System.out.println("New Jumps");
            System.out.println(ActionArray.viewJumps(doubleCheckMoves.marioMoves));

            System.out.println("Original Left");
            System.out.println(ActionArray.viewAction(moves, MarioAction.LEFT));
            System.out.println("New Left");
            System.out.println(ActionArray.viewAction(doubleCheckMoves.marioMoves, MarioAction.LEFT));

            System.out.println("Original Right");
            System.out.println(ActionArray.viewAction(moves, MarioAction.RIGHT));
            System.out.println("New Right");
            System.out.println(ActionArray.viewAction(doubleCheckMoves.marioMoves, MarioAction.RIGHT));

            System.out.println("Original Speed");
            System.out.println(ActionArray.viewAction(moves, MarioAction.SPEED));
            System.out.println("New Speed");
            System.out.println(ActionArray.viewAction(doubleCheckMoves.marioMoves, MarioAction.SPEED));

            System.out.println("Original Duck");
            System.out.println(ActionArray.viewAction(moves, MarioAction.DUCK));
            System.out.println("New Duck");
            System.out.println(ActionArray.viewAction(doubleCheckMoves.marioMoves, MarioAction.DUCK));

            throw new RuntimeException("Did not get the same moves upon playback");
        }

        boolean checkHaveFalse = false;
        boolean checkHaveTrue = false;
        boolean failure = false;
        System.out.println("Check consistency");
        for (int iter = 0; iter < 3; iter++) {
            System.out.println("Iteration " + iter + " jumps");
            if (canComplete(level.deepcopy(), moves, false)) {
                checkHaveTrue = true;
                if (!expectComplete) {
                    failure = true;
                }
            }
            else {
                checkHaveFalse = true;
                if (expectComplete) {
                    failure = true;
                }
            }
            if (checkHaveTrue && checkHaveFalse) {
                simulateMoves(level.deepcopy(), moves, true);
                throw new RuntimeException("Inconsistent result with same moves");
            }
            if (failure) {
                simulateMoves(level.deepcopy(), moves, true);
                throw new RuntimeException("Expected " + expectComplete + " and got the opposite when simulating later");
            }
        }
    }

    private static Pair<ArrayList<Integer>, ArrayList<Integer>> getJumps(ArrayList<boolean[]> moves) {
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
        if (jumpStarts.size() > 0 && jumpEnds.size() < jumpStarts.size())
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

        if (debugVolume > -1) {
            /* Debug information */
            System.out.println("Jumps ");
            for (int i = 0; i < jumpStarts.size(); i++) {
                System.out.println("(" + jumpStarts.get(i) + ", " + jumpEnds.get(i) + ")");
            }
        }
        return new Pair<>(jumpStarts, jumpEnds);
    }

    private boolean canComplete(Level level, ArrayList<boolean[]> moves, boolean view) {
        EvaluationInfo info = simulateMoves(level, moves, view);
        return info.marioStatus == Mario.STATUS_WIN;
    }

    private int timeToComplete(Level level, ArrayList<boolean[]> moves, boolean view) {
        EvaluationInfo info = simulateMoves(level, moves, view);
        if (info.marioStatus == Mario.STATUS_WIN) {
            return info.timeSpentOnLevel;
        }
        else {
            /*
             * If mario does not complete the level, we can think of it as Mario needing
             * an infinite amount of time to complete the level
             */
            return Integer.MAX_VALUE;
        }
    }

    private void viewMoves(Level level, ArrayList<boolean[]> moves) {
        System.out.println("Viewing");
        EvaluationInfo info = simulateMoves(level, moves, true);
        System.out.println("Died to fall: " + info.marioDiedToFall);
        System.out.println("Died to enemy: " + info.marioDiedToEnemy);
        System.out.println("Died to time: " + info.marioRanOutOfTime);
        System.out.println("Mario status: " + info.marioStatus);
    }

    private EvaluationInfo simulateMoves(Level level, ArrayList<boolean[]> moves, boolean view) {
        MarioProcess simulationProcess = new MarioProcess();
        simulationProcess.launchMario(new String[0], new ForcedActionsAgent(moves, false), view);
        return simulationProcess.simulateOneLevel(level);
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
