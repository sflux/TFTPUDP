package TFTPUDPServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class TFTPUDPServerThread implements Runnable {

    InetAddress iNetaddress; // ip address
    int clientPort; // port number of client
    final int threadPort; 
    protected DatagramSocket datagramSocket = null;
    DatagramPacket packetRecieve; // packet received
    DatagramPacket packetSend; // packet to be sent out

    Boolean finalPacket = false; // the last packet that needs to be sent
    Boolean allSent = false; // if all the data has been sent from a file
    
    
    Random arbitrary = new Random();

    final static byte RRQOP = 1;
    final static byte WRQOP = 2;
    final static byte DATAOP = 3;
    final static byte ACKOP = 4;
    final static byte ERROROP = 5;

    byte validatorByte;

    /**
     * Creates a new socket with a random port and a 4 second response timer.
     *
     * @param packet - received from server and passed here
     * @throws SocketException
     */
    public TFTPUDPServerThread(DatagramPacket packet) throws SocketException {
        this.threadPort = arbitrary.nextInt(65535 - 1024) + 1024; // random port between 1024 and 65535
        packetRecieve = packet;
        datagramSocket = new DatagramSocket(threadPort); // random port
        datagramSocket.setSoTimeout(4000);
    }

    /**
     * takes packets and extracts their op code. 5 cases.
     * receive rrq and send data packet to client
     * receive wrq and send an ack
     * receive ack and send data
     * receive data and send ack
     * receive last data send empty ack
     */
    @Override
    public void run() {

        ByteArrayOutputStream sendBytes = new ByteArrayOutputStream();
        int countSent = 0; // how much has been sent so far, only used for read
        byte[] insert = {};
        String stringFile = ""; //string name of file
        int countBlock = 0; // block to be sent with a packet
        int expectedBlock = 0; // block number you are expecting from a packet 
        
        byte[] blockByte = new byte[2];

        try {
            while (finalPacket == false) {
                iNetaddress = packetRecieve.getAddress();
                clientPort = packetRecieve.getPort();
                byte[] inBuf = packetRecieve.getData();
                byte[] opCode = {inBuf[0], inBuf[1]};

                switch (opCode[1]) {
                    case RRQOP: // get the file and start sending 
                        stringFile = getFileName(inBuf); // gets the filename out a packet
                        validatorByte = RRQOP;
                        try {
                            insert = getFile(stringFile); // load in the file to send over
                        } catch (NoSuchFileException n) {
                            sendCreatedError("The file you've tried to access doesn't exist.. Please check its name & directory."); // client receives
                            System.err.println("File doesn't exist"); //server prints
                            finalPacket = true; // no more packets so can finish
                            break;
                        }

                        countBlock++;
                        blockByte  = intToByteArray(countBlock); // first block, starts with 1 so increment first

                        ByteArrayOutputStream byteOut = new ByteArrayOutputStream(); // data we want sent in this packet

                        int amount = insert.length; // amount of bytes needing to be sent
                        int amountLeft = 0;
                        amountLeft = amendAmountLeft(amount, countSent, amountLeft);

                        int sentSoFarInner = countSent; // used so that incrementing inside the loop doesn't cause it to loop forever
                        for (int ctr = countSent; ctr < (countSent + amountLeft); ctr++) { // make sure doesnt fill with empty
                            byteOut.write(insert[ctr]); // write to the bytearray
                            sentSoFarInner++;
                        }
                        countSent = sentSoFarInner; // reassigned back after the loop

                        if (countSent == amount) {
                            allSent = true;
                        }

                        byte[] holdBytes = byteOut.toByteArray(); 
                        sendCreatedData(holdBytes, blockByte);

                        if (countSent == amount) {
                            if (amount % 512 == 0) { // this will check if the last packet is 512 if it is then a empty packet needs to be sent
                                countBlock++;
                                blockByte  = intToByteArray(countBlock); // may have to seperate up
                                byte[] emptyP = {0, DATAOP, blockByte [0], blockByte [1]};
                                packetSend = new DatagramPacket(emptyP, emptyP.length, iNetaddress, packetRecieve.getPort());
                                datagramSocket.send(packetSend);
                                allSent = true;
                            }
                        }
                        break;
                    case ACKOP: // if ack send data
                        if (allSent == true) { // if all sent and recevied last ack can finish
                            finalPacket = true;
                            break;
                        } else {
                            ByteArrayOutputStream sendToWrite = new ByteArrayOutputStream();
                            countBlock++;
                            blockByte  = intToByteArray(countBlock);

                            amount = insert.length;
                            amountLeft = 0;
                            amountLeft = amendAmountLeft(amount, countSent, amountLeft);

                            sentSoFarInner = countSent;
                            for (int i = countSent; i < (countSent + amountLeft); i++) {
                                sendToWrite.write(insert[i]);
                                sentSoFarInner++;
                            }
                            countSent = sentSoFarInner;

                            if (countSent == amount) {
                                allSent = true;
                            }

                            byte[] dataToSend = sendToWrite.toByteArray();
                            sendCreatedData(dataToSend, blockByte );

                            if (countSent == amount && amount % 512 == 0) { // if last packet to be sent is 512 then send a empty packet 
                                countBlock++;
                                blockByte  = intToByteArray(countBlock); // may have to seperate up
                                byte[] emptyP = {0, DATAOP, blockByte [0], blockByte [1]};
                                packetSend = new DatagramPacket(emptyP, emptyP.length, iNetaddress, packetRecieve.getPort());
                                datagramSocket.send(packetSend);
                                allSent = true;

                            }
                        }
                        break;
                    case WRQOP: // send back an Ack for data to then be sent
                        stringFile = getFileName(inBuf);
                        validatorByte = WRQOP;
                        blockByte [0] = 0;
                        blockByte [1] = 0;//for first req sends a block number of 0 for WRQ
                                //System.out.println("received WRQ");

                        byte[] ack = {0, ACKOP, blockByte [0], blockByte [1]}; // sends an ack back meaning that data can then be sent through
                        packetSend = new DatagramPacket(ack, ack.length, iNetaddress, clientPort);
                        datagramSocket.send(packetSend);
                        break;
                    case DATAOP: // data is being send to write to file should reply with an ack

                        byte[] currentBlock = {inBuf[2], inBuf[3]};
                        expectedBlock++;
                        byte[] expectedBlockNo = intToByteArray(expectedBlock);// increment block number before it's compared
                        if (currentBlock[0] == expectedBlockNo[0] && currentBlock[1] == expectedBlockNo[1]) {
                            expectedBlock++;

                            sendBytes.write(packetRecieve.getData(), 4, packetRecieve.getLength() - 4); // gets the data out of the byte

                            sendCreatedAck(blockByte);
                        }
                        break;
                    default:
                }

                if (inBuf[1] == DATAOP && packetRecieve.getLength() < 516) { // if lastpacket of data
                    finalPacket = true;
                } else {
                    try {
                        datagramSocket.receive(packetRecieve);
                    } catch (SocketTimeoutException ste) {
                        datagramSocket.send(packetSend);
                    }
                }

            } 
        } catch (IOException e) {
            System.err.println(e);
        }

        if (validatorByte == WRQOP) { // writes data to a file if its a write request
            try {
                fileToOutputStream(sendBytes, stringFile);
            } catch (IOException sf) {
                System.err.println(sf);
            }
        }

        // Close the socket
        //System.out.println("Socket for this thread is closed");
        datagramSocket.close();
    }

    /**
     * Will read the content of the file into a byte array
     *
     * @param fileName - file you want to read the data in from
     * @return the byte array of the files content
     * @throws IOException
     */
    private byte[] getFile(String fileName) throws IOException {
        Path dir = Paths.get(fileName);

        byte[] fileContent = Files.readAllBytes(dir);
        return fileContent;
    }

    /**
     * This will get the name of the file out a request packet
     *
     * @param inBuf a requests buffer to extract the name from
     * @return the file name
     * @throws UnsupportedEncodingException
     */
    private String getFileName(byte[] inBuffer) throws UnsupportedEncodingException {
        int lengthofName = 0;
        for (int ctr = 2; inBuffer[ctr] != (byte) 0; ctr++) { // position ctr = 2 is where file name starts in byte data (inBuffer), count through
            lengthofName++;
        }

        byte[] fileName = new byte[lengthofName];

        for (int ctr1 = 0; ctr1 < lengthofName; ctr1++) { // use the count to 0 to get all the filename
            fileName[ctr1] = inBuffer[ctr1 + 2];
        }

        String fileNameA = new String(fileName); // make a string
        return fileNameA;
    }

    

    /**
     * Method takes an integer input representing a block number.
     * Creates a byte array of size 2 as blocks are never more than 2 bytes.
     * int - block is compared with identity element 0xFF using bitwise AND operation.
     * 0xFF represents eight ones in binary used to compare with int to return as a byte.
     *
     * @param block
     * @return byte array of the block number separated up
     */
    public static byte[] intToByteArray(int block) {
        byte[] com = new byte[2];
        com[0] = (byte) (block & 0xFF);
        com[1] = (byte) ((block >> 8) & 0xFF);
        return com;
    }

    /**
     * Takes byte array and file and wraps the array in the file
     *
     * @param bOut - byte array containing the data from the server
     * @param fileName - file to write it to
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void fileToOutputStream(ByteArrayOutputStream out, String fileName) throws FileNotFoundException, IOException {
        File file = new File(fileName);
        OutputStream outputStream = new FileOutputStream(fileName);
        outputStream.write(out.toByteArray());

    }

    /**
     * 
     * @param amount - amount of bytes needing to  be sent
     * @param countSent - amount of bytes already sent
     * @param returnAmount - int you want returned
     * @return amountLeft - int that is returned
     */
    public int amendAmountLeft(int amount, int countSent, int returnAmount) {
        if (((amount - countSent) / 512) >= 1) {
            returnAmount = 512;
        } else {
            returnAmount = (amount - countSent);
        }
        return returnAmount;
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
        byte[] initialData = {0, DATAOP, blockByte[0], blockByte[1]}; //first four bytes contain op code and block number
        byte[] data = new byte[dataToSend.length + initialData.length]; //array containing data size plus first four bytes

        System.arraycopy(initialData, 0, data, 0, initialData.length); // input initialData to data at position 0 in byte array
        System.arraycopy(dataToSend, 0, data, initialData.length, dataToSend.length); //input dataToSend to data at position where dataStart ended 

        packetSend = new DatagramPacket(data, data.length, iNetaddress, packetRecieve.getPort()); //insert into a packet

        datagramSocket.send(packetSend);
    }
    
    /**
     * input a message you want to be sent to the client when an error is found in the connection.
     * String message placed into byte array.
     * first four bytes represent opcode and error code
     * arrays amalgamated and sent over socket
     *
     * @param message - message included in error packet
     * @throws IOException
     */
    private void sendCreatedError(String message) throws IOException {
        byte[] byteMessage = message.getBytes("US-ASCII"); // send a message with the error

        byte[] initialError = {0, ERROROP, 0, 1}; //op code + error code

        byte[] error = new byte[initialError.length + byteMessage.length + 1]; //error is opcode + error code plus string message size

        System.arraycopy(initialError, 0, error, 0, initialError.length); // error1 placed into error at position 0 size error1
        System.arraycopy(byteMessage, 0, error, initialError.length, byteMessage.length); //message in bytes places into error starting where error1 finished

        error[initialError.length + byteMessage.length] = (byte) 0; //byte literal set final position to 0

        DatagramPacket errorPacket = new DatagramPacket(error, error.length, iNetaddress, packetRecieve.getPort());
        datagramSocket.send(errorPacket);
    }

}
