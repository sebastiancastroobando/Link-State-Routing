package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  public Vector<LSA> getLSAVector() {
    Vector<LSA> lsaVector = new Vector<LSA>();
    for (LSA lsa : _store.values()) {
      lsaVector.add(lsa);
    }
    return lsaVector;
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  public String getShortestPath(String destinationIP) {
    /**
     * Our LSD holds an LSA for each router in the network. Each LSA contains the direct 
     * neighbors of the router. We use this information to find the shortest path to the
     * destinationIP.
     * 
     * This would use Dijkstra's algorithm. However, there are no weights on the edges. 
     * Thus, Dijkstra's algorithm would be the same as BFS.
     */
    // first check if the destinationIP is in the LSD
    if (!_store.containsKey(destinationIP)) {
      return "Destination IP not found in LSD. Try again later;";
    }

    // Check if we are detecting ourselves
    if (destinationIP.equals(rd.simulatedIPAddress)) {
      return "Destination IP is the same as the router's IP. No path needed;";
    }

    HashMap<String, String> prev = new HashMap<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(rd.simulatedIPAddress); // Start BFS from the router's simulated IP

    while (!queue.isEmpty()) {
        String currentNode = queue.poll();
        LSA currentLSA = _store.get(currentNode);

        for (LinkDescription ld : currentLSA.links) {
            String neighbor = ld.linkID;
            // Check if the neighbor has not been visited
            if (!prev.containsKey(neighbor) && !neighbor.equals(rd.simulatedIPAddress)) {
                queue.add(neighbor);
                prev.put(neighbor, currentNode); // Mark as visited by adding to prev

                if (neighbor.equals(destinationIP)) {
                    // Destination found, exit the loop
                    queue.clear();
                    break;
                }
            }
        }
    }

    LinkedList<String> path = new LinkedList<>();
    for (String at = destinationIP; at != null; at = prev.get(at)) {
        path.addFirst(at);
    }

    return String.join(" -> ", path);
  }

  /**
   * Function that adds a link to the current router's LSA
   * @param linkService
   */
  public LSA addLinkToSelfLSA(LinkService linkService, int portNum) {
    // get the current router's LSA
    LSA selfLSA = _store.get(rd.simulatedIPAddress);
    // make sure we are not adding duplicates
    for (LinkDescription ld : selfLSA.links) {
      // if the linkID matches, return
      if (ld.linkID.equals(linkService.getTargetIP())) {
        return null;
      }
    }
    // Create a linkDescription from the linkService to add to the LSA
    LinkDescription link = new LinkDescription();

    link.linkID = linkService.getTargetIP();
    link.portNum = portNum;
    
    // Add the link to the LSA
    selfLSA.links.add(link);
    
    // increment the sequence number
    selfLSA.lsaSeqNumber += 1;

    // update the LSA in the store
    _store.put(rd.simulatedIPAddress, selfLSA);
    return selfLSA;
  }

  public SOSPFPacket addEntries(SOSPFPacket packet) {
    Vector<LSA> lsaVector = packet.lsaArray;
    Vector<LSA> toKeep = new Vector<LSA>();
    Vector<LSA> toAdd = new Vector<LSA>();
    for (LSA lsa : lsaVector) {
      String key = lsa.linkStateID;
      if (_store.containsKey(key)) {
        LSA storedLSA = _store.get(key);
        // check if the sequence number is higher
        if (storedLSA.lsaSeqNumber < lsa.lsaSeqNumber) {
          _store.put(key, lsa);
          toKeep.add(lsa);
        }
        // ignore if the sequence number is lower
      } else {
        _store.put(key, lsa);
        toKeep.add(lsa);
      }
    }
    // add entries which were not in the packet already
    for (String key : _store.keySet()) {
      for (LSA lsa : toKeep) {
        if (!key.equals(lsa.linkStateID)) {
          toAdd.add(lsa);
        }
      }
    }
    for (LSA lsa : toAdd) {
      toKeep.add(lsa);
    }
    packet.lsaArray = toKeep;
    return packet;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    lsa.links.add(ld);
    return lsa;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
