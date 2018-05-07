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
				char[] predictionChar = new char[1000];
				double currentBiggest = Double.POSITIVE_INFINITY;
				double[] predictionArray = new double[6];
				String predictionString = "";

				state.updateState(this.ourPlayerData);

			 /*//new updates start here
			 	int action = -1;
				int position = -1;

				action, position = state.getActionPair();
				*/
				// Vector stateVector = state.getState();
				//
				// try{
				// 	ProcessBuilder pb = new ProcessBuilder("python3","prediction.py",""+stateVector);
				// 	Process p = pb.start();
				//
				// 	BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
				// 	int check = in.read(predictionChar, 0, predictionChar.length);
				// 	if (check > -1){
				// 		predictionString = new String(predictionChar);
				// 	}
				// }
				// catch(Exception e){
				// 	System.out.println(e);
				// }


				// predictionString = predictionString.substring(0, predictionString.indexOf("]"));
				// String intermediateOne = predictionString.replace("[[", "").trim();
				// String[] intermediateThree = intermediateOne.split("\\s+");
				// for (int i =0; i < intermediateThree.length - 1; i++){
				//   predictionArray[i] = Double.parseDouble(intermediateThree[i]);
				// }

				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				long ms = timestamp.getTime();
				predictionArray = state.getAction(player);
				try {
					SOCDBHelper.saveInputVector(state.stateToString(state.getInputVectorOne(player)), state.stateToString(state.getInputVectorTwo(player)), ms);
				}
				catch (Exception e){
					 System.err.println("Error updating on saveInputVectorOne:" + e);
				}

				try {
					SOCDBHelper.saveOutputVectorOne(state.stateToString(state.getOutputVectorOne(player)), ms);
				}
				catch (Exception e){
					 System.err.println("Error updating on saveOutputVectorOne:" + e);
				}

				try {
					SOCDBHelper.saveOutputVectorTwo(state.stateToString(state.getOutputVectorTwo(player, predictionArray)), ms);
				}
				catch (Exception e){
					 System.err.println("Error updating on saveOutputVectorTwo:" + e);
				}
				try {
					SOCDBHelper.saveOutputVectorThree(state.stateToString(state.getOutputVectorThree(player, predictionArray)), ms);
				}
				catch (Exception e){
					 System.err.println("Error updating on saveOutputVectorThree:" + e);
				}
				// try {
				// 	SOCDBHelper.saveVectorTwo(state.getInputVector(), state.getInputVectorTwo(), state.getOutputVectorTwo());
				// }
				// catch (Exception e){
				// 	 System.err.println("Error updating on saveVectorTwo:" + e);
				// }
				//
				// try {
				// 	SOCDBHelper.saveVectorThree(state.getInputVector(), state.getInputVectorTwo(), state.getOutputVectorThree());
				// }
				// catch (Exception e){
				// 	 System.err.println("Error updating on saveVectorThree:" + e);
				// }

				//predictionArray now holds the prediction probabilities. Go through them to see what to build
				while(canDo == false){
					Object[] array = new Object[2];
					array = nextBiggest(predictionArray, currentBiggest);
					prediction = (Integer)array[0];
					if (prediction == -1){
						System.out.println("nextBiggest function failed");
					}
					currentBiggest = (Double)array[1];
					canDo = canBuild(prediction, player, turnLookahead);
				}

				System.out.println("DOING ACTION " + prediction);
				System.out.println("0 = building settlement, 1 = building city, 2 = building road, 3 = playing dev card, 4 = buying dev card, 5 = endturn");

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
						System.out.println("PROBLEM WAS WITH SETTLEMENT");
						buildingPlan.clear();
						buildingPlan.push(state.getBestSettlement(player));
				  }

					//build a city
					else if (prediction == 1 && game.couldBuildCity(player.playerNumber)) {
						canDo = true;
						System.out.println("PROBLEM WAS WITH CITY");
						buildingPlan.clear();
						buildingPlan.push(state.getBestCity(player));
						System.out.println("BUILD ON CITY SPOT: " + state.getBestCity(player).getCoordinates());
			    }

					//build a road
					else if (prediction == 2 && game.couldBuildRoad(player.playerNumber)){
						canDo = true;
						System.out.println("PROBLEM WAS WITH ROAD");
						buildingPlan.clear();
						buildingPlan.push(state.getBestRoad(player));
					}

					//play a dev card
					else if (prediction == 3){
						String card = state.getBestDevCard(player);
						if (card.equals("RB")) {
							canDo = true;
							System.out.println("PROBLEM WAS WITH RB");
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
							System.out.println("PROBLEM WAS WITH DISC");
							buildingPlan.clear();
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.DISC));
						}
						else if (card.equals("MONO")) {
							canDo = true;
							System.out.println("PROBLEM WAS WITH MONO");
							buildingPlan.clear();
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.MONO));
						}
						else if (card.equals("KNIGHT")) {
							canDo = true;
							System.out.println("PROBLEM WAS WITH KNIGHT");
							buildingPlan.clear();
							buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.KNIGHT));
						}
						else if (card.equals("nothing")){
							System.out.println("PROBLEM WAS WITH NOTHING");
							canDo = false;
						}
						else{
							System.out.println("PROBLEM WAS WITH ELSE STATEMENT");
							canDo = false;
						}
					}
					//buy a dev card
					else if (prediction == 4 && game.couldBuyDevCard(player.playerNumber)) {
				  	canDo = true;
						System.out.println("PROBLEM WAS WITH BUYING DEV");
				    SOCPossibleCard posTemp = new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.UNKNOWN);
					  buildingPlan.clear();
					  buildingPlan.push(posTemp);
				  }

					//end turn
					else {
						System.out.println("PROBLEM WAS WITH END TURN");
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
