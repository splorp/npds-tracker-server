# NPDS Tracker Server

A Java based server for tracking and listing [Newton Personal Data Sharing](http://npds.free.fr/) clients.

The [NPDS Tracker Server](http://npds.free.fr/trackers/) was originally developed by [Victor Rehorst](http://www.chuma.org/) and [Paul Guyot](http://kallisys.com/).

Additional contributions and updates have been by generously provided and applied by [Morgan Aldridge](http://makkintosshu.com/), [Grant Hutchinson](http://splorp.com/), [Ron Parker](http://quadzilla.net/), and Manuel Probsthain.

Many thanks to [Matt Vaughn](http://chromatin.cshl.edu/vaughn/) for developing NPDS in the first place.

Looking for support? Join the [NPDS mailing list](http://npds.free.fr/list/).


## Requirements

### Java 1.3 or later

The current release of the NPDS Tracker Server has been tested with Java SE (Standard Edition) versions 1.4, 1.5, and 1.6. It will also work with versions earlier than 1.3, but it is not recommended. See the [Basic Configuration](#basic-configuration) section for more information.

The Java SE runtime can be downloaded from [Oracle](http://www.oracle.com/technetwork/java/javase/).

### An internet connection

The NPDS Tracker Server uses TCP port 3680, as assigned by [IANA](http://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xml).

Trackers may also be configured to also listen on port 80 or 8080.

### A text editor

For editing the various configuration and template files.

### A terminal program

For compiling the Java source, installing the compiled .jar file, and configuring the related files and components. A terminal program can also be used to remote administer the NPDS Tracker Server using Telnet. See the [Remote Administration](#remote-administration) section for more information.


## Files

```readme.md``` — You’re soaking in it

```npdstracker.java``` — Java source code for the tracker

```npdstracker.ini``` — Settings and configuration for the tracker

```template.html``` — HTML page template used by the tracker

```template.css``` — Stylesheet used in conjunction with the HTML template by the tracker

```npdscmd.txt``` — Initial tracker commands. Registered servers are written to this file upon shutdown

```startnpds.sh``` — An example shell script for Unix-like operating systems

```manifest.mf``` — Information specific to the Java source code


## Getting Started

### Got Java?

First, determine whether Java is installed. Open a command line and type:

```sh
which java
```

This command will return the path where Java is installed on your system.

For example: ```/usr/bin/java```

If a path is not displayed after running this command, then Java is not currently installed on your system. You will need to download and install the [Java SE](http://www.oracle.com/technetwork/java/javase/) software before proceeding.

To determine the version of Java installed, type the following:

```sh
java -version
```

This command will return information about the current Java installation, including the version number and related software details.

For example: ```java version "1.6.0_30"```

### Grab the source

Download the [latest version](https://github.com/splorp/npds-tracker-server/archive/master.zip) of the NPDS Tracker Server source. Decompress the archive and move the files to a logical location on your system.


## Basic Set Up

### Compile the source

Assuming that Java 1.3 or later has been correctly installed, open a command line and compile the server source:

```sh
javac npdstracker.java
```

### Configure the server

Edit ```npdstracker.ini``` and change the settings specific to your installation.

If all of the source files remain in the same directory, you will not need to change the defaults for the ```pageTemplate```, ```cssTemplate```, or ```logfile``` path settings.

See the [Settings](#settings) section for setting descriptions and syntax.

### Start the server

Start the server at the command line:

```sh
java npdstracker
```

For Java 1.1 and 1.2 installations, the ```-Dsun.net.inetaddr.ttl=0``` parameter must be added to the command line. This additional parameter compensates for DNS caching bugs that exist in these versions of the Java runtime.

```sh
java -Dsun.net.inetaddr.ttl=0 npdstracker
```

Test the connection to the tracker by launching your web browser using the following URL:

```sh
http://<ip/hostname>:3680/
```

Configure an [NPDS Tracker Client](http://npds.free.fr/modules/#trackerclient) to point at the tracker, confirming that it registers properly.


## Advanced Set Up

The basic set up is great for development, testing, and Windows deployment. However, for those running Linux or Mac OS X, it may be preferable to install a tidier and more permanent version of the tracker.

For a cleaner installation, create a Java archive (JAR) file at the command line:

```sh
jar cvfm npdstracker.jar manifest.mf *.class
```

Install the JAR file to ```/usr/local/bin``` from at the command line:

```sh
mkdir -p /usr/local/bin
install npdstracker.jar /usr/local/bin
```

Install the ```npdstracker.ini``` and ```npdscmd.txt``` files in ```/etc``` at the command line:

```sh
mkdir -p /etc/npdstracker
install npdstracker.ini npdscmd.txt /etc/npdstracker
```

Install the ```template.html``` and ```template.css``` files in ```/usr/local/share``` at the command line:

```sh
mkdir -p /usr/local/share/npdstracker
install template.html template.css /usr/local/share/npdstracker
```

Create the log file at the command line:

```sh
touch /var/log/npdstracker.log
```

Edit ```/etc/npdstracker/npdstracker.ini``` to point to the new paths for all the aforementioned files, especially:

```sh
pageTemplate = /usr/local/share/npdstracker/template.html
cssTemplate = /usr/local/share/npdstracker/template.css
logfile = /var/log/npdstracker.log
```

You can now manually start the tracker at the command line.

```sh
java -jar /usr/local/bin/npdstracker.jar -c /etc/npdstracker/npdscmd.txt -o /etc/npdstracker/npdstracker.ini
```

See [Command Line Usage](#command-line-usage) for further details.

On Darwin or Mac OS X, you can create a launch daemon to automatically start the tracker on boot. Create a ```/Library/LaunchDaemons/fr.free.npds.npdstracker.plist``` file and paste in the following XML. This assumes that you have followed the steps for [Advanced Configuration](#advanced-configuration).

```xml
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
```

The launch daemon may be loaded immediately by running the following at the command line, or you can wait until the next boot cycle:

```sh
sudo launchctl load /Library/LaunchDaemons/fr.free.npds.npdstracker.plist
```

On Linux or BSD systems, you can create ```init.d``` or ```rc.d``` scripts, respectively, to automatically start the tracker on boot.


## Settings

Configuration settings for the tracker are located in the ```npdstracker.ini``` file.

### Syntax

The following syntax must be followed when editing the ```npdstracker.ini``` file:

+ Setting values cannot be enclosed in quotation marks
+ Setting values cannot contain spaces (with the exception of ```shareServer``` values)
+ Spaces must appear on either side of the equal sign
+ Setting parameter names are case sensitive

### Parameters

#### kPort
```sh
kPort = 3680
```
Specifies the TCP listening port.

The default port is 3680, as assigned by [IANA](http://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xml). However, port 8080 is commonly used for clients that reside behind a firewall. Multiple port numbers may be specified, separated by spaces.


#### trackerName
```sh
trackerName = Default
```
Specifies the name of the site or tracker for use in the HTML template.


#### trackerHost
```sh
trackerHost = http://npds.free.fr/
```
Specifies the URL of the site or server host for use in the HTML template.


#### adminPass
```sh
adminPass = qwerty
```
Specifies the password for the remote administration interface.

This parameter was named ```adminpasswd``` in NPDS Tracker Server 0.1.36 and earlier. Make sure that this parameter name is correct in the ```npdstracker.ini``` file.


#### validateTime
```sh
validateTime = 30
```
Specifies how often to validate clients.


#### validateTries
```sh
validateTries = 3
```
Specifies how many validation attempts before clients are removed from the list.


#### shareEnabled
```sh
shareEnabled = true
```
Specifies whether other trackers may obtain SHARE’d records from this tracker.

Set to ‘true’ to allow sharing, set to anything else to disallow sharing.

#### pageTemplate
```sh
pageTemplate = template.html
```
Specifies the path and name of the HTML template.

Refer to the [Page Template Tag Syntax](#page-template-tag-syntax) section for tag usage.


#### cssTemplate
```sh
cssTemplate = template.css
```
Specifies the path and name of the template stylesheet.

Used in conjunction with ```pageTemplate```.


#### shareServer
```sh
shareServer = misato.chuma.org 3680
shareServer = www.unna.org 3680
shareServer = newtzilla.quadzilla.net 3680
shareServer = newton.tek-ed.com 3680
```
Specifies servers to obtain SHARE’d records from.

Each ```shareServer``` entry consists of a tracker hostname or address and a port number. To stop obtaining SHARE’d records from a tracker, comment out or delete the tracker’s ```shareServer``` entry.


#### logFile
```sh
logFile = npdstracker.log
```
Specifies the path and name of the log file.

Log entries generated by the tracker are rather verbose and the log file can grow quite large over time. You can turn off logging altogether (redirecting to stdout) by commenting out the ```logFile``` setting once you are sure that the server is configured properly.

This parameter was named ```logfile``` in NPDS Tracker Server 0.1.36 and earlier. Make sure that this parameter name is correct in the ```npdstracker.ini``` file.


#### logVerbose
```sh
logVerbose = true
```
Specifies whether messages should be written to the log file.

If set to ‘false’, the tracker will only create a log entry if it encounters a serious problem.

This parameter was named ```shouldIlog``` in NPDS Tracker Server 0.1.36 and earlier. Make sure that this parameter name is correct in the ```npdstracker.ini``` file.


#### privateHostToAccept
```sh
privateHostToAccept =
```
Specifies a private hostname or address to register as a client.

Must be a public DNS entry.


## Command Line Usage

```sh
java npdstracker [-h] [-c <cmdfile>] [-o <optionsfile>]
```

```-h``` — Display help

```-c <cmdfile>```  — Specifies the path of the ```npdscmd.txt``` file containing any commands to run at startup (defaults to none)

```-o <optionsfile>``` — Specifies the path of the ```npdstracker.ini``` file containing configuration and option settings (defaults to settings at compile time)


## Remote Administration

To access the remote administration interface, you’ll need the following:

+ A terminal program or standalone Telnet client
+ The hostname and port of the tracker you wish to administer
+ The password of the tracker you wish to administer

Any changes made using the administration interface are applied immediately to the tracker, but are not saved to the ```npdstracker.ini``` file. Changes are lost once the tracker is restarted.

### Connection

This example assumes that the tracker is configured to use the default port number.

In your terminal program, open the Telnet connection to the tracker.

```sh
telnet tracker.example.com 3680
```

Hit ‘Enter’

After you connect to the tracker, you have five seconds to enter the following:

```sh
ADMIN <password>
```

Hit ‘Enter’

You will be presented with the following text:

```sh
Welcome to the NPDS Tracker Server administration interface!
```

Next, type:

```sh
HELP
```

Hit ‘Enter’

This will display a list of available commands.

### Commands

```ABOUT``` — Display the current tracker settings

```HALT``` — Stop the tracker (with confirmation)

```HELP``` — Displays this list of commands

```LOGS``` — Dumps the tracker log

```SHARE``` — Change the tracker share settings

```SLIST``` — View or modify the list of trackers to obtain shared records from

```VTEST``` — Trigger a tracker validation

```STATS``` — Display the tracker statistics (describe)

```VERIFY``` — Change the tracker verification settings

```QUIT``` — Exit the administration interface and close the connection


## Page Template Tag Syntax

The HTML template (```template.html```) can be customized using the following pseudo-SGML tags to insert information into the page.

```<hit-counter/>``` — The number of hits since the tracker was restarted

```<http-doc/>``` — What comes after the GET (usually “/”)

```<last-validation/>``` — The date and time of the last validation or “Validation is in progress.”

```<meta-refresh/>``` — The meta element containing the http-equiv="refresh" value

```<servers/>``` — The list of registered NPDS clients formatted as a table

```<server-counter/>``` — The number of registered NPDS clients

```<server-shares/>``` — A linked list of SHARE’d trackers formatted as an unordered list

```<stylesheet/>``` — Inserts the stylesheet as specified in npdstracker.ini (default: template.css)

```<trackerHost/>``` — The URL of the host site or tracker as specified in npdstracker.ini

```<trackerName/>``` — The name of the host site or tracker as specified in npdstracker.ini

```<url/>``` — The URL of this tracker, obtained by reading the HTTP header

```<validate-time/>``` — The time (in minutes) between validations

```<version/>``` — The current version of the tracker software



## Licensing

Coming soon.



## Version History

### 0.1.37 — TBD

+ [RP] New ```<server-shares/>``` template tag inserts a list of shared trackers
+ [GH] Added command line usage information
+ [GH] Added remote administration documentation
+ [GH] Added getting started documentation
+ [GH] Added configuration settings documentation
+ [GH] Removed a bunch of configuration comments from the ```npdstracker.ini``` file for cleanliness
+ [GH] Reinstated classes on unordered lists so they can be styled independently
+ [GH] Wrapped link lists in ```<nav>``` elements
+ [GH] Renamed several configuration parameters
+ [GH] Previous ```npdstracker.ini``` files are no longer compatible with this version of the tracker

### 0.1.36 — 28 August 2012

+ [MA] Split out display of HTML status into its own method
+ [MA] Updated HTTP GET processing to support rendering the HTML status page
+ [MA] Fixed stylesheet configuration and template insertion
+ [RP] Added host name and link to the header and page title
+ [MA] Removed rogue slash in ```npdstracker.java```
+ [MA] Added manifest file
+ [MA] Added advanced configuration documentation
+ [GH] Added Java compatibility documentation
+ [GH] Converted template to HTML5
+ [GH] Removed deprecated elements and formatting attributes
+ [GH] Minor formatting and nomenclature edits abound!
+ [GH] Converted read me to Markdown format

### 0.1.35 — 08 September 2010

+ [GH] Moved source to GitHub
+ [GH] Added user definable stylesheet template
+ [GH] Moved stylesheet from template.html into its own file
+ [GH] Moved version history from Java source file into read me
+ [GH] Minor text formatting updates
+ [GH] Changed all document encoding to UTF-8

### 0.1.34 — 06 April 2006

+ [VR] Added ```shouldIlog``` setting in INI file to disable all logging

### 0.1.33 — 22 February 2004

+ [MP] Added SSI tag for displaying the number of registered clients in the template
+ [MP] Made the ```<meta-refresh/>``` SSI XHTML-compatible
+ [MP] Changed the default port number to 3680
+ [MP] Refined the listing table elements for extensive use of CSS
+ [MP] Added code to allow one hostname with a private IP to register

### 0.1.32 — 02 May 2003

+ [VR] Makes sure that the hostname isn’t empty on a REGUP command
+ [VR] Changed ```kRTFMStr``` to reflect new NPDS website address
	
### 0.1.31 — 30 July 2002

+ [PG] The tracker can now listen on several ports

### 0.1.30 — 28 April 2002

+ [PG] Fixed the bug in 192.168.* address filtering

### 0.1.29 — 16 March 2002

+ [PG] Fixed the bug in processing of console commands, so now lowercase commands work
+ [PG] The console now says “server validation test started” before it finishes the validation
+ [PG] The tracker was waiting forever for an answer from fellow trackers, which was bad
+ [PG] Added style information to colorize the table
+ [PG] The tracker performs a verification on startup
+ [PG] Vector copy constructor is no longer called, so this should compile on Java < 1.2 (but it will have problems because of bugs in the JVM)
+ [PG] The tracker now checks the hosts passed as REGUP parameters to be correct (to resolve and to be not private)

### 0.1.28 — 29 November 2001

+ [PG] No longer dumps the shared trackers to npdscmd.txt
+ [PG] Fixed the ```shareEnabled``` .ini read process (```getBoolean``` is not what we want, it’s ```valueOf```)

### 0.1.27 — 20 October 2001

+ [VR] Now saves registered client information for automatic reloading upon a crash or restart

### 0.1.26 — 18 August 2001

+ [PG] Fixed the ```java.lang.ArrayIndexOutOfBoundsException``` bug introduced in version 0.1.24

### 0.1.25 — 06 June 2001

+ [PG] Fixed the ```java.lang.IllegalArgumentException: timeout value is negative``` bug (well, I think)

### 0.1.24

+ [PG] Fixed the reload 0 bug
+ [PG] Fixed the lock bug
+ [PG] Set the timeout to 20 seconds (was 10 seconds) so my Newton is no longer considered as down
+ [PG] Added last validation template element

### 0.1.23

+ [VR] No actual code changes, but to workaround a bug in JDK < 1.1 you must use Java 1.2 or better and set the ```sun.net.inetaddr.ttl``` property to 0 on the command line, like this: ```java -Dsun.net.inetaddr.ttl=0 npdstracker```

### 0.1.22

+ [PG] Implemented the template stuff
+ [PG] Reorganized the ```ProcessQuery``` method
+ [PG] There is now a single class with embedded sub classes (so we’ll have a single .java binary)
+ [PG] Improved answer to the GET request (with many headers now)

### 0.1.21

+ [PG] No longer uses the bugged URL interface to check if Newton clients are up. Instead, I use a socket
+ [PG] The REGUP command tokenizer now accepts any standard token (and no longer only spaces which wasn’t protocol-compliant)
+ [PG] Added several syntax checking with an appropriate status message

### 0.1.20

+ [VR] Retrieving SHARE records from other trackers now works!

### 0.1.19

+ [VR] Will now read ```optionsfile``` and ```cmdfile``` from default location
+ [VR] Fixed up options file parsing
+ [VR] HTML now easily customizable using ```npdstracker.ini```, ```header.html```, ```footer.html```

### 0.1.18

+ [VR] Minor admin console fixes
+ [VR] Implemented command-line arguments for ```logfile```, ```cmdfile```, ```optionsfile```
+ [VR] Now can specify files to log to, read options from, or read initial commands from
+ [VR] Finally implemented LOGS command in admin console
+ [VR] Code and syntax cleanups
+ [PG] Use ```SimpleDateFormat``` in the ```ReturnRFCTime``` function. (this also fixes the GMT bug)
+ [PG] Fixed a little HTML bug

### 0.1.17

+ [VR] VTEST command added to the admin console
+ [VR] SHARE command is now actually sent (forgot to flush ```PrintWriter```)
+ [VR] Changed return code of SHARE command if sharing is disabled

### 0.1.16

+ [PG] The tracker now only sleeps in the validator loop

### 0.1.15

+ [PG] Now the tracker always uses CRLF except for the log
+ [PG] Fixed a little bug in the validation date

### 0.1.14

+ [PG] Now supports multiple connections

### 0.1.13

+ [VR] Trying to fix intermittent bug in client validation code

### 0.1.12

+ [VR] More bugs in client validation fixed

### 0.1.11

+ [VR] Added more features to GET code
+ [VR] Fixed up RFC times somewhat
+ [VR] Fixed bug in validation code with “Connection Refused” socket exceptions

### 0.1.10

+ [VR] Fixed bug with exception passing and tracker socket code
+ [VR] Rewrote GET code to return a nice HTML 4.01 compliant table

### 0.1.9

+ [VR] Admin console is now complete save for the LOGS command
+ [VR] Now retrieves, updates, and properly handles records from other trackers via the SHARE command

### 0.0.8

+ [VR] Added SHARE, ABOUT, VERIFY, and STATS command to admin console
+ [VR] Fixed the problem with the verification code repeating many times per minute
+ [VR] Added a REGUP counter for the STATS admin command

### 0.0.7

+ [VR] Now accepts commands regardless of case
+ [VR] Checks for an existing host entry before processing a REGUP
+ [VR] Started implementing the ADMIN command for live configuration of the tracker

### 0.0.6

+ [VR] First implementation of SHARE command
+ [VR] Cleaned up some return codes
+ [VR] Fixed some status handling and checking

### 0.0.5

+ [VR] Client validation actually works! Woo hoo!
+ [VR] Implemented a socket timeout so that the loop doesn’t get stuck
+ [VR] Removed the QHTML extended command

### 0.0.4

+ [VR] Implemented Return Codes

### 0.0.3

+ [VR] Fixed GET method so it returns DTD HTML 2.0 compliant pages
+ [VR] Rewrote internal storage of records for extensibility
+ [VR] Now we store time client last checked and its status
+ [VR] Implemented ```QueryMethod``` function for finding a record when we do a REGDN (could also use this method for an possible SRCH command later)

### 0.0.2

+ [VR] Added QHTML method for returning HTML-ized results
+ [VR] Added GET method support for returning a basic web page to web browsers