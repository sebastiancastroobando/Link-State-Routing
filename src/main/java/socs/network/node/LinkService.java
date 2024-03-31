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
            /*if (this.link == null) {
              break;
            }*/
            SOSPFPacket incomingPacket = receive();
            if (incomingPacket != null) {
                if (incomingPacket.sospfType == 1) {
                    // LinkState hello
                    System.out.println("We received a linkstate update request");
                } else if (incomingPacket.sospfType == 0) {
                    System.out.println("We recieved a hello message");
                } else if (incomingPacket.sospfType == 4) {
                  // QUIT
                  break;
                } else {
                    System.out.println("not really important");
                }
            } else {
              break;
            }
        }
        closeConnection();
    }
}
