package ch.idsia.tools;

import ch.idsia.mario.engine.sprites.Mario;

import java.text.DecimalFormat;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy
 * Date: Apr 12, 2009
 * Time: 12:44:51 AM
 * Package: .Tools
 */
public class EvaluationInfo
{
    private static final int MagicNumberUndef = -42;
    /* Constants related to mario's status */
    public static final float lowestValidPosition = 225.0f;
    /*
    * Frame gap thresholds used to categorize difficulty of a jump --
    * jumps done in a tighter time frame are considered to be harder
    */
    public static final int hardJumpThreshold = 10; /* If a jump is done <= 5 frames after the previous jump is completed,
                                                      * it qualifies as a hard jump */
    public static final int mediumJumpThreshold = 20; /* If a jump is done <= 20 frames after the previous jump is completed,
                                                        * it qualifies as a medium jump */
    public static final int easyJumpThreshold = 30; /* If a jump is done <= 30 frames after the previous jump is completed,
                                                      * it qualifies as a medium jump */
    public static final int trivialJumpThreshold = 20000; /* All remaining jumps are effectively classified as trivial */
    /* The following weights are used to determine the difficulty of a level -- harder jumps contribute more to the difficulty */
    public static final int hardJumpWeight = 10;
    public static final int mediumJumpWeight = 5;
    public static final int easyJumpWeight = 3;
    public static final int trivialJumpWeight = 1;

    public static final int badPipeTileWeight = -2;
    public static final int goodPipeTileWeight = 2;
    public static final int gapWeight = 2;

    public static final int enemyWeight = 2;
    public static final int repeatWeight = 2;

    /* Statistics on player actions during the level */
    public int levelType = MagicNumberUndef;
    public int marioStatus = MagicNumberUndef;
    public int livesLeft = MagicNumberUndef;
    public double lengthOfLevelPassedPhys = MagicNumberUndef;
    public int lengthOfLevelPassedCells = MagicNumberUndef;
    public int totalLengthOfLevelCells = MagicNumberUndef;
    public double totalLengthOfLevelPhys = MagicNumberUndef;
    public int timeSpentOnLevel = MagicNumberUndef;
    public int totalTimeGiven = MagicNumberUndef;
    public int numberOfGainedCoins = MagicNumberUndef;
//    public int totalNumberOfCoins = MagicNumberUndef;
    public int totalActionsPerfomed = MagicNumberUndef;
    public int jumpActionsPerformed = MagicNumberUndef;
    public int hardJumpActionsPerformed = MagicNumberUndef;
    public int mediumJumpActionsPerformed = MagicNumberUndef;
    public int easyJumpActionsPerformed = MagicNumberUndef;
    public int trivialJumpActionsPerformed = MagicNumberUndef;
    public int totalFramesPerfomed = MagicNumberUndef;
    public boolean marioDiedToFall = false;
    public boolean marioDiedToEnemy = false;
    public boolean marioRanOutOfTime = false;
    // Number Of collisions with creatures
    // if large
    // if fire
    public String Memo = "";
    public int timeLeft = MagicNumberUndef;
    public String agentName = "undefinedAgentName";
    public String agentType = "undefinedAgentType";
    public int levelDifficulty = MagicNumberUndef;
    public int levelRandSeed = MagicNumberUndef;
    public int marioMode = MagicNumberUndef;
    public int killsTotal = MagicNumberUndef;

    public double computeBasicFitness()
    {
        // neglect totalActionsPerfomed;
        // neglect totalLengthOfLevelCells;
        // neglect totalNumberOfCoins;
        return lengthOfLevelPassedPhys - timeSpentOnLevel + numberOfGainedCoins + marioStatus*5000;
    }

    public double computeDistancePassed()
    {
        return lengthOfLevelPassedPhys;
    }

    public int computeKillsTotal()
    {
        return this.killsTotal;
    }
    
    public double computeJumpFraction(){
        return (double)this.jumpActionsPerformed/this.totalActionsPerfomed;
    }

    //TODO: possible fitnesses adjustments: penalize for collisions with creatures and especially for  suicide. It's a sin.

    public double [] toDouble()
    {
        
        return new double[]
                {
                        marioStatus,
                        lengthOfLevelPassedPhys,
                        totalLengthOfLevelCells,
                        timeSpentOnLevel,
                        numberOfGainedCoins,
//                        totalNumberOfCoins,
                        totalActionsPerfomed,
                        totalFramesPerfomed,
                        computeBasicFitness()
                };
    }

    private DecimalFormat df = new DecimalFormat("0.00");

    public String toString()
    {

        String ret = "\nStatistics. Score:";
        ret += "\n                  Player/Agent type : " + agentType;
        ret += "\n                  Player/Agent name : " + agentName;
        ret += "\n                       Mario Status : " + ((marioStatus == Mario.STATUS_WIN) ? "Win!" : "Loss...");
        ret += "\n                         Level Type : " + levelType;
        ret += "\n                   Level Difficulty : " + levelDifficulty;
        ret += "\n                    Level Rand Seed : " + levelRandSeed;
        ret += "\n                         Lives Left : " + livesLeft;
        ret += "\nTotal Length of Level (Phys, Cells) : " + "(" + totalLengthOfLevelPhys + "," + totalLengthOfLevelCells + ")";
        ret += "\n                      Passed (Phys) : " + df.format(lengthOfLevelPassedPhys / totalLengthOfLevelPhys *100) + "% ( " + df.format(lengthOfLevelPassedPhys) + " of " + totalLengthOfLevelPhys + ")";
        ret += "\n                     Passed (Cells) : " + df.format((double)lengthOfLevelPassedCells / totalLengthOfLevelCells *100) + "% ( " + lengthOfLevelPassedCells + " of " + totalLengthOfLevelCells + ")";
        ret += "\n             Time Spent(Fractioned) : " + timeSpentOnLevel + " ( " + df.format((double)timeSpentOnLevel/totalTimeGiven*100) + "% )";
        ret += "\n              Time Left(Fractioned) : " + timeLeft + " ( " + df.format((double)timeLeft/totalTimeGiven*100) + "% )";
        ret += "\n                   Total time given : " + totalTimeGiven;
//        ret += "\nCoins Gained: " + numberOfGainedCoins/totalNumberOfCoins*100 + "%. (" + numberOfGainedCoins + " of " + totalNumberOfCoins + ")";
        ret += "\n                       Coins Gained : " + numberOfGainedCoins;
        ret += "\n             Jump Actions Performed : " + jumpActionsPerformed;
        ret += "\n     Trivial Jump Actions Performed : " + trivialJumpActionsPerformed;
        ret += "\n        Easy Jump Actions Performed : " + easyJumpActionsPerformed;
        ret += "\n      Medium Jump Actions Performed : " + mediumJumpActionsPerformed;
        ret += "\n        Hard Jump Actions Performed : " + hardJumpActionsPerformed;
        ret += "\n        Mario Died to Fall : "          + marioDiedToFall;
        ret += "\n        Mario Died to Enemy: "          + marioDiedToEnemy;
        ret += "\n        Mario Ran out of Time : "       + marioRanOutOfTime;
        ret += "\n             Total Actions Perfomed : " + totalActionsPerfomed;
        ret += "\n              Total Frames Perfomed : " + totalFramesPerfomed;
        ret += "\n               Simple Basic Fitness : " + df.format(computeBasicFitness());
        ret += "\nMemo: " + ((Memo.equals("")) ? "Empty" : Memo);
        return ret;
    }
}
