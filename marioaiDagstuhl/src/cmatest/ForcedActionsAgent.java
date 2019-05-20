package cmatest;

import ch.idsia.ai.agents.Agent;
import ch.idsia.ai.agents.ai.BasicAIAgent;
import ch.idsia.mario.environments.Environment;

import java.util.ArrayList;

public class ForcedActionsAgent extends BasicAIAgent implements Agent
{
    protected boolean action[] = new boolean[Environment.numberOfButtons];
    protected String name = "ForcedActionsAgent";
    private int tickCounter = 0;
    private ArrayList<boolean[]> moves;

    public ForcedActionsAgent(ArrayList<boolean[]> moves) {
        super("ForcedActionsAgent");
        this.moves = moves;
    }

    public void setMoves(ArrayList<boolean[]> moves) {
        this.moves = moves;
    }

    public void reset()
    {
        action = new boolean[Environment.numberOfButtons];// Empty action
    }

    public void printLevel(byte[][] levelScene)
    {
        for (int i = 0; i < levelScene.length; i++)
        {
            for (int j = 0; j < levelScene[i].length; j++)
            {
                //if ((levelScene[i][j] > 1 && levelScene[i][j] <= 15) || levelScene[i][j] == 20
                //		|| levelScene[i][j] == 21 || levelScene[i][j] == 22 || levelScene[i][j] == 25)
                //	System.out.print(">");
                System.out.print(levelScene[i][j]+"\t");
            }
            System.out.println("");
        }
    }

    public boolean[] getAction(Environment observation)
    {
        if (tickCounter < 0)
        {
            throw new RuntimeException("The current tick is negative which is invalid");
        }
        // If the tickCounter is greater than the number of frames, cycle back to the beginning
        int frameNumber = tickCounter % moves.size();
        boolean [] action = moves.get(frameNumber);
        tickCounter++;

        return action;
    }

    public AGENT_TYPE getType()
    {
        return Agent.AGENT_TYPE.AI;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String Name)
    {
        this.name = Name;
    }
}
