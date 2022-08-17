package TFTPUDPClient;

import java.util.Random;
import java.io.FileNotFoundException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Scanner;
import java.nio.file.NoSuchFileException;

public class TFTPUDPClient {

    InetAddress iNetaddress;
    DatagramSocket datagramSocket;
    DatagramPacket packetSend; // packet o be sent
    DatagramPacket packetRecieve; // packet received

    final static byte RRQOP = 1;
    final static byte WRQOP = 2;
    final static byte DATAOP = 3;
    final static byte ACKOP = 4;
    final static byte ERROROP = 5;

    static byte op = 0;

    static String mainFile;

    final int lengthOfPacket = 516;

    byte[] inBuffer;
    byte[] insert = {};

    Random arbitrary = new Random();
    int clientPort = arbitrary.nextInt(65535 - 1024) + 1024; // arbitrary port above 1024
    int serverPort = 2044; // main server port for rrq & wrq

    int indexBlock = 0;

    boolean error = false;
    boolean finalPacket = false;

    /**
     * instantiates a new client and takes console arguments to perform rrq and
     * wrq
     *
     * @param args
     * @throws SocketException
     * @throws Exception
     */
    public static void main(String[] args) throws SocketException, Exception { //IOException?
        TFTPUDPClient client = new TFTPUDPClient();
        client.commandArgs(mainFile);
    }

    /**
     * Takes input from the console for IP, rrq/wrq and the name of the file. If
 rrq then writes data to mainFile and stores. Server port set to 2044 as
 port 69 has validation issues. After request sent to server port new
 arbitrary port is created to pass packets.
     *
     * @param fileName - mainFile you want to write to the server (wrq) or the
     * mainFile you want to write the read request into (rrq)
     * @throws UnknownHostException
     * @throws SocketException
     * @throws Exception
     */
    public void commandArgs(String fileName) throws UnknownHostException, SocketException, Exception {
        //System.out.println("mainFile name " + fileName);
        Scanner sc = new Scanner(System.in);
        System.out.println("enter the IP address you want to send to, loopback address for same machine file sharing is 127.0.0.1");
        String ipAddress = sc.nextLine();
        iNetaddress = InetAddress.getByName(ipAddress);

        datagramSocket = new DatagramSocket(clientPort, iNetaddress); //open our socket with arbitrary port

        System.out.println("enter the filename and its format e.g. file.txt");
        mainFile = sc.nextLine();

        System.out.println("input 1 for read request and 2 for write request");

        switch (sc.nextInt()) {
            case 1:
                op = RRQOP;
                System.out.println("you've selected read request");
                break;
            case 2:
                op = WRQOP;
                System.out.println("you've selected write request");
                try {
                    insert = readFile(mainFile);
                } catch (NoSuchFileException e) {
                    datagramSocket.close();
                    System.exit(0);
                }
                break;
            default:
                System.err.println("Not a valid command. Enter 1 for read and 2 for write.");
                datagramSocket.close();
                System.exit(0);
        }
        ByteArrayOutputStream byteRequest = new ByteArrayOutputStream();
        byteRequest.write(generateRequestBlock(op)); // puts the bytes in that are needed for a intital request
        byte[] makeRequest = byteRequest.toByteArray(); // and then is put into a byte array
        packetSend = new DatagramPacket(makeRequest, makeRequest.length, iNetaddress, serverPort); // using the servers intital port, makes req packet 
        datagramSocket.send(packetSend);
        datagramSocket.setSoTimeout(4000); // 4 second timer to recieve a packet

        //receive a mainFile from the network
        ByteArrayOutputStream rrqFile = passThrough(); //send and receive
        //write the mainFile on to computer
        if (op == RRQOP && error == false) {
            writeFile(rrqFile);
        }
        datagramSocket.close();
    }

