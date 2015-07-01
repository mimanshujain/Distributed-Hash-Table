## Peer to Peer Distributed-Hash-Table based on chord protocol
* Simple Key-Value storage based on Chord design which is a Ring-based routing architecture for the Nodes(which stores the values)
* SHA-1 hash function is used to lexically arrange nodes in a ring and find the location for a particular key to be stored.
* Each node maintains a successor and predecessor pointer for nodes in the ring.
* Multiple Instances form a Chord ring and serve insert/query requests in a distributed fashion according to the Chord protocol.
* References
  * Read about Chord [Here](http://conferences.sigcomm.org/sigcomm/2001/p12-stoica.pdf)
  * Lecture Slides [Here](http://www.cse.buffalo.edu/~stevko/courses/cse486/spring15/lectures/14-dht.pdf)
