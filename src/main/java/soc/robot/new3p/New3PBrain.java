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
    	currentChoiceEval = turnLookAhead == 1 ? minPossibleEval(player, 1) : state.evalFunction();

    	for(int devCardNum : devCardNums) {
    		if (devCardNum == 0) {
    			playerSimulation(player, turnLookAhead);
    		} else if(devCardNum == SOCDevCardConstants.ROADS && game.canPlayRoadBuilding(player.playerNumber) && 
    				!ourPlayerData.hasPlayedDevCard() && player.getNumPieces(SOCPlayingPiece.ROAD) >= 2) {
    			Double prevEval = currentChoiceEval;
    			SOCPossibleRoad road1 = null;
    			SOCPossibleRoad road2 = null;

    			@SuppressWarnings("unchecked")
				HashSet<Integer> roads = (HashSet<Integer>) player.getPotentialRoads().clone();
    			for(Integer road : roads) {
    	    		SOCRoad temp = new SOCRoad(player, road, game.getBoard());
    	   		 	game.putTempPiece(temp);
    				@SuppressWarnings("unchecked")
					HashSet<Integer> secondRoads = (HashSet<Integer>) player.getPotentialRoads().clone();
    				for(Integer secondRoad : secondRoads) {
        	    		SOCRoad secondTemp = new SOCRoad(player, secondRoad, game.getBoard());
        	    		game.putTempPiece(secondTemp);
        	    		playerSimulation(player, turnLookAhead);
        	    		game.undoPutTempPiece(secondTemp);

        	    		if(prevEval != currentChoiceEval) {
        	    			prevEval = currentChoiceEval;
        	    			road1 = new SOCPossibleRoad(ourPlayerData, temp.getCoordinates(), null);
        	    			road2 = new SOCPossibleRoad(ourPlayerData, secondTemp.getCoordinates(), null);
        	    		}
    				}
    				game.undoPutTempPiece(temp);
    			}

    			if(road1 != null) {
    				 buildingPlan.clear();
    				 buildingPlan.push(road2);
    				 buildingPlan.push(road1);
	        		 buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.ROADS));
    			}
    		} else if(devCardNum == SOCDevCardConstants.DISC && game.canPlayDiscovery(player.playerNumber) && !ourPlayerData.hasPlayedDevCard()) {
    			Double prevEval = currentChoiceEval;

				Boolean origCouldSettlement = game.couldBuildSettlement(player.playerNumber);
				Boolean origCouldRoad = game.couldBuildRoad(player.playerNumber);
				Boolean origCouldCity = game.couldBuildCity(player.playerNumber);

    			for(int i=0; i<5; i++) {
    				player.getResources().add(2, i);

    				Boolean newCouldSettlement = game.couldBuildSettlement(player.playerNumber);
    				Boolean newCouldRoad = game.couldBuildRoad(player.playerNumber);
    				Boolean newCouldCity = game.couldBuildCity(player.playerNumber);

    				if((!origCouldSettlement && newCouldSettlement) || (!origCouldRoad && newCouldRoad)
    						|| (!origCouldCity && newCouldCity))
    					playerSimulation(player, turnLookAhead);

    				player.getResources().subtract(2, i);
    			}

    			for(int i=0; i<5; i++) {
    				for(int j=i+1; j<5; j++) {
	    				player.getResources().add(1, i);
	    				player.getResources().add(1, j);

	    				Boolean newCouldSettlement = game.couldBuildSettlement(player.playerNumber);
	    				Boolean newCouldRoad = game.couldBuildRoad(player.playerNumber);
	    				Boolean newCouldCity = game.couldBuildCity(player.playerNumber);

	    				if((!origCouldSettlement && newCouldSettlement) || (!origCouldRoad && newCouldRoad)
	    						|| (!origCouldCity && newCouldCity))
	    					playerSimulation(player, turnLookAhead);

	    				player.getResources().subtract(1, i);
	    				player.getResources().subtract(1, j);
	    			}
    			}

    			if(prevEval != currentChoiceEval) {
      				 buildingPlan.clear();
      				 buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.DISC));
       			}
    		} else if(devCardNum == SOCDevCardConstants.MONO && game.canPlayMonopoly(player.playerNumber) && !ourPlayerData.hasPlayedDevCard()) {
    			SOCResourceSet temp = new SOCResourceSet(5, 0, 0, 0, 0, 0); //Simplifying with 5 cards received
    			Double prevEval = currentChoiceEval;

    			player.getResources().add(temp);
    			playerSimulation(player, turnLookAhead);
    			player.getResources().subtract(temp);

    			temp = new SOCResourceSet(0, 5, 0, 0, 0, 0);
    			player.getResources().add(temp);
    			playerSimulation(player, turnLookAhead);
    			player.getResources().subtract(temp);

    			temp = new SOCResourceSet(0, 0, 5, 0, 0, 0);
    			player.getResources().add(temp);
    			playerSimulation(player, turnLookAhead);
    			player.getResources().subtract(temp);

    			temp = new SOCResourceSet(0, 0, 0, 5, 0, 0);
    			player.getResources().add(temp);
    			playerSimulation(player, turnLookAhead);
    			player.getResources().subtract(temp);

    			temp = new SOCResourceSet(0, 0, 0, 0, 5, 0);
    			player.getResources().add(temp);
    			playerSimulation(player, turnLookAhead);
    			player.getResources().subtract(temp);

    			if(prevEval != currentChoiceEval) {
   				 buildingPlan.clear();
	        	 buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.MONO));
    			}
    		} else if(devCardNum == SOCDevCardConstants.KNIGHT && game.canPlayKnight(player.playerNumber) && !ourPlayerData.hasPlayedDevCard()) {
    			int origKnights = player.getNumKnights();
    			player.incrementNumKnights();
    			Double prevEval = currentChoiceEval;
    			playerSimulation(player, turnLookAhead);
    			player.setNumKnights(origKnights);
    			if(prevEval != currentChoiceEval) {
    				 buildingPlan.clear();
	        		 buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.KNIGHT));
    			}
    		}
    	}
    }

    protected void playerSimulation(SOCPlayer player, int turnLookAhead) {
    	SOCGame game = player.game;
        boolean builtSettlement = false;
	boolean builtCity =  false;
	boolean builtRoad = false;
	boolean builtDev  = false;

    	if(game.couldBuildSettlement(player.playerNumber)) {
	    	System.out.println("SETTLEMENT for player: " + player + " " + player.getPotentialSettlements().size());
        	@SuppressWarnings("unchecked")
			HashSet<Integer> settlements = (HashSet<Integer>) player.getPotentialSettlements().clone();
        	for(Integer settlement : settlements) {
	    		 SOCSettlement temp = new SOCSettlement(player, settlement, game.getBoard());
	    		 game.putTempPiece(temp);
	        	 state.updateState(this.ourPlayerData);
	        	 Double eval = turnLookAhead == 1 ? minPossibleEval(player, 1) : state.evalFunction();
	        	 if(eval > currentChoiceEval) {
	        		 currentChoiceEval = eval;
		    		 SOCPossibleSettlement posTemp = new SOCPossibleSettlement(player, settlement, null);
	        		 buildingPlan.clear();
	        		 buildingPlan.push(posTemp);
							 builtCity = builtRoad = builtDev = false;
							 builtSettlement = true;
	        	 }
	    		 game.undoPutTempPiece(temp);
	    	 }
    	}

    	if(game.couldBuildRoad(player.playerNumber)) {
	    	System.out.println("ROADS for player: " + player + " " + player.getPotentialRoads().size());
	    	@SuppressWarnings("unchecked")
			HashSet<Integer> roads = (HashSet<Integer>) player.getPotentialRoads().clone();
	    	for(Integer road : roads) {
	    		SOCRoad temp = new SOCRoad(player, road, game.getBoard());
	   		 	game.putTempPiece(temp); //seemingly cant create an error if this is commented??
	   		 	state.updateState(this.ourPlayerData);
		   		 Double eval = turnLookAhead == 1 ? minPossibleEval(player, 1) : state.evalFunction();
	        	 if(eval > currentChoiceEval) {
	        		 currentChoiceEval = eval;
		    		 SOCPossibleRoad posTemp = new SOCPossibleRoad(player, road, null);
	        		 buildingPlan.clear();
	        		 buildingPlan.push(posTemp);
							 builtCity = builtSettlement = builtDev = false;
							 builtRoad = true;
	        	 }
	   		 	game.undoPutTempPiece(temp);
	    	}
    	}

    	if(game.couldBuildCity(player.playerNumber)) {
	    	@SuppressWarnings("unchecked")
			Vector<SOCSettlement> cities = (Vector<SOCSettlement>) player.getSettlements().clone();
	    	for(SOCSettlement set : cities) {
	    		int city = set.getCoordinates();
	    		SOCCity temp = new SOCCity(player, city, game.getBoard());
	   		 	game.putTempPiece(temp);
	   		 	state.updateState(this.ourPlayerData);
		   		 Double eval = turnLookAhead == 1 ? minPossibleEval(player, 1) : state.evalFunction();
	        	 if(eval > currentChoiceEval) {
	        		 currentChoiceEval = eval;
		    		 SOCPossibleCity posTemp = new SOCPossibleCity(player, city);
	        		 buildingPlan.clear();
	        		 buildingPlan.push(posTemp);
							 builtSettlement = builtRoad = builtDev = false;
							 builtCity = true;
	        	 }
	   		 	game.undoPutTempPiece(temp);
	    	}
    	}

    	if(game.couldBuyDevCard(player.playerNumber)) {
    		player.getInventory().addDevCard(1, 1, SOCDevCardConstants.UNKNOWN);
   		 	state.updateState(this.ourPlayerData);
   		 	Double eval = turnLookAhead == 1 ? minPossibleEval(player, 1) : state.evalFunction();
   		 	if(eval > currentChoiceEval) {
   		 		currentChoiceEval = eval;
   		 		SOCPossibleCard posTemp = new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.UNKNOWN);
	        	buildingPlan.clear();
	        	buildingPlan.push(posTemp);
						builtCity = builtRoad = builtSettlement = false;
						builtRoad = true;
   		 	}
   		 	player.getInventory().removeDevCard(1, SOCDevCardConstants.UNKNOWN);
    	}

			Vector stateVector = state.getState();

			if (builtSettlement) {
				try {
					SOCDBHelper.finalStateRepresentation(state.stateToString(stateVector), 0, player);
				}
				catch (Exception e){
					 System.err.println("Error updating on Settlement:" + e);
				}
			}

			else if (builtCity) {
				try {
					SOCDBHelper.finalStateRepresentation(state.stateToString(stateVector), 1, player);
				}
				catch (Exception e){
					 System.err.println("Error updating on City:" + e);
				}
			}

			else if (builtRoad) {
				try {
					SOCDBHelper.finalStateRepresentation(state.stateToString(stateVector), 2, player);
				}
				catch (Exception e){
					 System.err.println("Error updating on Road:" + e);
				}
			}

			else if (builtDev) {
				try {
					SOCDBHelper.finalStateRepresentation(state.stateToString(stateVector), 4, player);
				}
				catch (Exception e){
					 System.err.println("Error updating on Dev Card:" + e);
				}
			}

			else{
				try {
					SOCDBHelper.finalStateRepresentation(state.stateToString(stateVector), 5, player);
				}
				catch (Exception e){
					 System.err.println("Error updating on endturn:" + e);
				}
			}

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