    /**
     * Takes opCode 1 or 2 for rrq or wrq and generates the byte array to be
     * sent to the server.
     *
     * @param opCode - rrq or wrq
     * @return byte array containsRequest ready to be inserted into a packet
     * @throws UnsupportedEncodingException
     */
    public byte[] generateRequestBlock(byte opCode) throws UnsupportedEncodingException {
        String dataType = "octet";
        byte[] containsRqst = new byte[(2 + mainFile.length() + 1 + dataType.length() + 1)];
        int ctr = 0;

        containsRqst[ctr] = 0; //first two bytes are retained for the request code
        ctr++;
        containsRqst[ctr] = opCode;
        ctr++;

        byte[] nameByte = mainFile.getBytes("US-ASCII"); // filename to bytes and place into byte array after opCode
        for (byte byteIndex : nameByte) {
            containsRqst[ctr] = byteIndex;
            ctr++;
        }
        containsRqst[ctr] = (byte) 0; // 1 byte separates filename with mode
        ctr++;

        byte[] modeByte = dataType.getBytes("US-ASCII"); // mode to bytes and place into byte array
        for (byte byteMode : modeByte) {
            containsRqst[ctr] = byteMode;
            ctr++;
        }
        containsRqst[ctr] = (byte) 0; // 1 byte finishes the structure of a request
        return containsRqst;
    }

    /**
     * Client and server communications happen through this method. Three cases,
     * receive ack and send data, receive data and send ack, receive error and terminate.
     *
     * @return byteIn - a byte array of data client wants to write to a file for rrq
     * @throws IOException
     */
    public ByteArrayOutputStream passThrough() throws IOException {
        ByteArrayOutputStream byteIn = new ByteArrayOutputStream();
        inBuffer = new byte[lengthOfPacket];
        packetRecieve = new DatagramPacket(inBuffer, lengthOfPacket, iNetaddress, datagramSocket.getLocalPort()); //incoming packet
        int sentSoFar = 0; // used if writing a mainFile to server
        boolean allSent = false; //  data sent awaiting acknowledgment
        int compareBlock = 0; //compare expected and actual blockNo

        while (finalPacket == false) {
            try {
                datagramSocket.receive(packetRecieve);
            } catch (SocketTimeoutException ste) {
                System.out.println("System timed out after 4 second..");
                datagramSocket.send(packetSend); //resend
            }

            byte[] opCode = {inBuffer[0], inBuffer[1]}; //first two bytes always constitute opCode

            switch (opCode[1]) {
                case ACKOP:// if ack send data
                    if (allSent == true) {
                        finalPacket = true;
                        break;
                    } else {
                        ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
                        indexBlock++;
                        byte[] blockByte = intToByteArray(indexBlock);
                        int amount = insert.length;
                        int amountLeft = 0;
                        amountLeft = amendAmountLeft(amount, sentSoFar, amountLeft);

                        int sentSoFarInner = sentSoFar;
                        for (int i = sentSoFar; i < (sentSoFar + amountLeft); i++) {
                            packetStream.write(insert[i]);
                            sentSoFarInner++;
                        }
                        sentSoFar = sentSoFarInner;

                        if (sentSoFar == amount) {
                            allSent = true;
                        }

                        byte[] dataToSend = packetStream.toByteArray();
                        sendCreatedData(dataToSend, blockByte);

                        if (sentSoFar == amount && amount == 512 || amount == 0) { // this will check if the last packet is 512 if it is then a empty packet needs to be sent
                            indexBlock++;
                            blockByte = intToByteArray(indexBlock);
                            byte[] emptyP = {0, DATAOP, blockByte[0], blockByte[1]};

                            packetSend = new DatagramPacket(emptyP, emptyP.length, iNetaddress, packetRecieve.getPort());

                            datagramSocket.send(packetSend);
                            allSent = true;
                        }
                    }
                    break;
                case DATAOP:  // receive data send ack
                    byte[] blockNumber = {inBuffer[2], inBuffer[3]};

                    compareBlock++;
                    byte[] expectedBlockNo = intToByteArray(compareBlock);
                    if (blockNumber[0] == expectedBlockNo[0] && blockNumber[1] == expectedBlockNo[1]) {
                        compareBlock++;

                        if (packetRecieve.getLength() < 516) { // checks if last packet by seeing if its a data packet and its not full if it ack is sent then socket will be closed
                            finalPacket = true;
                        }
                        byteIn.write(packetRecieve.getData(), 4, packetRecieve.getLength() - 4); // gets the data out of the packet and puts into the buffer
                        sendCreatedAck(blockNumber);

                    }
                    break;
                default:
                    finalPacket = true; // close
                    System.err.println("File you tried to read doesn't exist");
            }
        }
        return byteIn;
    }

