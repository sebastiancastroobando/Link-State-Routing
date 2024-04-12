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

  public boolean hasChanged(Vector<LSA> incomingVector) {
    if (incomingVector.size() != _store.size()) {
      return true;
    }
    for (LSA lsa : incomingVector) {
      if (lsa == null) {
        System.out.println("LSA NULL WHEN IT SHOULDNT BE");
        continue;
      }
      if (!_store.containsKey(lsa.linkStateID)) {
        return true;
      }
      if (_store.get(lsa.linkStateID).lsaSeqNumber != lsa.lsaSeqNumber) {
        return true;
      }
    }
    return false;
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
     * 
     * The IP may not be a key in the LSD, it might be a neighbor of a key we have in the LSD.
     */

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

        if (currentLSA == null) {
            // This router has no LSA, skip. It might not happen but just in case
            continue;
        }

        for (LinkDescription ld : currentLSA.links) {
            String neighbor = ld.linkID;
            // Check if neighbor is the destination
            if (neighbor.equals(destinationIP)) {
                prev.put(neighbor, currentNode);
                queue.clear();
                break;
            }
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

    // If the destination is not in the prev map, then there is no path
    if (!prev.containsKey(destinationIP)) {
        return "Sorry, no path to destination IP found within the link state database;";
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

    // safety check
    if (selfLSA == null) {
      System.out.println("Problem getting selfLSA, please check addLinkToSelfLSA");
    }
    
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

  public void deleteEntry(String quittingIP) {
    // safety check
    System.out.println("Attempting to delete: " + quittingIP);
    if (_store.containsKey(quittingIP)) {
      System.out.println("DELETING ENTRY: " + quittingIP);
      _store.remove(quittingIP);
    } 
    return;
  }

  public void removeSelfFromLink (String targetIP) {
    // Get the LSA entry
    LSA lsa = _store.get(targetIP);
    if (lsa == null) {
      return;
    }

    // traverse the linked list
    for (LinkDescription ld : lsa.links) {
      if (ld.linkID.equals(rd.simulatedIPAddress)) {
        lsa.links.remove(ld);
        lsa.lsaSeqNumber += 1;
        _store.put(targetIP, lsa);
        break;
      }
    }
  }

  public void removeLinkFromSelfLSA(String targetIP) {
    // get the current router's LSA
    LSA selfLSA = _store.get(rd.simulatedIPAddress);
    if (selfLSA == null) {
      return;
    }
    // make sure we are not adding duplicates
    for (LinkDescription ld : selfLSA.links) {
      // if the linkID matches, return
      if (ld.linkID.equals(targetIP)) {
        selfLSA.links.remove(ld);
        selfLSA.lsaSeqNumber += 1;
        _store.put(rd.simulatedIPAddress, selfLSA);
        break;
      }
    }
    // increment the sequence number
    selfLSA.lsaSeqNumber += 1;

    // update the LSA in the store
    _store.put(rd.simulatedIPAddress, selfLSA);
  }

  public void addNeighborToSelfLSA(String targetIP, int portNum) {
    // get the current router's LSA
    LSA selfLSA = _store.get(rd.simulatedIPAddress);

    // safety check
    if (selfLSA == null) {
      System.out.println("Problem getting self LSA;\n");
    }

    // make sure we are not adding duplicates
    for (LinkDescription ld : selfLSA.links) {
      // if the linkID matches, return
      if (ld.linkID.equals(targetIP)) {
        return;
      }
    }
    // Create a linkDescription from the linkService to add to the LSA
    LinkDescription link = new LinkDescription();
    link.linkID = targetIP;
    link.portNum = portNum;

    // Add the link to the LSA
    selfLSA.links.add(link);

    // increment the sequence number
    selfLSA.lsaSeqNumber += 1;

    // update the LSA in the store
    _store.put(rd.simulatedIPAddress, selfLSA);
  }

  public SOSPFPacket addEntries(SOSPFPacket packet) {
    Vector<LSA> lsaVector = packet.lsaArray;
    if (packet.finalMessage) {
      deleteEntry(packet.srcIP);
    }

    // First, check if the LSA is already in the store
    for (LSA lsa : lsaVector) {
      String key = lsa.linkStateID;
      if (_store.containsKey(key)) {
        // Check if the sequence number is greater
        // for debugging, lsaSeqNum 
        //System.out.println("\nNEW ENTRY : LSA Seq Number: " + lsa.lsaSeqNumber);
        //System.out.println("Store Seq Number: " + _store.get(key).lsaSeqNumber);
        if (_store.get(key).lsaSeqNumber < lsa.lsaSeqNumber) {
          // Update the entry
          _store.put(key, lsa);
        }
      } else {
        // Add it as a new entry
        _store.put(key, lsa);
      }
    }
    packet.lsaArray = lsaVector;
    return packet;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = 0;
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
