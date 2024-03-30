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
  Link[] ports = new Link[4];
  // We need 4 ports for each router, so we need to keep track of the ports that are being used
  Thread[] portThreads = new Thread[4];
  // Request handler thread
  Thread requestHandlerThread;
  // Array of active sockets
  Socket[] sockets = new Socket[4];

  private volatile String userAnswer = "";
  private volatile boolean attachmentInProgess = false;
  private Object attachLock = new Object();

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

  // add link method
  private void addLink(String processIP, short processPort, String simIP, int port) {
    RouterDescription remoteRouter = new RouterDescription();
    remoteRouter.processIPAddress = processIP;
    remoteRouter.processPortNumber = processPort;
    remoteRouter.simulatedIPAddress = simIP;

    ports[port] = new Link(rd, remoteRouter);
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
    /*Socket socket = null;
    InputStreamReader inputStreamReader = null;
    OutputStreamWriter outputStreamWriter = null;
    BufferedReader bufferedReader = null;
    BufferedWriter bufferedWriter = null;*/

    int port = getAvailablePort();
    if (port == -1) {
        System.out.println("Can't connect to more routers");
        return;
    }

    try {
      // IP address of server (remote router) and TCP port
      Socket socket = new Socket(processIP, processPort);
      //System.out.println("ACCEPTED");
      // Read from the server and output to the server
      InputStream input = socket.getInputStream();
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
      ObjectInputStream objectInputStream = new ObjectInputStream(input);

      objectOutputStream.writeObject(packet);
      objectOutputStream.flush();
      //ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
      //System.out.println("TWO");

      //bufferedReader = new BufferedReader(inputStreamReader);
      //bufferedWriter = new BufferedWriter(outputStreamWriter);

      SOSPFPacket msgFromServer = (SOSPFPacket) objectInputStream.readObject();
      if (msgFromServer.sospfType == 2) {
        // REJECTED
        System.out.println("Connection rejected");
        objectInputStream.close();
        objectOutputStream.close();
        socket.close();
      } 
      else if (msgFromServer.sospfType == 3) {
        // ACCEPTED
        addLink(processIP, processPort, simulatedIP, port);
        System.out.println("ACCEPTED");

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
    //Socket socket = null;

    // We will receive a SOSPFPacket from the remote router
    SOSPFPacket packet = null;

    try {
      serverSocket = new ServerSocket(rd.processPortNumber);
      while (!Thread.currentThread().isInterrupted()) {
        Socket socket = null;
        // Note that accept is "blocking" and will wait for a connection to be made.
        // only one connection can be made at a time.
        serverSocket.setSoTimeout(1000);
        while (socket == null) {
            try {
                socket = serverSocket.accept();
            } catch (SocketTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("ROUTER NOT ONLINE");
                    serverSocket.close();
                    return;
                }
                continue;
            }
        }
        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        packet = (SOSPFPacket) objectInputStream.readObject();


        System.out.println("\nReceived attach request from " + packet.srcIP + ";");

        int port = getAvailablePort();
        if (port == -1) {
          // No ports available means that we don't have to ask the user if they want to attach the remote router
          // send SOSPF packet with REJECT HELLO type
          SOSPFPacket rejectPacket = new SOSPFPacket();
          rejectPacket.sospfType = 2; // No need to put other fields?
          objectOutputStream.writeObject(rejectPacket);
          objectOutputStream.flush();
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
            // if we accept and there is a port available, then we attach the remote router and this is a thread
            /*RouterDescription remoteRouter = new RouterDescription();
            remoteRouter.processIPAddress = packet.srcProcessIP;
            remoteRouter.processPortNumber = packet.srcProcessPort;
            remoteRouter.simulatedIPAddress = packet.srcIP; 
            
            ports[port] = new Link(rd, remoteRouter); */
            addLink(packet.srcProcessIP, packet.srcProcessPort, packet.srcIP, port);
            //LinkService linkService = new LinkService(ports[port]);
            //portThreads[port] = new Thread(linkService);

            // send SOSPF packet with ACCEPT HELLO type
            SOSPFPacket acceptPacket = new SOSPFPacket();
            acceptPacket.sospfType = 3; // We need to put the other fields, but this is just for testing
            objectOutputStream.writeObject(acceptPacket);
            objectOutputStream.flush();
          } 
          else {
            // send SOSPF packet with REJECT HELLO type
            SOSPFPacket rejectPacket = new SOSPFPacket();
            rejectPacket.sospfType = 2; // No need to put other fields?
            objectOutputStream.writeObject(rejectPacket);
            objectOutputStream.flush();
          } 
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      
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
