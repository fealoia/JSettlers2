import pandas as pd
import numpy as np
import sys

#Usage: python corr.py <data.csv>
# SQLITE: SELECT * FROM firstThreePlacements INNER JOIN games ON games.gamename=firstThreePlacements.game;

first_three_placements = {}

try:
    first_three_placements = pd.read_csv(sys.argv[1])
except:
    print("Unable to open CSV")
    raise

variation = []
final_VP = []

for row in first_three_placements.iterrows():
    data = row[1]
    roll_numbers = [data['firstPlacementNumberOne'],data['firstPlacementNumberTwo'],data['firstPlacementNumberThree'],
            data['secondPlacementNumberOne'],data['secondPlacementNumberTwo'],data['secondPlacementNumberThree']]
    resources = [data['firstPlacementResourceOne'],data['firstPlacementResourceTwo'],data['firstPlacementResourceThree'],
            data['secondPlacementResourceOne'],data['secondPlacementResourceTwo'],data['secondPlacementResourceThree']]

    expectedFrequency = [0,0,0,0,0,0]
    for idx,num in enumerate(roll_numbers):
        if num == 2 or num == 12:
            expectedFrequency[resources[idx]] += .03
        elif num == 3 or num == 11:
            expectedFrequency[resources[idx]] += .06
        elif num == 4 or num == 10:
            expectedFrequency[resources[idx]] += .08
        elif num == 5 or num == 9:
            expectedFrequency[resources[idx]] += .11
        elif num == 6 or num == 8:
            expectedFrequency[resources[idx]] += .14
   
    variation.append(np.var(expectedFrequency[1:6]))
    if data['player'] == data['player1']:
        final_VP.append(data['score1'])
    elif data['player'] == data['player2']:
        final_VP.append(data['score2'])
    elif data['player'] == data['player3']:
        final_VP.append(data['score3'])
    elif data['player'] == data['player4']:
        final_VP.append(data['score4'])

correlation = np.corrcoef(np.array(variation),np.array(final_VP))
print(correlation)
