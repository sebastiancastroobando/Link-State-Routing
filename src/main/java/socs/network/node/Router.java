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
  // We need 4 ports for each router, so we need to keep track of the ports that are being used
  Thread[] portThreads = new Thread[4];


  // Helper methods -----------------------------------------------------------
  
  // get available port
  private int getAvailablePort() {
    // Since we keep a reference to the threads, could we actually check if the thread is alive?
    // then we could check if the port is available... trying to avoid race conditions...
    for (int i = 0; i < ports.length; i++) {
      if (ports[i] == null) {
        return i;
      }
    }
    return -1;
  }

  // --------------------------------------------------------------------------

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
    // Define the server socket
    ServerSocket serverSocket = null;
    Socket socket = null;

    // We will receive a SOSPFPacket from the remote router
    SOSPFPacket packet = null;

    try {
      serverSocket = new ServerSocket(rd.processPortNumber);
      while (true) {
        // Note that accept is "blocking" and will wait for a connection to be made.
        // only one connection can be made at a time.
        socket = serverSocket.accept();
        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        packet = (SOSPFPacket) objectInputStream.readObject();

        if (packet.sospfType == 0) {
          // First, check if the we have space in ports array
          int port = getAvailablePort();
          if (port == -1) {
            // No ports available means that we don't have to ask the user if they want to attach the remote router
            // send SOSPF packet with REJECT HELLO type
            SOSPFPacket rejectPacket = new SOSPFPacket();
            rejectPacket.sospfType = 2; // No need to put other fields?
            objectOutputStream.writeObject(rejectPacket);
            objectOutputStream.flush();
          } else {

            // Ask the user if they want to attach the remote router
            System.out.println("received HELLO from " + packet.srcIP + ";");
            
            // User has two options : Y or N, but ask again if the user inputs something else
            System.out.println("Do you accept this request? (Y/N)");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String response = br.readLine();
            while (!response.equals("Y") && !response.equals("N")) {
              System.out.println("Invalid input. Please enter Y or N");
              response = br.readLine();
            }
            // if user answers Y, then attach the remote router
            if (response.equals("Y")) {
              // if we accept and there is a port available, then we attach the remote router and this is a thread
              RouterDescription remoteRouter = new RouterDescription();
              remoteRouter.processIPAddress = packet.srcProcessIP;
              remoteRouter.processPortNumber = packet.srcProcessPort;
              remoteRouter.simulatedIPAddress = packet.srcIP;
              ports[port] = new Link(rd, remoteRouter);
              LinkService linkService = new LinkService(ports[port]);
              portThreads[port] = new Thread(linkService);

              // send SOSPF packet with ACCEPT HELLO type
              SOSPFPacket acceptPacket = new SOSPFPacket();
              acceptPacket.sospfType = 3; // We need to put the other fields, but this is just for testing
              objectOutputStream.writeObject(acceptPacket);
              objectOutputStream.flush();
            } else {
              System.out.println("You rejected the attach request;");
              // send SOSPF packet with REJECT HELLO type
              SOSPFPacket rejectPacket = new SOSPFPacket();
              rejectPacket.sospfType = 2; // No need to put other fields?
              objectOutputStream.writeObject(rejectPacket);
              objectOutputStream.flush();
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      
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
