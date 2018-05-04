package soc.state;

import java.util.Vector;
import java.util.HashSet;
import java.text.DecimalFormat;
import java.util.Random;

import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCRoad;
import soc.game.SOCGame;
import soc.game.SOCSettlement;
import soc.game.SOCCity;
import soc.robot.SOCRobotBrain;
import soc.robot.new3p.New3PBrain;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCInventory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import soc.robot.SOCPlayerTracker;
import soc.server.database.SOCDBHelper;
import soc.robot.SOCPossibleCard;
import soc.game.SOCPlayingPiece;




public class SOCPlayerState {

	static Random random = new Random();

        static String mfOne;
	static double mfOne_weightOne;
        static double mfOne_weightTwo;
        static double mfOne_weightThree;
        static double mfOne_weightFour;
        static double mfOne_weightFive;

        static String mfTwo;
        static double mfTwo_weightOne;
        static double mfTwo_weightTwo;
        static double mfTwo_weightThree;
        static double mfTwo_weightFour;
        static double mfTwo_weightFive;

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
	protected double eval;
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
	protected double[] relativeResources = new double[5];
	protected int[] hasPort = new int[7];
	protected double settlementMin = 3;
	protected double settlementMax = 13343.463369963369;
	protected double cityMin =  3;
	protected double cityMax =  15440.944444444445;
	protected double roadMin = 0;
	protected double roadMax =  608.3699132852703;
	protected double devCardNormalizer = 1;
	protected int z = 0;



	//Helper variables
	private SOCBoard board;
	private SOCGame boardGame;
	private static String currentGameName;
	private SOCBuildingSpeedEstimate estimate;
	private double boardClay;
	private double boardOre;
	private double boardSheep;
	private double boardWheat;
	private double boardWood;
	private SOCResourceSet resources;
	private SOCInventory newInventory;
	private SOCPossibleSettlement bestSettlement;
	private SOCPossibleRoad bestRoad;
	private SOCPossibleCity bestCity;




	public SOCPlayerState(SOCBoard board, SOCGame game) {
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
	     
		this.boardGame = game;
        }

	public SOCPlayerState(SOCPlayerState state) {
		this(state.board, state.boardGame);
	}

