package cmatest;

import ch.idsia.mario.engine.level.Level;

public class LevelStatistics {

    /* Constants used by Level class to represent blocks */
    private static final int GROUND_ROCK = 9;
    private static final int TOP_LEFT_PIPE = 10;
    private static final int TOP_RIGHT_PIPE = 11;
    private static final int LEFT_PIPE = 26;
    private static final int RIGHT_PIPE = 27;

    private static final int MagicNumberUndef = -42;

    private Level level;

    /* Number of pipe tiles in level that aren't part of a valid pipe */
    public int numBrokenPipeTiles = MagicNumberUndef;
    /* Number of rocks on ground level */
    public int numGroundRocks = MagicNumberUndef;
    /* Total number of ground rocks (including those not on ground level) */
    public int numRocks = MagicNumberUndef;

    public LevelStatistics(Level level) {
        this.level = level;
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

        int y = yTop - 1;
        while (level.getBlock(xLeft, y) == LevelStatistics.LEFT_PIPE
                && level.getBlock(xLeft + 1, y) == LevelStatistics.RIGHT_PIPE) y--;
        if (y == yTop - 1) return false;

        return level.getBlock(xLeft, y) == LevelStatistics.GROUND_ROCK
                && level.getBlock(xLeft + 1, y) == LevelStatistics.GROUND_ROCK;
    }

    private boolean containedInValidPipe(int x, int y) {
        for (int y_i = y; y_i < level.height; y_i++) {
            if (isValidPipe(x - 1, y_i) || isValidPipe(x, y_i)) return true;
        }
        return false;
    }

    private void processTile(int x, int y) {
        if (isPipeTile(x, y) || !containedInValidPipe(x, y)) {
            numBrokenPipeTiles++;
        }
        if (level.getBlock(x, y) == LevelStatistics.GROUND_ROCK) {
            numRocks++;
            if (y == level.height - 1) numGroundRocks++;
        }
    }

    private void processLevel() {
        for (int x = 0; x < level.width; x++) {
            for (int y = 0; y < level.height; y++) {
                processTile(x, y);
            }
        }
    }
}
