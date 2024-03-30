package socs.network.node;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import ch.qos.logback.core.encoder.EchoEncoder;
import socs.network.message.SOSPFPacket;

public class LinkService implements Runnable {
    private Link link;

    public LinkService(Link link) {
        this.link = link;
    }

    public void sender(SOSPFPacket packet) {
        try {
            link.out.writeObject(packet);
        } catch (Exception e) {
            System.out.println("Problem sending packet through Link X"); // todo find name
            e.printStackTrace();
        }
    }

    public SOSPFPacket receiver () {
        SOSPFPacket incomingPacket = null;
        try {
            incomingPacket = (SOSPFPacket) link.in.readObject();
        } catch (Exception e) {
            System.out.println("Problem reading the incoming packet!");
            e.printStackTrace();
        }
        return incomingPacket;
    }

    public void run() {
        while (true) {
            SOSPFPacket incomingPacket = receiver();
            if (incomingPacket != null) {
                if (incomingPacket.sospfType == 1) {
                    // LinkState hello
                    System.out.println("We received a linkstate update request");
                } else if (incomingPacket.sospfType == 0) {
                    System.out.println("We recieved a hello message");
                } else {
                    System.out.println("not really important");
                }
            }
        }
    }
}
