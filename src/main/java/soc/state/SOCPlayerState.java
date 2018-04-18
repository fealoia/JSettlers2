package soc.state;

import java.util.Vector;

import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCRoad;
import soc.game.SOCGame;
import soc.game.SOCSettlement;
import soc.robot.SOCRobotBrain;
import soc.robot.new3p.New3PBrain;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCPossibleRoad;
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
	protected int hasKnightToPlay;
	protected int hasDISCToPlay;
	protected int hasMONOToPlay;
	protected int hasRBToPlay;
	protected double resourceTotal;
	protected double relativeClay;
	protected double relativeOre;
	protected double relativeSheep;
	protected double relativeWheat;
	protected double relativeWood;
	protected double longestRoadEval;
	protected double largestArmyEval;
	protected double sum;
	protected double relativeResourceEval;
	protected double nextBestSettlementValue;
	protected double nextBestSettlementAndRoadValue = 0;
	protected double nextBestCityValue;
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


	public void updateState(SOCPlayer player) {
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


		estimate.recalculateEstimates(player.getNumbers());
		int[] playerResources = estimate.getRollsPerResource();

		clayPort = (portClay) ? 1 : 0;
		orePort = (portOre) ? 1 : 0;
		sheepPort = (portSheep) ? 1 : 0;
		wheatPort = (portWheat) ? 1 : 0;
		woodPort = (portWood) ? 1 : 0;

		relativeClay = playerResources[1] > 0 ? (1.0 / playerResources[1]) / boardClay : 0;
		relativeOre = playerResources[2] > 0 ? (1.0 / playerResources[2]) / boardOre : 0;
		relativeSheep = playerResources[3] > 0 ? (1.0 / playerResources[3]) / boardSheep : 0;
		relativeWheat = playerResources[4] > 0 ? (1.0 / playerResources[4]) / boardWheat : 0;
		relativeWood = playerResources[5] > 0 ? (1.0 / playerResources[5]) / boardWood : 0;

		hasKnightToPlay = newInventory.hasPlayable(SOCDevCardConstants.KNIGHT) ? 1 : 0;
		hasDISCToPlay = newInventory.hasPlayable(SOCDevCardConstants.DISC) ? 1 : 0;
		hasMONOToPlay = newInventory.hasPlayable(SOCDevCardConstants.MONO) ? 1 : 0;
		hasRBToPlay = newInventory.hasPlayable(SOCDevCardConstants.ROADS) ? 1 : 0;

		portClay = player.getPortFlag(SOCBoard.CLAY_PORT);
		portOre = player.getPortFlag(SOCBoard.ORE_PORT);
		portSheep = player.getPortFlag(SOCBoard.SHEEP_PORT);
		portWheat = player.getPortFlag(SOCBoard.WHEAT_PORT);
		portWood = player.getPortFlag(SOCBoard.WOOD_PORT);
		portMisc = player.getPortFlag(SOCBoard.MISC_PORT);


		//getting a value of possible settlement positions - 0 if no settlements can be built
		nextBestSettlementValue = calculateNextSettlementValue(player);

		//getting a value of possible settlement positions after placing an additional road
		HashSet<Integer> roads = (HashSet<Integer>) player.getPotentialRoads().clone();
			for(Integer road : roads) {
				SOCRoad temp = new SOCRoad(player, road, board);
				player.getGame().putTempPiece(temp);
				double newNextSettlementAndRoadValue = calculateNextSettlementValue(player);
				if (newNextSettlementAndRoadValue > nextBestSettlementAndRoadValue){
					nextBestSettlementAndRoadValue = newNextSettlementAndRoadValue;
				}
				player.getGame().undoPutTempPiece(temp);
			}


		//getting a value for possible cities
		nextBestCityValue = calculateNextCityValue(player);

	}

	public double calculateNextCityValue(SOCPlayer player){
		double bestCityValue = 0;

		Vector<SOCSettlement> settlements = player.getSettlements();
		for (SOCSettlement settlement : settlements)
		{
				int settlementNodeCoord = settlement.getCoordinates();
				Vector<Integer> hexes = board.getAdjacentHexesToNode(settlementNodeCoord);
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
				double newCityValue = y/2.5;
				if (newCityValue > bestCityValue) {
					bestCityValue = newCityValue;
				}
		}
		return bestCityValue;

	}

	public double calculateNextSettlementValue(SOCPlayer player){
		double bestSettlementValue = 0;
		Vector<SOCRoad> allroads = player.getRoads();
		for(SOCRoad road : allroads){
			int adjNodes[] = road.getAdjacentNodes();
			for (int node : adjNodes){
				if (player.canPlaceSettlement(node)){
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
					double newSettlementValue = y/2.5;
					if (newSettlementValue > bestSettlementValue) {
						bestSettlementValue = newSettlementValue;
					}
				}
			}
		}
		return bestSettlementValue;
	}


	public double evalFunction(){

		//number of resources in hand
		double resourcesInHand = .5 * (resources.getAmount(0) + resources.getAmount(0)*(clayPort) +
												resources.getAmount(1) + resources.getAmount(1)*(orePort) +
												resources.getAmount(2) + resources.getAmount(2)*(sheepPort) +
												resources.getAmount(3) + resources.getAmount(3)*(wheatPort) +
												resources.getAmount(4) + resources.getAmount(4)*(woodPort));

		//playable dev cards in hand
		double devCardsInHand = 	2.5 * (hasKnightToPlay + hasDISCToPlay + hasMONOToPlay + hasRBToPlay);

		//relative longest road
		longestRoadEval = Math.cbrt(200*relativeLongestRoadLength);

		//relative largest army
		largestArmyEval = Math.cbrt(400*relativeKnightsPlayed);


		//relative resources eval
		if (portMisc){
			relativeResourceEval =  13*(relativeOre + relativeOre*(orePort) +
													relativeClay + relativeClay*(clayPort) +
													relativeWood + relativeWood*(woodPort) +
													relativeSheep + relativeSheep*(sheepPort) +
													relativeWheat + relativeWheat*(wheatPort));
		}
		else{
			relativeResourceEval =  8*(relativeOre + relativeOre*(orePort) +
													relativeClay + relativeClay*(clayPort) +
													relativeWood + relativeWood*(woodPort) +
													relativeSheep + relativeSheep*(sheepPort) +
													relativeWheat + relativeWheat*(wheatPort));
		}

		sum = resourcesInHand + devCardsInHand + victoryPoints +
					longestRoadEval + largestArmyEval +
					nextBestSettlementValue + nextBestSettlementAndRoadValue + nextBestCityValue +
					relativeResourceEval;
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

	public Vector getState(){
		Vector stateVector = new Vector(26);
		//amount of clay in hand
		stateVector.addElement(resources.getAmount(0));
		//amount of ore in hand
		stateVector.addElement(resources.getAmount(1));
		//amount of sheep in hand
		stateVector.addElement(resources.getAmount(2));
		//amount of wheat in hand
		stateVector.addElement(resources.getAmount(3));
		//amount of wood in hand
		stateVector.addElement(resources.getAmount(4));
		//has knight to play this turn
		stateVector.addElement(hasKnightToPlay);
		//has year of plenty to play this turn
		stateVector.addElement(hasDISCToPlay);
		//has monopoly to play this turn
		stateVector.addElement(hasMONOToPlay);
		//has road builder to play this turn
		stateVector.addElement(hasRBToPlay);
		//number of VP player has
		stateVector.addElement(victoryPoints);
		//relative longest road
		stateVector.addElement(relativeLongestRoadLength);
		//relative largest army
		stateVector.addElement(relativeKnightsPlayed);
		//rating of best settlement to build
		stateVector.addElement(nextBestSettlementValue);
		//rating of best settlement to build after one road
		stateVector.addElement(nextBestSettlementAndRoadValue);
		//rating of best city to build
		stateVector.addElement(nextBestCityValue);
		//relative clay
		stateVector.addElement(relativeClay);
		//relative ore
		stateVector.addElement(relativeOre);
		//relative sheep
		stateVector.addElement(relativeSheep);
		//relative wheat
		stateVector.addElement(relativeWheat);
		//relative wood
		stateVector.addElement(relativeWood);
		//has clay port
		stateVector.addElement(clayPort);
		//has ore port
		stateVector.addElement(orePort);
		//has sheep port
		stateVector.addElement(sheepPort);
		//has wheat port
		stateVector.addElement(wheatPort);
		//has wood port
		stateVector.addElement(woodPort);
		//has misc port
		stateVector.addElement(miscPort);

		return stateVector;

	}

	public String stateToString(Vector stateVector){
		stateVector.toString();
		StringBuilder rel = new StringBuilder();
		for (Object element : stateVector) {
			rel.append("\'" + element + "\',");
		}
		return rel.toString();
	}


	public String toString() {
		return "{" + victoryPoints + ", " + relativeLongestRoadLength + ", " + relativeKnightsPlayed + ", " + opponentRoadsAway + ", [" +
		relativeClay + ", " + relativeOre + ", " + relativeSheep + ", " + relativeWheat + ", " + relativeWood + "], [" +
		portClay + ", " + portOre + ", " + portSheep + ", " + portWheat + ", " + portWood + ", " + portMisc + "]}";
	}
}