    /**
     * The byte array given to it is written to the mainFile
     *
     * @param outByte - byte array containing the data from the server
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void writeFile(ByteArrayOutputStream outByte) throws FileNotFoundException, IOException { // if read then insert to a mainFile
        OutputStream rrqStream = new FileOutputStream(mainFile);
        rrqStream.write(outByte.toByteArray());
    }

    /**
     * Will read the content of the mainFile into a byte array
     *
     * @param fileName - mainFile you want to read the data in from
     * @return the byte array of the files content
     * @throws IOException
     */
    public byte[] readFile(String fileName) throws IOException {
        Path directory = Paths.get(fileName);
        byte[] fileContent = Files.readAllBytes(directory);
        return fileContent;
    }

    /**
     * Method takes an integer input representing a block number.
     * Creates a byte array of size 2 as blocks are never more than 2 bytes.
     * int - block is compared with identity element 0xFF using bitwise AND operation.
     * 0xFF represents eight ones in binary used to compare with int to return as a byte.
     *
     * @param block - block number to be converted
     * @return byte array containing int block in bytes
     */
    public static byte[] intToByteArray(int block) {
        byte[] com = new byte[2];
        com[0] = (byte) (block & 0xFF);
        com[1] = (byte) ((block >> 8) & 0xFF);
        return com;
    }

    /**
     * 
     * @param amount - amount of bytes needing to be sent
     * @param sentSoFar - 
     * @param amountLeft
     * @return 
     */
    public int amendAmountLeft(int amount, int sentSoFar, int amountLeft) {
        if (((amount - sentSoFar) / 512) >= 1) {
            amountLeft = 512;
        } else {
            amountLeft = (amount - sentSoFar);
        }
        return amountLeft;
    }

    /**
     * takes a byte array containing block info and writes it into a
     * byte to be written into a packet which is sent through the server Thread socket.
     * @param blockByte - the counter on which block were at
     * @throws IOException 
     */
    private void sendCreatedAck(byte[] blockByte) throws IOException {
        byte[] emptyP = {0, ACKOP, blockByte[0], blockByte[1]};

        packetSend = new DatagramPacket(emptyP, emptyP.length, iNetaddress, packetRecieve.getPort());

        datagramSocket.send(packetSend);
    }

    /**
     * takes a byte array of data and a byte array containing block info.
     * opCode and block number inserted into initialData
     * data array set to size input data + first 4 bytes
     * initial data inserted to data and then data info after.
     * 
     * @param dataToSend
     * @param blockByte
     * @throws IOException 
     */
    private void sendCreatedData(byte[] dataToSend, byte[] blockByte) throws IOException {
        byte[] dataStart = {0, DATAOP, blockByte[0], blockByte[1]};
        byte[] data = new byte[dataToSend.length + dataStart.length];

        System.arraycopy(dataStart, 0, data, 0, dataStart.length); // input dataStart to data at position 0 in byte array
        System.arraycopy(dataToSend, 0, data, dataStart.length, dataToSend.length); //input dataToSend to data at position where dataStart ended 

        packetSend = new DatagramPacket(data, data.length, iNetaddress, packetRecieve.getPort()); //

        datagramSocket.send(packetSend);
    }

}
