package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;
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

  // We will receive a vector of LSAs from a neighbor router
  // We need to add each LSA to our database
  public void addLSAVector(Vector<LSA> LSAVector) {
    // for each LSA in the vector, add it to the database
    // if the sequence number is higher than the one we have
    for (LSA lsa : LSAVector) {
      if (lsa == null) {
        continue;
      }
      // if we don't have an entry for this LSA, add it
      if (!_store.containsKey(lsa.linkStateID)) {
        _store.put(lsa.linkStateID, lsa);
      } else {    
        // if we have an entry for this LSA, check the sequence number
        LSA currentLSA = _store.get(lsa.linkStateID);
        if (lsa.lsaSeqNumber > currentLSA.lsaSeqNumber) {
          // if the new LSA has a higher sequence number, update the entry
          _store.put(lsa.linkStateID, lsa);
        }
      }
    }
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  public String getShortestPath(String destinationIP) {
    // Dijkstra's algorithm
    // Pseudocode from https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm

    // The set of routers that we have already found the shortest path to
    Vector<String> S = new Vector<String>();
    // The set of routers that we have not found the shortest path to
    Vector<String> Q = new Vector<String>();
    // The set of routers that we have found the shortest path to
    HashMap<String, String> previous = new HashMap<String, String>();
    // The set of routers that we have found the shortest path to
    HashMap<String, Integer> distance = new HashMap<String, Integer>();

    // Initialize the distance to all routers to infinity
    for (String router : _store.keySet()) {
      distance.put(router, Integer.MAX_VALUE);
      Q.add(router);
    }

    // The distance to ourself is 0
    distance.put(rd.simulatedIPAddress, 0);

    // While there are still routers we haven't found the shortest path to
    while (!Q.isEmpty()) {
      // Find the router in Q with the smallest distance
      String u = null;
      int minDistance = Integer.MAX_VALUE;
      for (String router : Q) {
        if (distance.get(router) < minDistance) {
          u = router;
          minDistance = distance.get(router);
        }
      }

      // Remove u from Q
      Q.remove(u);
      // Add u to S
      S.add(u);

      // For each neighbor v of u
      LSA uLSA = _store.get(u);
      for (LinkDescription link : uLSA.links) {
        String v = link.linkID;
        int alt = distance.get(u) + 1;
        if (alt < distance.get(v)) {
          distance.put(v, alt);
          previous.put(v, u);
        }
      }
    }

    // Reconstruct the path
    String path = "";
    String u = destinationIP;
    while (previous.containsKey(u)) {
      path = u + " -> " + path;
      u = previous.get(u);
    }

    return path;
  }

  /**
   * Function that adds a link to the current router's LSA
   * @param linkService
   */
  public void addLinkToSelfLSA(LinkService linkService, int portNum) {
    // get the current router's LSA
    LSA selfLSA = _store.get(rd.simulatedIPAddress);

    // Create a linkDescription from the linkService to add to the LSA
    LinkDescription link = new LinkDescription();

    link.linkID = linkService.getConnectedRouterSimluatedIP();
    link.portNum = portNum;
    
    // Add the link to the LSA
    selfLSA.links.add(link);
    
    // increment the sequence number
    selfLSA.lsaSeqNumber += 1;

    // update the LSA in the store
    _store.put(rd.simulatedIPAddress, selfLSA);
  }

  public void addEntries(Vector<LSA> lsaVector) {
    for (LSA lsa : lsaVector) {
      String key = lsa.linkStateID;
      if (_store.containsKey(key)) {
        LSA storedLSA = _store.get(key);
        // check if the sequence number is higher
        if (storedLSA.lsaSeqNumber < lsa.lsaSeqNumber) {
          _store.put(key, lsa);
        }
        // ignore if the sequence number is lower
      } else {
        _store.put(key, lsa);
      }
    }
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
