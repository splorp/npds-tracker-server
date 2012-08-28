
NPDS Tracker Server

A Java based server for tracking and listing Newton Personal Data Sharing clients.

http://npds.info/


The NPDS Tracker Server was originally developed by Victor Rehorst <http://www.chuma.org/> and Paul Guyot <http://kallisys.com/>, with additional contributions by Morgan Aldridge <http://makkintosshu.com/>, Grant Hutchinson <http://splorp.com/>, Ron Parker <http://quadzilla.net/>, and Manuel Probsthain.

Many thanks to Matt Vaughn <http://chromatin.cshl.edu/vaughn/> for developing NPDS in the first place.


Need support? Join the NPDS mailing list.

npds@ml.free.fr



-----------------------------------------------------------

Requirements

-----------------------------------------------------------


Java 1.1 or higher

	Java 1.3 or higher is highly recommended due to DNS caching bugs in 
	prior versions of Java. If you must use Java 1.1 or 1.2, there is a 
	workaround for this bug. Add the parameter ‘-Dsun.net.inetaddr.ttl=0’
	to the Java command-line.

An internet connection

	The NPDS Tracker Server uses TCP port 3680, as assigned by IANA.
	
	<http://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xml>
	
	Trackers may also be configured to also listen on port 80 or 8080.

A text editor

	For editing the configuration and template files.
	
	
	
-----------------------------------------------------------

Files

-----------------------------------------------------------

readme.txt

	You’re reading it now

npdstracker.java

	Tracker source code

npdstracker.ini

	Tracker settings and configuration

template.html

	Tracker page template

template.css

	Tracker page stylesheet

npdscmd.txt

	Initial tracker commands. Tracker will write currently tracked servers to this file on shutdown.

startnpds.sh

	Example startup script for *NIX-like operating systems
	
manifest.mf

	Contains information specific to the Java source file



-----------------------------------------------------------

Basic Configuration

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

Advanced Configuration

-----------------------------------------------------------


The basic configuration is great for development, testing, and Windows 
deployment, but for those running Linux or Mac OS X, it’s preferable to
install npdstracker more permanently and appropriately. Specifically:

* For a cleaner installation, create a JAR file at the command line:

	jar cvfm npdstracker.jar manifest.fm *.class

* Install the JAR file to /usr/local/bin from at the command line:

	mkdir -p /usr/local/bin
	install npdstracker.jar /usr/local/bin

* Install the npdstracker.ini & npdscmd.txt files in /etc at the command 
line:

	mkdir -p /etc/npdstracker
	install npdstracker.ini npdscmd.txt /etc/npdstracker

* Install the template.html & template.css files in /usr/local/share at the 
command line:

	mkdir -p /usr/local/share/npdstracker
	install template.html template.css /usr/local/share/npdstracker

* Create the log file at the command line:

	touch /var/log/npdstracker.log

* Edit /etc/npdstracker/npdstracker.ini to point to the new paths for all 
the aforementioned files, especially:

	pageTemplate = /usr/local/share/npdstracker/template.html
	cssTemplate = /usr/local/share/npdstracker/template.css
	logfile = /var/log/npdstracker.log

* You can now manually start npdstracker at the command line:

	java -jar /usr/local/bin/npdstracker.jar -c /etc/npdstracker/npdscmd.txt -o /etc/npdstracker/npdstracker.ini

* Or, on Darwin/Mac OS X, you can create a launch daemon to automatically start 
npdstracker on boot by creating & editing /Library/LaunchDaemons/fr.free.npds.npdstracker.plist
and pasting in the following (this is assuming following the above Advanced 
Configuration steps):

	<?xml version="1.0" encoding="UTF-8"?>
	<!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
	<plist version="1.0">
		<dict>
			<key>Label</key>
			<string>fr.free.npds.npdstracker</string>
			<key>ProgramArguments</key>
			<array>
				<string>java</string>
				<string>-jar</string>
				<string>/usr/local/bin/npdstracker.jar</string>
				<string>-c</string>
				<string>/etc/npdstracker/npdscmd.txt</string>
				<string>-o</string>
				<string>/etc/npdstracker/npdstracker.ini</string>
			</array>
			<key>KeepAlive</key>
			<dict>
				<key>NetworkState</key>
				<true/>
			</dict>
			<key>RunAtLoad</key>
			<true/>
		</dict>
	</plist>

