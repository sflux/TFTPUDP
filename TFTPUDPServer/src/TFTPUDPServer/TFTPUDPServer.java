package TFTPUDPServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;


public class TFTPUDPServer {

    int requestPort = 2044; //port handles rrq and wrq,would be set to 69 but due to validation issues had to be above 1024
    DatagramSocket datagramSocket;

    public static void main(String[] args) throws IOException {
        System.out.println("Server running, ready to receive requests from client.");
        TFTPUDPServer recieveRqst = new TFTPUDPServer();
        
    }

    /**
     * Socket port set to 2044. All requests are received on this port.
     * Upon packet arrival new thread is instantiated to deal with data transfers.
     *
     * @throws SocketException - error with socket
     * @throws IOException - no packet
     */
    public TFTPUDPServer() throws SocketException, IOException {
        this.datagramSocket = new DatagramSocket(requestPort);
        while (true) {
            byte[] packetData = new byte[516];
            DatagramPacket datagramPacket = new DatagramPacket(packetData, 516);
            //System.out.println("Server waiting for packet");
            datagramSocket.receive(datagramPacket);
            new Thread(new TFTPUDPServerThread(datagramPacket)).start();
            //System.out.println("New Thread Server Started");
        }
    }
}