package soc.state;

import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossibleSettlement;

public class SOCPlayerState {
	//State representation variables
	protected int relativeLongestRoadLength;
	protected int relativeKnightsPlayed;
	protected int relativeTurnsToDesiredNode;
	protected double relativeClay;
	protected double relativeOre;
	protected double relativeSheep;
	protected double relativeWheat;
	protected double relativeWood;
	protected boolean portClay;
	protected boolean portOre;
	protected boolean portSheep;
	protected boolean portWheat;
	protected boolean portWood;
	protected boolean portMisc;
	
	//Helper variables
	private SOCBoard board;
	private double boardClay;
	private double boardOre;
	private double boardSheep;
	private double boardWheat;
	private double boardWood;

	public SOCPlayerState(SOCBoard board) {
		this.relativeLongestRoadLength = -5;
		this.relativeKnightsPlayed = -3;
		this.relativeTurnsToDesiredNode = Integer.MAX_VALUE;
	
		this.board = board;
		SOCPlayerNumbers boardNumbers = new SOCPlayerNumbers(board);
		
		for(int coord : board.getLandHexCoords()) {
			int hexType = board.getHexTypeFromCoord(coord);
			int hexNumber = board.getHexNumFromCoord(coord);
			
			boardNumbers.addNumberForResource(hexNumber, hexType, coord);
		}
		
		SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate();
		estimate.recalculateEstimates(boardNumbers);
		int[] boardResources = estimate.getRollsPerResource();
		
		boardClay = (1 / boardResources[0]) * 3.0; //Each hex can be associated with up to 3 nodes
		boardOre = (1 / boardResources[1]) * 3.0;
		boardSheep = (1 / boardResources[2]) * 3.0;
		boardWheat = (1 / boardResources[3]) * 3.0;
		boardWood = (1 / boardResources[4]) * 3.0;
	}
	
	public SOCPlayerState(SOCPlayerState state) {
		this(state.board);
	}
	
	public static SOCPlayerState simulateState(SOCPlayerState currentState, SOCPlayer player) {
		SOCPlayerState simulation = new SOCPlayerState(currentState);
		simulation.updateState(player, 0);
		return simulation;
	}
	
	public void updateState(SOCPlayer player, int favoriteSettlement) {
		relativeLongestRoadLength = player.getLongestRoadLength() - 
				Math.max(5, player.game.getPlayerWithLongestRoad().getLongestRoadLength());
		relativeKnightsPlayed = player.getNumKnights() - 
				Math.max(3, player.game.getPlayerWithLargestArmy().getNumKnights());
	
		//SOCPossibleSettlement playerFavoritePossible = new SOCPossibleSettlement(player, favoriteSettlement, )
		
		int[] playerResources = SOCBuildingSpeedEstimate.getRollsForResourcesSorted(player);
		relativeClay = (1 / playerResources[0]) / boardClay;
		relativeOre = (1 / playerResources[1]) / boardOre;
		relativeSheep = (1 / playerResources[2]) / boardSheep;
		relativeWheat = (1 / playerResources[3]) / boardWheat;
		relativeWood = (1 / playerResources[4]) / boardWood;
		
		portClay = player.getPortFlag(SOCBoard.CLAY_PORT);
		portOre = player.getPortFlag(SOCBoard.ORE_PORT);
		portSheep = player.getPortFlag(SOCBoard.SHEEP_PORT);
		portWheat = player.getPortFlag(SOCBoard.WHEAT_PORT);
		portWood = player.getPortFlag(SOCBoard.WOOD_PORT);
		portMisc = player.getPortFlag(SOCBoard.MISC_PORT);
	}
	
	public String toString() {
		return "{" + relativeLongestRoadLength + ", " + relativeKnightsPlayed + ", " + relativeTurnsToDesiredNode + ", [" +
		relativeClay + ", " + relativeOre + ", " + relativeSheep + ", " + relativeWheat + ", " + relativeWood + "], [" +
		portClay + ", " + portOre + ", " + portSheep + ", " + portWheat + ", " + portWood + ", " + portMisc + "]}";
	}
}