        public static void updateWeights(SOCGame game) {
             if(currentGameName == game.getName()) return;
             currentGameName = game.getName();
        
	     mfOne_weightOne = random.nextDouble();
             mfOne_weightTwo = (1-mfOne_weightOne) * random.nextDouble();
             mfOne_weightThree = (1 - (mfOne_weightOne + mfOne_weightTwo)) * random.nextDouble();
             mfOne_weightFour = (1 - (mfOne_weightOne + mfOne_weightTwo + mfOne_weightThree)) * random.nextDouble();
             mfOne_weightFive = 1 - mfOne_weightFour - mfOne_weightThree - mfOne_weightTwo - mfOne_weightOne;
	     
             mfTwo_weightOne = random.nextDouble();
             mfTwo_weightTwo = (1-mfTwo_weightOne) * random.nextDouble();
             mfTwo_weightThree = (1 - (mfTwo_weightOne + mfTwo_weightTwo)) * random.nextDouble();
             mfTwo_weightFour = (1 - (mfTwo_weightOne + mfTwo_weightTwo + mfTwo_weightThree)) * random.nextDouble();
             mfTwo_weightFive = 1 - mfTwo_weightFour - mfTwo_weightThree - mfTwo_weightTwo - mfTwo_weightOne;
	
             SOCPlayer[] players = game.getPlayers();
             boolean first = false;

             
             for(SOCPlayer player: players) {
                 if(player.getName().indexOf("mf") > -1) {
                    if(first == false) {
                        mfOne = player.getName();
                    } else {
                        mfTwo = player.getName();
                    }

                    try {
                        SOCDBHelper.saveWeights(getWeights(first), player);
                    } catch (Exception e){
                        System.err.println("Error saving weights:" + e);
                    }
                    first = true;
                 } 
             }             
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

		relativeClay = playerResources[1] > 0 ? (1.0 / playerResources[1]) / boardClay : 0;
		relativeResources[0] = relativeClay;
		relativeOre = playerResources[2] > 0 ? (1.0 / playerResources[2]) / boardOre : 0;
		relativeResources[1] = relativeOre;
		relativeSheep = playerResources[3] > 0 ? (1.0 / playerResources[3]) / boardSheep : 0;
		relativeResources[2] = relativeSheep;
		relativeWheat = playerResources[4] > 0 ? (1.0 / playerResources[4]) / boardWheat : 0;
		relativeResources[3] = relativeWheat;
		relativeWood = playerResources[5] > 0 ? (1.0 / playerResources[5]) / boardWood : 0;
		relativeResources[4] = relativeWood;

		Arrays.sort(relativeResources);

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

		hasPort[0]= 0;
		clayPort = (portClay) ? 1 : 0;
		hasPort[1] = clayPort;
		orePort = (portOre) ? 1 : 0;
		hasPort[2] = orePort;
		sheepPort = (portSheep) ? 1 : 0;
		hasPort[3] = sheepPort;
		wheatPort = (portWheat) ? 1 : 0;
		hasPort[4] = wheatPort;
		woodPort = (portWood) ? 1 : 0;
		hasPort[5] = woodPort;
		hasPort[6] = 0;
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


	public double stateEvalFunction(){

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

		eval = resourcesInHand + devCardsInHand + victoryPoints +
					longestRoadEval + largestArmyEval +
					nextBestSettlementValue + nextBestSettlementAndRoadValue + nextBestCityValue +
					relativeResourceEval;
		return eval;
	}

	public double settlementEvalFunction(Integer settlement, SOCPlayer player){
		int leastRelResourceType = 6;
		int secondLeastRelResourceType = 6;
		double leastRelResource = relativeResources[0];
		double secondLeastRelResource = relativeResources[1];
		boolean hexMatchesLeastRelResources = false;
		double multiplier = 1.0;

		if (leastRelResource == relativeClay) leastRelResourceType = 1;
		if (leastRelResource == relativeOre) leastRelResourceType = 2;
		if (leastRelResource == relativeSheep) leastRelResourceType = 3;
		if (leastRelResource == relativeWheat) leastRelResourceType = 4;
		if (leastRelResource == relativeWood) leastRelResourceType = 5;

		if (secondLeastRelResource == relativeClay) secondLeastRelResourceType = 1;
		if (secondLeastRelResource == relativeOre) secondLeastRelResourceType = 2;
		if (secondLeastRelResource == relativeSheep) secondLeastRelResourceType = 3;
		if (secondLeastRelResource == relativeWheat) secondLeastRelResourceType = 4;
		if (secondLeastRelResource == relativeWood) secondLeastRelResourceType = 5;

		if (leastRelResourceType == 6 || secondLeastRelResourceType == 6) System.out.println("ERROR!! DIDNT MATCH");

		Vector<Integer> hexes = board.getAdjacentHexesToNode(settlement);
		int y = 0;
		for (Integer hex : hexes){
			int x;
			int hexType = board.getHexTypeFromCoord(hex);
			if (hexType == leastRelResourceType || hexType == secondLeastRelResourceType) multiplier = multiplier + .5;
			if (hexType <= 6){
				if (hasPort[hexType] == 1) multiplier = multiplier + .5;
			}
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

		double resourcesGained = multiplier * y;

		//getting how impactful builidng this settlement would be to other players
		// ie. how many other players can/want to build there now or in the future
		int opponentsAdjacentToSettlement = 0;
		int opponentsAdjacentWithOneRoadToSettlement = 0;

		SOCPlayer[] players = player.game.getPlayers();
		for(SOCPlayer current : players) {
			if(current == player) continue;
			HashSet<Integer> settlements = (HashSet<Integer>) current.getPotentialSettlements().clone();
			if (settlements.contains(settlement)){
				opponentsAdjacentToSettlement++;
			}
			//adding an additional road to each player
			HashSet<Integer> roads = (HashSet<Integer>) current.getPotentialRoads().clone();
			for(Integer road : roads){
					SOCRoad temp = new SOCRoad(current, road, board);
					current.game.putTempPiece(temp);
					HashSet<Integer> settlementsWithAdditionalRoad = (HashSet<Integer>) current.getPotentialSettlements().clone();
					if (settlementsWithAdditionalRoad.contains(settlement)){
						opponentsAdjacentWithOneRoadToSettlement++;
						current.game.undoPutTempPiece(temp);
						break;
					}
					current.game.undoPutTempPiece(temp);
			}
		}

		double opponentImpact = opponentsAdjacentToSettlement + .5 * opponentsAdjacentWithOneRoadToSettlement;


		//comparing relative resources with the new settlment to previous values without new settlement
		double currentRelativeClay = relativeClay;
		double currentRelativeOre = relativeOre;
		double currentRelativeSheep = relativeSheep;
		double currentRelativeWheat = relativeWheat;
		double currentRelativeWood = relativeWood;

		SOCSettlement temp = new SOCSettlement(player, settlement, player.game.getBoard());
		player.game.putTempPiece(temp);
		updateState(player);

		double clayDifference = relativeClay - currentRelativeClay;
		double oreDifference = relativeOre - currentRelativeOre;
		double sheepDifference = relativeSheep - currentRelativeSheep;
		double wheatDifference = relativeWheat - currentRelativeWheat;
		double woodDifference = relativeWood - currentRelativeWood;

		double relativeResourceGain = clayDifference + oreDifference + sheepDifference + wheatDifference + woodDifference;
		player.game.undoPutTempPiece(temp);
		updateState(player);
		eval = resourcesGained + opponentImpact + relativeResourceGain;
		// try {
		// 	SOCDBHelper.settlementNormalization(eval);
		// }
		// catch (Exception e){
		// 	 System.err.println("Error updating on settlement:" + e);
		// }
		return (eval - settlementMin)/(settlementMax - settlementMin);
	}

	public double cityEvalFunction(Integer city, SOCPlayer player){
		//getting the diffrence in in-hand resources
		int leastRelResourceType = 6;
		int secondLeastRelResourceType = 6;
		double leastRelResource = relativeResources[0];
		double secondLeastRelResource = relativeResources[1];
		boolean hexMatchesLeastRelResources = false;
		double multiplier = 1.0;

		if (leastRelResource == relativeClay) leastRelResourceType = 1;
		if (leastRelResource == relativeOre) leastRelResourceType = 2;
		if (leastRelResource == relativeSheep) leastRelResourceType = 3;
		if (leastRelResource == relativeWheat) leastRelResourceType = 4;
		if (leastRelResource == relativeWood) leastRelResourceType = 5;

		if (secondLeastRelResource == relativeClay) secondLeastRelResourceType = 1;
		if (secondLeastRelResource == relativeOre) secondLeastRelResourceType = 2;
		if (secondLeastRelResource == relativeSheep) secondLeastRelResourceType = 3;
		if (secondLeastRelResource == relativeWheat) secondLeastRelResourceType = 4;
		if (secondLeastRelResource == relativeWood) secondLeastRelResourceType = 5;

		if (leastRelResourceType == 6 || secondLeastRelResourceType == 6) System.out.println("ERROR!! DIDNT MATCH");

		Vector<Integer> hexes = board.getAdjacentHexesToNode(city);
		int y = 0;
		for (Integer hex : hexes){
			int x;
			int hexType = board.getHexTypeFromCoord(hex);
			if (hexType == leastRelResourceType || hexType == secondLeastRelResourceType) multiplier = multiplier + .5;
			if (hexType <= 6){
				if (hasPort[hexType] == 1) multiplier = multiplier + .5;
			}
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

		double resourcesGained = multiplier * y;


		//getting the relative resource differences
		double currentRelativeClay = relativeClay;
		double currentRelativeOre = relativeOre;
		double currentRelativeSheep = relativeSheep;
		double currentRelativeWheat = relativeWheat;
		double currentRelativeWood = relativeWood;

		SOCCity temp = new SOCCity(player, city, player.game.getBoard());
		player.game.putTempPiece(temp);
		updateState(player);

		double clayDifference = relativeClay - currentRelativeClay;
		double oreDifference = relativeOre - currentRelativeOre;
		double sheepDifference = relativeSheep - currentRelativeSheep;
		double wheatDifference = relativeWheat - currentRelativeWheat;
		double woodDifference = relativeWood - currentRelativeWood;

		double relativeResourceGain = clayDifference + oreDifference + sheepDifference + wheatDifference + woodDifference;
		player.game.undoPutTempPiece(temp);
		updateState(player);

		eval = resourcesGained + relativeResourceGain;
		// try {
		// 	SOCDBHelper.cityNormalization(eval);
		// }
		// catch (Exception e){
		// 	 System.err.println("Error updating on settlement:" + e);
		// }
		return (eval - cityMin)/(cityMax-cityMin);
	}


	public double roadEvalFunction(Integer road, SOCPlayer player){
		//getting value of new settlements that can be built with additional road
		double bestCurrentSettlementValue = -1;
		double bestSettlementWithAdditionalRoad = -1;
		double currentLongestRoadEval = Math.cbrt(200*relativeLongestRoadLength);
		double newEval;

		HashSet<Integer> settlements = (HashSet<Integer>) player.getPotentialSettlements().clone();

		for(Integer settlement : settlements){
			newEval = settlementEvalFunction(settlement, player);
			if (newEval > bestCurrentSettlementValue){
				bestCurrentSettlementValue = newEval;
			}
		}

		//adding the road
		SOCRoad temp = new SOCRoad(player, road, player.game.getBoard());
		player.game.putTempPiece(temp);
		updateState(player);

		double newLongestRoadEval = Math.cbrt(200*relativeLongestRoadLength);

		//checking new best settlement value
		HashSet<Integer> newSettlements = (HashSet<Integer>) player.getPotentialSettlements().clone();

		for(Integer settlement : newSettlements){
			newEval = settlementEvalFunction(settlement, player);
			if (newEval > bestSettlementWithAdditionalRoad) {
				bestSettlementWithAdditionalRoad = newEval;
			}
		}

		player.game.undoPutTempPiece(temp);
		updateState(player);

		double changeInSettlementEval = bestSettlementWithAdditionalRoad - bestCurrentSettlementValue;
		//opponent impact: how does a road negatively influence other players -- left to implement
		double opponentImpact = 0;

		//longest road difference
		double changeInLongestRoadEval = newLongestRoadEval - currentLongestRoadEval;

		eval = changeInSettlementEval + opponentImpact +  changeInLongestRoadEval;
		// try {
		// 	SOCDBHelper.roadNormalization(eval);
		// }
		// catch (Exception e){
		// 	 System.err.println("Error updating on road:" + e);
		// }
		return (eval - roadMin)/(roadMax-roadMin);
	}

	public double temp(SOCPlayer player){
		Boolean origCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		Boolean origCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		Boolean origCouldCity = player.game.couldBuildCity(player.playerNumber);

		Boolean newCouldSettlement = false;
		Boolean newCouldRoad = false;
		Boolean newCouldCity = false;

		double roadBuildingEval = -1;
		double discEval = -1;
		double monoEval = -1;
		double knightEval = -1;

		//RB
		SOCPossibleRoad road1 = getBestRoad(player);
		double evalRoadOne =  roadEvalFunction(road1.getCoordinates(), player);
		SOCRoad temp1 = new SOCRoad(player, road1.getCoordinates(), player.game.getBoard());
		player.game.putTempPiece(temp1);
		updateState(player);
		SOCPossibleRoad road2 = getBestRoad(player);
		double evalRoadTwo = roadEvalFunction(road2.getCoordinates(), player);
		player.game.undoPutTempPiece(temp1);
		updateState(player);
		roadBuildingEval = evalRoadOne + evalRoadTwo;
		try {
			SOCDBHelper.devNormalization("RB", roadBuildingEval);
		}
		catch (Exception e){
			 System.err.println("Error updating on RB:" + e);
		}

		//DISC
		double bestEvalOne = -1;
		double bestEvalTwo = -1;
		double settlementEval = -1;
		double cityEval = -1;
		double roadEval = -1;

		for(int i=0; i<5; i++) {
			player.getResources().add(2, i);
			updateState(player);

			newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
			newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
			newCouldCity = player.game.couldBuildCity(player.playerNumber);

			if(!origCouldSettlement && newCouldSettlement){
				SOCPossibleSettlement settlement = getBestSettlement(player);
				settlementEval = settlementEvalFunction(settlement.getCoordinates(), player);
			}
			if(!origCouldRoad && newCouldRoad){
				SOCPossibleRoad road = getBestRoad(player);
				roadEval = roadEvalFunction(road.getCoordinates(), player);
			}
			if (!origCouldCity && newCouldCity) {
				SOCPossibleCity city = getBestCity(player);
				cityEval = cityEvalFunction(city.getCoordinates(), player);
			}

			double maxOne = Math.max(settlementEval, Math.max(roadEval, cityEval));
			if (maxOne > bestEvalOne){
				bestEvalOne = maxOne;
			}
			player.getResources().subtract(2, i);
			updateState(player);
		}

		for(int i=0; i<5; i++) {
			for(int j=i+1; j<5; j++) {
				player.getResources().add(1, i);
				player.getResources().add(1, j);
				updateState(player);

				newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
				newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
				newCouldCity = player.game.couldBuildCity(player.playerNumber);

				if(!origCouldSettlement && newCouldSettlement){
					SOCPossibleSettlement settlement = getBestSettlement(player);
					settlementEval = settlementEvalFunction(settlement.getCoordinates(), player);
				}
				if(!origCouldRoad && newCouldRoad){
					SOCPossibleRoad road = getBestRoad(player);
					roadEval = roadEvalFunction(road.getCoordinates(), player);
				}
				if (!origCouldCity && newCouldCity) {
					SOCPossibleCity city = getBestCity(player);
					cityEval = cityEvalFunction(city.getCoordinates(), player);
				}
				double maxTwo = Math.max(settlementEval, Math.max(roadEval, cityEval));
				if(maxTwo > bestEvalTwo){
					bestEvalTwo = maxTwo;
				}

				player.getResources().subtract(1, i);
				player.getResources().subtract(1, j);
				updateState(player);
			}
		}

		discEval = Math.max(bestEvalOne, bestEvalTwo);
		try {
			SOCDBHelper.devNormalization("DISC", discEval);
		}
		catch (Exception e){
			 System.err.println("Error updating on discovery:" + e);
		}

		double bestClay = -1;
		double bestOre = -1;
		double bestSheep = -1;
		double bestWheat = -1;
		double bestWood = -1;

		double settlementEvalClay = -1;
		double roadEvalClay = -1;
		double cityEvalClay = -1;

		double settlementEvalOre = -1;
		double roadEvalOre = -1;
		double cityEvalOre = -1;

		double settlementEvalSheep = -1;
		double roadEvalSheep = -1;
		double cityEvalSheep = -1;

		double settlementEvalWheat = -1;
		double roadEvalWheat = -1;
		double cityEvalWheat = -1;

		double settlementEvalWood = -1;
		double roadEvalWood = -1;
		double cityEvalWood = -1;

		SOCResourceSet temp = new SOCResourceSet(5, 0, 0, 0, 0, 0); //Simplifying with 5 cards received
		player.getResources().add(temp);
		updateState(player);

		newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		newCouldCity = player.game.couldBuildCity(player.playerNumber);

		if(!origCouldSettlement && newCouldSettlement){
			SOCPossibleSettlement settlementClay = getBestSettlement(player);
			settlementEvalClay = settlementEvalFunction(settlementClay.getCoordinates(), player);
		}
		if(!origCouldRoad && newCouldRoad){
			SOCPossibleRoad roadClay = getBestRoad(player);
			roadEvalClay = roadEvalFunction(roadClay.getCoordinates(), player);
		}
		if (!origCouldCity && newCouldCity) {
			SOCPossibleCity cityClay = getBestCity(player);
			cityEvalClay = cityEvalFunction(cityClay.getCoordinates(), player);
		}
		player.getResources().subtract(temp);
		updateState(player);
		bestClay = Math.max(settlementEvalClay, Math.max(roadEvalClay, cityEvalClay));

		temp = new SOCResourceSet(0, 5, 0, 0, 0, 0);
		player.getResources().add(temp);
		updateState(player);
		newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		newCouldCity = player.game.couldBuildCity(player.playerNumber);

		if(!origCouldSettlement && newCouldSettlement){
			SOCPossibleSettlement settlementOre = getBestSettlement(player);
			settlementEvalOre = settlementEvalFunction(settlementOre.getCoordinates(), player);
		}
		if(!origCouldRoad && newCouldRoad){
			SOCPossibleRoad roadOre = getBestRoad(player);
			roadEvalOre = roadEvalFunction(roadOre.getCoordinates(), player);
		}
		if (!origCouldCity && newCouldCity) {
			SOCPossibleCity cityOre = getBestCity(player);
			cityEvalOre = cityEvalFunction(cityOre.getCoordinates(), player);
		}
		player.getResources().subtract(temp);
		updateState(player);
		bestOre = Math.max(settlementEvalOre, Math.max(roadEvalOre, cityEvalOre));

		temp = new SOCResourceSet(0, 0, 5, 0, 0, 0);
		player.getResources().add(temp);
		updateState(player);
		newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		newCouldCity = player.game.couldBuildCity(player.playerNumber);

		if(!origCouldSettlement && newCouldSettlement){
			SOCPossibleSettlement settlementSheep = getBestSettlement(player);
			settlementEvalSheep = settlementEvalFunction(settlementSheep.getCoordinates(), player);
		}
		if(!origCouldRoad && newCouldRoad){
			SOCPossibleRoad roadSheep = getBestRoad(player);
			roadEvalSheep = roadEvalFunction(roadSheep.getCoordinates(), player);
		}
		if (!origCouldCity && newCouldCity) {
			SOCPossibleCity citySheep = getBestCity(player);
			cityEvalSheep = cityEvalFunction(citySheep.getCoordinates(), player);
		}
		player.getResources().subtract(temp);
		updateState(player);
		bestSheep = Math.max(settlementEvalSheep, Math.max(roadEvalSheep, cityEvalSheep));

		temp = new SOCResourceSet(0, 0, 0, 5, 0, 0);
		player.getResources().add(temp);
		updateState(player);
		newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		newCouldCity = player.game.couldBuildCity(player.playerNumber);

		if(!origCouldSettlement && newCouldSettlement){
			SOCPossibleSettlement settlementWheat = getBestSettlement(player);
			settlementEvalWheat = settlementEvalFunction(settlementWheat.getCoordinates(), player);
		}
		if(!origCouldRoad && newCouldRoad){
			SOCPossibleRoad roadWheat = getBestRoad(player);
			roadEvalWheat = roadEvalFunction(roadWheat.getCoordinates(), player);
		}
		if (!origCouldCity && newCouldCity) {
			SOCPossibleCity cityWheat = getBestCity(player);
			cityEvalWheat = cityEvalFunction(cityWheat.getCoordinates(), player);
		}
		player.getResources().subtract(temp);
		updateState(player);
		bestWheat = Math.max(settlementEvalWheat, Math.max(roadEvalWheat, cityEvalWheat));

		temp = new SOCResourceSet(0, 0, 0, 0, 5, 0);
		player.getResources().add(temp);
		updateState(player);
		newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		newCouldCity = player.game.couldBuildCity(player.playerNumber);

		if(!origCouldSettlement && newCouldSettlement){
			SOCPossibleSettlement settlementWood = getBestSettlement(player);
			settlementEvalWood = settlementEvalFunction(settlementWood.getCoordinates(), player);
		}
		if(!origCouldRoad && newCouldRoad){
			SOCPossibleRoad roadWood = getBestRoad(player);
			roadEvalWood = roadEvalFunction(roadWood.getCoordinates(), player);
		}
		if (!origCouldCity && newCouldCity) {
			SOCPossibleCity cityWood = getBestCity(player);
			cityEvalWood = cityEvalFunction(cityWood.getCoordinates(), player);
		}
		player.getResources().subtract(temp);
		updateState(player);
		bestWood = Math.max(settlementEvalWood, Math.max(roadEvalWood, cityEvalWood));

		monoEval = Math.max(bestClay, Math.max(bestOre, Math.max(bestSheep, Math.max(bestWheat, bestWood))));
		try {
			SOCDBHelper.devNormalization("MONO", monoEval);
		}
		catch (Exception e){
			 System.err.println("Error updating on MONO:" + e);
		}

		int origKnights = player.getNumKnights();
		player.incrementNumKnights();
		updateState(player);
		if (relativeKnightsPlayed <= -1){
			knightEval = .25;
		}
		if (relativeKnightsPlayed == 0){
			knightEval = .35;
		}
		if (relativeKnightsPlayed == 1){
			knightEval = .50;
		}
		if (relativeKnightsPlayed > 1){
			knightEval = .35;
		}
		player.setNumKnights(origKnights);
		updateState(player);
		try {
			SOCDBHelper.devNormalization("KNIGHT", knightEval);
		}
		catch (Exception e){
			 System.err.println("Error updating on KNIGHT:" + e);
		}

		return 0;

	}

	public double devCardEvalFunction (String card, SOCPlayer player){
		double roadBuildingEval = -1;
		double discEval = -1;
		double monoEval = -1;
		double knightEval = -1;

		Boolean origCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
		Boolean origCouldRoad = player.game.couldBuildRoad(player.playerNumber);
		Boolean origCouldCity = player.game.couldBuildCity(player.playerNumber);

		Boolean newCouldSettlement = false;
		Boolean newCouldRoad = false;
		Boolean newCouldCity = false;

		if (card.equals("RB")) {
			SOCPossibleRoad road1 = getBestRoad(player);
			double evalRoadOne =  roadEvalFunction(road1.getCoordinates(), player);
			SOCRoad temp1 = new SOCRoad(player, road1.getCoordinates(), player.game.getBoard());
			player.game.putTempPiece(temp1);
			updateState(player);
			SOCPossibleRoad road2 = getBestRoad(player);
			double evalRoadTwo = roadEvalFunction(road2.getCoordinates(), player);
			player.game.undoPutTempPiece(temp1);
			updateState(player);
			roadBuildingEval = evalRoadOne + evalRoadTwo;
			return roadBuildingEval;
		}

		else if (card.equals("DISC")) {
			double bestEvalOne = -1;
			double bestEvalTwo = -1;
			double settlementEval = -1;
			double cityEval = -1;
			double roadEval = -1;

			for(int i=0; i<5; i++) {
				player.getResources().add(2, i);
				updateState(player);

				newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
				newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
				newCouldCity = player.game.couldBuildCity(player.playerNumber);

				if(!origCouldSettlement && newCouldSettlement){
					SOCPossibleSettlement settlement = getBestSettlement(player);
					settlementEval = settlementEvalFunction(settlement.getCoordinates(), player);
				}
				if(!origCouldRoad && newCouldRoad){
					SOCPossibleRoad road = getBestRoad(player);
					roadEval = roadEvalFunction(road.getCoordinates(), player);
				}
				if (!origCouldCity && newCouldCity) {
					SOCPossibleCity city = getBestCity(player);
					cityEval = cityEvalFunction(city.getCoordinates(), player);
				}

				double maxOne = Math.max(settlementEval, Math.max(roadEval, cityEval));
				if (maxOne > bestEvalOne){
					bestEvalOne = maxOne;
				}
				player.getResources().subtract(2, i);
				updateState(player);
			}

			for(int i=0; i<5; i++) {
				for(int j=i+1; j<5; j++) {
					player.getResources().add(1, i);
					player.getResources().add(1, j);
					updateState(player);

					newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
					newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
					newCouldCity = player.game.couldBuildCity(player.playerNumber);

					if(!origCouldSettlement && newCouldSettlement){
						SOCPossibleSettlement settlement = getBestSettlement(player);
						settlementEval = settlementEvalFunction(settlement.getCoordinates(), player);
					}
					if(!origCouldRoad && newCouldRoad){
						SOCPossibleRoad road = getBestRoad(player);
						roadEval = roadEvalFunction(road.getCoordinates(), player);
					}
					if (!origCouldCity && newCouldCity) {
						SOCPossibleCity city = getBestCity(player);
						cityEval = cityEvalFunction(city.getCoordinates(), player);
					}
					double maxTwo = Math.max(settlementEval, Math.max(roadEval, cityEval));
					if(maxTwo > bestEvalTwo){
						bestEvalTwo = maxTwo;
					}

					player.getResources().subtract(1, i);
					player.getResources().subtract(1, j);
					updateState(player);
				}
			}

			discEval = Math.max(bestEvalOne, bestEvalTwo);
			return discEval;
		}

		else if (card.equals("MONO")) {
			double bestClay = -1;
			double bestOre = -1;
			double bestSheep = -1;
			double bestWheat = -1;
			double bestWood = -1;

			double settlementEvalClay = -1;
			double roadEvalClay = -1;
			double cityEvalClay = -1;

			double settlementEvalOre = -1;
			double roadEvalOre = -1;
			double cityEvalOre = -1;

			double settlementEvalSheep = -1;
			double roadEvalSheep = -1;
			double cityEvalSheep = -1;

			double settlementEvalWheat = -1;
			double roadEvalWheat = -1;
			double cityEvalWheat = -1;

			double settlementEvalWood = -1;
			double roadEvalWood = -1;
			double cityEvalWood = -1;

			SOCResourceSet temp = new SOCResourceSet(5, 0, 0, 0, 0, 0); //Simplifying with 5 cards received
			player.getResources().add(temp);
			updateState(player);

			newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
			newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
			newCouldCity = player.game.couldBuildCity(player.playerNumber);

			if(!origCouldSettlement && newCouldSettlement){
				SOCPossibleSettlement settlementClay = getBestSettlement(player);
				settlementEvalClay = settlementEvalFunction(settlementClay.getCoordinates(), player);
			}
			if(!origCouldRoad && newCouldRoad){
				SOCPossibleRoad roadClay = getBestRoad(player);
				roadEvalClay = roadEvalFunction(roadClay.getCoordinates(), player);
			}
			if (!origCouldCity && newCouldCity) {
				SOCPossibleCity cityClay = getBestCity(player);
				cityEvalClay = cityEvalFunction(cityClay.getCoordinates(), player);
			}
			player.getResources().subtract(temp);
			updateState(player);
			bestClay = Math.max(settlementEvalClay, Math.max(roadEvalClay, cityEvalClay));

			temp = new SOCResourceSet(0, 5, 0, 0, 0, 0);
			player.getResources().add(temp);
			updateState(player);
			newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
			newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
			newCouldCity = player.game.couldBuildCity(player.playerNumber);

			if(!origCouldSettlement && newCouldSettlement){
				SOCPossibleSettlement settlementOre = getBestSettlement(player);
				settlementEvalOre = settlementEvalFunction(settlementOre.getCoordinates(), player);
			}
			if(!origCouldRoad && newCouldRoad){
				SOCPossibleRoad roadOre = getBestRoad(player);
				roadEvalOre = roadEvalFunction(roadOre.getCoordinates(), player);
			}
			if (!origCouldCity && newCouldCity) {
				SOCPossibleCity cityOre = getBestCity(player);
				cityEvalOre = cityEvalFunction(cityOre.getCoordinates(), player);
			}
			player.getResources().subtract(temp);
			updateState(player);
			bestOre = Math.max(settlementEvalOre, Math.max(roadEvalOre, cityEvalOre));

			temp = new SOCResourceSet(0, 0, 5, 0, 0, 0);
			player.getResources().add(temp);
			updateState(player);
			newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
			newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
			newCouldCity = player.game.couldBuildCity(player.playerNumber);

			if(!origCouldSettlement && newCouldSettlement){
				SOCPossibleSettlement settlementSheep = getBestSettlement(player);
				settlementEvalSheep = settlementEvalFunction(settlementSheep.getCoordinates(), player);
			}
			if(!origCouldRoad && newCouldRoad){
				SOCPossibleRoad roadSheep = getBestRoad(player);
				roadEvalSheep = roadEvalFunction(roadSheep.getCoordinates(), player);
			}
			if (!origCouldCity && newCouldCity) {
				SOCPossibleCity citySheep = getBestCity(player);
				cityEvalSheep = cityEvalFunction(citySheep.getCoordinates(), player);
			}
			player.getResources().subtract(temp);
			updateState(player);
			bestSheep = Math.max(settlementEvalSheep, Math.max(roadEvalSheep, cityEvalSheep));

			temp = new SOCResourceSet(0, 0, 0, 5, 0, 0);
			player.getResources().add(temp);
			updateState(player);
			newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
			newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
			newCouldCity = player.game.couldBuildCity(player.playerNumber);

			if(!origCouldSettlement && newCouldSettlement){
				SOCPossibleSettlement settlementWheat = getBestSettlement(player);
				settlementEvalWheat = settlementEvalFunction(settlementWheat.getCoordinates(), player);
			}
			if(!origCouldRoad && newCouldRoad){
				SOCPossibleRoad roadWheat = getBestRoad(player);
				roadEvalWheat = roadEvalFunction(roadWheat.getCoordinates(), player);
			}
			if (!origCouldCity && newCouldCity) {
				SOCPossibleCity cityWheat = getBestCity(player);
				cityEvalWheat = cityEvalFunction(cityWheat.getCoordinates(), player);
			}
			player.getResources().subtract(temp);
			updateState(player);
			bestWheat = Math.max(settlementEvalWheat, Math.max(roadEvalWheat, cityEvalWheat));

			temp = new SOCResourceSet(0, 0, 0, 0, 5, 0);
			player.getResources().add(temp);
			updateState(player);
			newCouldSettlement = player.game.couldBuildSettlement(player.playerNumber);
			newCouldRoad = player.game.couldBuildRoad(player.playerNumber);
			newCouldCity = player.game.couldBuildCity(player.playerNumber);

			if(!origCouldSettlement && newCouldSettlement){
				SOCPossibleSettlement settlementWood = getBestSettlement(player);
				settlementEvalWood = settlementEvalFunction(settlementWood.getCoordinates(), player);
			}
			if(!origCouldRoad && newCouldRoad){
				SOCPossibleRoad roadWood = getBestRoad(player);
				roadEvalWood = roadEvalFunction(roadWood.getCoordinates(), player);
			}
			if (!origCouldCity && newCouldCity) {
				SOCPossibleCity cityWood = getBestCity(player);
				cityEvalWood = cityEvalFunction(cityWood.getCoordinates(), player);
			}
			player.getResources().subtract(temp);
			updateState(player);
			bestWood = Math.max(settlementEvalWood, Math.max(roadEvalWood, cityEvalWood));

			monoEval = Math.max(bestClay, Math.max(bestOre, Math.max(bestSheep, Math.max(bestWheat, bestWood))));
			return monoEval;
		}
		else if (card.equals("KNIGHT")){
			int origKnights = player.getNumKnights();
			player.incrementNumKnights();
			updateState(player);
			if (relativeKnightsPlayed <= -1){
				knightEval = .25;
			}
			if (relativeKnightsPlayed == 0){
				knightEval = .35;
			}
			if (relativeKnightsPlayed == 1){
				knightEval = .50;
			}
			if (relativeKnightsPlayed > 1){
				knightEval = .35;
			}
			player.setNumKnights(origKnights);
			updateState(player);
			return knightEval;
		}
		else {
			return -1;
		}
	}

	public SOCPossibleSettlement getBestSettlement(SOCPlayer player){
		double currentEval = Double.NEGATIVE_INFINITY;
		double newEval;
		HashSet<Integer> settlements = (HashSet<Integer>) player.getPotentialSettlements().clone();
		for(Integer settlement : settlements) {
			newEval = settlementEvalFunction(settlement, player);
			DecimalFormat df = new DecimalFormat("#.#####");
			newEval = Double.valueOf(df.format(newEval));
			if(newEval > currentEval) {
					currentEval = newEval;
					bestSettlement = new SOCPossibleSettlement(player, settlement, null);
			}
		}
		return bestSettlement;
	}

	public SOCPossibleCity getBestCity(SOCPlayer player){
		double currentEval = Double.NEGATIVE_INFINITY;
		double newEval;
		int city;
		Vector<SOCSettlement> cities = (Vector<SOCSettlement>) player.getSettlements().clone();
		for(SOCSettlement set : cities) {
			city = set.getCoordinates();
			if (player.isPotentialCity(city)){
				System.out.println("SPOT " + city + " IS VALID");
				newEval = cityEvalFunction(city, player);
				DecimalFormat df = new DecimalFormat("#.#####");
				newEval = Double.valueOf(df.format(newEval));
				if (newEval > currentEval){
					currentEval =  newEval;
					bestCity = new SOCPossibleCity(player, city);
				}
			}
		}
		return bestCity;
	}

	public SOCPossibleRoad getBestRoad(SOCPlayer player){
		double currentEval = Double.NEGATIVE_INFINITY;
		double newEval;
		HashSet<Integer> roads = (HashSet<Integer>) player.getPotentialRoads().clone();
		for(Integer road : roads) {
			newEval  = roadEvalFunction(road, player);
			DecimalFormat df = new DecimalFormat("#.#####");
			newEval = Double.valueOf(df.format(newEval));
			if(newEval > currentEval){
				currentEval = newEval;
				bestRoad = new SOCPossibleRoad(player, road, null);
			}
		}
		return bestRoad;
	}

	public String getBestDevCard(SOCPlayer player){
		double roadBuildingEval = -1;
		double discEval = -1;
		double monoEval = -1;
		double knightEval = -1;
		String card = "nothing";

		double storingValue = temp(player);

		//road builder
		if (player.getInventory().hasPlayable(SOCDevCardConstants.ROADS) && player.game.canPlayRoadBuilding(player.playerNumber) &&
									!player.hasPlayedDevCard() && player.getNumPieces(SOCPlayingPiece.ROAD) >= 2) {
			roadBuildingEval = devCardEvalFunction("RB", player);
		}
		//YOP
		else if (player.getInventory().hasPlayable(SOCDevCardConstants.DISC) && player.game.canPlayDiscovery(player.playerNumber) && !player.hasPlayedDevCard()) {
			discEval = devCardEvalFunction("DISC", player);
		}
		//monopoly
		else if (player.getInventory().hasPlayable(SOCDevCardConstants.MONO) && player.game.canPlayMonopoly(player.playerNumber) && !player.hasPlayedDevCard()) {
			monoEval = devCardEvalFunction("MONO", player);
		}
		//knight
		else if (player.getInventory().hasPlayable(SOCDevCardConstants.KNIGHT) && player.game.canPlayKnight(player.playerNumber) && !player.hasPlayedDevCard()) {
			knightEval = devCardEvalFunction("KNIGHT", player);
		}

		double bestCard = Math.max(roadBuildingEval, Math.max(discEval, Math.max(monoEval, knightEval)));
		if (bestCard != -1){
			if (bestCard == roadBuildingEval) {
				card = "RB";
			}
			else if (bestCard == discEval) {
				card = "DISC";
			}
			else if (bestCard == monoEval) {
			card = "MONO";
			}
			else {
				card = "KNIGHT";
			}
		}

		return card;
	}


	public double[] getAction(SOCPlayer player){
		double[] predictionArray = new double[6];

		double settlementEval = 0;
		double cityEval = 0;
		double roadEval = 0;
		double devCardEval = 0;
		double endTurn = -100;

                Boolean pOne = mfOne == player.getName();

		if (player.game.couldBuildSettlement(player.playerNumber)){
				SOCPossibleSettlement settlement = getBestSettlement(player);
				settlementEval = (pOne ? mfOne_weightOne : mfTwo_weightOne) *
                                        (settlementEvalFunction(settlement.getCoordinates(), player));
		}

		if (player.game.couldBuildCity(player.playerNumber)){
				SOCPossibleCity city = getBestCity(player);
				cityEval = (pOne ? mfOne_weightTwo : mfTwo_weightTwo) * 
                                        (cityEvalFunction(city.getCoordinates(), player));
		}

		if (player.game.couldBuildRoad(player.playerNumber)){
			SOCPossibleRoad road = getBestRoad(player);
			roadEval = (pOne ? mfOne_weightThree : mfTwo_weightThree) * 
                            (roadEvalFunction(road.getCoordinates(), player));
		}

		String devCard = getBestDevCard(player);
		if(!devCard.equals("nothing")){
			devCardEval = (pOne ? mfOne_weightFour : mfTwo_weightFour) * 
                            (devCardEvalFunction(devCard, player));
		}

		//.179340 is the calculated value of buy dev card
		double buyDevCard = (pOne ? mfOne_weightFive : mfTwo_weightFive) * .04;

		predictionArray[0] = settlementEval;
		predictionArray[1] = cityEval;
		predictionArray[2] = roadEval;
		predictionArray[3] = devCardEval;
		predictionArray[4] = buyDevCard;
		predictionArray[5] = endTurn;

		return predictionArray;

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
		stateVector.add(resources.getAmount(0));
		//amount of ore in hand
		stateVector.add(resources.getAmount(1));
		//amount of sheep in hand
		stateVector.add(resources.getAmount(2));
		//amount of wheat in hand
		stateVector.add(resources.getAmount(3));
		//amount of wood in hand
		stateVector.add(resources.getAmount(4));
		//has knight to play this turn
		stateVector.add(hasKnightToPlay);
		//has year of plenty to play this turn
		stateVector.add(hasDISCToPlay);
		//has monopoly to play this turn
		stateVector.add(hasMONOToPlay);
		//has road builder to play this turn
		stateVector.add(hasRBToPlay);
		//number of VP player has
		stateVector.add(victoryPoints);
		//relative longest road
		stateVector.add(relativeLongestRoadLength);
		//relative largest army
		stateVector.add(relativeKnightsPlayed);
		//rating of best settlement to build
		stateVector.add(nextBestSettlementValue);
		//rating of best settlement to build after one road
		stateVector.add(nextBestSettlementAndRoadValue);
		//rating of best city to build
		stateVector.add(nextBestCityValue);
		//relative clay
		stateVector.add(relativeClay);
		//relative ore
		stateVector.add(relativeOre);
		//relative sheep
		stateVector.add(relativeSheep);
		//relative wheat
		stateVector.add(relativeWheat);
		//relative wood
		stateVector.add(relativeWood);
		//has clay port
		stateVector.add(clayPort);
		//has ore port
		stateVector.add(orePort);
		//has sheep port
		stateVector.add(sheepPort);
		//has wheat port
		stateVector.add(wheatPort);
		//has wood port
		stateVector.add(woodPort);
		//has misc port
		stateVector.add(miscPort);

		return stateVector;

	}

	public static String getWeights(Boolean first){
		StringBuilder rel = new StringBuilder();
                if (first == false) {
                    rel.append("\'" + mfOne_weightOne + "\',");
                    rel.append("\'" + mfOne_weightTwo + "\',");
                    rel.append("\'" + mfOne_weightThree + "\',");
                    rel.append("\'" + mfOne_weightFour + "\',");
                    rel.append("\'" + mfOne_weightFive + "\',");
                } else {
                    rel.append("\'" + mfTwo_weightOne + "\',");
                    rel.append("\'" + mfTwo_weightTwo + "\',");
                    rel.append("\'" + mfTwo_weightThree + "\',");
                    rel.append("\'" + mfTwo_weightFour + "\',");
                    rel.append("\'" + mfTwo_weightFive + "\',");
                }
		return rel.toString();
	}

	public String stateToString(Vector stateVector){
		stateVector.toString();
		StringBuilder rel = new StringBuilder();
		for (Object element : stateVector) {
			rel.append("\'" + element + "\',");
		}
		return rel.toString();
	}

	public void saveState(){
				try {
				//	SOCDBHelper.saveWeights(getWeights(false), player);
				}
				catch (Exception e){
					 System.err.println("Error updating on settlement:" + e);
				}
	}

	public String toString() {
		return "{" + victoryPoints + ", " + relativeLongestRoadLength + ", " + relativeKnightsPlayed + ", " + opponentRoadsAway + ", [" +
		relativeClay + ", " + relativeOre + ", " + relativeSheep + ", " + relativeWheat + ", " + relativeWood + "], [" +
		portClay + ", " + portOre + ", " + portSheep + ", " + portWheat + ", " + portWood + ", " + portMisc + "]}";
	}
}
