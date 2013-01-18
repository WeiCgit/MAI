package UvA.states;

import java.util.Arrays;

import ch.idsia.mario.environments.Environment;


public class MarioState implements State 
{
	private static final long serialVersionUID = 4326470085716280782L;
	
	// state representation
	private final int viewDim = 6;//22;	//size of statespace that  is represented
	private final int miscDims = 4; // dimensions for extra information about state
	// 2 windows that contain info on objects and enemies = viewDim x viewDim
	// miscDims spaces for features such as mayMarioJump() and isMarioOnGround()
	private final int amountOfInput = 2*viewDim*viewDim+miscDims;
	private double[] representation = new double[amountOfInput];
	
	public transient float xPos = 32;
	protected transient float oldXPos = 32;
	
	
	// enemies killed total
	private static int totalKilledByStomp = 0;
	private static int totalKilledByFire = 0;
	private static int totalKilledByShell = 0;
	
	// enemies killed in current scene
	private static int killedByStomp = 0;
	private static int killedByFire = 0;
	private static int killedByShell = 0;
	
	private static int marioMode = 2; //TODO initialise properly
	private static int lastMarioMode = 2; //TODO initialise properly
	private static int collided = 0;
	
	
	public static double rewardSoFar = 0;
	private boolean dieCheck;
	// Parameters for how important the reward for X is 
	private final int REWARD_DISTANCE = 1;
	private final int REWARD_KILLED_STOMP = 0;
	private final int REWARD_KILLED_FIRE = 1;
	private final int REWARD_KILLED_SHELL = 1;
	private final int REWARD_COLLIDED = -50;
	private final int REWARD_FALL = -1000;
	
	
	/**
	 * Constructor, creates state representation
	 * @param environment is the mario environment
	 */

	public MarioState(Environment environment, float xPosIn) {
		if(environment != null) 
			updateRepresentation( (Environment) environment);
			
		this.oldXPos = xPosIn;
	} // end constructor env + xPosIn used by mario
	
	/**
	 * Constructor for when environment was not available: input is representation alone
	 * Used by clone
	 */
	public MarioState(double[] reprIn, float oldXPosIn){
		this.representation = new double[reprIn.length];
		System.arraycopy(reprIn, 0, this.representation, 0, reprIn.length);
		this.oldXPos = oldXPosIn;
	} // end constructor for representation input used by clone
	
	
	/**
	 * updateRepresentation creates the representation of the state
	 */
	private void updateRepresentation(Environment environment) {
        byte[][] scene = environment.getLevelSceneObservation();
        byte[][] enemies = environment.getEnemiesObservation();
		
		
	    int which = 0;
	    for (int i = -viewDim/2; i < viewDim/2; i++)
	    {
	        for (int j = -viewDim/2; j < viewDim/2; j++)
	        {
	            representation[which++] = probe(i, j, scene);
	        }
	    }
	    for (int i = -viewDim/2; i < viewDim/2; i++)
	    {
	        for (int j = -viewDim/2; j < viewDim/2; j++)
	        {
	            representation[which++] = probe(i, j, enemies);
	        }
	    }
	    
	    representation[representation.length - 4] = environment.getMarioMode();
	    representation[representation.length - 3] = environment.mayMarioJump() ? 1.0 : 0.0;
	    representation[representation.length - 2] = environment.isMarioOnGround() ? 1.0 : 0.0;
	    representation[representation.length - 1] = environment.canShoot() ? 1.0 : 0.0;

	    this.oldXPos = xPos;
	    xPos = environment.getMarioFloatPos()[0];
	    dieCheck = environment.getMarioFloatPos()[1] > 225;
	    
	    // update enemies killed
		killedByFire = environment.getKillsByFire() - totalKilledByFire;
		killedByStomp = environment.getKillsByStomp() - totalKilledByStomp;
		killedByShell = environment.getKillsByShell() - totalKilledByShell;
		totalKilledByFire = environment.getKillsByFire();
		totalKilledByStomp = environment.getKillsByStomp();
		totalKilledByShell = environment.getKillsByShell();
		marioMode = environment.getMarioMode();
		
		// calculate if collided (lose mario mode)
	    if(marioMode < lastMarioMode){
	    	collided = 1;
	    	lastMarioMode = marioMode;
	    }
	    else
	    	collided = 0;
	} // end updateRepresentation
		
	/** TODO: get better reward function
	 * Get the reward of prey or predator based on the state.
	 * @return reward of mario
	 */ 
	public float getReward() {
		if(dieCheck) {
			System.out.println("Dieing!!!!!");
			return REWARD_FALL;
		} // end hack to check if gonna die
		float distance = xPos - oldXPos;
		
		float reward = (float) (distance*REWARD_DISTANCE + killedByStomp*REWARD_KILLED_STOMP + 
				killedByFire*REWARD_KILLED_FIRE + killedByShell*REWARD_KILLED_SHELL + 
				collided*REWARD_COLLIDED - 0.5);
		return reward;
	} // end getReward

	/**
	 * Used in getAction for getting x and y positions relative to mario
	 * @param x: absolute x position
	 * @param y: absolute y position
	 * @param scene: scene object created in basicmarioAIagent
	 * @return
	 */
	private double probe(int x, int y, byte[][] scene)
	{
	    int realX = x + Environment.HalfObsWidth;
	    int realY = y + Environment.HalfObsHeight;
	    return (scene[realX][realY] != 0) ? 1.0 : 0.0;
	} // end probe
	
	
	/**
	 * Return a clone of this state
	 * @return clone
	 */
	public State clone() {
		return new MarioState(representation, oldXPos);
	} // end clone
	
	/**
	 * Reset the info to no info
	 */
	public void reset() {
		for(int i = 0; i < representation.length; i++)
		representation[i] = 0.0;
	} // end reset
	
	public static void resetStatic(int mode){
		totalKilledByStomp = 0;
		totalKilledByFire = 0;
		totalKilledByShell = 0;
		killedByStomp = 0;
		killedByFire = 0;
		killedByShell = 0;
		marioMode = mode;
		lastMarioMode = marioMode;
		collided = 0;
		rewardSoFar = 0;
	}// end resetStatic
	
	
	@Override
	public String toString() {
		String string = "";
		for(int i = 0; i<representation.length; i++) 
		{
			string += String.format(" %.1f", representation[i]);
			if( ( (i+1) % 7) == 0)
				string += "\n";
			if( ( (i+1) % 49) == 0)
				string += "\n";
		}
		return string; 
	} // end toString

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MarioState other = (MarioState) obj;
		if (amountOfInput != other.amountOfInput)
			return false;
		if (!Arrays.equals(representation, other.representation))
			return false;
		return true;
	}
		
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + amountOfInput;
		result = prime * result + Arrays.hashCode(representation);
		return result;
	}

	/**
	 * Override getrepresentation, returns representation of the state
	 */
	@Override
	public double[] getRepresentation() {
		return this.representation;
	}
	
	/**
	 * getxPos, necessarily to create new state
	 */
	public float getXPos() {
		return this.oldXPos;
	} // end get xPos


} // end mariostate class
