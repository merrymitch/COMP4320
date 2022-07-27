import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.awt.*;

/*
 * Author: Mary Mitchell
 * Class: COMP 4320
 * Due: Saturday, July 30, 2022
 * Based on the code sample provided in the ppt
 * from lecture.
 * Modified a lot of project1 to make project2 implementation smoother.
 * Sources: General Java documentation and stackoverflow tips.
 */
class UDPClient {
    public static void main(String args[]) throws Exception {
        //Initialize some key variables for the client
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        DatagramSocket clientSocket = new DatagramSocket();
        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];

        System.out.println("Starting the UDPClient!");

        // Get the IP Address of the server from the user
        InetAddress IPAddress = null;
        do {
            System.out.println("Enter the server's IP Address: ");
            String serverIPAddress = inFromUser.readLine();
            try {
                IPAddress = InetAddress.getByName(serverIPAddress);
            } catch (UnknownHostException e) {
                System.out.println("Invalid server IP Address! Please try again.");
                continue;
            }
            break;
        } while(true);

        // Get the port number of the server from the user
        int portNumber;
        System.out.println("Enter the server's port number: ");
        portNumber = Integer.parseInt(inFromUser.readLine());

        // Get the gremlin probability from the user
        double gremlinProb;
        do {
            System.out.println("Enter the gremlin probability (must be between 0-1): ");
            gremlinProb = Double.parseDouble(inFromUser.readLine());
            if (gremlinProb <= 1.0 && gremlinProb >= 0.0) {
                break;
            } else {
                System.out.println("Invalid gremlin probability! Must be between 0-1. Please try again.");
                continue;
            }
        } while(true);

        // Get the HTTP request from the user
        // GET TestFile.html HTTP/1.0
        String sentence;
        String fileName;
        do {
            System.out.println("Enter the HTTP request: ");
            sentence = inFromUser.readLine();
            String[] splitSentence = sentence.split(" ");
            if (splitSentence.length == 3) {
                if (splitSentence[0].equalsIgnoreCase("GET")) {
                    fileName = splitSentence[1];
                    break;
                }  else {
                    System.out.println("Server only accepts GET requests! Please try again.");
                }
            } else {
                System.out.println("Invalid HTTP request! Must have three arguments. Please try again.");
            }
        } while(true);

