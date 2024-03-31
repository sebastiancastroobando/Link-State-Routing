package socs.network.message;

import java.io.*;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

public class SOSPFPacket implements Serializable {

  //for inter-process communication
  public String srcProcessIP;
  public short srcProcessPort;

  //simulated IP address
  public String srcIP;
  public String dstIP;

  //common header
  /**
   * sosfType = 0: Attach Request
   * sosfType = 1: Accept Attach
   * sosfType = 2: REJECT Attach
   * sosfType = 2: HELLO
   * sosfType = 3: ACCEPT HELLO
   * sosfType = 4: QUIT
   */
  public short sospfType; 
  public String routerID;

  //used by HELLO message to identify the sender of the message
  //e.g. when router A sends HELLO to its neighbor, it has to fill this field with its own
  //simulated IP address
  public String neighborID; //neighbor's simulated IP address

  //used by LSAUPDATE
  public Vector<LSA> lsaArray = null;

  public SOSPFPacket() {}

  public SOSPFPacket(String srcProcessIP, short srcProcessPort, String srcIP, String dstIP) {
    this.srcProcessIP = srcProcessIP;
    this.srcProcessPort = srcProcessPort;
    this.srcIP = srcIP;
    this.dstIP = dstIP;
    this.routerID = srcIP;
    this.neighborID = dstIP;
  }
}
