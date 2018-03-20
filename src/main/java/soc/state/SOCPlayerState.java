package soc.state;

import java.util.Vector;

import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCRoad;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossibleSettlement;

public class SOCPlayerState {
	//State representation variables
	protected int relativeLongestRoadLength;
	protected int relativeKnightsPlayed;
	protected int opponentRoadsAway;
	protected int victoryPoints;
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
	private SOCBuildingSpeedEstimate estimate;
	private double boardClay;
	private double boardOre;
	private double boardSheep;
	private double boardWheat;
	private double boardWood;

	public SOCPlayerState(SOCBoard board) {
		this.relativeLongestRoadLength = -5;
		this.relativeKnightsPlayed = -3;
		this.opponentRoadsAway = Integer.MAX_VALUE;
		this.estimate = new SOCBuildingSpeedEstimate();

		this.board = board;
		SOCPlayerNumbers boardNumbers = new SOCPlayerNumbers(board);
		
		for(int coord : board.getLandHexCoords()) {
			int hexType = board.getHexTypeFromCoord(coord);
			int hexNumber = board.getNumberOnHexFromCoord(coord);
			
			boardNumbers.addNumberForResource(hexNumber, hexType, coord);
		}
		
		estimate.recalculateEstimates(boardNumbers);
		int[] boardResources = estimate.getRollsPerResource();
		//Each hex can be associated with up to 3 nodes
		boardClay = (1.0 / boardResources[1]) * 3; 
		boardOre = (1.0 / boardResources[2]) * 3;  
		boardSheep = (1.0 / boardResources[3]) * 3; 
		boardWheat = (1.0 / boardResources[4]) * 3; 
		boardWood = (1.0 / boardResources[5]) * 3; 
	}
	
	public SOCPlayerState(SOCPlayerState state) {
		this(state.board);
	}
	
	public static SOCPlayerState simulateState(SOCPlayerState currentState, SOCPlayer player) {
		SOCPlayerState simulation = new SOCPlayerState(currentState);
		simulation.updateState(player, null);
		return simulation;
	}
	
	public void updateState(SOCPlayer player, SOCPossibleSettlement favoriteSettlement) {		
		SOCPlayer[] players = player.game.getPlayers();
		int oppLR = 0;
		int oppLA = 0;
		
		for(int i=0; i<players.length; i++) {
			if(i == player.playerNumber) continue;
			if(players[i].getLongestRoadLength() > oppLR)
				oppLR = players[i].getLongestRoadLength();
			if(players[i].getNumKnights() > oppLR)
				oppLA = players[i].getNumKnights();
		}

		relativeLongestRoadLength = player.getLongestRoadLength() - 
				Math.max(5,oppLR);
		relativeKnightsPlayed = player.getNumKnights() - 
				Math.max(3, oppLA);
		victoryPoints = player.getTotalVP();
		
		if(favoriteSettlement != null) {
			Vector<Integer> adjNodes = board.getAdjacentNodesToNode(favoriteSettlement.getCoordinates());
			Vector<Integer> singleEdges = board.getAdjacentEdgesToNode(favoriteSettlement.getCoordinates());
			Vector<Integer> doubleEdges = new Vector<>();
			Vector<Integer> tripleEdges = new Vector<>();
			
			for(Integer node : adjNodes) {
				Vector<Integer> adjEdges = board.getAdjacentEdgesToNode(node);
				for(Integer edge : adjEdges) {
					doubleEdges.add(edge);
				}
				
				Vector<Integer> neighbors = board.getAdjacentNodesToNode(node);
				for(Integer edge : neighbors) {
					tripleEdges.add(edge);
				}
			}
			
			for(int i=0; i<players.length; i++) {
				if(i==player.playerNumber) continue;
				Vector<SOCRoad> roads = players[i].getRoads();
				for(SOCRoad road : roads) {
					if(singleEdges.contains(road.getCoordinates())) {
						opponentRoadsAway = 1;
						break;
					}
					
					if(opponentRoadsAway > 2 && doubleEdges.contains(road.getCoordinates())) {
						opponentRoadsAway = 2;
						continue;
					}
					
					if(opponentRoadsAway > 3 && tripleEdges.contains(road.getCoordinates())) {
						opponentRoadsAway = 3;
					}
				}
				
				if(opponentRoadsAway == 1) break;
			}
		}
		
		estimate.recalculateEstimates(player.getNumbers());
		int[] playerResources = estimate.getRollsPerResource();

		relativeClay = playerResources[1] > 0 ? (1.0 / playerResources[1]) / boardClay : 0;
		relativeOre = playerResources[2] > 0 ? (1.0 / playerResources[2]) / boardOre : 0;
		relativeSheep = playerResources[3] > 0 ? (1.0 / playerResources[3]) / boardSheep : 0;
		relativeWheat = playerResources[4] > 0 ? (1.0 / playerResources[4]) / boardWheat : 0;
		relativeWood = playerResources[5] > 0 ? (1.0 / playerResources[5]) / boardWood : 0;
		
		portClay = player.getPortFlag(SOCBoard.CLAY_PORT);
		portOre = player.getPortFlag(SOCBoard.ORE_PORT);
		portSheep = player.getPortFlag(SOCBoard.SHEEP_PORT);
		portWheat = player.getPortFlag(SOCBoard.WHEAT_PORT);
		portWood = player.getPortFlag(SOCBoard.WOOD_PORT);
		portMisc = player.getPortFlag(SOCBoard.MISC_PORT);
	}
	
	public String toString() {
		return "{" + victoryPoints + ", " + relativeLongestRoadLength + ", " + relativeKnightsPlayed + ", " + opponentRoadsAway + ", [" +
		relativeClay + ", " + relativeOre + ", " + relativeSheep + ", " + relativeWheat + ", " + relativeWood + "], [" +
		portClay + ", " + portOre + ", " + portSheep + ", " + portWheat + ", " + portWood + ", " + portMisc + "]}";
	}
}
