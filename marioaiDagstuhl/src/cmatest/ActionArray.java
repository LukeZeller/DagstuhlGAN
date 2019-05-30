package cmatest;

import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.mario.environments.Environment;

import java.util.ArrayList;
import java.util.Arrays;

public class ActionArray {
    public enum MarioAction {
        LEFT,
        RIGHT,
        DUCK,
        SPEED,
        JUMP
    }

    public static boolean[] createAction(MarioAction... actions) {
        boolean [] newAction = new boolean[Environment.numberOfButtons];
        return addedActions(newAction, actions);
    }

    public static boolean[] addedActions(boolean [] currentAction, MarioAction... actionsToModify)
    {
        boolean [] newAction = currentAction.clone();
        for (MarioAction action: actionsToModify) {
            modifyAction(newAction, action, true);
        }
        return newAction;
    }

    public static boolean[] removedActions(boolean [] currentAction, MarioAction... actionsToModify)
    {
        boolean [] newAction = currentAction.clone();
        for (MarioAction action: actionsToModify) {
            modifyAction(newAction, action, false);
        }
        return newAction;
    }

    public static void modifyAction(boolean [] action, MarioAction actionToModify, boolean newValue) {
        action[getIndex(actionToModify)] = newValue;
    }

    private static int getIndex(MarioAction action) {
        switch (action) {
            case LEFT:
                return Mario.KEY_LEFT;
            case RIGHT:
                return Mario.KEY_RIGHT;
            case DUCK:
                return Mario.KEY_DOWN;
            case SPEED:
                return Mario.KEY_SPEED;
            case JUMP:
                return Mario.KEY_JUMP;
            default:
                throw new RuntimeException("Unhandled type of MarioAction");
        }
    }

    public static boolean areIdenticalMoves(ArrayList<boolean []> moveA, ArrayList<boolean []> moveB) {
        if (moveA.size() != moveB.size())
            return false;
        for (int i = 0; i < moveA.size(); i++) {
            boolean sameAction = Arrays.equals(moveA.get(i), moveB.get(i));
            if (!sameAction)
                return false;
        }
        return true;
    }

    public static String convertMovesToString(ArrayList<boolean[]> moves) {
        return convertMovesToString(moves, "\n");
    }

    public static String convertMovesToString(ArrayList<boolean[]> moves, String delimeter)
    {
        String result = "";
        for (int i = 0; i < moves.size(); i++)
        {
            boolean [] move = moves.get(i);
            result += "               ";
            result += move[Mario.KEY_LEFT] ? "L" : " ";
            result += move[Mario.KEY_RIGHT] ? "R" : " ";
            result += move[Mario.KEY_DOWN] ? "D" : " ";
            result += move[Mario.KEY_JUMP] ? "J" : " ";
            result += move[Mario.KEY_SPEED] ? "S" : " ";
            if (i != moves.size() - 1)
              result += delimeter;
        }
        return result;
    }

    public static String viewJumps(ArrayList<boolean[]> moves) {
        return viewAction(moves, MarioAction.JUMP);
    }

    public static String viewAction(ArrayList<boolean[]> moves, MarioAction action) {
        int index = getIndex(action);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            boolean [] move = moves.get(i);
            if (move[index]) {
                result.append("X");
            }
            else {
                result.append("_");
            }
        }
        return result.toString();
    }

    public static ArrayList<boolean[]> shiftedJumps(ArrayList<boolean[]> moves,
                                              int oldStart,
                                              int oldEnd,
                                              int newStart,
                                              int newEnd) {
        ArrayList<boolean[]> newMoves = new ArrayList<>(moves);
        removeAllJumpsInRange(newMoves, oldStart, oldEnd);
        addAllJumpsInRange(newMoves, newStart, newEnd);
        return newMoves;
    }

    public static void removeAllJumpsInRange(ArrayList<boolean[]> moves, int start, int end) {
        for (int currentFrame = start; currentFrame < end; currentFrame++){
            boolean [] currentAction = moves.get(currentFrame);
            boolean [] newAction = removedActions(currentAction, MarioAction.JUMP);
            moves.set(currentFrame, newAction);
        }
    }

    public static void addAllJumpsInRange(ArrayList<boolean[]> moves, int start, int end) {
        for (int currentFrame = start; currentFrame < end; currentFrame++){
            boolean [] currentAction = moves.get(currentFrame);
            boolean [] newAction = addedActions(currentAction, MarioAction.JUMP);
            moves.set(currentFrame, newAction);
        }
    }

    public static void rstripBlankActions(ArrayList<boolean[]> moves, boolean [] toRemove) {
        while (!moves.isEmpty() && Arrays.equals(moves.get(moves.size() - 1), toRemove)) {
            moves.remove(moves.size() - 1);
        }
    }
}
