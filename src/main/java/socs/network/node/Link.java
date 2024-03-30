package socs.network.node;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

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

  public void destroy() {
    this.sourceRouter = null;
    this.targetRouter = null;
    if (this.in != null) {
      try {
        this.in.close();
      } catch (IOException e) {
        System.out.println("Failed to in.close() in Link");
        e.printStackTrace();
      }
      this.in = null;
    }
    if (this.out != null) {
      try {
        this.out.close();
      } catch (SocketException e) {
        System.out.println("Connection severed, in Link.out.close()");
      } catch (IOException e) {
        System.out.println("Failed to out.close() in Link");
        e.printStackTrace();
      }
      this.out = null;
    }
    if (this.socket != null) {
      try {
        this.socket.close();
      } catch (Exception e) {
        System.out.println("Failed to socket.close() in Link");
        e.printStackTrace();
      }
      this.socket = null;
    }
  }
}
