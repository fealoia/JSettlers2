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

first_three_placement_prob = []
final_VP = []

for row in first_three_placements.iterrows():
    data = row[1]
    if "robot" not in data['player']:
        continue

    roll_numbers = [data['firstPlacementNumberOne'],data['firstPlacementNumberTwo'],data['firstPlacementNumberThree'],
            data['secondPlacementNumberOne'],data['secondPlacementNumberTwo'],data['secondPlacementNumberThree'],
            data['thirdPlacementNumberOne'],data['thirdPlacementNumberTwo'],data['thirdPlacementNumberthree']]
    
    totalProb = 0
    for num in roll_numbers:
        if num == 2 or num == 12:
            totalProb += .03
        elif num == 3 or num == 11:
            totalProb += .06
        elif num == 4 or num == 10:
            totalProb += .08
        elif num == 5 or num == 9:
            totalProb += .11
        elif num == 6 or num == 8:
            totalProb += .14
    
    first_three_placement_prob.append(totalProb)
    if data['player'] == data['player1']:
        final_VP.append(data['score1'])
    elif data['player'] == data['player2']:
        final_VP.append(data['score2'])
    elif data['player'] == data['player3']:
        final_VP.append(data['score3'])
    elif data['player'] == data['player4']:
        final_VP.append(data['score4'])

correlation = np.corrcoef(np.array(first_three_placement_prob),np.array(final_VP))
print(correlation)
