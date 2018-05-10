import numpy as np
import tensorflow as tf
import keras
from matplotlib import pyplot as plt
from keras.datasets import cifar10
from keras import Sequential
from keras.layers import Input, Dense, Flatten, Conv2D, MaxPooling2D, Dropout
from keras import optimizers
from keras.models import Model
from sklearn.model_selection import train_test_split
from keras.models import model_from_json
import MySQLdb



def loadData():
    try:
      con = MySQLdb.connect(user='root', password= 'lunita', database='socdata')
    except MySQLdb.Error as err:
      if err.errno == errorcode.ER_ACCESS_DENIED_ERROR:
        print("Something is wrong with your user name or password")

    cursor = con.cursor()

    query = ("SELECT * FROM aOrdered;")


    cursor.execute(query)

    data = cursor.fetchall()


    i = 0
    x = []
    y = []
    z = []
    timestamp = []


    for row in data:
        x.append(row[:884])
        y.append(row[-31:])

    for row in y:
        timestamp.append(row[-1:])
        z.append(row[:30])

    cursor.close()
    con.close()


    return x,z

def loadDataTwo():
    try:
      con = MySQLdb.connect(user='root', password= 'lunita', database='socdata')
    except MySQLdb.Error as err:
      if err.errno == errorcode.ER_ACCESS_DENIED_ERROR:
        print("Something is wrong with your user name or password")

    cursor = con.cursor()

    a = []
    timestamptwo = []

    query = ("SELECT * FROM bOrdered;")


    cursor.execute(query)
    data = cursor.fetchall()
    for row in data:
        a.append(row[:365])
        timestamptwo.append(row[-1:])


    cursor.close()
    con.close()

    return a

#
#     x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.33, random_state=42)
#     print(len(x_test))
#     print(len(y_train))
#
#     yTrain = np.zeros((1129,6), dtype=np.int)
#     yTest = np.zeros((557,6), dtype=np.int)
#     for i in range (0,1129):
#         j = y_train[i]
#         yTrain[i][j] = 1
#     for i in range(0,557):
#         j = y_test[i]
#         yTest[i][j] = 1
#
#     return x_train, x_test, yTrain, yTest
#
def buildNN():
    input1 = Input(shape=(884,))
    input2 = Input(shape=(30,))
    dense1 = Dense(50, activation = 'relu')(input1)
    intermediate = keras.layers.concatenate([dense1, input2])
    dense2 = Dense(50, activation = 'relu')(intermediate)
    output = Dense(365, activation = 'softmax')(dense2)
    model = Model(inputs = [input1, input2], outputs = output)

    return model
#
def trainNN(model, inputVectorOne, inputVectorTwo, outputVector):
    sgd = optimizers.SGD(lr=0.01)
    model.compile(optimizer='rmsprop', loss='categorical_crossentropy', metrics=['accuracy'])
    model.fit([inputVectorOne, inputVectorTwo], outputVector, epochs=50, batch_size=32)


if __name__ == "__main__":
    inputVectorOne, inputVectorTwo = loadData()
    outputVector = loadDataTwo()

    inputVectorOneTrain, inputVectorOneTest, inputVectorTwoTrain, inputVectorTwoTest, outputVectorTrain, outputVectorTest = train_test_split(inputVectorOne, inputVectorTwo, outputVector, test_size=0.1, random_state=42)

    inputOneTrain = np.array(inputVectorOneTrain)
    inputOneTest = np.array(inputVectorOneTest)

    inputTwoTrain = np.array(inputVectorTwoTrain)
    inputTwoTest = np.array(inputVectorTwoTest)

    outputTrain = np.array(outputVectorTrain)
    outputTest = np.array(outputVectorTest)

    nn = buildNN()
    nn.summary()

    trainNN(nn, inputOneTrain, inputTwoTrain, outputTrain)

    print(nn.evaluate([inputOneTest, inputTwoTest], outputTest))
    #
    model_json = nn.to_json()
    with open("model.json", "w") as json_file:
        json_file.write(model_json)
    nn.save_weights("model.h5")
    print("Saved model to disk")

    # prediction = [0, 0, 2, 1, 3, 0, 0, 0, 0, 3, -5, -3, 2.0, 3.6, 4.8, 0.27777777777777773, 0.0980392156862745, 0.16666666666666666, 0.07407407407407407, 0.1111111111111111, 0, 0, 0, 0, 0, 0]
    # q = nn.predict(np.array([prediction,]))
    # print(q)
