package socs.network.node;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Link {

  public RouterDescription sourceRouter;
  public RouterDescription targetRouter;
  public Socket socket;
  public ObjectInputStream in;
  public ObjectOutputStream out;

  public Link(RouterDescription r1, RouterDescription r2, Socket socket, ObjectInputStream in, ObjectOutputStream out) {
    this.sourceRouter = r1;
    this.targetRouter = r2;
    this.socket = socket;
    this.in = in;
    this.out = out;
  }
}
