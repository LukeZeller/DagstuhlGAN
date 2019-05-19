package communication;

import ch.idsia.ai.agents.AgentsPool;
import ch.idsia.ai.agents.human.HumanKeyboardAgent;
import ch.idsia.mario.engine.level.Level;
import ch.idsia.mario.simulation.BasicSimulator;
import ch.idsia.mario.simulation.Simulation;
import ch.idsia.tools.CmdLineOptions;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.EvaluationOptions;
import ch.idsia.tools.ToolsConfigurator;
import competition.icegic.robin.AStarAgent;
import cmatest.ForcedActionsAgent;

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
        FORCED_AGENT,
    }

    /**
     * Default mario launcher does not have any command line parameters
     */
    public void launchMario() {
    	String[] options = new String[] {""};
    	launchMario(options, AgentType.ASTAR_AGENT);
    }
 
    /**
     * This version of launching Mario allows for several parameters
     * @param options General command line options (currently not really used)
     * @param humanPlayer Whether a human is playing rather than a bot
     */
    public void launchMario(String[] options, AgentType agent) {
        this.evaluationOptions = new CmdLineOptions(options);  // if none options mentioned, all defaults are used.
        // set agents
        createAgentsPool(agent);
        // Short time for evolution, but more for human
        if(agent != AgentType.HUMAN_PLAYER) evaluationOptions.setTimeLimit(20);
        // TODO: Make these configurable from commandline?
        evaluationOptions.setMaxFPS(agent != AgentType.HUMAN_PLAYER); // Slow for human players, fast otherwise
        evaluationOptions.setVisualization(true); // Set true to watch evaluations
        // Create Mario Component
        ToolsConfigurator.CreateMarioComponentFrame(evaluationOptions);
        evaluationOptions.setAgent(AgentsPool.getCurrentAgent());
        System.out.println(evaluationOptions.getAgent().getClass().getName());
        // set simulator
        this.simulator = new BasicSimulator(evaluationOptions.getSimulationOptionsCopy());
    }

    /**
     * Set the agent that is evaluated in the evolved levels
     */
    public static void createAgentsPool(AgentType agent)
    {
    	// Could still generalize this more
        switch(agent){
            case HUMAN_PLAYER:
                AgentsPool.setCurrentAgent(new HumanKeyboardAgent());
                break;
            case ASTAR_AGENT:
                AgentsPool.setCurrentAgent(new AStarAgent());
                break;
            case FORCED_AGENT:
                AgentsPool.setCurrentAgent(new ForcedActionsAgent());
                break;
            default:
                throw new RuntimeException("Unhandled agent type");
        }
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
