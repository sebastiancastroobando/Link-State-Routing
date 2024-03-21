package socs.network.node;

public class LinkService implements Runnable {
    private Link link;

    public LinkService(Link link) {
        this.link = link;
    }

    public void run() {
        // Here we would handle the different types of messages that could be sent between routers
    }
}
