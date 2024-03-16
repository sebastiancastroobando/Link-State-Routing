### Java Socket Programming
- GetInputStream().read() is a blocking call. Meaning that the method will block until input data is available
    - Blocking operations = bad throughput. We want to develop some way to handle concurrent socket requests. 
    - **We need to implement multi-threaded server to handle concurrent socket requests/messages**

### Simulation of Link State Routing Protocol
- We need to implement network description synchronization between routers. More specifically, implement the `Router` class.
- We need to use "Process IP" and "Process Port" to establish connection via socket. - In the "simulated network", we assign a "simulated ip address" to each router. This IP address is only used to identify the router program instrance in the simulted netowrk space, but not used to communicate via sockets. 