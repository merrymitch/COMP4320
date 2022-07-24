import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
/*
 * Author: Mary Mitchell
 * Class: COMP 4320
 * Due: Friday, July 22, 2022
 * Based on the code sample provided in the ppt
 * from lecture.
 * Sources: General Java documentation and stackoverflow tips.
 */
class UDPServer {
    public static void main(String args[]) throws Exception {
        //Initializing some key variables for the server
        DatagramSocket serverSocket = new DatagramSocket(9876);
        byte[] receiveData = new byte[1024];
        byte[] sendData = new byte[1024];
        System.out.println("Starting the UDPServer!");

        //Infinite loop waiting for clients to send requests
        while(true) {
            //Receive the request from the client
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            System.out.println("Receiving a packet!");
            String sentence = new String(receivePacket.getData());
            System.out.println("Request received: " + sentence);

            //Get return info from the request packet
            InetAddress IPAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();

            //Get the filename requested from client
            String[] request = sentence.split(" ");
            String fileName = request[1];
            String responseCode = "";

            //Read the file and store the data from it
            //Used stackoverflow for tips on reading and storing file data (StringBuilder documentation)
            StringBuilder builder = new StringBuilder();
            try {
                BufferedReader readFile = new BufferedReader(new FileReader(fileName));
                String fileData;
                System.out.println("Server is starting to read the file!");
                while ((fileData = readFile.readLine()) != null) {
                    System.out.println(fileData);
                    builder.append(fileData + "\n");
                }
                readFile.close();
                responseCode = "200 Document Follows";
            } catch(FileNotFoundException e) {
                System.out.println("Server could not find the file!");
                responseCode = "404 Not Found";
                builder = null;
            }

            //Now find out the amount of data in the file (content-length)
            byte[] fileBytes = null;
            try {
                fileBytes = Files.readAllBytes(Paths.get(fileName));
            } catch(FileNotFoundException e) {
                System.out.println("Server could not find the file!");
                responseCode = "404 Not Found";
                fileBytes = null;
            }

            //Create the response message
            String response = "HTTP/1.0" + " " + responseCode + "\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + fileBytes.length + "\r\n"
                    + "\r\n"
                    + builder;
            byte[] responseData = response.getBytes();

            //Segment the response into packets without headers first
            ArrayList<byte[]> noHeaderPackets = segmentation(responseData);
            //Then add the headers to the packets
            ArrayList<byte[]> headerPackets = addHeaders(noHeaderPackets);

            //Send the packets back to the client
            for (byte[] packet: headerPackets) {
                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, IPAddress, port);
                System.out.println("Packet " + packet[0] + " sending...");
                serverSocket.send(sendPacket);

            }

            //Send the null packet to indicate the data is done sending
            byte[] nullBytes = new byte[1024];
            DatagramPacket nullPacket = new DatagramPacket(nullBytes, nullBytes.length, IPAddress, port);
            System.out.println("NULL Packet sending...");
            serverSocket.send(nullPacket);
            System.out.println("All packets sent!");
        }
    }

    /*
     * segmentation takes the response data and segments it into 1022 byte packets.
     * This leaves room for the header info to be added later.
     */
    public static ArrayList<byte[]> segmentation(byte[] data) {
        ArrayList<byte[]> returnPackets = new ArrayList<>();
        boolean finished = false;
        int index1 = 0;
        int index2 = 1022;
        while (!finished) {
            byte[] packet = new byte[1022];
            int packetIndex = 0;
            for(int i = index1; i < index2; i++) {
                packet[packetIndex] = data[i];
                packetIndex++;
                if(i == data.length - 1) {
                    finished = true;
                    break;
                }
            }
            System.out.println(packet);
            returnPackets.add(packet);
            index1 = index1 + 1022;
            index2 = index2 + 1022;
        }
        return returnPackets;
    }

    /*
     * This adds the headers to the packets made after the segmentation
     * function. This returns an array list with 1024 byte packets
     * ready to be sent.
     */
    public static ArrayList<byte[]> addHeaders(ArrayList<byte[]> packets) {
        ArrayList<byte[]> returnPacket = new ArrayList<byte[]>();
        int numPackets = packets.size();
        int sequenceNumber = 1;
        for (int i = 0; i < numPackets; i++) {
            byte[] packet = new byte[1024];
            packet[0] = (byte) sequenceNumber;
            packet[1] = checkSum(packets.get(i));
            int temp = 0;
            for(int j = 2; j < packets.get(i).length + 2; j++) {
                packet[j] = packets.get(i)[temp];
                temp++;
            }
            returnPacket.add(packet);
            sequenceNumber++;
        }
        return returnPacket;
    }

    /*
     * Calculates the check sum for each of the packets' headers.
     */
    public static byte checkSum(byte[] data) {
        byte b = 0;
        for(byte d: data) {
            b += d;
        }
        return b;
    }
}
