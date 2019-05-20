package communication;

import ch.idsia.ai.agents.Agent;
import ch.idsia.ai.agents.AgentsPool;
import ch.idsia.ai.agents.human.HumanKeyboardAgent;
import ch.idsia.mario.engine.level.Level;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.mario.simulation.BasicSimulator;
import ch.idsia.mario.simulation.Simulation;
import ch.idsia.tools.CmdLineOptions;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.EvaluationOptions;
import ch.idsia.tools.ToolsConfigurator;
import cmatest.marioobjectives.ActionArray;
import competition.icegic.robin.AStarAgent;
import cmatest.ForcedActionsAgent;

import java.util.ArrayList;

public class MarioProcess extends Comm {
    private EvaluationOptions evaluationOptions;
    private Simulation simulator;

    public MarioProcess() {
        super();
        this.threadName = "MarioProcess";
    }

    public enum AgentType {
        HUMAN_PLAYER,
        ASTAR_AGENT,
        FORWARD_FORCED_AGENT,
        FORWARD_JUMPING_FORCED_AGENT
    }

    /**
     * Default mario launcher does not have any command line parameters
     */
    public void launchMario() {
    	String[] options = new String[] {""};
    	launchMario(options, AgentType.ASTAR_AGENT);
    }
 
    /**
     * This version of launching Mario allows for using the default configuration of a given agent type
     * @param options General command line options (currently not really used)
     * @param agentType What type of agent is playing (ex. HUMAN_PLAYER, ASTAR_AGENT, FORWARD_FORCED_AGENT)
     */
    public void launchMario(String[] options, AgentType agentType) {
        Agent agent = createNewAgent(agentType);
        boolean isHumanPlayer = agentType == AgentType.HUMAN_PLAYER;
        launchMario(options, agent, isHumanPlayer);
    }

    /**
     * This version of launching Mario allows for several parameters when a specific agent is needed
     * @param options General command line options (currently not really used)
     * @param agent Used to pass in a specific agent
     * @param isHumanPlayer Used to configure settings such as the amount of time given to complete a level,
     *                      maximum fps, and whether the visualization is one
     */
    public void launchMario(String[] options, Agent agent, boolean isHumanPlayer) {
        this.evaluationOptions = new CmdLineOptions(options);  // if none options mentioned, all defaults are used.
        // set agents
        createAgentsPool(agent);
        // set up evaluation options based on whether there is a human player
        configureEvaluationOptions(isHumanPlayer);
        // Create Mario Component
        ToolsConfigurator.CreateMarioComponentFrame(evaluationOptions);
        evaluationOptions.setAgent(AgentsPool.getCurrentAgent());
        System.out.println(evaluationOptions.getAgent().getClass().getName());
        // set simulator
        this.simulator = new BasicSimulator(evaluationOptions.getSimulationOptionsCopy());
    }

    private void configureEvaluationOptions(boolean isHumanPlayer) {
        // Short time for evolution, but more for human
        if(!isHumanPlayer) evaluationOptions.setTimeLimit(20);
        evaluationOptions.setMaxFPS(!isHumanPlayer); // Slow for human players, fast otherwise
        evaluationOptions.setVisualization(true); // Set true to watch evaluations
    }

    private static Agent createNewAgent(AgentType agentType) {
        switch(agentType) {
            case HUMAN_PLAYER:
                return new HumanKeyboardAgent();
            case ASTAR_AGENT:
                return new AStarAgent();
            case FORWARD_JUMPING_FORCED_AGENT: {
                ArrayList<boolean[]> moves = new ArrayList<>();
                // Set up the array for the right & jump press action
                boolean[] jumpPress = ActionArray.createAction(
                        ActionArray.MarioAction.JUMP,
                        ActionArray.MarioAction.RIGHT
                );
                // Set up the array for the right & jump release action
                boolean[] jumpRelease = ActionArray.createAction(
                        ActionArray.MarioAction.RIGHT
                );
                // We only need to add two actions since the forced actions agent will cycle through them in order
                moves.add(jumpPress);
                moves.add(jumpRelease);
                return new ForcedActionsAgent(moves);
            }
            case FORWARD_FORCED_AGENT: {
                ArrayList<boolean[]> moves = new ArrayList<>();
                // Set up the array for moving right
                boolean[] moveRight = ActionArray.createAction(
                        ActionArray.MarioAction.RIGHT
                );
                // We only need to add one action since the forced actions agent will repeat it indefinitely
                moves.add(moveRight);
                return new ForcedActionsAgent(moves);
            }
            default:
                throw new RuntimeException("Unhandled agent type");
        }
    }

    /**
     * Set the agent that is evaluated in the evolved levels
     */
    public static void createAgentsPool(Agent agent) {
        AgentsPool.setCurrentAgent(agent);
    }

    public void setLevel(Level level) {
        evaluationOptions.setLevel(level);
        this.simulator.setSimulationOptions(evaluationOptions);
    }

    /**
     * Simulate a given level
     * @return
     */
    public EvaluationInfo simulateOneLevel(Level level) {
        setLevel(level);
        EvaluationInfo info = this.simulator.simulateOneLevel();
        return info;
    }

    public EvaluationInfo simulateOneLevel() {
        evaluationOptions.setLevelFile("sample_1.json");
        EvaluationInfo info = this.simulator.simulateOneLevel();
        return info;
    }

    @Override
    public void start() {
        this.launchMario();
    }

    @Override
    public void initBuffers() {

    }
}
