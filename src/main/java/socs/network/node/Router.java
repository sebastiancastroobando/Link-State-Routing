package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;
import socs.network.message.LSA;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import java.util.Vector;

public class Router {

  protected LinkStateDatabase lsd;
  public Object LSDLock = new Object();

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  LinkService[] linkServices = new LinkService[4];
  // We need 4 ports for each router, so we need to keep track of the ports that are being used
  // Request handler thread
  Thread requestHandlerThread;
  // Thread propagationThread;


  private volatile String userAnswer = "";
  private volatile boolean attachmentInProgess = false;
  private Object attachLock = new Object();

  // Helper methods -----------------------------------------------------------

  private void closeSeveredConnections() {
    // link thread close themselves, so we just need to check if the link is null
    for (int i = 0; i < linkServices.length; i++) {
      if (linkServices[i] != null && linkServices[i].link == null) {
        linkServices[i] = null;
      }
    }
  }
  
  public void propagation(int ignorePort) {
    System.out.println("We got into propagation!");
  }

  // get available port
  private int getAvailablePort() {
    // First step is to close any severe connections
    closeSeveredConnections();

    // then we could check if the port is available... trying to avoid race conditions...
    int ret = -1;
    for (int i = 0; i < linkServices.length; i++) {
      if (linkServices[i] == null) {
        ret = i;
        break;
      }
    }
    return ret;
  }

  // add link method
  private void addLinkService(String processIP, short processPort, String simIP, int port, Socket socket, ObjectInputStream in, ObjectOutputStream out) {
    RouterDescription remoteRouter = new RouterDescription();
    remoteRouter.processIPAddress = processIP;
    remoteRouter.processPortNumber = processPort;
    remoteRouter.simulatedIPAddress = simIP;

    linkServices[port] = new LinkService(new Link(rd, remoteRouter, socket, in, out), lsd, LSDLock, this);
  }

  // updates linkServices if need be, upon get
  private LinkService getLinkService(int index) {
    LinkService ls = linkServices[index];
    if (ls != null && ls.link == null) {
      linkServices[index] = null;
      ls = null;
    }
    return ls;
  }

