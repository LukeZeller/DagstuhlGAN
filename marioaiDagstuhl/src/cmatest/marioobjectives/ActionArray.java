package cmatest.marioobjectives;

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
        switch (actionToModify) {
            case LEFT:
                action[Mario.KEY_LEFT] = newValue;
                break;
            case RIGHT:
                action[Mario.KEY_RIGHT] = newValue;
                break;
            case DUCK:
                action[Mario.KEY_DOWN] = newValue;
                break;
            case SPEED:
                action[Mario.KEY_SPEED] = newValue;
                break;
            case JUMP:
                action[Mario.KEY_JUMP] = newValue;
                break;
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
}
