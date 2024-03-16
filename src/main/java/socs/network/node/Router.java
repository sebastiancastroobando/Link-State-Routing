package socs.network.node;

import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
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
    // start a socket connection with the remote router
    // send HELLO packets
     // Start a socket connection with remote router
     Socket socket = null;
     InputStreamReader inputStreamReader = null;
     OutputStreamWriter outputStreamWriter = null;
 
     // To improve performance, we will use buffer to read and write. The idea is to read and write in bulk.
     BufferedReader bufferedReader = null;
     BufferedWriter bufferedWriter = null;
 
     try {
      // First we need to establish a connection with the remote router
      socket = new Socket(processIP, processPort);
      // Then we need to create a reader and writer to read and write to the socket
      inputStreamReader = new InputStreamReader(socket.getInputStream());
      outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
      bufferedReader = new BufferedReader(inputStreamReader);
      bufferedWriter = new BufferedWriter(outputStreamWriter);
 
      // Send the HELLO message to the remote router
      bufferedWriter.write("HELLO\n");
      // Flush the buffer to send the message
      bufferedWriter.flush();
 
      // Wait for the response from the remote router
      String response = bufferedReader.readLine(); // This will block until the remote router sends a message
 
      // If response is SUCCESS, then we can create a link between the routers
      if (response.equals("SUCCESS")) {
        // Create a link between the routers
        RouterDescription remoteRouter = new RouterDescription();
        remoteRouter.simulatedIPAddress = simulatedIP;
        remoteRouter.processIPAddress = processIP;
        remoteRouter.processPortNumber = processPort;

        // Create a link between the routers
        Link link = new Link(rd, remoteRouter);
        // Add the link to the ports array
        for (int i = 0; i < ports.length; i++) {
          if (ports[i] == null) {
            ports[i] = link;
            break;
          }
        }
      } 
      // REJECTED : close socket connection, but we are already closing the socket in the finally block?
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      // Close all the resources
      try {
        if (bufferedReader != null) {
          bufferedReader.close();
        }
        if (bufferedWriter != null) {
          bufferedWriter.close();
        }
        if (inputStreamReader != null) {
          inputStreamReader.close();
        }
        if (outputStreamWriter != null) {
          outputStreamWriter.close();
        }
        if (socket != null) {
          socket.close();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  /**
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   */
  private void requestHandler() {
    
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
