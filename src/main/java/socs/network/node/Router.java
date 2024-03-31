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
import java.net.SocketTimeoutException;
import java.io.InputStream;

public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  LinkService[] linkServices = new LinkService[4];
  // We need 4 ports for each router, so we need to keep track of the ports that are being used
  Thread[] portThreads = new Thread[4];
  // Request handler thread
  Thread requestHandlerThread;

  private volatile String userAnswer = "";
  private volatile boolean attachmentInProgess = false;
  private Object attachLock = new Object();

  // Helper methods -----------------------------------------------------------
  
  // get available port
  private int getAvailablePort() {
    // Since we keep a reference to the threads, could we actually check if the thread is alive?
    // then we could check if the port is available... trying to avoid race conditions...
    int ret = -1;
    for (int i = 0; i < linkServices.length; i++) {
      if (linkServices[i] == null) {
        ret = i;
        break;
      } else if (linkServices[i].link == null) {
        // TODO : feels weird to have to check if link is null here, let's see if there is an alternative
        linkServices[i] = null;
        ret = i;
        break;
      }
    }
    // getAvailablePort should not join threads, its purpose is to check if a port is available
    if (ret != -1 && portThreads[ret] != null) {
      try {
        portThreads[ret].interrupt();
        portThreads[ret].join();
      } catch (InterruptedException e) {
      }
    }
    return ret;
  }

  // add link method
  private LinkService addLinkService(String processIP, short processPort, String simIP, int port, Socket socket, ObjectInputStream in, ObjectOutputStream out) {
    RouterDescription remoteRouter = new RouterDescription();
    remoteRouter.processIPAddress = processIP;
    remoteRouter.processPortNumber = processPort;
    remoteRouter.simulatedIPAddress = simIP;

    linkServices[port] = new LinkService(new Link(rd, remoteRouter, socket, in, out));
    return linkServices[port];
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
    // First create packet to send to the remote router, packet type 0 is an attach request
    SOSPFPacket attachRequestPacket = new SOSPFPacket(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress, simulatedIP);
    
    // Check if we have available ports 
    int port = getAvailablePort();
    if (port == -1) {
        System.out.println("Can't connect to more routers");
        return;
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
        addLinkService(processIP, processPort, simulatedIP, port, socket, in, out);
        System.out.println("Your attach request has been ACCEPTED;");
      } 
      else if (msgFromServer.sospfType == 2) {
        // Attach request rejected
        in.close();
        out.close();
        socket.close();
        System.out.println("Your attach request has been REJECTED;");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
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
          // No available ports, reject the request
          SOSPFPacket rejectPacket = new SOSPFPacket();
          rejectPacket.sospfType = 2;
          out.writeObject(rejectPacket);
          out.flush();
          // Close the streams and the socket
          socket.getInputStream().close();
          socket.getOutputStream().close();
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
            LinkService linkService = addLinkService(requestPacket.srcProcessIP, requestPacket.srcProcessPort, requestPacket.srcIP, availablePort, socket, in, out);

            // Start the link service thread to handle incoming packets
            portThreads[availablePort] = new Thread(linkService);
            portThreads[availablePort].start();
            
            // send SOSPF packet with ACCEPT HELLO type
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

  public void start() {
    // Start the request handler thread
    requestHandlerThread = new Thread(new Runnable() {
      public void run() {
        requestHandler();
      }
    });
    // Here we can do some checks to see if the thread is alive and more!
    requestHandlerThread.start();
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    // First step, check the links available
    for (int i = 0; i < linkServices.length; i++) {
      LinkService cur_linkserv = getLinkService(i);
      if (cur_linkserv == null) {
        continue;
      }
      SOSPFPacket packet = new SOSPFPacket();
      packet.sospfType = 0;
      boolean success = cur_linkserv.send(packet);
      if (!success) {
        cur_linkserv.closeConnection();
        linkServices[i] = null;
      }
    }
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
    try {
        for (int i = 0; i < linkServices.length; i++) {
          LinkService cur_linkserv = getLinkService(i);
          if (cur_linkserv == null) {
            continue;
          }
          SOSPFPacket packet = new SOSPFPacket();
          // send QUIT message - this router is shutting down
          packet.sospfType = 4;
          cur_linkserv.send(packet);
          cur_linkserv.closeConnection();
          linkServices[i] = null;
        }

        for (int i = 0; i < portThreads.length; i++) {
          Thread cur_thread = portThreads[i];
          if (cur_thread == null) {
            continue;
          }
          cur_thread.interrupt();
          cur_thread.join();
        }
        requestHandlerThread.interrupt();
        requestHandlerThread.join();
    } catch (InterruptedException e) {
        return;
    }
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
              System.out.print("Invalid argument\n");
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
        } else if (command.equals("connect ")) {
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
