package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;
import java.util.LinkedList;
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

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  String getShortestPath(String destinationIP) {
    String ret = null;
    if (_store.containsKey(destinationIP)) {
      LSA destLSA = _store.get(destinationIP);
      ret = destLSA.toString();
    }
    return ret;
  }

  // add the whole vector to LSD
  public void addLSAVector(Vector<LSA> LSAVector) {
    // if sequence number is the same
    // do nothing
    LSA selfLSA = _store.get(rd.simulatedIPAddress);
    LSA headVectorLSA = LSAVector.elementAt(0);
    if (selfLSA.lsaSeqNumber >= headVectorLSA.lsaSeqNumber) {
      return;
    }

    // add every LSA per addEntry rules
    for (int i = 0; i < LSAVector.size(); i++) {
      if (LSAVector.elementAt(i) == null) {
        continue;
      }
      addEntry(LSAVector.elementAt(i));
    }
    // increment the sequence number
    selfLSA.lsaSeqNumber += 1;
    _store.put(rd.simulatedIPAddress, selfLSA);
  }

  // make LSD entry for current node
  private void addEntry(LSA lsa) {
    if (!_store.containsKey(lsa.linkStateID)) {
      // if there is no record of destination node
      // add the lsa entry
      _store.put(lsa.linkStateID, lsa);
    } else {
      // if there is a record of the destination node
      // check if we need to update said entry based on
      // the incoming lsa
      LinkDescription head = lsa.links.getFirst();
      if (!head.linkID.equals(rd.simulatedIPAddress)) {
        // current LSD entry is 'foreign', modify the head portNum
        // and update the head pointer of this LSD entry
        LinkedList<LinkDescription> LSDlinks = _store.get(head.linkID).links;
        LinkDescription LSDhead = LSDlinks.getFirst();
        if (LSDhead.linkID.equals(rd.simulatedIPAddress)) {
          // if the current lsa entry for this destination node
          // is properly set up, if it's shorter than the updated
          // incoming lsa => return
          if (LSDlinks.size() <= lsa.links.size() + 1) {
            return;
          }
        } else {
          System.out.println("Current LSD entry has incorrect head: from LinkStateDatabase.java");
        }
        int new_port = -1;
        for (int i = 0; i < LSDlinks.size(); i++) {
          if (LSDlinks.get(i) == null) {
            continue;
          }
          if (LSDlinks.get(i).linkID.equals(head.linkID)) {
            new_port = LSDlinks.get(i).portNum;
          }
        }
        head.portNum = new_port;
        lsa.links.set(0, head);
        // head is now modified, need to update lsa.links.head pointer
        LinkDescription new_head = new LinkDescription();
        new_head.linkID = rd.simulatedIPAddress;
        new_head.portNum = -1;
        lsa.links.addFirst(new_head);
        // AS OF NOW THE ONLY LOCAL PORT IS AT lsa.links[1]
        // THE LINKID AT lsa.links.tail SHOULD MATCH THE lsa.linkStateID
        // IF WE SEND TO lsa.linkStateID, WE USE THE TARGET NODE AT lsa.links[1]
        // AS OF NOW THE LINKEDLISTS ARE ONLY USEFULL TO GET THE COST OF THE PATH
        // SEEMS WASTEFUL, MAYBE THE ONLY WAY TO DO IT IDK
        _store.put(lsa.linkStateID, lsa);
      } else {
        // lsa is a path to the current node from a
        // 'foreign' node, path is definately longer
        // => ignore
      }
    }
  }

  // used to translate a linkService to LSA
  // should be used outside of this class
  public LSA linkServiceToLSA(LinkService linkService) {
    LSA lsa = new LSA();
    lsa.linkStateID = linkService.link.targetRouter.simulatedIPAddress;
    // seqNum unclear right now
    lsa.lsaSeqNumber = _store.get(rd.simulatedIPAddress).lsaSeqNumber + 1;
    LinkDescription ld_head = new LinkDescription();
    ld_head.linkID = linkService.link.sourceRouter.simulatedIPAddress;
    ld_head.portNum = -1;
    LinkDescription ld = new LinkDescription();
    ld.linkID = linkService.link.targetRouter.simulatedIPAddress;
    ld.portNum = linkService.link.targetRouter.processPortNumber;
    lsa.links.add(ld_head);
    lsa.links.add(ld);
    return lsa;
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
