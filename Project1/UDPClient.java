import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
/*
 * Author: Mary Mitchell
 * Class: COMP 4320
 * Due: Friday, July 22, 2022
 * Based on the code sample provided in the ppt
 * from lecture.
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
        ArrayList<byte[]> received = new ArrayList<byte[]>();
        ArrayList<Integer> corruptedPackets = new ArrayList<Integer>();
        fileName = "ClientOutputWithNoGremlinErrors.html";
        FileOutputStream outputStream = new FileOutputStream(fileName);
        int numPackets = 0;
        byte[] gremlinData;
        boolean allDone = false;
        while(!allDone) {
            //Receive one of the packets
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            numPackets++;
            if(numPackets == 1) {
                System.out.println("Starting to receive packets!");
            }

            //Check if the null packet has been received
            if(receivePacket.getData()[0] == (byte) '\u0000') {
                System.out.println("Null packet received!");
                break;
            }

            //Print the data from the packet (not the header info in cells 0 and 1)
            String modifiedSentence = new String(Arrays.copyOfRange(receivePacket.getData(), 2, 1023));
            System.out.print(modifiedSentence);

            //Run the packet through the gremlin function
            gremlinData = receivePacket.getData();
            gremlinData = gremlinFunction(gremlinData, gremlinProb);
            if (gremlinData != null) { //If the packet was chosen for damage, set the new data to the damaged data
                receivePacket.setData(gremlinData);
            }

            //Error detection using check sum
            if(receivePacket.getData()[1] != checkSumPacket(receiveData)) {
                corruptedPackets.add((int) receivePacket.getData()[0]);
            }

            //Write data from the server to a file
            //NOTE this will contain any errors that the gremlin file created!
            outputStream.write(Arrays.copyOfRange(receivePacket.getData(), 2, 1023));

        }

        //Print the error detection results
        System.out.println("Error detection results!");
        System.out.println("Total number of packets received (including null): " + numPackets);
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
        Random rand = new Random();
        double packetDamage = rand.nextDouble();
        if (packetDamage <= probability) {
            double byteDamage = rand.nextDouble();
            if (byteDamage <= 0.5) {
                int randByte = rand.nextInt(packet.length - 2) + 2;
                packet[randByte] = (byte) ~ packet[randByte];
            } else if (packetDamage <= 0.8) {
                int randByte1 = rand.nextInt(packet.length - 2) + 2;
                int randByte2 = rand.nextInt(packet.length - 2) + 2 ;
                while (randByte1 == randByte2) {
                    randByte1 = rand.nextInt(packet.length - 2) + 2;
                    randByte2 = rand.nextInt(packet.length - 2) + 2;
                }
                packet[randByte1] = (byte) ~ packet[randByte1];
                packet[randByte2] = (byte) ~ packet[randByte2];
            } else {
                int randByte1 = rand.nextInt(packet.length - 2) + 2;
                int randByte2 = rand.nextInt(packet.length - 2) + 2;
                int randByte3 = rand.nextInt(packet.length - 2) + 2;
                while (randByte1 == randByte2 || randByte1 == randByte3 || randByte2 == randByte3) {
                    randByte1 = rand.nextInt(packet.length - 2) + 2;
                    randByte2 = rand.nextInt(packet.length - 2) + 2;
                    randByte3 = rand.nextInt(packet.length - 2) + 2;
                }
                packet[randByte1] = (byte) ~ packet[randByte1];
                packet[randByte2] = (byte) ~ packet[randByte2];
                packet[randByte3] = (byte) ~ packet[randByte3];
            }
            return packet;
        } else {
            packet = null;
            return packet;
        }
    }

    /*
     * checkSumPacket is used to determine the final check sum of each
     * packet after going through the gremlin function.
     */
    public static byte checkSumPacket(byte[] packet) {
        byte b = 0;
        for(int i = 2; i < packet.length; i++) {
            b += packet[i];
        }
        return b;
    }

}
