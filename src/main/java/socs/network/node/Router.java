package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.ServerSocket;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processIPAddress = config.getString("socs.network.router.processIP");
    rd.processPortNumber = Short.parseShort(config.getString("socs.network.router.processPort"));
    lsd = new LinkStateDatabase(rd);
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort, String simulatedIP) {
    // First create packet to send to the remote router
    SOSPFPacket packet = new SOSPFPacket();
    
    packet.srcProcessIP = rd.processIPAddress;
    packet.srcProcessPort = rd.processPortNumber;
    
    packet.srcIP = rd.processIPAddress;
    packet.dstIP = processIP;
    
    packet.sospfType = 0;
    packet.routerID = rd.simulatedIPAddress;
    packet.neighborID = simulatedIP;

    // Socket and buffer definition
    Socket socket = null;
    InputStreamReader inputStreamReader = null;
    OutputStreamWriter outputStreamWriter = null;
    BufferedReader bufferedReader = null;
    BufferedWriter bufferedWriter = null;

    try {
      // IP address of server (remote router) and TCP port
      socket = new Socket(processIP, processPort);

      // Read from the server and output to the server
      inputStreamReader = new InputStreamReader((socket.getInputStream()));
      outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());

      bufferedReader = new BufferedReader(inputStreamReader);
      bufferedWriter = new BufferedWriter(outputStreamWriter);

      

      while(true) {
        // Server is the remote (or neighbor) router
        String msgFromServer = bufferedReader.readLine();
      }



    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      // TODO
    }
  }


  /**
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   */
  private void requestHandler() {
    // define the server socket
    ServerSocket serverSocket = null;
    Socket socket = null;

    try {
      serverSocket = new ServerSocket(rd.processPortNumber);
      while(true) {
        socket = serverSocket.accept();
        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
        SOSPFPacket packet = (SOSPFPacket) objectInputStream.readObject();
        
        // We need to check if the SOSPFPacket is a of type HELLO, if so then prompt the user to accept or reject the request
        if(packet.sospfType == 0) {
          System.out.println("received HELLO from " + packet.srcIP);
          System.out.println("Do you accept this request? (Y/N)");
          
          BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
          String response = br.readLine();
          if(response.equals("Y") || response.equals("y")){
            // Send a HELLO message back to the remote router
            SOSPFPacket helloPacket = new SOSPFPacket();
            helloPacket.srcProcessIP = rd.processIPAddress;
            helloPacket.srcProcessPort = rd.processPortNumber;
            helloPacket.srcIP = rd.processIPAddress;
            helloPacket.dstIP = packet.srcIP;
            helloPacket.sospfType = 0;
            helloPacket.routerID = rd.simulatedIPAddress;
            helloPacket.neighborID = packet.srcIP;

            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(helloPacket);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      System.out.println("Closing the server socket");
    }
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort, String simulatedIP) {
   
  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {

  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3] );
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3]);
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
