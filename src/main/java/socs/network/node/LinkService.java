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
          if(incomingPacket.sospfType == 2) {
            System.out.println("\nReceived HELLO from " + incomingPacket.srcIP);
            // When we receive the HELLO, the router can either be null, INIT, or TWO_WAY
            // If the router is NULL, we need to set the router to INIT
            if(link.targetRouter.status == null) {
              // Set the router to INIT
              link.targetRouter.status = RouterStatus.INIT;
              // Inform user that the router is now in INIT
              System.out.println("Set " + incomingPacket.srcIP + " STATE to INIT");
              // Send a HELLO back
              SOSPFPacket helloPacket = new SOSPFPacket(link.sourceRouter.processIPAddress, link.sourceRouter.processPortNumber, link.sourceRouter.simulatedIPAddress, incomingPacket.srcIP);
              helloPacket.sospfType = 2;
              this.send(helloPacket);
            } else if (link.targetRouter.status == RouterStatus.INIT) {
              // If the router is already in INIT, we need to set the router to TWO_WAY
              link.targetRouter.status = RouterStatus.TWO_WAY;
              // Inform user that the router is now in TWO_WAY
              System.out.print("Set " + incomingPacket.srcIP + " STATE to TWO_WAY\n>> ");
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