It can be loaded immediately by running the following at the command line, or 
you can wait until the next reboot:

	sudo launchctl load /Library/LaunchDaemons/fr.free.npds.npdstracker.plist

* Or, on Linux or BSD systems you can create init.d or rc.d scripts,
respectively, to automatically start npdstracker on boot.



-----------------------------------------------------------

Page Template Tag Syntax

-----------------------------------------------------------

The HTML template (```template.html```) can use the following pseudo-SGML tags to insert various bits of information into the page.

<hit-counter/>

	The number of hits since last restart
	
<http-doc/>

	What comes after the GET (usually “/”)
	
<last-validation/>

	The date and time of the last validation or “Validation is in progress.”
	
<meta-refresh/>

	The meta element containing the http-equiv="refresh" value
	
<servers/>

	The list of NPDS servers formatted as a table
	
<server-counter/>

	The number of registered NPDS servers
	
<stylesheet/>

	Inserts the stylesheet as specified in npdstracker.ini (default: template.css)
	
<trackerHost/>

	The URL of the host site or server as specified in npdstracker.ini
	
<trackerName/>

	The name of the host site or server as specified in npdstracker.ini
	
<url/>

	The URL of this server, obtained by reading the HTTP header
	
<validate-time/>

	The time (in minutes) between validations
	
<version/>

	The current version of the tracker software



-----------------------------------------------------------

Remote Administration

-----------------------------------------------------------

Coming soon.



-----------------------------------------------------------

Licensing

-----------------------------------------------------------

Coming soon.



-----------------------------------------------------------

Version History

-----------------------------------------------------------

Please note that the version numbering scheme is defined as major.minor.build.

0.1.36 [TBD]

	[MA] Split out display of HTML status into its own method
	[MA] Updated HTTP GET processing to support rendering the HTML status page
	[MA] Fixed stylesheet configuration and template insertion
	[RP] Added host name and link to the header and page title
	[MA] Removed rogue slash in npdstracker.java
	[MA] Added manifest file
	[MA] Added advanced configuration documentation
	[GH] Converted template to HTML5
	[GH] Removed deprecated elements and formatting attributes
	[GH] Minor formatting and nomenclature edits abound!

0.1.35 [08 September 2010]

	[GH] Moved source to Github
	[GH] Added user definable stylesheet template
	[GH] Moved stylesheet from template.html into its own file
	[GH] Moved version history from Java source file into read me
	[GH] Minor text formatting updates
	[GH] Changed encoding to UTF-8

0.1.34 [06 April 2006]

	[VR] Added ‘shouldIlog’ setting in INI file to disable all logging

0.1.33 [22 February 2004]

	[MP] Added SSI tag for displaying the number of registered servers in the template
	[MP] Made the <meta-refresh/> SSI XHTML-compatible
	[MP] Changed the default port number to 3680
	[MP] Refined the listing table elements for extensive use of CSS
	[MP] Added code to allow one hostname with a private IP to register

0.1.32 [02 May 2003]

	[VR] Makes sure that the hostname isn’t empty on a REGUP command
	[VR] Changed kRTFMStr to reflect new NPDS website address
	
0.1.31 [30 July 2002]

	[PG] NPDS Tracker can now listen on several ports

0.1.30 [28 April 2002]

	[PG] Fixed the bug in 192.168 address filtering

0.1.29 [16 March 2002]

	[PG] Fixed the bug in processing of console commands, so now lowercase commands work
	[PG] The console now says “server validation test started” before it finishes the validation
	[PG] The tracker was waiting forever for an answer from fellow trackers, which was bad
	[PG] Added style information to colorize the table
	[PG] The server performs a verification on startup
	[PG] Vector copy constructor is no longer called, so this should compile on Java < 1.2 (but it will have problems because of bugs in the JVM)
	[PG] The server now checks the hosts passed as REGUP parameters to be correct (to resolve and to be not private)

0.1.28 [29 November 2001]

	[PG] No longer dumps the shared servers to npdscmd.txt
	[PG] Fixed the shareEnabled .ini read process (getBoolean is not what we want, it’s valueOf)

0.1.27 [20 October 2001]

	[VR] Now saves registered server information for automatic reloading upon a crash or restart

