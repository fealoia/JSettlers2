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



def loadData():
    try:
      con = MySQLdb.connect(user='root', password= 'lunita', database='socdata')
    except MySQLdb.Error as err:
      if err.errno == errorcode.ER_ACCESS_DENIED_ERROR:
        print("Something is wrong with your user name or password")

    cursor = con.cursor()

    query = ("SELECT * FROM finalStateRepresentation;")


    cursor.execute(query)

    data = cursor.fetchall()


    i = 0
    x = []
    y = []

    for row in data:
        x.append(row[:-1])
        y.append(row[len(row)-1])

    xtrain = np.asarray(x)
    ytrain = np.asarray(y)

    cursor.close()
    con.close()

    x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.33, random_state=42)
    print(len(x_test))
    print(len(y_train))

    yTrain = np.zeros((1129,6), dtype=np.int)
    yTest = np.zeros((557,6), dtype=np.int)
    for i in range (0,1129):
        j = y_train[i]
        yTrain[i][j] = 1
    for i in range(0,557):
        j = y_test[i]
        yTest[i][j] = 1

    return x_train, x_test, yTrain, yTest

def buildNN():
    nm = Sequential()
    nm.add(Dense(12, input_dim = 26, activation='relu'))
    nm.add(Dense(8, activation='relu'))
    nm.add(Dense(6, activation = 'softmax'))

    return nm

def trainNN(model, x_train, y_train):
    sgd = optimizers.SGD(lr=0.01)
    model.compile(loss='categorical_crossentropy', optimizer=sgd, metrics=['accuracy'])
    model.fit(x_train, y_train, epochs=20, batch_size=32)


if __name__ == "__main__":
    x_train, x_test, y_train, y_test = loadData()
    nn = buildNN()
    trainNN(nn, x_train, y_train)

    model_json = nn.to_json()
    with open("model.json", "w") as json_file:
        json_file.write(model_json)
    nn.save_weights("model.h5")
    print("Saved model to disk")

    # prediction = [0, 0, 2, 1, 3, 0, 0, 0, 0, 3, -5, -3, 2.0, 3.6, 4.8, 0.27777777777777773, 0.0980392156862745, 0.16666666666666666, 0.07407407407407407, 0.1111111111111111, 0, 0, 0, 0, 0, 0]
    # q = nn.predict(np.array([prediction,]))
    # print(q)
