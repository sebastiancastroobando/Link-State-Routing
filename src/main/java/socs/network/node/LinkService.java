package socs.network.node;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.net.SocketException;

import socs.network.message.SOSPFPacket;

public class LinkService implements Runnable {
    public Link link;

    public LinkService(Link link) {
        this.link = link;
    }

    public boolean send(SOSPFPacket packet) {
        boolean ret = false;
        try {
          link.out.writeObject(packet);
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
      if (this.link != null) {
        this.link.destroy();
        this.link = null;
      }
    }

    public void run() {
      while (!Thread.currentThread().isInterrupted()) {
        SOSPFPacket incomingPacket = receive();
        if (incomingPacket != null) {
          if(incomingPacket.sospfType == 3) {
            // print status for debugging
            // System.out.println("Status of " + link.sourceRouter.simulatedIPAddress + ": " + link.sourceRouter.status);
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
              this.send(helloPacket);
            } else if (link.targetRouter.status == RouterStatus.INIT) {
              // Inform user that the router has received a HELLO
              System.out.println("\nReceived HELLO from " + incomingPacket.srcIP);
              // Set router to TWO_WAY
              link.targetRouter.status = RouterStatus.TWO_WAY;
              link.sourceRouter.status = RouterStatus.TWO_WAY;
              // Inform user that the router is now in TWO_WAY
              System.out.print("Set " + incomingPacket.srcIP + " STATE to TWO_WAY\n>> ");
              // Send HELLO back
              SOSPFPacket helloPacket = new SOSPFPacket(link.sourceRouter.processIPAddress, link.sourceRouter.processPortNumber, link.sourceRouter.simulatedIPAddress, incomingPacket.srcIP);
              helloPacket.sospfType = 3;
              this.send(helloPacket);
            } else if (link.targetRouter.status == RouterStatus.TWO_WAY) {
              // Should we inform the user that the router is already in TWO_WAY?
            }
          }
        }
            /* 
            if (incomingPacket != null) {
                if (incomingPacket.sospfType == 2) {
                    // LinkState hello
                    System.out.println("We received a linkstate update request");
                } else if (incomingPacket.sospfType == 0) {
                    System.out.println("We recieved a hello message");
                } else if (incomingPacket.sospfType == 5) {
                  // QUIT
                  break;
                } else {
                    System.out.println("not really important");
                }
            } else {
              break;
            }
            */
      }
      closeConnection();
    }   
}