0.1.26 [18 August 2001]

	[PG] Fixed the java.lang.ArrayIndexOutOfBoundsException bug which happened on Wed Aug 15 11:50:45 EST 2001. This bug was introduced with 0.1.24.

0.1.25 [06 June 2001]

	[PG] Fixed the java.lang.IllegalArgumentException: timeout value is negative bug (well, I think)

0.1.24

	[PG] Fixed the reload 0 bug
	[PG] Fixed the lock bug
	[PG] Set the timeout to 20 secs (was 10 secs) so my Newton is no longer considered as down ;)
	[PG] Added last validation template element

0.1.23

	[VR] No actual code changes, but to workaround a bug in JDK < 1.1 you must use Java 1.2 or better and set the sun.net.inetaddr.ttl property to 0 on the command line, like this: ‘java -Dsun.net.inetaddr.ttl=0 npdstracker’

0.1.22

	[PG] Implemented the template stuff
	[PG] Re-organized the ProcessQuery method
	[PG] There is now a single class with embedded sub classes (so we’ll have a single .java binary)
	[PG] Improved answer to the GET request (with many headers now)

0.1.21

	[PG] No longer uses the bugged URL interface to check if Newton servers are up. Instead, I use a socket.
	[PG] The REGUP command tokenizer now accepts any standard token (and no longer only spaces which wasn’t protocol-compliant)
	[PG] Added several syntax checking with an appropriate status message.

0.1.20

	[VR] Retrieving SHARE records from other trackers now works!!

0.1.19

	[VR] Will now read optionsfile and cmdfile from default location
	[VR] Fixed up options file parsing
	[VR] HTML now easily customizable (see npdstracker.ini, header.html, footer.html)

0.1.18

	[VR] Minor admin console fixes
	[VR] Implemented command-line arguments for logfile, cmdfile, optionsfile
	[VR] Now can specify files to log to, read options from, or read initial commands from
	[VR] Finally implemented LOGS command in admin console.
	[VR] Code and syntax cleanups.
	[PG] Use SimpleDateFormat in the ReturnRFCTime function. (this also fixes the GMT bug).
	[PG] Fixed a little HTML bug.

0.1.17

	[VR] VTEST command added to the admin console
	[VR] SHARE command is now actually sent (forgot to flush PrintWriter)
	[VR] Changed return code of SHARE command if sharing is disabled

0.1.16

	[PG] The server now only sleeps in the validator loop.

0.1.15

	[PG] Now the server always uses CRLF except for the log
	[PG] Fixed a little bug in the validation date.

0.1.14

	[PG] Now supports multiple connections

0.1.13

	[VR] Trying to fix intermittent bug in server validation code

0.1.12

	[VR] More bugs in server validation fixed

0.1.11

	[VR] Added more features to GET code
	[VR] Fixed up RFC times somewhat
	[VR] Fixed bug in validation code with Connection Refused socket exceptions

0.1.10

	[VR] Fixed bug with exception passing and server socket code
	[VR] Rewrote GET code to return a nice HTML table (HTML 4.01 compliant)

0.1.9

	[VR] Admin console is now complete save for the LOGS command
	[VR] Now retrieves, updates, and properly handles records from other servers via the SHARE command

0.0.8

	[VR] Added SHARE, ABOUT, VERIFY, and STATS command to admin console
	[VR] Fixed the problem with the verification code repeating many times per minute
	[VR] Added a REGUP counter for the STATS admin command

0.0.7

	[VR] Now accepts commands regardless of case
	[VR] Checks for an existing host entry before processing a REGUP
	[VR] Started implementing the ADMIN command for live configuration of the server

0.0.6

	[VR] First implementation of SHARE command
	[VR] Cleaned up some return codes
	[VR] Fixed some status handling and checking

0.0.5

	[VR] Server validation actually works!  woohoo!
	[VR] Implemented a socket timeout so that the loop doesn’t get stuck
	[VR] Removed the QHTML extended command

0.0.4

	[VR] implemented Return Codes

0.0.3

	[VR] Fixed GET method - now returns DTD HTML 2.0 compliant pages
	[VR] Rewrote internal storage of records - is now good and extensible
	[VR] Now we store time server last checked, and server status
	[VR] Implemented QueryMethod function for finding a record when we do a REGDN - could also use this method for an possible SRCH command later

0.0.2

	[VR] Added QHTML method for returning HTMLized results
	[VR] Added GET method support for returning a basic web page to web browsers