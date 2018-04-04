package soc.state;

import java.util.Vector;

import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCRoad;
import soc.robot.SOCRobotBrain;
import soc.robot.new3p.New3PBrain;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossibleSettlement;
import soc.game.SOCDevCardConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCInventory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import soc.robot.SOCPlayerTracker;


public class SOCPlayerState {
	//State representation variables
	protected int relativeLongestRoadLength;
	protected int relativeKnightsPlayed;
	protected int opponentRoadsAway;
	protected int victoryPoints;
	protected int clayPort;
	protected int orePort;
	protected int sheepPort;
	protected int wheatPort;
	protected int woodPort;
	protected int miscPort;
	protected int opponentRoadAwayEval;
	protected double resourceTotal;
	protected double relativeClay;
	protected double relativeOre;
	protected double relativeSheep;
	protected double relativeWheat;
	protected double relativeWood;
	protected double longestRoadEval;
	protected double knightEval;
	protected double sum;
	protected double resourceEval;
	protected double currentSettlementValue = 0;
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
	private SOCResourceSet resources;
	private SOCInventory newInventory;



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


	public void updateState(SOCPlayer player, SOCPossibleSettlement favoriteSettlement) {
		SOCPlayer[] players = player.game.getPlayers();
		int oppLR = 0;
		int oppLA = 0;
		resources = player.getResources();
		newInventory = player.getInventory();
		resourceTotal = player.getResources().getTotal();

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
			Vector<Integer> doubleEdges = new Vector<Integer>();
			Vector<Integer> tripleEdges = new Vector<Integer>();

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


//getting a value of possible settlemetn positions
		Vector<SOCRoad> allroads = player.getRoads();
		for(SOCRoad road : allroads){
			int adjNodes[] = road.getAdjacentNodes();
			for (int node : adjNodes){
				if (player.canPlaceSettlement(node)){
					double newSettlementValue = calculateSettlementValue(node, player);
					if (newSettlementValue > currentSettlementValue){
						currentSettlementValue = newSettlementValue;
					}
				}
			}
		}
	}

	public double calculateSettlementValue(int node, SOCPlayer player){
		Vector<Integer> hexes = board.getAdjacentHexesToNode(node);
		int y = 0;
		for (Integer hex : hexes){
			int x;
			int hexType = board.getHexTypeFromCoord(hex);
			int hexNumber = board.getNumberOnHexFromCoord(hex);
			if (hexNumber == 8 || hexNumber == 6){
				x = 5;
			}
			else if (hexNumber == 9 || hexNumber == 5){
				x = 4;
			}
			else if (hexNumber == 10 || hexNumber == 4){
				x = 3;
			}
			else if (hexNumber == 11 || hexNumber == 3){
				x = 2;
			}
			else {
				x = 1;
			}
			y = y + x;
		}

		return y/2.5;
	}

	public double evalFunction(){


		longestRoadEval = Math.cbrt(200*relativeLongestRoadLength);
		knightEval = Math.cbrt(400*relativeKnightsPlayed);

		if (opponentRoadsAway == Integer.MAX_VALUE)
			opponentRoadAwayEval = 7;
		else if(opponentRoadsAway == 3)
			opponentRoadAwayEval = 5;
		else if(opponentRoadsAway == 2)
			opponentRoadAwayEval = 2;
		else if(opponentRoadsAway == 1)
			opponentRoadAwayEval = -1;

		clayPort = (portClay) ? 1 : 0;
		orePort = (portOre) ? 1 : 0;
		sheepPort = (portSheep) ? 1 : 0;
		wheatPort = (portWheat) ? 1 : 0;
		woodPort = (portWood) ? 1 : 0;

		if (portMisc){
			resourceEval =  13*(relativeOre + relativeOre*(orePort) +
													relativeClay + relativeClay*(clayPort) +
													relativeWood + relativeWood*(woodPort) +
													relativeSheep + relativeSheep*(sheepPort) +
													relativeWheat + relativeWheat*(wheatPort));
		}
		else{
			resourceEval =  8*(relativeOre + relativeOre*(orePort) +
													relativeClay + relativeClay*(clayPort) +
													relativeWood + relativeWood*(woodPort) +
													relativeSheep + relativeSheep*(sheepPort) +
													relativeWheat + relativeWheat*(wheatPort));
		}

		int devCardTotal = newInventory.getTotal();
		double resourceTotalEval = resourceTotal * .5;

		sum = longestRoadEval + knightEval + opponentRoadAwayEval + resourceEval + devCardTotal + resourceTotalEval + currentSettlementValue;
		return sum;
	}

	//accessor Methods
	public int getRelLongestRoad(){
		return relativeLongestRoadLength;
	}

	public int getRelKnightsPlayed(){
		return relativeKnightsPlayed;
	}

	public int getopponentRoadsAway(){
		return opponentRoadsAway;
	}

	public String getRelativeResources(){
		StringBuilder rel = new StringBuilder();
		rel.append(Double.toString(relativeClay) + "\',");
		rel.append("\'" + Double.toString(relativeOre) + "\',");
		rel.append("\'" + Double.toString(relativeSheep) + "\',");
		rel.append("\'" + Double.toString(relativeWheat) + "\',");
		rel.append("\'" + Double.toString(relativeWood));
		return rel.toString();
	}

	public String getPorts(){
		StringBuilder rel = new StringBuilder();
		rel.append(String.valueOf(portClay) + "\',");
		rel.append("\'" + String.valueOf(portOre) + "\',");
		rel.append("\'" + String.valueOf(portSheep) + "\',");
		rel.append("\'" + String.valueOf(portWheat) + "\',");
		rel.append("\'" + String.valueOf(portWood) + "\',");
		rel.append("\'" + String.valueOf(portMisc));
		return rel.toString();
	}


	public String toString() {
		return "{" + victoryPoints + ", " + relativeLongestRoadLength + ", " + relativeKnightsPlayed + ", " + opponentRoadsAway + ", [" +
		relativeClay + ", " + relativeOre + ", " + relativeSheep + ", " + relativeWheat + ", " + relativeWood + "], [" +
		portClay + ", " + portOre + ", " + portSheep + ", " + portWheat + ", " + portWood + ", " + portMisc + "]}";
	}
}