  // --------------------------------------------------------------------------
  public Router(){}

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processIPAddress = config.getString("socs.network.router.processIP");
    rd.processPortNumber = Short.parseShort(config.getString("socs.network.router.processPort"));
    lsd = new LinkStateDatabase(rd);
    //System.out.println("Simulated IP: " + rd.simulatedIPAddress);
    System.out.println("To attach to this router, run: attach " + rd.processIPAddress + " " + rd.processPortNumber + " " + rd.simulatedIPAddress);
    //System.out.println("Process Port Number: " + rd.processPortNumber);
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
    String shortestPath = lsd.getShortestPath(destinationIP);
    if (shortestPath == null) {
      System.out.println("Destination router does not exist");
    } else {
      System.out.println(shortestPath);
    }
  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {
    // Check if the port number is valid
    if (portNumber < 0 || portNumber >= linkServices.length) {
      System.out.println("DISCONNECT ERROR: Invalid port number;");
      return;
    }
    // Check if the link service is initialized
    if (linkServices[portNumber] == null) {
      System.out.println("DISCONNECT ERROR: No link service at port " + portNumber + ";");
      return;
    }

    System.out.println("Disconnecting from " + linkServices[portNumber].link.targetRouter.simulatedIPAddress + ";");
    
    // Send QUIT message to the target router
    SOSPFPacket quitPacket = new SOSPFPacket();
    quitPacket.sospfType = 5; // QUIT type
    quitPacket.srcIP = rd.simulatedIPAddress;
    linkServices[portNumber].send(quitPacket);
    // Close the connection
    linkServices[portNumber].stopThread();
    linkServices[portNumber].closeConnection();
    linkServices[portNumber] = null;
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private int processAttach(String processIP, short processPort, String simulatedIP) {
    // Check if the simulated IP is the same as the current router
    if (simulatedIP.equals(rd.simulatedIPAddress)) {
      System.out.println("ATTACHMENT ERROR: Can't attach the router to itself;");
      return -1;
    }

    // First create packet to send to the remote router, packet type 0 is an attach request
    SOSPFPacket attachRequestPacket = new SOSPFPacket(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress, simulatedIP);
    
    // Check if we have available ports 
    int availablePort = getAvailablePort();
    if (availablePort == -1) {
        System.out.println("ATTACHMENT ERROR: No available ports;");
        return -1;
    }

    try {
      // Create a socket to connect to target router (processIP, processPort)
      Socket socket = new Socket(processIP, processPort);
    
      // Create input and output streams
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

      // Send the attach request packet
      out.writeObject(attachRequestPacket);
      out.flush();

      // Wait for response from the remote router, readObject will block until it receives something
      // This should be a SOSPFPacket with type 2 or 3 (ACCEPTED or REJECTED respectively)
      SOSPFPacket msgFromServer = (SOSPFPacket) in.readObject();
      if (msgFromServer.sospfType == 1) {
        // Attach request accepted
        addLinkService(processIP, processPort, simulatedIP, availablePort, socket, in, out);
        // Start the link service thread to handle incoming packets
        linkServices[availablePort].startThread();
        System.out.println("Your attach request has been ACCEPTED;");
        return availablePort;
      } 
      else if (msgFromServer.sospfType == 2) {
        // Attach request rejected
        in.close();
        out.close();
        socket.close();
        System.out.println("Your attach request has been REJECTED;");
        return -1;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return -1;
  }


  /**
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   * @description: Send ACCEPT or REJECT response to the attach request from the remote router. 
   */
  private void requestHandler() {
    // Define the server socket that will be listening for incoming connections requests
    ServerSocket serverSocket = null;

    // Packet to store the incoming request
    SOSPFPacket requestPacket = null;

    try {
      // Create the server socket
      serverSocket = new ServerSocket(rd.processPortNumber);

      while (!Thread.currentThread().isInterrupted()) {
        Socket socket = null;
        // Set a timeout for the server socket
        serverSocket.setSoTimeout(1000);
        while (socket == null) {
          try {
            // accept is a blocking call, it will wait until a connection is made
            // if the timeout is reached, a SocketTimeoutException is thrown 
            // and the loop will continue
            socket = serverSocket.accept();
          } catch (SocketTimeoutException e) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Server socket interrupted, closing...");
                serverSocket.close();
                return;
            }
            continue;
          }
        }
        // Create input and output streams for the socket
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        // Read the incoming packet
        requestPacket = (SOSPFPacket) in.readObject();

        System.out.println("\nReceived attach request from " + requestPacket.srcIP + ";");

        int availablePort = getAvailablePort();
        if (availablePort == -1) {
          // Inform the user that there are no available ports
          System.out.print("Rejecting attach request from " + requestPacket.srcIP + " due to no available ports;\n>> ");
          // No available ports, reject the request
          SOSPFPacket rejectPacket = new SOSPFPacket();
          rejectPacket.sospfType = 3;
          out.writeObject(rejectPacket);
          out.flush();
          // Close the streams and the socket
          in.close();
          out.close();
          socket.close();
        } 
        else {
          System.out.print("Do you accept this request? (Y/N)\n>> ");
          synchronized(attachLock) {
            attachmentInProgess = true;
            attachLock.wait();
          }
          // if user answers Y, then attach the remote router
          if (userAnswer.equals("Y")) {
            // User accepted the request to attach

            // Create a link service for the new connection
            addLinkService(requestPacket.srcProcessIP, requestPacket.srcProcessPort, requestPacket.srcIP, availablePort, socket, in, out);
            linkServices[availablePort].startThread();

            // Start the link service thread to handle incoming packets
            
            
            // send SOSPF packet with ACCEPT Attach type
            SOSPFPacket acceptPacket = new SOSPFPacket();
            acceptPacket.sospfType = 1; // We need to put the other fields, but this is just for testing
            out.writeObject(acceptPacket);
            out.flush();
          } 
          else {
            // User rejected the request to attach, send REJECT type

            // Send SOSPF packet with REJECT type
            SOSPFPacket rejectPacket = new SOSPFPacket();
            rejectPacket.sospfType = 2; 
            out.writeObject(rejectPacket);
            out.flush();
            // Close the streams and the socket
            in.close();
            out.close();
            socket.close();
          } 
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // TODO : function name is misleading, should be startRequestHandler. Also, is there another way to start the thread?
  public void start() {
    // Start the request handler thread
    requestHandlerThread = new Thread(new Runnable() {
      public void run() {
        requestHandler();
      }
      
    });

    requestHandlerThread.start();

  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    // initialize LSA update packet
    // destination IP will change when sending packet
    SOSPFPacket LSAUpdatePacket = new SOSPFPacket(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress, null);
    LSAUpdatePacket.sospfType = 6; // LSAUPDATE type

    // String to know all the destinations we are multicasting to
    String destinations = "";

    // Send HELLO message through all initialized link services
    for (int i = 0; i < linkServices.length; i++) {
      if (linkServices[i] == null) {
        // Link service at this port is not initialized
        continue;
      }
      // Maybe we do some checks here to see if the link service is alive and link

      // Create a new HELLO packet
      SOSPFPacket helloPacket = new SOSPFPacket(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress, linkServices[i].link.targetRouter.simulatedIPAddress);
      helloPacket.sospfType = 3; // HELLO type

      // If the link is already set to TWO_WAY, then don't change the status to INIT
      if (linkServices[i].link.targetRouter.status != RouterStatus.TWO_WAY) {
        // Set to INIT so we expect a hello back and set to TWO_WAY
        linkServices[i].link.targetRouter.status = RouterStatus.INIT;
      }
      // Send the HELLO packet, first is to confirm two way communication from this router
      linkServices[i].send(helloPacket);

      while (linkServices[i].link.sourceRouter.status != RouterStatus.TWO_WAY) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          
        }
      }

      // Add the link to the self LSA in the link state database
      lsd.addLinkToSelfLSA(linkServices[i], i);

      // Add the destination to the destinations string
      destinations += linkServices[i].getConnectedRouterSimluatedIP() + "; ";
    }

    

    // multicast LSA update packet to all neighbors
    System.out.println("\nMulticasting LSA update to: " + destinations + "\n>> ");
    for (int i = 0; i < linkServices.length; i++) {
      if (linkServices[i] == null) {
        continue;
      }
      // Set the destination IP to the connected router's simulated IP
      LSAUpdatePacket.dstIP = linkServices[i].getConnectedRouterSimluatedIP();
      LSAUpdatePacket.neighborID = linkServices[i].getConnectedRouterSimluatedIP();

      // Get vector of LSA's from the link state database
      LSAUpdatePacket.lsaArray = lsd.getLSAVector();

      linkServices[i].send(LSAUpdatePacket);
    }
  }

  

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort, String simulatedIP) {
    // check if we are trying to connect to ourselves
    if (simulatedIP.equals(rd.simulatedIPAddress)) {
      System.out.println("Connection failed: Can't connect to itself;");
      return;
    }

    // check if we have available ports
    int availablePort = getAvailablePort();
    if (availablePort == -1) {
      System.out.println("Connection failed: No available ports;");
      return;
    }

    // First attach the router
    System.out.println("Attaching to " + simulatedIP + ". Waiting for response...");
    int portUsed = processAttach(processIP, processPort, simulatedIP);
    if (portUsed == -1) {
      System.out.println("Connection failed: No available ports;");
      return;
    }
    // Then start the router
    System.out.println("Starting link connection...");
    // Create a new HELLO packet
    SOSPFPacket helloPacket = new SOSPFPacket(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress, linkServices[portUsed].link.targetRouter.simulatedIPAddress);
    helloPacket.sospfType = 3; // HELLO type

    // If the link is already set to TWO_WAY, then don't change the status to INIT
    if (linkServices[portUsed].link.targetRouter.status != RouterStatus.TWO_WAY) {
      // Set to INIT so we expect a hello back and set to TWO_WAY
      linkServices[portUsed].link.targetRouter.status = RouterStatus.INIT;
    }
    // Send the HELLO packet, first is to confirm two way communication from this router
    linkServices[portUsed].send(helloPacket);
  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    // Output the list of all the neighbors (set to TWO_WAY)
    for (int i = 0; i < linkServices.length; i++) {
      LinkService cur_linkserv = getLinkService(i);
      if (cur_linkserv == null) {
        System.out.println("Port " + i + " : (Free)");
        continue;
      }
      // if link status is NULL, it means it's attach but not yet initialized
      if (cur_linkserv.link.targetRouter.status == null) {
        System.out.println("Port " + i + " : " + cur_linkserv.link.targetRouter.simulatedIPAddress + " (Attached, but not initialized)");
        continue;
      } else {
        System.out.println("Port " + i + " : " + cur_linkserv.link.targetRouter.simulatedIPAddress + " (" + cur_linkserv.link.targetRouter.status + ")");
      }
    }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {
    // TODO : Send Link State Update to all neighbors informing them that this router is going down

    // First make sure that that servered connections are closed
    closeSeveredConnections();
    // Close all connections using the processDisconnect method for each port with a link service
    for (int i = 0; i < linkServices.length; i++) {
      if (linkServices[i] != null) {
        processDisconnect((short) i);
      }
    }

    // Close the request handler thread
    try{
      requestHandlerThread.interrupt();
      requestHandlerThread.join();
      // propagationThread.interrupt();
      // propagationThread.join();
    } catch (InterruptedException e) {
        return;
    }
    // Close the router
    System.exit(0);
  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
         // We need to check if the user is trying to attach a router
         if (attachmentInProgess) {
          synchronized(attachLock) {
            if (command.equals("Y")) {
              System.out.print("You accepted the attach request;\n");
              userAnswer = command;
              attachLock.notifyAll();
              attachmentInProgess = false;
            } else if (command.equals("N")) {
              userAnswer = command;
              attachLock.notifyAll();
              System.out.print("You rejected the attach request;\n");
              attachmentInProgess = false;
            } else {
              System.out.print("Invalid argument. Please answer with \"Y\" or \"N\"\n");
            }
          }
          System.out.print(">> ");
          command = br.readLine();
          continue;
        }
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
          break;
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3] );
        } else if (command.equals("start")) {
          processStart();
        } else if (command.startsWith("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3]);
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        }
        else {
          System.out.println("Invalid argument");
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
