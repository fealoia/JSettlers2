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

import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class New3PClient extends SOCRobotClient
{
    /** Our class name, for {@link #rbclass}: {@code "soc.robot.sample3p.Sample3PClient"} */
    private static final String RBCLASSNAME_SAMPLE = New3PClient.class.getName();

    /**
     * Constructor for connecting to the specified server, on the specified port.
     *
     * @param h  server hostname
     * @param p  server port
     * @param nn nickname for robot
     * @param pw password for robot
     * @param co  required cookie for robot connections to server
     */
    public New3PClient(final String h, final int p, final String nn, final String pw, final String co)
    {
        super(h, p, nn, pw, co);

        rbclass = RBCLASSNAME_SAMPLE;
    }

    /**
     * Factory to provide our client's {@link Sample3PBrain} to games instead of the standard brain.
     *<P>
     * Javadocs from original factory:
     *<BR>
     * {@inheritDoc}
     */
    @Override
    public SOCRobotBrain createBrain
        (final SOCRobotParameters params, final SOCGame ga, final CappedQueue<SOCMessage> mq)
    {
        return new New3PBrain(this, params, ga, mq);
    }

    /**
     * Main method.
     * @param args  Expected arguments: server hostname, port, bot username, bot password, server cookie
     */
    public static void main(String[] args)
    {
        if (args.length < 5)
        {
            System.err.println("Java Settlers sample robotclient");
            System.err.println("usage: java " + RBCLASSNAME_SAMPLE + " hostname port_number userid password cookie");

            return;
        }

        New3PClient cli = new New3PClient(args[0], Integer.parseInt(args[1]), args[2], args[3], args[4]);
        cli.init();
    }

}