        //Get the data from the request and send it
        sendData = sentence.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);
        System.out.println("Sending request to server: " + sentence);
        clientSocket.send(sendPacket);
        System.out.println("Request sent to server!");

        //Initialize some variables for receiving packets from the server
        DatagramPacket receivePacket;
        ArrayList<Packet> received = new ArrayList<Packet>();
        ArrayList<Integer> corruptedPackets = new ArrayList<Integer>();
        fileName = "ClientFiles/" + fileName;
        FileOutputStream outputStream = new FileOutputStream(fileName);
        int numPackets = 0;
        byte[] gremlinData;

        //Begin loop to receive packets
        while(true) {
            //Receive one of the packets
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            numPackets++; //Increment the number of packets received
            if(numPackets == 1) { //Notify user that packets are starting to come through
                System.out.println("Starting to receive packets!");
            }

            //Modify the received DatagramPacket to specifications of Packet class
            byte[] currentData = receivePacket.getData();
            Packet currentPacket = new Packet();
            ByteBuffer byteBuffer = ByteBuffer.wrap(receivePacket.getData());
            ArrayList<Short> header = new ArrayList<>();
            short sn = byteBuffer.getShort(); //Get sequence number from DatagramPacket
            short cs = byteBuffer.getShort(); //Get check sum from DatagramPacket
            header.add(sn);
            header.add(cs);
            currentPacket.setHeader(header); //Set the header field for the Packet
            byte[] onlyData = new byte[currentData.length - byteBuffer.position()]; //The rest of the DatagramPacket is data
            for(int i = 0; i < onlyData.length; i++) { //Copy the rest of the data to the new byte array
                onlyData[i] = currentData[i + byteBuffer.position()];
            }
            currentPacket.setData(onlyData); //Set the data field of the Packet

            //Check if null packet has been received
            short nullHeader = -420;
            if(sn == nullHeader) {
                numPackets--; //Don't count the null packet
                System.out.println("\nNull packet received!");
                break; //End the loop
            }

            //Print the data from the packet and its sequence number
            String modifiedSentence = new String(currentPacket.getData());
            System.out.println("\nReceived packet " + sn + ":");
            System.out.print(modifiedSentence);

            //Run the packet through the gremlin function
            gremlinData = currentPacket.getData();
            gremlinData = gremlinFunction(gremlinData, gremlinProb);
            if (gremlinData != null) { //If the packet was chosen for damage, set the new data to the damaged data
                currentPacket.setData(gremlinData);
            }

            //Now that all modifications have been made add the Packet to the list of received packets
            received.add(currentPacket);

            //Error detection using check sum
            if(cs != checkSum(currentPacket.getData())) {
                corruptedPackets.add((int) sn);
            }
        }

        //Reassemble the packets and write the data from them into the output file
        System.out.println("Writing data to file (errors from gremlin included)!");
        byte[] fileWrite = reassemble(received);
        outputStream.write(fileWrite);

        //Print the error detection results
        System.out.println("******************************* ERROR DETECTION RESULTS *********************************");
        System.out.println("Total number of packets received: " + numPackets);
        System.out.println("The total number of packets damaged: " + corruptedPackets.size());
        for(Integer i: corruptedPackets) {
            System.out.println("Packet " +  i + " was damaged.");
        }

        //Close the output stream
        outputStream.close();

        //Display the output file on desktop
        //Consulted java documentation on how to display a file on desktop
        try {
            File file = new File(fileName);
            Desktop desktop = Desktop.getDesktop();
            desktop.open(file);
        } catch (Exception e) {
            System.out.println("Error displaying the output file!");
        }

        //Close the client socket!
        clientSocket.close();
    }

    /*
     * gremlinFunction first generates a random number between 0 and 1.
     * If that number is less than or equal to the user's probability then
     * the package passed in will be damaged. If the package is to be damaged then
     * another random number is chosen and 1, 2, or 3 bytes will be damaged depending
     * on the number chosen.
     */
    public static byte[] gremlinFunction(byte[] packet, double probability) {
        //Generate a random number between 0-1
        Random rand = new Random();
        double packetDamage = rand.nextDouble();

        //If the random number is less than or equal to the given probability then it is to be damaged
        if (packetDamage <= probability) {
            //Generate new random number between 0-1
            double byteDamage = rand.nextDouble();

            //0.5 probability 1 byte is damaged
            if (byteDamage <= 0.5) {
                int randByte = rand.nextInt(packet.length);
                packet[randByte] = (byte) ~ packet[randByte];
            } else if (packetDamage <= 0.8) { //0.3 probability 2 bytes are damaged
                int randByte1 = rand.nextInt(packet.length);
                int randByte2 = rand.nextInt(packet.length);
                while (randByte1 == randByte2) {
                    randByte1 = rand.nextInt(packet.length);
                    randByte2 = rand.nextInt(packet.length);
                }
                packet[randByte1] = (byte) ~ packet[randByte1];
                packet[randByte2] = (byte) ~ packet[randByte2];
            } else { //0.2 probability 3 bytes are damaged
                int randByte1 = rand.nextInt(packet.length);
                int randByte2 = rand.nextInt(packet.length);
                int randByte3 = rand.nextInt(packet.length);
                while (randByte1 == randByte2 || randByte1 == randByte3 || randByte2 == randByte3) {
                    randByte1 = rand.nextInt(packet.length);
                    randByte2 = rand.nextInt(packet.length);
                    randByte3 = rand.nextInt(packet.length);
                }
                packet[randByte1] = (byte) ~ packet[randByte1];
                packet[randByte2] = (byte) ~ packet[randByte2];
                packet[randByte3] = (byte) ~ packet[randByte3];
            }
            return packet;
        } else { //Else the packet is not to be damaged
            packet = null;
            return packet;
        }
    }

    /*
     * Calculates the checksum of packet data for use in error detection.
     * Sums up all the bytes of data and returns a short.
     */
    public static short checkSum(byte[] packet) {
        short checkSum = 0;
        for(byte data: packet) {
            checkSum += data;
        }
        return checkSum;
    }

    /*
     * reassemble is a pretty simple function.
     * Takes the final ArrayList of Packets and reassembles them by sequence number.
     */
    public static byte[] reassemble(ArrayList<Packet> packets) {
        int packetsSize = 0;
        //Determine the size of the byte array needed for all of the data
        for(Packet packet: packets) {
            packetsSize += packet.getData().length;
        }

        //Copy the data in the proper order to the new array
        byte[] output = new byte[packetsSize];
        int index = 0;
        for(int i = 0; i < packets.size(); i++) { //'i' represents the desired sequence number
            for(Packet packet: packets) { //Find the packet with sequence number i
                ArrayList<Short> headers = new ArrayList<Short>();
                headers = packet.getHeader();
                short sn = headers.get(0);
                if(sn == i) { //once desired sequence number is found, copy the data of the packet into the back of array
                    for(int j = 0; j < packet.getData().length; j++) {
                        output[index + j] = packet.getData()[j];
                    }
                    index += packet.getData().length;
                    break;
                }
            }
        }
        return output;
    }
}
