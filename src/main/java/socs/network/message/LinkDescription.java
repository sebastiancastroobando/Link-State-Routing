package socs.network.message;

import java.io.Serializable;

public class LinkDescription implements Serializable {
  public String linkID; // simulated IP of destination? 
  public int portNum; // Port number (0-3) of the router (next hop?)

  public String toString() {
    return linkID + ","  + portNum;
  }
}
