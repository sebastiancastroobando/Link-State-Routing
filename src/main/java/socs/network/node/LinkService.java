package socs.network.node;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.EOFException;
import java.io.OptionalDataException;
import java.net.Socket;
import java.net.SocketException;

import socs.network.message.SOSPFPacket;

public class LinkService {
  // Link that the service is running on
  public Link link;
  // Router that the service is running on
  public Router router;

  // Link State Database
  public LinkStateDatabase lsd;
  public Object LSDLock;

  // Lock for sending packets
  private Object sendLock = new Object();

  public Thread linkServiceThread;

  public LinkService(Link link, Router router) {
      this.link = link;
      this.lsd = router.lsd;
      this.LSDLock = router.LSDLock;
      this.router = router;
  }

  public String getTargetIP() {
    return this.link.targetRouter.simulatedIPAddress;
  }

  public boolean send(SOSPFPacket packet) {
      boolean ret = false;
      try {
        synchronized(sendLock) {
          link.out.writeObject(packet);
          link.out.flush();
        }
      } catch (SocketException e) {
        System.out.println("Connection severed, in LinkService.send()");
      } catch (Exception e) {
          System.out.println("Problem sending packet through Link X"); // todo find name
          e.printStackTrace();
      }
      return ret;
  }

  public SOSPFPacket receive() {
      SOSPFPacket incomingPacket = null;
      try {
        incomingPacket = (SOSPFPacket) link.in.readObject();
      } catch (SocketException e) {
        return null;
      } catch (EOFException e) {
        return null;
      } catch (Exception e) {
        System.out.println("Something went wrong when reading incoming packet");
        e.printStackTrace();
      }
      return incomingPacket;
  }

  public void closeConnection() {
    // The thread is blocked on the receive() method, so we need to close the connection
    if (this.link != null) {
      this.link.destroy();
      this.link = null;
    }
  }

  // Start thread on the link service
  public void startThread() {
    LinkServiceThread service = new LinkServiceThread();
    this.linkServiceThread = new Thread(service);
    this.linkServiceThread.start();
  }
  
  // Stop thread on the link service
  public void stopThread() {
    this.linkServiceThread.interrupt();
    try {
      this.linkServiceThread.join();
    } catch (InterruptedException e) {
      return;
    } catch (Exception e) {
      System.out.println("Something went wrong while stopping the link service thread;");
    }
  }

  class LinkServiceThread implements Runnable {
    public void run() {
      while (!Thread.currentThread().isInterrupted() && link != null) {
        SOSPFPacket incomingPacket = receive();
        if (incomingPacket != null) {
          if(incomingPacket.sospfType == 3) {
            // print status for debugging
            if(link.targetRouter.status == null) {
              // Inform user that the router has received a HELLO
              System.out.println("\nReceived HELLO from " + incomingPacket.srcIP);
              // Set the router to INIT
              link.targetRouter.status = RouterStatus.INIT;
              link.sourceRouter.status = RouterStatus.INIT;
              // Inform user that the router is now in INIT
              System.out.println("Set " + incomingPacket.srcIP + " STATE to INIT");
              // Send a HELLO back
              SOSPFPacket helloPacket = new SOSPFPacket(link.sourceRouter.processIPAddress, link.sourceRouter.processPortNumber, link.sourceRouter.simulatedIPAddress, incomingPacket.srcIP);
              helloPacket.sospfType = 3;
              send(helloPacket);
            } else if (link.targetRouter.status == RouterStatus.INIT) {
              // Inform user that the router has received a HELLO
              System.out.println("\nReceived HELLO from " + incomingPacket.srcIP);
              // Set router to TWO_WAY
              link.targetRouter.status = RouterStatus.TWO_WAY;
              //link.sourceRouter.status = RouterStatus.TWO_WAY;
              // Inform user that the router is now in TWO_WAY
              System.out.print("Set " + incomingPacket.srcIP + " STATE to TWO_WAY\n>> ");
              // Send HELLO back
              SOSPFPacket helloPacket = new SOSPFPacket(link.sourceRouter.processIPAddress, link.sourceRouter.processPortNumber, link.sourceRouter.simulatedIPAddress, incomingPacket.srcIP);
              helloPacket.sospfType = 3;
              send(helloPacket);
            } else if (link.targetRouter.status == RouterStatus.TWO_WAY) {
              // Should we inform the user that the router is already in TWO_WAY?
              link.sourceRouter.status = RouterStatus.TWO_WAY;
            }
          } else if (incomingPacket.sospfType == 5) {
            // Inform user that the router is quitting
            System.out.print("\nReceived QUIT from " + incomingPacket.srcIP + ". Closing connection.\n>> ");
            // The thread has to close itself
            closeConnection();
            return;
          } else if (incomingPacket.sospfType == 6) {
            // Inform user that the router has received an LSA update packet
            System.out.print("\nReceived LSA update from " + incomingPacket.srcIP + "\n>> ");
            
            // Before adding entry, we need to lock the LSD. 
            SOSPFPacket propagatePacket;
            synchronized(LSDLock) {
              propagatePacket = lsd.addEntries(incomingPacket);
            }

            // We wouldn't send the LSAUPDATE here...
            int ret = router.propagation(propagatePacket);
            if (ret > 0) {
              System.out.print(lsd.toString() + "\n>> ");
            }
          } else {
            // We closed the connection, print for debugging
            System.out.println("Connection severed");
          }
        } else {
          // If we receive a null packet, we should close the connection
          System.out.print("\nConnection severed with " + link.targetRouter.simulatedIPAddress + ". Closing connection.\n>> ");
          closeConnection();

          // TODO - LSA update? But this is extra stuff since we would use quit function
          return;
        }
      }
      closeConnection();
    }
  }
}
