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

import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
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
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class New3PBrain extends SOCRobotBrain
{
	Double currentChoiceEval;
	SOCPossiblePiece currentChoice;
	
    public New3PBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue<SOCMessage> mq)
    {
        super(rc, params, ga, mq);
        
    	this.currentChoiceEval = Double.NEGATIVE_INFINITY;
    	this.currentChoice = null;
    }
    
    @Override
    protected final void planBuilding()
    {
    	playerOptions(ourPlayerData);

    	if (! buildingPlan.empty())
        {
            lastTarget = buildingPlan.peek();
            if(!(lastTarget instanceof SOCPossibleCard))
            	negotiator.setTargetPiece(ourPlayerNumber, lastTarget);
        }
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
    
    private void playerOptions(SOCPlayer player) {
    	Set<Integer> devCardNums = new HashSet<>(Arrays.asList(0,
    			SOCDevCardConstants.ROADS, SOCDevCardConstants.DISC, SOCDevCardConstants.MONO,
    			SOCDevCardConstants.KNIGHT));
    	
      	state.updateState(this.ourPlayerData, decisionMaker.getFavoriteSettlement());
    	currentChoiceEval = state.evalFunction();
    	currentChoice = null;
    	
    	for(int devCardNum : devCardNums) {
    		if (devCardNum == 0) {
    			playerSimulation(player);
    		//	System.out.println("Final Eval:" + currentChoiceEval + " for Player: " + ourPlayerNumber);
    		} else if(devCardNum == SOCDevCardConstants.ROADS && game.canPlayRoadBuilding(player.playerNumber) && !ourPlayerData.hasPlayedDevCard()) {				
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
        	    		playerSimulation(player);
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
    					playerSimulation(player);
    					
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
	    					playerSimulation(player);
	    					
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
    			playerSimulation(player);
    			player.getResources().subtract(temp);
    			
    			temp = new SOCResourceSet(0, 5, 0, 0, 0, 0);
    			player.getResources().add(temp);
    			playerSimulation(player);
    			player.getResources().subtract(temp);
    			
    			temp = new SOCResourceSet(0, 0, 5, 0, 0, 0);
    			player.getResources().add(temp);
    			playerSimulation(player);
    			player.getResources().subtract(temp);
    			
    			temp = new SOCResourceSet(0, 0, 0, 5, 0, 0);
    			player.getResources().add(temp);
    			playerSimulation(player);
    			player.getResources().subtract(temp);
    			
    			temp = new SOCResourceSet(0, 0, 0, 0, 5, 0);
    			player.getResources().add(temp);
    			playerSimulation(player);
    			player.getResources().subtract(temp);
    			
    			if(prevEval != currentChoiceEval) {
   				 buildingPlan.clear();
	        	 buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.MONO));
    			}	
    		} else if(devCardNum == SOCDevCardConstants.KNIGHT && game.canPlayKnight(player.playerNumber) && !ourPlayerData.hasPlayedDevCard()) {
    			int origKnights = player.getNumKnights();
    			player.incrementNumKnights();
    			Double prevEval = currentChoiceEval;
    			playerSimulation(player);
    			player.setNumKnights(origKnights);
    			if(prevEval != currentChoiceEval) {
    				 buildingPlan.clear();
	        		 buildingPlan.push(new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.KNIGHT));
    			}
    		}
    	}
    }
    
    private void playerSimulation(SOCPlayer player) {
    	SOCGame game = player.game;
    	
    	if(game.couldBuildSettlement(player.playerNumber)) {
        	@SuppressWarnings("unchecked")
			HashSet<Integer> settlements = (HashSet<Integer>) player.getPotentialSettlements().clone();
        	for(Integer settlement : settlements) {
	    		 SOCSettlement temp = new SOCSettlement(player, settlement, game.getBoard());
	    		 game.putTempPiece(temp);
	        	 state.updateState(this.ourPlayerData, decisionMaker.getFavoriteSettlement());
	        	 Double eval = state.evalFunction();
	       // 	 System.out.println("Settlement Eval: " + eval);
	        	 if(eval > currentChoiceEval) {
	        		 currentChoiceEval = eval;
		    		 SOCPossibleSettlement posTemp = new SOCPossibleSettlement(player, settlement, null);
	        		 currentChoice = posTemp;
	        		 buildingPlan.clear();
	        		 buildingPlan.push(posTemp);
	        	 }
	    		 game.undoPutTempPiece(temp);
	    	 } 	 
    	}
    	
    	if(game.couldBuildRoad(player.playerNumber)) {
	    	@SuppressWarnings("unchecked")
			HashSet<Integer> roads = (HashSet<Integer>) player.getPotentialRoads().clone();
	    	for(Integer road : roads) {
	    		SOCRoad temp = new SOCRoad(player, road, game.getBoard());
	   		 	game.putTempPiece(temp);
	   		 	state.updateState(this.ourPlayerData, decisionMaker.getFavoriteSettlement());
		   		 Double eval = state.evalFunction();
	        //	 System.out.println("Road Eval: " + eval);
	        	 if(eval > currentChoiceEval) {
	        		 currentChoiceEval = eval;
		    		 SOCPossibleRoad posTemp = new SOCPossibleRoad(player, road, null);
	        		 currentChoice = posTemp;
	        		 buildingPlan.clear();
	        		 buildingPlan.push(posTemp);
	        	 }
	   		 	game.undoPutTempPiece(temp);
	    	}
    	}
    	
    	if(game.couldBuildCity(player.playerNumber)) {
	    	@SuppressWarnings("unchecked")
			HashSet<Integer> cities = (HashSet<Integer>) player.getPotentialCities().clone();
	    	for(Integer city : cities) {
	    		SOCCity temp = new SOCCity(player, city, game.getBoard());
	   		 	game.putTempPiece(temp);
	   		 	state.updateState(this.ourPlayerData, decisionMaker.getFavoriteSettlement());
		   		 Double eval = state.evalFunction();
	        //	 System.out.println("City Eval: " + eval);
	        	 if(eval > currentChoiceEval) {
	        		 currentChoiceEval = eval;
		    		 SOCPossibleCity posTemp = new SOCPossibleCity(player, city);
	        		 currentChoice = posTemp;
	        		 buildingPlan.clear();
	        		 buildingPlan.push(posTemp);
	        	 }
	   		 	game.undoPutTempPiece(temp);
	    	}
    	}
    	
    	if(game.couldBuyDevCard(player.playerNumber)) {
    		player.getInventory().addDevCard(1, 1, SOCDevCardConstants.UNKNOWN);
   		 	state.updateState(this.ourPlayerData, decisionMaker.getFavoriteSettlement());
   		 	Double eval = state.evalFunction();
   		 	if(eval > currentChoiceEval) {
   		 		currentChoiceEval = eval;
   		 		SOCPossibleCard posTemp = new SOCPossibleCard(ourPlayerData, 0, SOCDevCardConstants.UNKNOWN);
	        	buildingPlan.clear();
	        	buildingPlan.push(posTemp);
   		 	}
   		 	player.getInventory().removeDevCard(1, SOCDevCardConstants.UNKNOWN);
    	}
    }
}
