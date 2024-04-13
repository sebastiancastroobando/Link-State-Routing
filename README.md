# Link State Routing - Assignment #3

### Authors
- Denis Aleksandrov
- Sebastian Castro Obando

### How to compile and run
We wrote shell scripts to compile and run the project. These scripts just run the commands that we would run manually.

To compile the project, run the following command in the project root directory:
```bash
./compile.sh
```
To run a router, run the following command in the project root directory:
```bash
./run.sh router1
```
Note that there are 7 router configurations in the `conf` directory. You can run any of them by replacing `router1` with the desired router configuration file name. Also, you may notice we added 4 routers to the `conf` directory.

### Demo
The link for the demo will be uploaded on the submission page for the demo. Please check the submission page for the link.

**Important information**: 
- We implemeted the possibility to accept/reject incoming attach requests.
- During the demo, the grader may notice the sequence number start at 0 instead of `INTEGER.MIN_VALUE`. This is because we changed the sequence number to start at 0 to make it easier to debug.
- Additional functions were added for debugging purposes. 
    - `lsd` : to print the link state database of the router that runs the command.
    - `help` : to print the available commands.
- The `neighbors` command does not print empty if no router is connected, rather it shows the availability of the ports. Furthermore, if a "future" neighbor router runs `attach` but not `start`, it will show the router as "Attached, but not initialized". In the example below, router `192.168.1.2` has attached and started, but router `192.168.1.3` has only attached.
```text
>> neighbors
Port 0 : 192.168.1.2 (TWO_WAY)
Port 1 : 192.168.1.3 (Attached, but not initialized)
Port 2 : (Free)
Port 3 : (Free)
```
- Since the network does not have weights on the links, the shortest path which runs Dijkstra's algorithm will behave like a breadth-first search. 

#### Topology 1 - Simple topology
The first topology we will run is the topology that was shown in class during the live demo. This topology consist of 4 routers connected in a line. We will connect the routers progressively to show the link state database updates. 

After showing the basic functions like `attach`, `start`, `connect`, `neighbors`, we will connect `router4` to `router1` to show how the shortest path is calculated. Now that all routers are connected, we will disconnect `routerA` from `routerD` to show how the routers update their link state database. We will see that before the disconnection, the `detect` command will show that the shortest path is `routerD -> routerA` and after the disconnection, the shortest path will be `routerD -> routerC -> routerB -> routerA`.
![topology1](./topology1.png)

#### Topology 2 - Topology from the handout
For the second part of the demo, we wanted to show a more complex topology. Thus, we implemented the topology from the handout. This topology consists of 7 routers connected as shown below. There will be 2 routers "failures" (in reality a quit) to show how the routers update their link state database. Furthermore, we will be able to show how shortest path changes when a router is removed from the network. 
![topology2](./topology2.png)

### Contributions disclosure
Each author contributed equally to the project.