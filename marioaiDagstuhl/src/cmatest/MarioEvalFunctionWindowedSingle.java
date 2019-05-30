package cmatest;

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.engine.level.Level;
import ch.idsia.mario.engine.level.LevelParser;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.tools.EvaluationInfo;
import communication.GANProcess;
import communication.MarioProcess;
import communication.MarioProcess.AgentType;
import competition.icegic.robin.AStarAgent;
import fr.inria.optimization.cmaes.fitness.IObjectiveFunction;
import javafx.util.Pair;
import reader.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cmatest.ActionArray.*;
import static reader.JsonReader.JsonToDoubleArray;

public class MarioEvalFunctionWindowedSingle implements IObjectiveFunction {

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

    public MarioEvalFunctionWindowedSingle() throws IOException {
        // set up process for GAN
        ganProcess = new GANProcess();
        ganProcess.start();

        setupMarioProcesses();
        setupConsumptionOfNonDataResponses();
    }

    public MarioEvalFunctionWindowedSingle(String GANPath, String GANDim) throws IOException {
        // set up process for GAN
        ganProcess = new GANProcess(GANPath, GANDim);
        ganProcess.start();

        setupMarioProcesses();
        setupConsumptionOfNonDataResponses();
    }

    private void setupMarioProcesses()
    {
        // set up mario game
        Agent agent = new AStarAgent();
        marioProcess = new MarioProcess();
        marioProcess.launchMario(new String[0], agent, true); // true means there is a human player


//        marioProcess = new MarioProcess();
//        marioProcess.launchMario(new String[0], AgentType.HUMAN_PLAYER);
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
            if (debugVolume > 5) {
                System.out.println("Check consistency");
                boolean humanCompleted = info.marioStatus == Mario.STATUS_WIN;
                checkConsistency(level, moves, humanCompleted);
                checkLevelStable(level);
            }
        }
        double difficulty;
        if (info.marioStatus == Mario.STATUS_WIN && canComplete(level, moves, true)) {

            difficulty = calculateDifficultyForSuccess(level, info);
        }
        else {
            difficulty = calculateDifficultyForFailure(info);
        }
        // Since the CMA-ES will minimize the function, we need to negate our fitness value to maximize difficulty
        return -difficulty;
    }

    private double calculateDifficultyForFailure(EvaluationInfo info) {
        if (info.marioDiedToEnemy) {
            return -5;
        }
        else if (info.marioDiedToFall) {
            return -6;
        }
        else if (info.marioRanOutOfTime) {
            return -7;
        }
        else if (info.marioStatus == Mario.STATUS_WIN) {
            // Replaying a winning set of moves failed the level since the margin of error for the moves was too small
            return 0;
        }
        else {
            throw new RuntimeException("Invalid mario status collected from info: " + info.marioStatus);
        }
    }

    private double calculateDifficultyForSuccess(Level level, EvaluationInfo info) {
        /*
         * The more that the jumps can be shifted around, the easier the level is
         * Taking the reciprocal of the average number of feasible shifts ensures that
         * easier levels get smaller difficulty values
         */
        return 1 / averageFeasibleShiftsPerJump(level, info.marioMoves);
    }

    private double averageFeasibleShiftsPerJump(Level level, ArrayList<boolean[]> moves) {
        Pair<ArrayList<Integer>, ArrayList<Integer>> jumps = getJumps(moves);
        ArrayList<Integer> jumpStarts = jumps.getKey();
        ArrayList<Integer> jumpEnds = jumps.getValue();

        if (debugVolume > -1) {
            /* Debug information */
            System.out.println("Jumps ");
            for (int i = 0; i < jumpStarts.size(); i++) {
                System.out.println("(" + jumpStarts.get(i) + ", " + jumpEnds.get(i) + ")");
            }
        }

        if (debugVolume > 5) {
            System.out.println("Before removing redundant jumps");
            simulateMoves(level, moves, true);
        }
        moves = removedRedundantJumps(level, moves, jumpStarts, jumpEnds);
        int numJumps = jumpStarts.size();
        if (debugVolume > 5) {
            System.out.println("After removing redundant jumps");
            simulateMoves(level, moves, true);
        }

        if (debugVolume > -1) {
            /* Debug information */
            System.out.println("Jumps trimmed");
            for (int i = 0; i < jumpStarts.size(); i++) {
                System.out.println("(" + jumpStarts.get(i) + ", " + jumpEnds.get(i) + ")");
            }
        }

        int numberOfValidShifts = 0;
        if (numJumps == 0) {
            /*
             * If there are no jumps, the difficulty of the level is low, which is similar in difficulty to having
             * a lot of valid timing windows for 'jumps'
             */
            numberOfValidShifts = Integer.MAX_VALUE;
        }
        else {
            if (debugVolume > 5) {
                checkSeparationOfJumps(numJumps, jumpStarts, jumpEnds);
            }

            /*
             * For each jump, determine the number of integers s such that shifting the jump to start at frame s
             * can result in completing the level
             */
            for (int currentJump = 0; currentJump < numJumps; currentJump++) {
                int originalStart = jumpStarts.get(currentJump);
                int originalEnd = jumpEnds.get(currentJump);

                int endOfPreviousJump = currentJump == 0 ? 0 : jumpEnds.get(currentJump - 1);
                int startOfNextJump = currentJump == numJumps - 1 ? moves.size(): jumpStarts.get(currentJump + 1);

                if (debugVolume > -1) {
                    System.out.println("For jump " + currentJump + " the bounds are " + endOfPreviousJump + " " + startOfNextJump);
                }

                int numberOfValidStarts = 0;
                int [] directions = {-1, 1};
                for (int newStart = endOfPreviousJump; newStart < startOfNextJump; newStart++) {
                    boolean feasibleStart = false;
                    for (int newEnd = newStart + 1; newEnd < startOfNextJump; newEnd++) {
                        ArrayList<boolean[]> newMoves = shiftedJumps(moves, originalStart, originalEnd, newStart, newEnd);
                        if (canComplete(level, newMoves, false)) {
                            feasibleStart = true;
                            break;
                        }
                    }
                    if (feasibleStart) {
                        if (debugVolume > -1) {
                            System.out.println("Valid start for jump " + currentJump + " is " + newStart);
                        }
                        numberOfValidStarts++;
                    }
                }
                if (debugVolume > -1 ) {
                    System.out.println("Jump " + currentJump + " has " + numberOfValidStarts + " valid jumps");
                }
                numberOfValidShifts += numberOfValidStarts;
            }
            if (numberOfValidShifts == 0) {
                throw new RuntimeException("There should be at least some valid shifts since the level is completable");
            }
        }
        if (debugVolume > -1) {
            System.out.println("Number of valid shifts: " + numberOfValidShifts);
            System.out.println("Number of frames: " + moves.size());
        }
        return (double) numberOfValidShifts / numJumps;
    }

    private ArrayList<boolean[]> removedRedundantJumps(Level level,
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
            if (canComplete(level, movesWithoutJump, debugVolume > 0)) {
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

    private void checkLevelStable(Level level) {
        /* Check deepcopy */
        String checkLevel1 = level.toString();
        String checkLevel2 = level.deepcopy().toString();
        if (!checkLevel1.equals(checkLevel2)) {
            throw new RuntimeException("deepcopy failed to make an equivalent copy");
        }

        System.out.println("Please stomp a goomba or break a block");
        MarioProcess testProcess = new MarioProcess();
        testProcess.launchMario(new String[0], AgentType.HUMAN_PLAYER);

        String levelBeforePlaying = level.toString();
        testProcess.simulateOneLevel(level.deepcopy());
        String levelAfterPlaying = level.toString();

        if (!levelBeforePlaying.equals(levelAfterPlaying)) {
            throw new RuntimeException("Playing through a level changed the level");
        }
    }

    private void checkConsistency(Level level, ArrayList<boolean[]> moves, boolean expectComplete) {
        System.out.println("Double check that we get same moves back");
        EvaluationInfo doubleCheckMoves = simulateMoves(level, moves, true);
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
            if (canComplete(level, moves, false)) {
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
                simulateMoves(level, moves, true);
                throw new RuntimeException("Inconsistent result with same moves");
            }
            if (failure) {
                simulateMoves(level, moves, true);
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

        return new Pair<>(jumpStarts, jumpEnds);
    }

    private boolean canComplete(Level level, ArrayList<boolean[]> moves, boolean view) {
        EvaluationInfo info = simulateMoves(level, moves, view);
        return info.marioStatus == Mario.STATUS_WIN;
    }

    private EvaluationInfo simulateMoves(Level level, ArrayList<boolean[]> moves, boolean view) {
        MarioProcess simulationProcess = new MarioProcess();
        simulationProcess.launchMario(new String[0], new ForcedActionsAgent(moves, false), view);
        return simulationProcess.simulateOneLevel(level.deepcopy());
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
