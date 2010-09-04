
NPDS Tracker Server

A Java based server for tracking and listing Newton Personal Data Sharing clients.

http://npds.info/

Need support? Join the NPDS mailing list.

npds@ml.free.fr



(The following information has been taken from the original
NPDSTracker 0.1 read me file and really needs some love.)

-----------------------------------------------------------

Requirements

-----------------------------------------------------------


Sun Java 1.1 or better

	Java 1.3 or higher is HIGHLY RECOMMENDED due to DNS caching bugs in 
	prior Java versions.  If you have to use Java 1.2 there is a 
	workaround: add the parameter "-Dsun.net.inetaddr.ttl=0" to the 
	Java command-line.

A connection to the internet

	NPDSTracker has an official TCP port assigned by the IANA: 3680.
	Many trackers also listen on port 80 or 8080.

Text editor


-----------------------------------------------------------

Files

-----------------------------------------------------------

readme.txt

	You're reading it now

npdstracker.java

	Tracker source code

npdstracker.ini

	Settings file read at startup

template.html

	Tracker page template

npdscmd.txt

	Initial tracker commands. Tracker will write currently tracked servers to this file on shutdown.

startnpds.sh

	Example startup script for *NIX-like operating systems



-----------------------------------------------------------

Configuration

-----------------------------------------------------------


* Assuming you already have the Java SDK setup properly, open a command line
and compile the server:

	javac npdstracker.java

* Edit npdstracker.ini and change any settings you see fit. Pay attention to
the log settings - NPDSTracker logs are rather verbose and can become quite
large over time. You can turn logging off in the INI file once you are sure
your server is configured properly.  Also add any trackers which you want to
share records with.

* Start the server at the command line:

	java npdstracker

or, for Java 1.2.x:

	java -Dsun.net.inetaddr.ttl=0 npdstracker

* Test connecting to the server.  Fire up a web browser and point it to

	http://<server IP/hostname>:3680/

* Configure and NPDS server to point to the tracker and make sure it can 
register.


-----------------------------------------------------------

Licensing

-----------------------------------------------------------

Coming soon.


-----------------------------------------------------------

Version History

-----------------------------------------------------------

Need to move changelog from the npdstracker.java source file into this read me.
