import java.util.ArrayList;
import java.lang.*;
/*
 * Author: Mary Mitchell
 * Class: COMP 4320
 * Due: Saturday, July 30, 2022
 * New class for project 2 to make things more organized.
 * Simple Packet class to help maintain header and data info.
 */
public class Packet {
    //Public static variables for sizing packets
    public static final int PACKET_SIZE = 1024;
    public static final int HEADER_SIZE = 4;
    public static final int DATA_SIZE = PACKET_SIZE - HEADER_SIZE;

    //Two main variables of a Packet
    //Redo checksum from project1 to be of type short
    private ArrayList<Short> header; //ArrayList gives a bit of flexibility if more headers ever need to be added
    private byte[] data; //The data of the packets (not including the header)

    //Class constructor. Initialize the two variables of a Packet
    public Packet() {
        header = new ArrayList<>();
        data = new byte[DATA_SIZE];
    }

    //Getters and Setters
    public ArrayList<Short> getHeader() {
        return header;
    }

    public void setHeader(ArrayList<Short> header) {
        this.header = header;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
