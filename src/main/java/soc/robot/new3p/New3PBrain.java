/*
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 */
package soc.robot.new3p;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.io.*;
import java.sql.Timestamp;

import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.message.SOCMessage;
import soc.robot.SOCPossibleCard;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.state.SOCPlayerState;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;
import soc.server.database.SOCDBHelper;

public class New3PBrain extends SOCRobotBrain
{
	Double currentChoiceEval;

    public New3PBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue<SOCMessage> mq)
    {
        super(rc, params, ga, mq);
        this.currentChoiceEval = Double.NEGATIVE_INFINITY;
    }

    public New3PBrain(New3PBrain brain) {
    	this(brain.client, brain.robotParameters, brain.game, brain.gameEventQ);
        this.state = new SOCPlayerState(brain.state);
        setOurPlayerData();
    }

    protected final void planBuilding(int lookahead)
    {
    	playerOptions(ourPlayerData, lookahead);

    	if (! buildingPlan.empty() && lookahead > -1)
        {
            lastTarget = buildingPlan.peek();
            if(!(lastTarget instanceof SOCPossibleCard))
            	negotiator.setTargetPiece(ourPlayerNumber, lastTarget);
        }
    }

    @Override
    protected void planBuilding() {
    	planBuilding(0); // Will switch to 1 once I check out the results, but for now it's good to get data
    }

    @Override
    protected void buildOrGetResourceByTradeOrCard()
            throws IllegalStateException
        {
    	  if (buildingPlan.isEmpty())
              throw new IllegalStateException("buildingPlan empty when called");

    	  final boolean gameStatePLAY1 = (game.getGameState() == SOCGame.PLAY1);

          SOCPossiblePiece targetPiece = buildingPlan.peek();
          SOCResourceSet targetResources = targetPiece.getResourcesToBuild();  // may be null

          if(!(targetPiece instanceof SOCPossibleCard)) {
        	  negotiator.setTargetPiece(ourPlayerNumber, targetPiece);
        	  if(!expectWAITING_FOR_DISCOVERY && !expectWAITING_FOR_MONOPOLY) {
	        	  if (gameStatePLAY1 && (! doneTrading) && (! ourPlayerData.getResources().contains(targetResources)))
	              {
	                  waitingForTradeResponse = false;

	                  if (robotParameters.getTradeFlag() == 1)
	                  {
	                      makeOffer(targetPiece);
	                      // makeOffer will set waitingForTradeResponse or doneTrading.
	                  }
	              }

	              if (gameStatePLAY1 && ! waitingForTradeResponse)
	              {
	                  /**
	                   * trade with the bank/ports
	                   */
	                  if (tradeToTarget2(targetResources))
	                  {
	                      counter = 0;
	                      waitingForTradeMsg = true;
	                      pause(1500);
	                  }
	              }

	              if ((! (waitingForTradeMsg || waitingForTradeResponse))
	                      && ourPlayerData.getResources().contains(targetResources))
	                  {
	            	  	System.out.println("BUILDING: " + targetPiece);
	                  	buildRequestPlannedPiece();
	                  }
        	  }
          } else {
        	  switch(((SOCPossibleCard) targetPiece).type) {
        	  	case SOCDevCardConstants.ROADS:
        	  		System.out.println("PLAY ROAD BUILDER");
        	  		 waitingForGameState = true;
                     counter = 0;
                     expectPLACING_FREE_ROAD1 = true;
        	  		buildingPlan.pop();
        	  		whatWeWantToBuild = new SOCRoad(ourPlayerData, buildingPlan.pop().getCoordinates(), null);
        	  		client.playDevCard(game, SOCDevCardConstants.ROADS);
        	  		break;
        	  	case SOCDevCardConstants.DISC:
        	  	   System.out.println("PLAY DISCOVERY");
        	  	  expectWAITING_FOR_DISCOVERY = true;
                  waitingForGameState = true;
                  counter = 0;
      	  		if(!buildingPlan.empty())
      	  			buildingPlan.pop();
                  client.playDevCard(game, SOCDevCardConstants.DISC);
                  pause(1500);
                  break;
        	  	case SOCDevCardConstants.MONO:
        	  		System.out.println("PLAY MONOPOLY");
        	  		expectWAITING_FOR_MONOPOLY = true;
                    waitingForGameState = true;
                    counter = 0;
        	  		if(!buildingPlan.empty())
        	  			buildingPlan.pop();
                    client.playDevCard(game, SOCDevCardConstants.MONO);
                    pause(1500);
                    break;
        	  	case SOCDevCardConstants.KNIGHT:
        	  		System.out.println("PLAY KNIGHT");
        	  		if(!buildingPlan.empty())
        	  			buildingPlan.pop();
        	  		playKnightCard();
        	  		break;
        	  	case SOCDevCardConstants.UNKNOWN:
        	  		if(!buildingPlan.empty())
        	  			buildingPlan.pop();
                    client.buyDevCard(game);
                    waitingForDevCard = true;
        	  }
          }
        }

    private void playerOptions(SOCPlayer player, int turnLookAhead) {
    	Set<Integer> devCardNums = new HashSet<Integer>(Arrays.asList(0,
    			SOCDevCardConstants.ROADS, SOCDevCardConstants.DISC, SOCDevCardConstants.MONO,
    			SOCDevCardConstants.KNIGHT));

      state.updateState(this.ourPlayerData);
    	currentChoiceEval = turnLookAhead == 1 ? minPossibleEval(player, 1) : state.stateEvalFunction();
			playerSimulation(player, turnLookAhead);
		}


    protected void playerSimulation(SOCPlayer player, int turnLookahead) {
    		SOCGame game = player.game;
				boolean canDo = false;
				int prediction = -1;
				char[] predictionChar = new char[1000000];
				double currentBiggest = Double.POSITIVE_INFINITY;
				double[] predictionArray = new double[365];
				double[] predictionArrayTwo = new double[6];
				String predictionString = "";
				String typeOne = "bestSettlement";
				String typeTwo = "bestCity";
				String typeThree = "bestRoad";
				String valid = "NO";

				state.updateState(this.ourPlayerData);

			 /*//new updates start here
			 	int action = -1;
				int position = -1;

				action, position = state.getActionPair();
				*/
				Vector statevectorOne = state.getInputVectorOne(player);
				Vector statevectorTwo = state.getInputVectorTwo(player);

				try{
					ProcessBuilder pb = new ProcessBuilder("python3","predictionNew.py", ""+statevectorOne, ""+statevectorTwo);
					Process p = pb.start();

					BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
					int check = in.read(predictionChar, 0, predictionChar.length);
					if (check > -1){
						predictionString = new String(predictionChar);
					}
				}
				catch(Exception e){
					System.out.println("DIDNT GET THERE");
				}

				predictionString = predictionString.substring(0, predictionString.indexOf("]"));
				String intermediateOne = predictionString.replace("[[", "").trim();
				String[] intermediateThree = intermediateOne.split("\\s+");
				for (int i =0; i < intermediateThree.length - 1; i++){
				  predictionArray[i] = Double.parseDouble(intermediateThree[i]);
				}


				Vector<Integer> nodeVector = new Vector<Integer>(1);
				HashSet<Integer> legalNodes = player.game.getBoard().initPlayerLegalSettlements();
				for(Integer node : legalNodes){
					System.out.println("NODE " + node);
					nodeVector.add(node);
				}

				Vector<Integer> nodeVectorTwo = new Vector<Integer>(1);
				HashSet<Integer> legalEdges = player.game.getBoard().initPlayerLegalRoads();

				for(Integer edge : legalEdges){
					nodeVectorTwo.add(edge);
				}


				//Settlement NN check
				double currentMax = Double.NEGATIVE_INFINITY;
				double eval;
				int position = -1;
				for(int i =1; i < (2 * legalNodes.size()); i += 2){
					eval = predictionArray[i];
					if (eval > currentMax){
						currentMax = eval;
						position = i - 1;
					}
				}
				int bestSettlement = nodeVector.get(position/2);
				HashSet<Integer> settlements = (HashSet<Integer>) player.getPotentialSettlements().clone();
				if(settlements.contains(bestSettlement)){
					valid = "YES";
					try {
						SOCDBHelper.validPrediction(typeOne, valid);
					}
					catch (Exception e){
						 System.err.println("Error updating on saveInputVectorOne:" + e);
					}
				}
				else{
					valid = "NO";
					try {
						SOCDBHelper.validPrediction(typeOne, valid);
					}
					catch (Exception e){
						 System.err.println("Error updating on saveInputVectorOne:" + e);
					}
				}

				//City NN check
				currentMax = Double.NEGATIVE_INFINITY;
				position = -1;
				for(int i = 108 + 1; i < 108 + (2 * legalNodes.size()); i += 2){
					eval = predictionArray[i];
					if (eval > currentMax){
						currentMax = eval;
						position = i - 109;
					}
				}
				int bestCity =  nodeVector.get(position/2);
				Vector<SOCSettlement> potentialCities = player.getSettlements();
				Vector cities = new Vector(1);
				for (SOCSettlement city : potentialCities){
					cities.add(city.getCoordinates());
				}
				if(cities.contains(bestCity)){
					valid = "YES";
					try {
						SOCDBHelper.validPrediction(typeTwo, valid);
					}
					catch (Exception e){
						 System.err.println("Error updating on saveInputVectorOne:" + e);
					}
				}
				else{
					valid = "NO";
					try {
						SOCDBHelper.validPrediction(typeTwo, valid);
					}
					catch (Exception e){
						 System.err.println("Error updating on saveInputVectorOne:" + e);
					}
				}

				//Road NN check

				currentMax = Double.NEGATIVE_INFINITY;
				position = -1;
				for(int i = 217; i < (216 + legalEdges.size()); i += 2){
					eval = predictionArray[i];
					if (eval > currentMax){
						currentMax = eval;
						position = i - 217;
					}
				}
				System.out.println("THIS IS THE POSITION " + position);
				int bestRoad = nodeVectorTwo.get(position/2);
				HashSet<Integer> roads = (HashSet<Integer>) player.getPotentialRoads().clone();
				if(roads.contains(bestRoad)){
					valid = "YES";
					try {
						SOCDBHelper.validPrediction(typeThree, valid);
					}
					catch (Exception e){
						 System.err.println("Error updating on saveInputVectorOne:" + e);
					}
				}
				else{
					valid = "NO";
					try {
						SOCDBHelper.validPrediction(typeThree, valid);
					}
					catch (Exception e){
						 System.err.println("Error updating on saveInputVectorOne:" + e);
					}
				}




				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				long ms = timestamp.getTime();
				predictionArrayTwo = state.getAction(player);

				// try {
				// 	SOCDBHelper.saveInputVector(state.stateToString(state.getInputVectorOne(player)), state.stateToString(state.getInputVectorTwo(player)), ms);
				// }
				// catch (Exception e){
				// 	 System.err.println("Error updating on saveInputVectorOne:" + e);
				// }
				//
				// try {
				// 	SOCDBHelper.saveOutputVectorOne(state.stateToString(state.getOutputVectorOne(player)), ms);
				// }
				// catch (Exception e){
				// 	 System.err.println("Error updating on saveOutputVectorOne:" + e);
				// }
				//
				// try {
				// 	SOCDBHelper.saveOutputVectorTwo(state.stateToString(state.getOutputVectorTwo(player, predictionArray)), ms);
				// }
				// catch (Exception e){
				// 	 System.err.println("Error updating on saveOutputVectorTwo:" + e);
				// }
				// try {
				//	SOCDBHelper.saveOutputVectorThree(state.stateToString(state.getOutputVectorThree(player, predictionArray)), ms);
				// }
				// catch (Exception e){
				// 	 System.err.println("Error updating on saveOutputVectorThree:" + e);
				//
				// }



				//predictionArray now holds the prediction probabilities. Go through them to see what to build
				while(canDo == false){
					Object[] array = new Object[2];
					array = nextBiggest(predictionArrayTwo, currentBiggest);
					prediction = (Integer)array[0];
					if (prediction == -1){
						System.out.println("nextBiggest function failed");
					}
					currentBiggest = (Double)array[1];
					canDo = canBuild(prediction, player, turnLookahead);
				}

				// System.out.println("DOING ACTION " + prediction);
				// System.out.println("0 = building settlement, 1 = building city, 2 = building road, 3 = playing dev card, 4 = buying dev card, 5 = endturn");

		}

		public Object[] nextBiggest(double[] predictionArray, double currentBiggest){
			double newmax = Double.NEGATIVE_INFINITY;
			Object[] array = new Object[2];
			int prediction = -1;
			int i = 0;
			for (double element : predictionArray){
				// System.out.println("THIS IS THE ELEMNT " + element);
				// System.out.println("CURRENT BIGGESt " + currentBiggest);
				if ((element < currentBiggest) && (element > newmax)){
					newmax = element;
					prediction = i;
				}
				i = i + 1;
			}
			array[0] = prediction;
			array[1] = newmax;
 			return array;
		}


		public Boolean canBuild(int prediction, SOCPlayer player, int turnLookAhead){
					boolean canDo = false;

					//build a settlement
					if (prediction == 0 && game.couldBuildSettlement(player.playerNumber)){
						canDo = true;
						buildingPlan.clear();
						buildingPlan.push(state.getBestSettlement(player));
				  }

					//build a city
					else if (prediction == 1 && game.couldBuildCity(player.playerNumber)) {
						canDo = true;
						buildingPlan.clear();
						buildingPlan.push(state.getBestCity(player));
			    }

					//build a road
					else if (prediction == 2 && game.couldBuildRoad(player.playerNumber)){
						canDo = true;
						buildingPlan.clear();
						buildingPlan.push(state.getBestRoad(player));
					}

					//play a dev card
					else if (prediction == 3){
						String card = state.getBestDevCard(player);
						if (card.equals("RB")) {
							canDo = true;
							buildingPlan.clear();
							SOCPossibleRoad road1 = state.getBestRoad(player);
							SOCRoad temp1 = new SOCRoad(player, road1.getCoordinates(), player.game.getBoard());
							player.game.putTempPiece(temp1);
							state.updateState(player);
							SOCPossibleRoad road2 = state.getBestRoad(player);
							player.game.undoPutTempPiece(temp1);
							state.updateState(player);
							buildingPlan.push(road2);
							buildingPlan.push(road1);
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.ROADS));
						}
						else if (card.equals("DISC")) {
							canDo = true;
							buildingPlan.clear();
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.DISC));
						}
						else if (card.equals("MONO")) {
							canDo = true;
							buildingPlan.clear();
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.MONO));
						}
						else if (card.equals("KNIGHT")) {
							canDo = true;
							buildingPlan.clear();
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.KNIGHT));
						}
						else if (card.equals("nothing")){
							canDo = false;
						}
						else{
							canDo = false;
						}
					}
					//buy a dev card
					else if (prediction == 4 && game.couldBuyDevCard(player.playerNumber)) {
				  	canDo = true;
				    SOCPossibleCard posTemp = new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.UNKNOWN);
					  buildingPlan.clear();
					  buildingPlan.push(posTemp);
				  }

					//end turn
					else {
						canDo = true;
					}
					return canDo;
		}



    Double minPossibleEval(SOCPlayer player, int turnsLookAhead) {
		New3PBrain tempBrain = new New3PBrain(this);
		tempBrain.state.updateState(player);
		tempBrain.playerSimulation(player, -1);
    	double minEval = tempBrain.currentChoiceEval;

    	//Other player cities do not effect future moves
    	if(turnsLookAhead == 1) {
    		SOCPlayer[] players = player.game.getPlayers();
    		for(int i=0; i<players.length; i++) {
    			if(i == player.playerNumber) continue;

				int origKnights = players[i].getNumKnights();
    			if(players[i].hasUnplayedDevCards()) {
    				players[i].incrementNumKnights();
    			}
    			//Currently assuming max build in a turn is 1 road and 1 settlement -- for speed
    			@SuppressWarnings("unchecked")
				HashSet<Integer> roads = (HashSet<Integer>) players[i].getPotentialRoads().clone();
    			for(Integer road : roads) {
    	    		SOCRoad temp = new SOCRoad(players[i], road, game.getBoard());
    	   		 	game.putTempPiece(temp);
    	    		if(players[i].getNumPieces(SOCPlayingPiece.SETTLEMENT) >= 1) {
        	    		@SuppressWarnings("unchecked")
        				HashSet<Integer> settlements = (HashSet<Integer>) players[i].getPotentialSettlements().clone();
        	        	for(Integer settlement : settlements) {
        		    		SOCSettlement tempSet = new SOCSettlement(players[i], settlement, game.getBoard());
        	        		game.putTempPiece(tempSet);

        	        		tempBrain.state.updateState(player);
        	        		tempBrain.playerSimulation(player,  -1); //Definitely causing the issue
        	        		if(tempBrain.currentChoiceEval < minEval) {
        	        			minEval = tempBrain.currentChoiceEval;
        	        		}

        	        		game.undoPutTempPiece(tempSet);
        	        	}
        	        }
    				game.undoPutTempPiece(temp);
    			}
    			players[i].setNumKnights(origKnights);
    		}
    	}
    	return minEval;
    }
}
