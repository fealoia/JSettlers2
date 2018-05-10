import sys
import numpy as np
import tensorflow as tf
from matplotlib import pyplot as plt
from keras.datasets import cifar10
from keras import Sequential
from keras.layers import Dense, Flatten, Conv2D, MaxPooling2D, Dropout
from keras import optimizers
from sklearn.model_selection import train_test_split
from keras.models import model_from_json
import MySQLdb




def main(inputVectorOne = [], inputVectorTwo=[] ):
    # array = [0, 0, 2, 1, 3, 0, 0, 0, 0, 3, -5, -3, 2.0, 3.6, 4.8, 0.27777777777777773, 0.0980392156862745, 0.16666666666666666, 0.07407407407407407, 0.1111111111111111, 0, 0, 0, 0, 0, 0]
    arrayOne = []
    arrayTwo = []
    a = inputVectorOne.replace('[','')
    b = a.replace(']', '')
    c = b.replace(' ', '')
    for element in c.split(','):
        arrayOne.append((element))


    inputOne = np.array([arrayOne,])


    x = inputVectorTwo.replace('[','')
    y = x.replace(']', '')
    z = y.replace(' ', '')
    for element in z.split(','):
        arrayTwo.append((element))

    inputTwo = np.array([arrayTwo,])

    json_file = open('modelNew.json', 'r')
    loaded_model_json = json_file.read()
    json_file.close()
    loaded_model = model_from_json(loaded_model_json)
    # # load weights into new model
    loaded_model.load_weights("modelNew.h5")
    q = loaded_model.predict([inputOne, inputTwo])
    print(q)


if __name__ == "__main__":
   main(sys.argv[1], sys.argv[2])
