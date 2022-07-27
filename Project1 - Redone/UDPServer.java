import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/*
 * Author: Mary Mitchell
 * Class: COMP 4320
 * Due: Saturday, July 30, 2022
 * Based on the code sample provided in the ppt
 * from lecture.
 * Modified a lot of project1 to make project2 implementation smoother.
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
            String fileName = "ServerFiles/" + request[1];
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
                    builder.append(fileData + "\n"); //Add line to builder storing the data
                }
                readFile.close();
                responseCode = "200 Document Follows"; //No problems with file
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

            //Segment the response into Packets
            ArrayList<Packet> packets = segmentation(responseData);

            //Send the packets back to the client
            System.out.println("Beginning to send packets!");
            for (Packet packet: packets) { //For each packet, get it into proper form for DatagramPacket and send it
                ArrayList<Short> headerData = packet.getHeader();
                short sn = headerData.get(0);
                short cs = headerData.get(1);
                byte[] d = packet.getData();
                byte[] packetData = ByteBuffer.allocate(Packet.PACKET_SIZE).putShort(sn).putShort(cs).put(d).array();
                DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, IPAddress, port);
                System.out.println("Packet " + (int) sn + " sending...");
                serverSocket.send(sendPacket);
            }

            //Send the null packet to indicate the data is done sending
            short nullHeader = -420; //Random header for client to determine when 'null' packet arrives
            String nullData = "\0";
            byte[] nullBytes = ByteBuffer.allocate(Packet.PACKET_SIZE).putShort(nullHeader).putShort(nullHeader).put(nullData.getBytes()).array();
            DatagramPacket nullPacket = new DatagramPacket(nullBytes, nullBytes.length, IPAddress, port);
            System.out.println("NULL Packet sending...");
            serverSocket.send(nullPacket);
            System.out.println("All packets sent!");
        }
    }

    /*
     * Segments the response data into Packet objects of 1024 bytes.
     * Adds sequence numbers and checksums to the packets and puts them into
     * an ArrayList to return.
     */
    public static ArrayList<Packet> segmentation(byte[] fileData) {
        //If there is no data from the response then return an empty list
        ArrayList<Packet> output = new ArrayList<>();
        if (fileData.length <= 0) {
            return output;
        }

        //Maintain index of where the function is at in the fileData
        int index = 0;
        short sequenceNumber = 0; //Maintain sequence numbers to add to headers
        while (index < fileData.length) {
            //Create packet, header, and data variables to hold the info for this iteration
            Packet packet = new Packet();
            ArrayList<Short> header = new ArrayList<Short>();
            byte[] data;

            //Check if the remaining data to be segmented is smaller than the data size for each packet
            if (fileData.length - index < Packet.DATA_SIZE) {
                data = new byte[fileData.length - index];
            } else {
                data = new byte[Packet.DATA_SIZE];
            }

            //Put the required amount of data into the data variable for this packet
            for(int i = 0; i < data.length; i++) {
                data[i] = fileData[index];
                index++;
            }

            //Set the fields for the new packet
            packet.setData(data);
            header.add(sequenceNumber);
            short checkSum = checkSum(data); //Calculate the checksum for the header
            header.add(checkSum);
            packet.setHeader(header);

            output.add(packet); //Add new Packet to the output list
            sequenceNumber++; //Increment the sequence number for next packet
        }
        return output;
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
}
