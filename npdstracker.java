// NPDS Tracker Server
// Developed by Victor Rehorst and Paul Guyot
// Additional contributions by Morgan Aldridge, Grant Hutchinson, Ron Parker, and Manuel Probsthain
// Many thanks to Matt Vaughn for developing NPDS in the first place

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.*;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;


public class npdstracker
{
	//////////////////////////////////////////////////////////////////////////////
	//	VARIABLES AND CONSTANTS
	//////////////////////////////////////////////////////////////////////////////

	// ======= Constants ======= //

	// Version Information
	public static final String serverdesc = "NPDS Tracker Server for Java";
	public static final int majorversion = 0;
	public static final int minorversion = 1;
	public static final int build = 39;
	public static final int protocolversion = 1;
	public static final String versionStr = majorversion + "." + minorversion + "." + build + " beta";
	public static final String kServerStr = "Victor Rehorst's NPDS Tracker Server " + versionStr;
	public static final String kUserAgentStr = "Mozilla/5.0 (compatible; " + kServerStr + "; Java)";

	// 200, 400, 403, and 404 are standard HTTP codes
	// 202 is a special NPDS code
	public static final int HTTP_OK = 200;
	public static final int NPDS_OK = 202;
	public static final int HTTP_ERR = 400;
	public static final int HTTP_FORBID = 403;
	public static final int HTTP_NOTFOUND = 404;

	public static final int kValidateTimeUnit = 60000;	// 60000 =  1 minute in milliseconds
	public static final int kRefreshTimeUnit = 1000;	// 1000  =  1 second in milliseconds
	public static final int kTimeout = 20000;			// 20000 = 20 seconds in milliseconds
	public static final String defaultOptionsFile = "npdstracker.ini";
	public static final String defaultCmdFile = "npdscmd.txt";

	// Messages
	public static final String kRTFMStr = " Please check out the protocol before telnetting to the tracker (http://npds.free.fr/)";
	public static final String kAlreadyRegisteredStr = " host already exists in list";
	public static final String kNotRegisteredStr = " host is unknown";
	public static final String kInvalidHostStr = " host is invalid (doesn't resolve, check your client configuration)";
	public static final String kPrivateHostStr = " host address is for private network (check your client configuration)";
	public static final String kWeirdPortStr = " Weird port (not an integer)";
	public static final String kWeirdPortValueStr = " Weird port (not within 1-65535)";
	public static final String kUnsupportedVersionStr = " This version of the protocol is not supported by this tracker";
	
	// Default port
	public static final int DEFAULT_PORT = 3680;
	
	// ======= Variables ======= //

	// Server variables: time between validations, number of attempts to make before server is 
	// dropped, whether or not to share with other trackers, hit counter, admin password, log file
	// location all of these can be set at runtime using the -o switch
	public static int validateTime = 20;
	public static int validateTries = 3;
	public static boolean shareEnabled = true;
	public static String adminPass = "qwerty";
	public static Vector<Integer> kPort;
	public static String logFile = "";
	public static boolean logVerbose = true;
	public static String templateFile = "";
	public static String stylesheetFile = "";
	public static String imageDir = "";
	public static String cmdfile = defaultCmdFile;
	public static String hostName = "";
	public static String hostLink = "";
	// Define private host string
	public static String acceptPrivateHost = "";

	// Runtime variables: these are the data structures and other values used by the server while it is running
	// I put all Newton info in a single Vector because I have a semaphore for accessing it and it is easier like this.
	public static Vector<THostInfo> mHostInfoVector = new Vector<THostInfo>();
	// Identically, I put all server info in a single Vector.
	public static Vector<TServerInfo> mSharingInfoVector = new Vector<TServerInfo>();
	public static int hitcounter = 0;
	public static int regcounter = 0;
	// Non 0 if a validation is in progress, 0 otherwise.
	public static int mValidationInProgress = 0;
	// Time the last validation ended (in RFC format):
	public static String mLastValidation = "never";
	
	// Validator and Server
	// (Victor uses a lot of public static variables, he surely has a good reason for that)
	public static TValidator mValidator;
	
	// Vector with all the servers out there.
	private Vector <TServer>mServers;

	private static ExecutorService mExecutorService;
	
	private static DateFormat mRFCGMTFormatter;
	static
	{
		mRFCGMTFormatter = new SimpleDateFormat("EEE',' d-MMM-yyyy HH:mm:ss 'GMT'", Locale.US);
		mRFCGMTFormatter.setTimeZone(TimeZone.getTimeZone("Africa/Casablanca"));
	}

	//////////////////////////////////////////////////////////////////////////////
	//	EMBEDDED CLASSES
	//////////////////////////////////////////////////////////////////////////////
	
	// A class to handle exceptions when parsing the query
	public static class TQueryException extends Exception
	{
		private static final long serialVersionUID = -5454365483023069659L;

		public TQueryException(String s)
	    {
			super(s);
	    }
	}
	
	// A class to validate the Newton servers
	public static class TValidator extends Thread
	{
		public Date	mLastCheck;
		public Date	mNextCheck;

		public void run ()
		{
			// Initialization of variables to know when to check the registered Newtons.
			mNextCheck = new Date();

			// I'm looping forever. (I will be killed by the application as it quits).
			
			try {
				while (true)
				{
					try 
					{
						Date now = new Date();
						
						if (now.after(mNextCheck))
						{
							// get the current date
							mLastCheck = new Date();
							mNextCheck.setTime(mLastCheck.getTime() + (npdstracker.validateTime * npdstracker.kValidateTimeUnit));
							npdstracker.validateServers();
						}
						
						now = new Date();	// Updates the now value.
						
						// Let's sleep until next check if we have to
						
						long timeout = mNextCheck.getTime() - now.getTime();
						if (timeout > 0)
							sleep( timeout );
					} catch (InterruptedException e) {
						// Ignore any interrupt.
					}
				}	// while (true)
			} catch (Exception e)
			{
				// Oops, some exception occured.
				npdstracker.logMessage("TValidator: Exception " + e + " occurred");
				e.printStackTrace();
			}
		}
	}

	// ============================================================	//

	// A class to handle a connection the Newton servers.
	public static class TConnection extends Thread
	{
		// Variables
		private Socket mSocket;
		
		// Constructor
		TConnection ( Socket inSocket )
		{
			mSocket = inSocket;
		}
		
		// Thread entry point
		public void run ()
		{
			try {
				mSocket.setSoTimeout( npdstracker.kTimeout );
			
				BufferedReader in = null;
				PrintWriter out = null;
				String result;
				try
				{
					in = new BufferedReader( new InputStreamReader( mSocket.getInputStream() ) );
					out = new PrintWriter(new OutputStreamWriter( mSocket.getOutputStream() ) );
					npdstracker.logMessage("Handling connection from " + mSocket.getInetAddress().toString() );
					result = in.readLine();
					// pass the command to the query processor
					if (result != null)
					{
						result.toUpperCase();
						npdstracker.ProcessQuery( result, in, out, mSocket );
					}
				}
				catch (Exception e) 
				{
					System.err.println( "Look! Some exception!" );
					System.err.println( e );
					// Easier than calling System.getProperty("line.separator")
				}
			
				npdstracker.logMessage("Closing connection from " + mSocket.getInetAddress().toString());
				mSocket.close();
				System.gc();
			} catch (Exception e)
			{
				// Oops, some exception occured.
				npdstracker.logMessage("TConnection: Exception " + e + " occurred");
			}
		}
	}

	// ============================================================	//

	// A class for servers that listen to Newtons.
	public static class TServer extends Thread
	{
		// Variables
		private ServerSocket mServer;
		
		// Constructor
		TServer( int inPort ) throws IOException
		{
			mServer = new ServerSocket( inPort );
		}
		
		// Thread entry point
		public void run ()
		{
			// I'm looping waiting for connections.
			while (true)
			{
				try 
				{
					// Wait until a connection arrives.
					Socket s = mServer.accept();
				
					// Let a connection object handle the socket
					TConnection thisConnection = new TConnection( s );
					npdstracker.mExecutorService.execute(thisConnection);

				} catch (Exception e)
				{
					// Oops, some exception occured.
					npdstracker.logMessage("TServer: Exception " + e + " occurred");
				}
			}
		}
	}

	// ============================================================	//
	// * THostInfo *
	// ============================================================	//
	// A class to handle a data about Newton servers
	// I need such a class because of semaphores (locks).

	public static class THostInfo
	{
		// The Newton's hostname (including IP)
		public String mName;	// The string as shown in the logs and in the table.
		public String mHost;	// The host name only (used to check the server)
		public int mPort;		// The port only (default is 80)
		// Unique ID of the Newton (NOT CURRENTLY USED)
		public String mHash;
		// Plaintext description of the Newton
		public String mDesc;
		// Time this Newton was last validates (string in RFC format)
		public String mLastValidation;
		// Current status of this Newton: 0 is up, any other number is the number
		// of unsuccessful attempts made to validate, -1 is a SHARE record
		public int mStatus;
		// Unused yet. Because if a tracker dies, I may need to warn the Newton and
		// tell it that personally, I am up. (Sounds cool, doesn't it?)
		public TServerInfo mServer;
	}

	// ============================================================	//
	// * TServerInfo *
	// ============================================================	//
	// A class to handle a data about other tracker servers
	// I need such a class because of semaphores (locks).
	public static class TServerInfo
	{
		// List of external trackers to get data from
		public String mHost;
		public String mPort;
	}

	//////////////////////////////////////////////////////////////////////////////
	//	UTILITY FUNCTIONS
	//////////////////////////////////////////////////////////////////////////////
	
	// ====================================================================	//
	// * void logMessage( String ) [static]
	// ====================================================================	//
	// Sends a message to the System.out or the log file, if specified
		
	public static void logMessage(String message)
	{
		if (logVerbose)
		{
			Date theDate = new Date();
			if (logFile.equals(""))
				System.out.println(theDate.toString() + "   " + message);
			else
			{
				try {
					FileWriter outlogFile = new FileWriter(logFile, true);
					outlogFile.write(theDate.toString() + "   " + message + "\r\n");
					outlogFile.flush();
					outlogFile.close();
				} catch (IOException e) {System.out.println(theDate.toString() + "   FATAL - can't write to log file: " + logFile);}
			}
		}
	}

	// ====================================================================	//
	// * int QueryRecord( String ) [static, private]
	// ====================================================================	//
	// Given a name, returns the host index or -1 if it cannot be found.

	private static int QueryRecord (String host)
	{
		// Looks for a host with a given name.
		// Searches the mHostInfoVector list and returns an index or -1
		int index_i;
		
		synchronized (mHostInfoVector)
		{
			for (index_i = 0; index_i < mHostInfoVector.size(); index_i++)
			{
				THostInfo theInfo = (THostInfo) mHostInfoVector.elementAt(index_i);
				if (host.equals(theInfo.mName))
					return index_i;
			}
		}
		return -1;
	}

	// ====================================================================	//
	// * void ReturnCode( int, String, PrintWriter ) [static, private]
	// ====================================================================	//
	// Given a code and a message, outputs a NPDS/TP status code line.

	private static void ReturnCode(int codetype, String message, PrintWriter out)
	{
		out.print(codetype);
		out.flush();
		if ((codetype == HTTP_OK) || (codetype == NPDS_OK))
		{
			out.print(" OK" + message + "\r\n");
			out.flush();
		}
		else if (codetype == HTTP_ERR)
		{
			out.print(" Bad Request" + message + "\r\n");
			out.flush();
		}
		else if (codetype == HTTP_NOTFOUND)
		{
			out.print(" File Not Found" + message + "\r\n");
			out.flush();
		}
		else
		{
			out.print(" Undefined status" + message + "\r\n");
			out.flush();
		}
	}

	// ====================================================================	//
	// * String ReturnRFCTime( Date ) [static, private]
	// ====================================================================	//
	// Given a date, returns a RFC #1123 string.
	// (cf http://www.faqs.org/rfc/rfc1123.txt)

	private static String ReturnRFCTime(Date thisdate)
	{
		synchronized (mRFCGMTFormatter)
		{
	            return mRFCGMTFormatter.format(thisdate);
        	}

	}

	// ====================================================================	//
	// * String ReturnAbout( PrintWriter ) [static, private]
	// ====================================================================	//
	// Outputs to PrintWriter the about message. (the format is defined in the NPDS/TP protocol)

	private static void ReturnAbout(PrintWriter out)
	{
		logMessage("Processing ABOUT command");
		out.print("protocol: " + protocolversion + "\r\n");
		out.print("period: " + validateTime + "\r\n");
		out.print("tries: " + validateTries + "\r\n");
		out.print("share: " + shareEnabled + "\r\n");
		out.print("about: " + serverdesc + " version " + versionStr);
		out.print(" running on Java " + System.getProperty("java.version") + " by " + System.getProperty("java.vendor") + " ");
		out.print(System.getProperty("os.name") + " " + System.getProperty("os.arch") + " " + System.getProperty("os.version") + "\r\n");
		out.flush();
	}

	// ====================================================================	//
	// * void printUsage( void ) [static, private]
	// ====================================================================	//
	// Outputs to PrintWriter the about message. (the format is defined in the NPDS/TP protocol)

	private static void printUsage()
	{
		System.out.println("Java NPDS Server Tracker " + versionStr);
		System.out.println("Usage: java npdstracker [-h] [-c cmdfile] [-o optionsfile]");
		System.out.println("       -h : Display this command usage information");
		System.out.println("       -c cmdfile : Specifies the path of the npdscmd.txt file containing any commands to run at startup (defaults to none)");
		System.out.println("       -o optionsfile : Specifies the path of the npdstracker.ini file containing configuration and option settings (defaults to settings at compile time)");
	}

	// ====================================================================	//
	// * void ParseOptionsFile( String ) [static, private]
	// ====================================================================	//
	// Reads the server options from the INI file and parses the appropriately.

	private static void ParseOptionsFile(String optionsfile) throws FileNotFoundException, IOException
	{
		BufferedReader optionsreader = new BufferedReader (new FileReader(optionsfile));
		String tempoption = "";
		int linenumber = 1;
		tempoption = optionsreader.readLine();
		while (tempoption != null)
		{
			StringTokenizer st = new StringTokenizer(tempoption, " ");
			String garbage = "";
			try {
				garbage = st.nextToken();
			} catch (NoSuchElementException e) {}
			try {
			if (tempoption.startsWith("kPort"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("="))) {
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				} else {
					kPort = new Vector<Integer>();
					
					while (true) {
						try {
							garbage = st.nextToken();
							int thePort = Integer.parseInt(garbage);

							// Add that port to the list.
							kPort.addElement(new Integer(thePort));
							logMessage("Port found: " + thePort);
						} catch (NoSuchElementException aNSEE) {
							break;
						}
					}
				}
			}
			else if (tempoption.startsWith("adminPass"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					adminPass = st.nextToken();
			}
			else if (tempoption.startsWith("validateTime"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Eerror reading npdstracker.ini on line " + linenumber);
				else
					validateTime = Integer.parseInt(st.nextToken());
			}
			else if (tempoption.startsWith("validateTries"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					validateTries = Integer.parseInt(st.nextToken());
			}
			else if (tempoption.startsWith("shareEnabled"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					shareEnabled = Boolean.valueOf(st.nextToken()).booleanValue();
			}
			else if (tempoption.startsWith("shareServer"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
				{
					TServerInfo theServerInfo = new TServerInfo();
					theServerInfo.mHost = st.nextToken();
					theServerInfo.mPort = st.nextToken();
					mSharingInfoVector.addElement(theServerInfo);
				}
			}
			else if (tempoption.startsWith("logFile"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					logFile = st.nextToken();
			}
			else if (tempoption.startsWith("logVerbose"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					logVerbose = Boolean.valueOf(st.nextToken()).booleanValue();
			}
			else if (tempoption.startsWith("pageTemplate"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					templateFile = st.nextToken();
			}
			else if (tempoption.startsWith("cssTemplate"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					stylesheetFile = st.nextToken();
			}
			else if (tempoption.startsWith("imageDir"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
				{
					imageDir = st.nextToken();
					File imageDirFile = new File(imageDir);
					if ( imageDirFile.exists() == false )
					{
						logMessage( "Error image directory " + imageDir + " does not exist.  Defaulting to images in working directory.");
						imageDir = "images";
					}
				}
			}
			else if (tempoption.startsWith("trackerName"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					hostName = st.nextToken();
			}
			else if (tempoption.startsWith("trackerHost"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					hostLink = st.nextToken();
			}			
			else if (tempoption.startsWith("privateHostToAccept"))
			{
				garbage = st.nextToken();
				if (!(garbage.equals("=")))
					logMessage("Error reading npdstracker.ini on line " + linenumber);
				else
					acceptPrivateHost = st.nextToken();
			}
			else if (tempoption.startsWith("#") || garbage.equals(""))
			{
				// do nothing
			}
			} catch (NoSuchElementException e) { System.err.print("Error in options parsing " + optionsfile + " line " + linenumber + "\r\n");}
			tempoption = optionsreader.readLine();
			linenumber++;
		}
		optionsreader.close();
	}

	// ====================================================================	//
	// String StrReplace( String, String, String ) [static, public]
	// ====================================================================	//
	// Replaces every occurrence of a string by another string in a string.
	
	public static String StrReplace( String inString, String inPattern, String inReplacement )
	{
		String result = inString;
		int fromIndex = 0;
		while (true)
		{
			fromIndex = result.indexOf( inPattern, fromIndex );

			if (fromIndex == -1)
				break;
						
			int newIndex = fromIndex + inPattern.length();
			result = result.substring( 0, fromIndex ) + inReplacement + result.substring( newIndex );
			fromIndex = newIndex;
		}
		
		return result;
	}

	//////////////////////////////////////////////////////////////////////////////
	//	MAIN FUNCTIONS
	//////////////////////////////////////////////////////////////////////////////

	// ====================================================================	//
	// * void main( String[] ) [static]
	// ====================================================================	//
	// Main entry point.

	public static void main(String[] args)
	{
		String tempoptionsfile = defaultOptionsFile;
		// Parse command line options here
		// -l [logfile]     : The file to write logs to, will be created if it doesn't exist, otherwise appended to (unused?)
		// -c [cmdfile]     : The file to read initial commands from (REGUPs)
		// -o [optionsfile] : The file to read other options from
		for (int i = 0; i < args.length; i++)
		{
			if (args[i].equals("-c"))
			{
				cmdfile = args[i+1];
				i++;
			}
			else if (args[i].equals("-o"))
			{
				tempoptionsfile = args[i+1];
				i++;
			}
			else
			{
				System.out.println("Invalid arguments");
				printUsage();
				System.exit(0);
			}
		}
		// start the tracker server
        new npdstracker(cmdfile, tempoptionsfile);
	}

	// ====================================================================	//
	// * npdstracker() [private]
	// ====================================================================	//
	// Constructor
	
	private npdstracker(String tempcmdfile, String tempoptionsfile)
	{
		mExecutorService = Executors.newCachedThreadPool();
		String tempcmd = "";
		try
		{
			// Default values for options.
			kPort = new Vector<Integer>();
			kPort.addElement(new Integer(DEFAULT_PORT));
			
			if (!(tempoptionsfile.equals("")))
				ParseOptionsFile(tempoptionsfile);

			if (!(tempcmdfile.equals("")))
			{
				BufferedReader cmdreader = new BufferedReader(new FileReader(tempcmdfile));
				PrintWriter cmdwriter = new PrintWriter(new FileWriter(FileDescriptor.out));
				tempcmd = cmdreader.readLine();
				while (tempcmd != null)
				{
					ProcessQuery(tempcmd, null, cmdwriter, null);
					tempcmd = cmdreader.readLine();
				}
				cmdreader.close();
			}

			// Let's create the validation thread.
			mValidator = new TValidator();
			mExecutorService.execute(mValidator);
					
			mServers = new Vector<TServer>();
			
			// For each port, create a server.
			int nbServers = kPort.size();
			int indexServers;
			for (
					indexServers = 0;
					indexServers < nbServers;
					indexServers++) {
				int thePort = ((Integer) kPort.get(indexServers)).intValue();
				TServer theServer;
				try {
					theServer = new TServer(thePort);
				} catch (IOException theIOE) {
					logMessage("npdstracker: Cannot start server on port " + thePort + " (" + theIOE + ")");
					continue;
				}
				mExecutorService.execute(theServer);
				mServers.addElement(theServer);				
			}

			// ServerSocket timeout: I'll wait forever until a connection arrives.
			// mServer.setSoTimeout( 0 ); // (this is default)
		} catch(Exception e)
		{
			logMessage("npdstracker: Exception " + e + " occurred");
		}

		// Hello message
		logMessage(serverdesc + " version " + versionStr + " started");
	}
	

	// ====================================================================	//
	// void ProcessQuery( String, BufferedReader, PrintWriter, Socket ) [static]
	// ====================================================================	//
	// Function to handle the queries. (I need to have it public).

	public static void ProcessQuery(String query, BufferedReader in, PrintWriter out, Socket socket) throws SocketException, IOException
	{
		// I get the query command.
		// I now use exceptions for a cleaner way to procede (at least, I think so)
		
		try {	// Global try.
			// tokenize me, baby
			StringTokenizer st = new StringTokenizer( query );
				// We now accept any standard delimiter, hence HT
				// (the protocol says we should)
				// We also accept the other delimiters, but anyway, there shouldn't be any other delimiter
				// in the host name or the REGUP command, so we don't mind.
			String theCommand = st.nextToken().toUpperCase();

			// I do handle several commands.
			
			if (theCommand.equals("ABOUT"))
			{
				ReturnCode(HTTP_OK, "", out);
				ReturnAbout(out);
			} else if (theCommand.equals("REGUP"))
			{
				logMessage("Processing REGUP command");

				// Throw out the first token
				String hname = st.nextToken();
				String hdesc = query.substring(hname.length() + 7);	// REGUP hname. Note: this isn't really protocol 1.1 compliant.

				if (hname.equals("NPDS/TP"))
				{
					logMessage("v2 command received - not yet supported");
					throw new TQueryException ( kUnsupportedVersionStr );
				}
				else if ((QueryRecord(hname) == -1))
				{
					// Host is not in our list. Let's add it.

					THostInfo theInfo = new THostInfo();
					theInfo.mName = hname;
					// I process the host name (without the port) and the port if ever there is a colmun in the host.
					StringTokenizer host_st = new StringTokenizer(hname, ":");
					theInfo.mHost = host_st.nextToken();

					// First, check the host. We won't accept hosts that don't resolve or that are for private networks.
					byte theHostAddressAsBytes[];
					try {
						InetAddress theHostAddress = InetAddress.getByName( theInfo.mHost );
						
						theHostAddressAsBytes = theHostAddress.getAddress();
					} catch (UnknownHostException theException) {
						throw new TQueryException ( kInvalidHostStr );
					}
					
					// Check if the explicitely allowed hostname with a private IP is registering
					if (acceptPrivateHost.equals(theInfo.mHost))
					{
						// Accept host and write that to log 
						logMessage("Private IP host " + theInfo.mHost + " has now registered");
					} else {
						// Don't accept host and check that it's not any other private network address.
						// Don't know for IPv6, so I only work with 4 bytes addies.
						if (theHostAddressAsBytes.length == 4)
						{
							// 10.0.0.0/8
							if (theHostAddressAsBytes[0] == 10)
							{
								throw new TQueryException ( kPrivateHostStr );
							}
							// 172.16.0.0/12
							if ((theHostAddressAsBytes[0] == (byte) 172) && ((theHostAddressAsBytes[1] & 0xF0) == 16))
							{
								throw new TQueryException ( kPrivateHostStr );
							}
							// 192.168.0.0/16
							if ((theHostAddressAsBytes[0] == (byte) 192) && (theHostAddressAsBytes[1] == (byte) 168))
							{
								throw new TQueryException ( kPrivateHostStr );
							}
							// 0.0.0.0/8 or empty host string
							if (theHostAddressAsBytes[0] == 0)
							{
								throw new TQueryException ( kPrivateHostStr );
							}
						}
					}
					
					if (host_st.hasMoreTokens())
					{
						try {
							theInfo.mPort = Integer.parseInt(host_st.nextToken());
						} catch (NumberFormatException theException)
						{
							logMessage("Server \"" + hname + " " + hdesc + "\" wasn't inserted into the list because its port isn't correct (not an integer)");
							throw new TQueryException ( kWeirdPortStr );
						}
						
						if ((theInfo.mPort < 1) || (theInfo.mPort > 65535))
						{
							logMessage("Server \"" + hname + " " + hdesc + "\" wasn't inserted into the list because its port isn't correct (not between 1 - 65535)");
							throw new TQueryException ( kWeirdPortValueStr );
						}
					} else {
						theInfo.mPort = 80;
					}
					
					theInfo.mDesc = hdesc;
					Date tempDate = new Date();
					theInfo.mLastValidation = ReturnRFCTime(tempDate);
					theInfo.mStatus = 0;

					// Synchronized is not required here because the addElement method is synchronized.
					mHostInfoVector.addElement(theInfo);
					
					ReturnCode(HTTP_OK, "", out);

					logMessage("Inserted \"" + hname + " " + hdesc + "\" into the list");
					logMessage(mHostInfoVector.size() + " hosts now in the list");
					regcounter++;
				}
				else
				{
					// host is already in our list, 
					logMessage("Did not insert \"" + hname + "\" into list - host is already registered");
					throw new TQueryException ( kAlreadyRegisteredStr );
				} // if ((QueryRecord(hname) == -1)) ... else 
				saveServers();
			}
			else if (theCommand.equals("REGDN"))
			{
				logMessage("Processing REGDN command");

				String host = null;
				
				// throw out the first token
				host = st.nextToken();
					
				// Check there is no token left (there shouldn't be).
				if (st.hasMoreTokens())
				{
					logMessage("Bad syntax: the tokenizer found more than two elements");
					throw new TQueryException ( kRTFMStr );
				}
					
				int tempindex;
				
				// I need the tempindex, nobody should change the list before I do remove this element
				// But, because I don't want to lock the other connections, I'd better do only removal
				// in the synchronized statement
				synchronized (mHostInfoVector)
				{
					tempindex = QueryRecord(host);
					if (tempindex != -1)
					{
						mHostInfoVector.removeElementAt(tempindex);
					}
				}
				if (tempindex == -1)
				{
					logMessage("REGDN failed - host not found in list");
					throw new TQueryException ( kNotRegisteredStr );
				}
				else
				{
					ReturnCode(HTTP_OK, "", out);
					logMessage("Removed \"" + host + "\" from the list");
					logMessage(mHostInfoVector.size() + " hosts now in the list");
				} // if (tempindex == -1)
				saveServers();
			}
			else if (theCommand.equals("QUERY"))
			{
				logMessage("Processing QUERY command");
				ReturnCode(NPDS_OK, "", out);
				// Print out all of the Newtons registered
				synchronized (mHostInfoVector)
				{
					for (int index_i = 0; index_i < mHostInfoVector.size(); index_i++)
					{
						THostInfo theInfo = (THostInfo) mHostInfoVector.elementAt(index_i);

						out.print(theInfo.mName + " " + theInfo.mDesc + " " + theInfo.mLastValidation + " " + theInfo.mStatus + "\r\n");
						out.flush();
					}
				}
			}
			else if (theCommand.equals("SHARE"))
			{
				// Return list of entries in SHARE format
				logMessage("Processing SHARE command");
				if (shareEnabled == true)
				{
					ReturnCode(HTTP_OK, "", out);
					synchronized (mHostInfoVector)
					{
						for (int index_i = 0; index_i < mHostInfoVector.size(); index_i++)
						{
							THostInfo theInfo = (THostInfo) mHostInfoVector.elementAt(index_i);
							if (!(theInfo.mStatus == -1 || theInfo.mStatus == -2))
							{
								out.print("Address: " + theInfo.mName + "\tLast Verified: "
									+ theInfo.mLastValidation + "\t");
								String statstring;
								if (theInfo.mStatus == 0)
									statstring = "UP";
								else
									statstring = "DOWN";
								out.print("Status: " + statstring + "\tDescription: " + theInfo.mDesc + "\r\n");
								out.flush();
							}
						}
					}
				}
				else
				{
					ReturnCode(HTTP_FORBID, " server not sharing records", out);
					out.print("no-entries" + "\r\n");
					out.flush();
				}
			}

			else if (theCommand.equals("GET"))
			{
				String HTTPDocStr = st.nextToken();
				File stylesheet = new File(stylesheetFile);
				
				logMessage("Processing GET command" );
				if (HTTPDocStr.equals("/"))
				{
					htmlStatusPage(HTTPDocStr, in, out, socket);
				}
				else if (HTTPDocStr.equals("/" + stylesheet.getName()) && stylesheet.exists())
				{
					stylesheetPage(stylesheet, in, out, socket);
				}
				else if ( HTTPDocStr.endsWith(".gif") == true )
				{
					Path path = Paths.get( HTTPDocStr );
					File image = new File( imageDir + "/" + path.getFileName() );
					if ( image.exists() == true ) 
					{
					    imageFile( image, in, out, socket );
					}
					else
					{
						logMessage("File '" + HTTPDocStr + "' not found");
						ReturnCode(HTTP_NOTFOUND, "", out);
					}
				}
				else 
				{
					logMessage("File '" + HTTPDocStr + "' not found");
					ReturnCode(HTTP_NOTFOUND, "", out);
				}
			}
			else if (query.startsWith("ADMIN"))
			{
				logMessage("processing ADMIN command");
				try {
					String password = st.nextToken();
					if (password.equals(adminPass))
						adminConsole(in, out, socket);
					else
						throw new TQueryException( " Incorrect admin password" );
				} catch (NoSuchElementException e)
				{
					throw new TQueryException( "" );
				}
			}
			else
				throw new TQueryException("");			
		} catch (NoSuchElementException theException)
		{
			logMessage("Bad syntax");
			ReturnCode( HTTP_ERR, kRTFMStr, out );
		} catch (TQueryException theQueryException) {
			ReturnCode( HTTP_ERR, theQueryException.getMessage(), out );
		}
	}
	
	// ==================================================================== //
	// void htmlStatusPage( String, BufferedReader, PrintWriter, Socket ) [static, private]
	// ==================================================================== //
	
	private static void htmlStatusPage( String HTTPDocStr, BufferedReader in, PrintWriter out, Socket socket ) throws SocketException, IOException
	{
		// return a WEB PAGE
		out.print("HTTP/1.0 ");
		ReturnCode(HTTP_OK, "", out);
		hitcounter++;

		// I prepare the result strings.
		// Find out what my address is. I use the host header if present.
		String urlStr = socket.getLocalAddress().getHostName();
		String requestLine = in.readLine();
		while ((requestLine != null) && (requestLine.length() > 0) )
		{
			StringTokenizer headerTk = new StringTokenizer( requestLine, ": \t" );
			if (headerTk.hasMoreTokens() == false)
			{
				break;
			}
			String theHeader = headerTk.nextToken().toLowerCase();
			if (theHeader.equals("host"))
			{
				// Cool, I have a host header.
				urlStr = "http://" + headerTk.nextToken();
				break;
			}
			
			requestLine = in.readLine();
		}
		
		int thePort;
		thePort = socket.getLocalPort();

		if (thePort != 80) {
			urlStr += ":" + thePort;
		}
		
		urlStr += HTTPDocStr;

		String tableStr = "<table>\r\n<tr>\r\n<th>Status</th>\r\n<th>Server</th>\r\n<th>Last Verified</th>\r\n</tr>\r\n";
			
		int index_i;
		synchronized (mHostInfoVector)
		{
			for (index_i = 0; index_i < mHostInfoVector.size(); index_i++)
			{
				THostInfo theInfo = (THostInfo) mHostInfoVector.elementAt(index_i);
				String classStr;	// String representing the class of the raw.
				String labelStr;	// String for the label.
				
				switch ( theInfo.mStatus )
				{
					case -1:
						classStr = "up-sharing";
						labelStr = "Up (Sharing)";
						break;
						
					case -2:
						classStr = "down";
						labelStr = "Down (Sharing)";
						break;
					
					case 0:
						classStr = "up";
						labelStr = "Up";
						break;
						
					default:
						classStr = "down";
						labelStr = "Down";
						break;
				}
				
				tableStr += "<tr class=\"" + classStr + "\">\r\n<td><strong>"
							+ labelStr
							+ "</strong></td>\r\n<td><a href=\"http://"
							+ theInfo.mName
							+ "\">" + theInfo.mDesc + "</a></td>\r\n";
				tableStr += "<td>" + theInfo.mLastValidation + "</td>\r\n</tr>\r\n";
			}
		} // synchronized (mHostInfoVector)
		if (index_i == 0)
		{
			tableStr += "<tr>\r\n<td colspan=\"3\"><em>No devices registered.</em></td>\r\n</tr>\r\n";
		}
		tableStr += "</table>\r\n";	

		String validateTimeStr = Integer.toString( validateTime );
		String hitCounterStr = Integer.toString( hitcounter );
		// Refresh is validate time / 2
		long refreshCount = (npdstracker.validateTime * npdstracker.kValidateTimeUnit ) / (kRefreshTimeUnit * 2);
		
		String metaRefreshStr = "<meta http-equiv=\"refresh\" content=\"" + refreshCount + "; url=" + HTTPDocStr + "\" />\r\n";
		
		File stylesheet = new File(stylesheetFile);
		String stylesheetStr = "<link rel=\"stylesheet\" href=\"" + stylesheet.getName() + "\" type=\"text/css\" media=\"screen\" />\r\n";

		out.print( "Refresh: " + refreshCount + "; url=" + HTTPDocStr + "\r\n" );
		out.print( "Server: " + kServerStr + "\r\n" );

		Date now = new Date();

		out.print( "Date: " + ReturnRFCTime( now ) + "\r\n" );
		out.print( "Last-Modified: " + ReturnRFCTime( mValidator.mLastCheck ) + "\r\n" );
		out.print( "Content-type: text/html\r\n\r\n" );

		String lastValidationStr;
		if (mValidationInProgress != 0)
		{
			lastValidationStr = "Validation is in progress.";
		} else {
			lastValidationStr = "<strong>Last validation:</strong> " + mLastValidation + ".";
		}

		// Define count of servers as string
		String serverCounterStr = Integer.toString( mHostInfoVector.size() );
		
		// Contextual modification of string for template display
		if (serverCounterStr.equals("0"))
		{
			serverCounterStr = "no";
		}

		// Define contextual nouns and verbs as strings
		String serverNounStr;
		String serverVerbStr;
		String hitCounterNounStr;
		String validateTimeNounStr;

		if (serverCounterStr.equals("1"))
		{
			serverNounStr = "web server";
			serverVerbStr = "is";
		} else {
			serverNounStr = "web servers";
			serverVerbStr = "are";
		}		

		if (hitCounterStr.equals("1"))
		{
			hitCounterNounStr = "time";
		} else {
			hitCounterNounStr = "times";
		}		

		if (validateTimeStr.equals("1"))
		{
			validateTimeNounStr = "minute";
		} else {
			validateTimeNounStr = "minutes";
		}		

		// Contextual modification of string for template display
		if (validateTimeStr.equals("1"))
		{
			validateTimeStr = "";
		}

		// Define SHARE server list as hyperlinks
		String serverSharesStr;
		serverSharesStr = "<ul class=\"servers\">\r\n";
		for (int foo = 0; foo < mSharingInfoVector.size(); foo++)
			{
				TServerInfo theServerInfo = (TServerInfo) mSharingInfoVector.elementAt(foo);
				serverSharesStr += "<li><a href=\"http://" + theServerInfo.mHost
				+ ":" + theServerInfo.mPort
				+ "/\">" + theServerInfo.mHost
				+ "</a></li>\r\n";
			}
		serverSharesStr += "</ul>";

		BufferedReader template = new BufferedReader (new FileReader(templateFile));
		String templateLine = template.readLine();
		while (templateLine != null)
		{
			// Replace tags in the HTML template
			templateLine = StrReplace( templateLine, "<hit-counter/>", hitCounterStr );
			templateLine = StrReplace( templateLine, "<hit-counter-noun/>", hitCounterNounStr );
			templateLine = StrReplace( templateLine, "<http-doc/>", HTTPDocStr );
			templateLine = StrReplace( templateLine, "<last-validation/>", lastValidationStr );
			templateLine = StrReplace( templateLine, "<meta-refresh/>", metaRefreshStr );
			templateLine = StrReplace( templateLine, "<server-counter/>", serverCounterStr );
			templateLine = StrReplace( templateLine, "<server-noun/>", serverNounStr );
			templateLine = StrReplace( templateLine, "<server-verb/>", serverVerbStr );
			templateLine = StrReplace( templateLine, "<server-shares/>", serverSharesStr );
			templateLine = StrReplace( templateLine, "<servers/>", tableStr );
			templateLine = StrReplace( templateLine, "<stylesheet/>", stylesheetStr );
			templateLine = StrReplace( templateLine, "<tracker-host/>", hostLink);
			templateLine = StrReplace( templateLine, "<tracker-name/>", hostName);
			templateLine = StrReplace( templateLine, "<url/>", urlStr );
			templateLine = StrReplace( templateLine, "<validate-time/>", validateTimeStr );
			templateLine = StrReplace( templateLine, "<validate-time-noun/>", validateTimeNounStr );
			templateLine = StrReplace( templateLine, "<version/>", versionStr );

			out.print(templateLine + "\r\n");
			templateLine = template.readLine();
		}

		out.print( "\r\n\r\n" );
		out.flush();
		template.close();
	}
	
	// ==================================================================== //
	private static void stylesheetPage( File stylesheet, BufferedReader in, PrintWriter out, Socket inSocket ) throws SocketException, IOException
	{
		// return a CSS FILE
		out.print("HTTP/1.0 ");
		ReturnCode(HTTP_OK, "", out);
		out.print( "Server: " + kServerStr + "\r\n" );
		Date now = new Date();
		out.print( "Date: " + ReturnRFCTime( now ) + "\r\n" );
		Date modified = new Date (stylesheet.lastModified());
		out.print( "Last-Modified: " + ReturnRFCTime( modified ) + "\r\n" );
		out.print( "Content-type: text/css\r\n\r\n" );
		
		BufferedReader css = new BufferedReader (new FileReader(stylesheet.getPath()));
		String cssLine = css.readLine();
		while (cssLine != null)
		{
			out.print(cssLine + "\r\n");
			cssLine = css.readLine();
		}
		out.flush();
		css.close();
	}
	
	// ==================================================================== //
	private static void imageFile( File image, BufferedReader in, PrintWriter out, Socket inSocket ) throws SocketException, IOException
	{
		ImageInputStream imgStream1 = ImageIO.createImageInputStream(image);
		BufferedImage bufferedImage1 = ImageIO.read(image);
		OutputStream os = inSocket.getOutputStream();
		ImageIO.write(bufferedImage1,"gif",os);
		os.flush();
		out.flush();
		imgStream1.close();
		os.close();
	}

	// ====================================================================	//
	// void adminConsole( BufferedReader, PrintWriter, Socket ) [static, private]
	// ====================================================================	//
	// Function to handle administration console commands.
	
	private static void adminConsole( BufferedReader in, PrintWriter out, Socket inSocket ) throws SocketException, IOException
	{
		// Funky cool admin console
		inSocket.setSoTimeout( 0 );
		out.print("Welcome to the NPDS Tracker Server administration interface! (Type HELP for command reference.)\r\n");
		out.flush();
		String commandline = "";
		while (!(commandline.startsWith("Q")) || !(commandline.startsWith("q")))
		{
			out.print("> ");
			out.flush();
			commandline = in.readLine().toUpperCase();
			if (commandline.equals("HELP"))
			{
				out.print("Valid commands are:\r\n");
				out.print("\r\n");
				out.print("ABOUT     Display the current tracker settings\r\n");
				out.print("HALT      Stop the tracker (with confirmation)\r\n");
				out.print("HELP      Displays this list of commands\r\n");
				out.print("LOGS      Dumps the tracker log\r\n");
				out.print("SHARE     Change the tracker share settings\r\n");
				out.print("SLIST     View or modify the list of trackers to obtain shared records from\r\n");
				out.print("VTEST     Trigger a tracker validation\r\n");
				out.print("STATS     Display the tracker statistics\r\n");
				out.print("VERIFY    Change the tracker verification settings\r\n");
				out.print("QUIT      Exit the administration interface and close the connection\r\n");
				out.flush();
			}
			else if (commandline.equals("QUIT"))
			{
				out.print("Goodbye!\r\n");
				out.flush();
				break;
			}
			else if (commandline.equals("ABOUT"))
				ReturnAbout(out);

			else if (commandline.equals("HALT"))
			{
				out.print("Are you sure? (y/n)\r\n");
				out.flush();
				String confirm = in.readLine();
				if (confirm.startsWith("y") || confirm.startsWith("Y"))
				{
					logMessage("Tracker shutting down via administration interface.");
					System.gc();
					System.exit(0);
				}
			}
			else if (commandline.equals("LOGS"))
			{
				if (logFile.equals(""))
				{
					out.print("Sorry, logs can only be read remotely if they are being written to a file. (They aren't.)\r\n");
					out.flush();
				}
				else
				{
					out.print("Start your terminal capture feature, then hit enter.\r\n");
					out.flush();
					String foo = in.readLine();
					BufferedReader dumplogs = new BufferedReader (new FileReader(logFile));
					foo = dumplogs.readLine();
					while (foo != null)
					{
						out.print(foo + "\r\n");
						out.flush();
						foo = dumplogs.readLine();
					}
					dumplogs.close();
				}
			}
			else if (commandline.equals("SHARE"))
			{
				if (shareEnabled)
				{
					out.print("Sharing is currently enabled (TRUE)\r\n");
					out.flush();
				}
				else
				{
					out.print("Sharing is currently disabled (FALSE)\r\n");
					out.flush();
				}
				out.print("Set sharing (TRUE/FALSE): ");
				out.flush();
				String confirm = in.readLine();
				if (confirm.startsWith("T") || confirm.startsWith("t"))
				{
					shareEnabled = true;
					out.print("Sharing is enabled\r\n");
					out.flush();
				}
				else if (confirm.startsWith("F") || confirm.startsWith("f"))
				{
					shareEnabled = false;
					out.print("Sharing is disabled\r\n");
					out.flush();
				}
				else
				{
					out.print("Sharing setting unchanged\r\n");
					out.flush();
				}
			}
			else if (commandline.equals("VTEST"))
			{
				out.print("Client validation test started\r\n");
				out.flush();
				npdstracker.validateServers();
			}
			else if (commandline.equals("STATS"))
			{
				out.print("Pages served: " + hitcounter + "\r\n");
				out.print("REGUP commands processed: " + regcounter + "\r\n");
				out.print("NPDS clients currently registered: " + mHostInfoVector.size() + "\r\n");
				out.flush();
			}
			else if (commandline.equals("VERIFY"))
			{
				out.print("Tracker verifies every " + validateTime + " minutes\r\n");
				out.print("Verification is attempted " + validateTries + " times\r\n");
				out.print("Edit settings? ");
				out.flush();
				String confirm = in.readLine();
				confirm.toUpperCase();
				if (confirm.startsWith("Y") || confirm.startsWith("y"))
				{
					out.print("Enter delay between verification attempts: ");
					out.flush();
					validateTime = Integer.parseInt(in.readLine());
					out.print("Enter number of verification attempts: ");
					out.flush();
					validateTries = Integer.parseInt(in.readLine());
				}
			}
			else if (commandline.equals("SLIST"))
			{
				out.print("NPDS Trackers to get SHARE records from:\r\n");
				out.flush();
				synchronized (mSharingInfoVector)
				{
					for (int foo = 0; foo < mSharingInfoVector.size(); foo++)
					{
						TServerInfo theServerInfo = (TServerInfo) mSharingInfoVector.elementAt(foo);
						out.print(foo + ": " + theServerInfo.mHost + ":" + theServerInfo.mPort + "\r\n");
						out.flush();
					}
				}

				out.print("Add or delete a record? (A/D): ");
				out.flush();
				String confirm = in.readLine();
				if (confirm.startsWith("A") || confirm.startsWith("a"))
				{
					TServerInfo theServerInfo = new TServerInfo();
					
					out.print("Enter host: ");
					out.flush();
					theServerInfo.mHost = in.readLine();

					out.print("Enter port: (Leave empty for 3680) ");
					out.flush();
					String thePortStr = in.readLine();
					if (thePortStr != null)
						theServerInfo.mPort = thePortStr;
					else
						theServerInfo.mPort = "" + DEFAULT_PORT;
					
					mSharingInfoVector.addElement(theServerInfo);
				}
				else if (confirm.startsWith("D") || confirm.startsWith("d"))
				{
					out.print("Enter number to delete: ");
					out.flush();
					String deletenumber = in.readLine();
					mSharingInfoVector.removeElementAt(Integer.parseInt(deletenumber));
				}
			}
			else
			{
				out.print("Invalid command. (Type HELP for command reference.)\r\n");
				out.flush();
			}
		}
	}

	// ====================================================================	//
	// void validateServers( void ) [static, private]
	// ====================================================================	//
	// Checks the registered Newtons.
	
	public static void validateServers() throws SocketException
	{
		logMessage("Starting validation of records");
		
		mValidationInProgress += 1;
		
		// Use copy constructor
		Vector<THostInfo> theHosts = new Vector<THostInfo>(mHostInfoVector);
		
		for (int foo = 0; foo < theHosts.size(); foo++)
		{
			THostInfo theInfo = (THostInfo) theHosts.elementAt(foo);
			System.gc();
			// try to retrieve /traq/confirm.ns
			String checkResult = null;
			// Don't validate SHARE records
			if (!(theInfo.mStatus == -1 || theInfo.mStatus == -2))
			{
				try
				{
					logMessage("checking " + theInfo.mName);
					Socket checkSocket = new Socket( theInfo.mHost, theInfo.mPort );
					checkSocket.setSoTimeout( kTimeout );
						
					BufferedReader inshare = new BufferedReader(new InputStreamReader(checkSocket.getInputStream()));
					PrintWriter outshare = new PrintWriter(new OutputStreamWriter(checkSocket.getOutputStream()));
						
					// Here is what I say:
					// GET /traq/confirm.ns HTTP/1.0
					// Host: <the host of the Newton server - used by some firewalls>
					// User-Agent: <kUserAgentStr>
					// Accept: text/x-npds				// this is what the client says and it should confuse any Apache webserver.
														// Remind me to add this to the protocol.
						
					outshare.print("GET /traq/confirm.ns HTTP/1.0\r\n");
					outshare.print("Host: " + theInfo.mHost + "\r\n");
					outshare.print("User-Agent: " + kUserAgentStr + "\r\n");
					outshare.print("Accept: text/x-npds\r\n");
					outshare.print("\r\n");
					outshare.flush();
					logMessage("Waiting for reply");

					char[] buffer = new char[512];
					while(inshare.read(buffer) != -1);

					checkResult = new String(buffer);

					checkSocket.close();
						
					// The result should look like this:
					// HTTP_Version	202	foo
					// plenty of headers
					// CRLF CRLF
					// npds-status: SERVER_ALIVE_WELL

					// This last element isn't in the protocol. So I accept anybody without it.
					// I only check the 202.
					// (maybe one day, I'll check the content-type and the npds-status)
					// In fact, I'm pretty laxist (please don't repeat that) and I allow test pages served by some webserver).
					// I look for a 202 anywhere in the first 512 bytes.

					// if there was a zero-length result received, we assume the server is down
					if (checkResult.startsWith("\u0000") || (checkResult.length() == 0))
					{
						logMessage(theInfo.mName + " is down (timeout / host not found)");
						theInfo.mStatus += 1;
					}
					else if (checkResult.indexOf("202") > -1)
					{
						// the server is good: update its time and status
						logMessage(theInfo.mName + " is up");
						
						int index_j;

						synchronized ( mHostInfoVector )
						{
							for (index_j = 0; index_j < mHostInfoVector.size(); index_j++)
							{
								THostInfo theOriginalInfo = (THostInfo) mHostInfoVector.elementAt(index_j);
								if (theInfo.mName.equals(theOriginalInfo.mName))
								{
									theOriginalInfo.mLastValidation = ReturnRFCTime(new Date());
									theOriginalInfo.mStatus = 0;
									break;
								}
							}
								
							// If I haven't found it, it's that it has been removed while we were checking it.
						}
					}
					else
					{
						// the server is down / has magically changed into Apache
						logMessage(theInfo.mName + " is down (bad reply)");
						
						int index_j;
						
						synchronized ( mHostInfoVector )
						{
							for (index_j = 0; index_j < mHostInfoVector.size(); index_j++)
							{
								THostInfo theOriginalInfo = (THostInfo) mHostInfoVector.elementAt(index_j);
								if (theInfo.mName.equals(theOriginalInfo.mName))
								{
									theOriginalInfo.mStatus += 1;
									break;
								}
							}
						}
					}
				}
				catch (Exception e) 
				{
					// if there was an exception, we assume the server is down
					logMessage(theInfo.mName + " is down (timeout / connection refused / other exception) " + e);
					
					int index_j;
					
					synchronized ( mHostInfoVector )
					{
						for (index_j = 0; index_j < mHostInfoVector.size(); index_j++)
						{
							THostInfo theOriginalInfo = (THostInfo) mHostInfoVector.elementAt(index_j);
							if (theInfo.mName.equals(theOriginalInfo.mName))
							{
								theOriginalInfo.mStatus += 1;
								break;
							}
						}
					}
				}
			}
		} // for (int foo = 0; foo < theHosts.size(); foo++)
		
		// check for servers which we haven't been able to reach in a while and toast them
		synchronized (mHostInfoVector)
		{
			// for (int foo = 0; foo < mHostInfoVector.size(); foo++)
			// This should lead to a problem. I'd better start from the end.
			int theLastIndex = mHostInfoVector.size() - 1;
			for (int foo = theLastIndex; foo >= 0; foo--)
			{
				THostInfo theInfo = (THostInfo) mHostInfoVector.elementAt(foo);
				if ( (theInfo.mStatus > validateTries) || (theInfo.mStatus == -1) || (theInfo.mStatus == -2) )
				// Remove both down Newtons and shared Newton (as shared Newton will be got later)
				// (notice: this isn't a good idea later, we should first check that the other tracker servers
				// are still running, but as nobody does share, it isn't really a problem)
				{
					if ( theInfo.mStatus > validateTries )
					    logMessage(theInfo.mName + " removed. Too many failed connections.");
					mHostInfoVector.removeElementAt( foo );
				}
			} // for (int foo = theLastIndex; foo >= 0; foo--)
		} // synchronized (mHostInfoVector)

		// retrieve the latest info from other trackers
		synchronized( mSharingInfoVector )
		{
			for (int foo = 0; foo < mSharingInfoVector.size(); foo++)
			{
				TServerInfo theServerInfo = (TServerInfo) mSharingInfoVector.elementAt(foo);
				logMessage("Making SHARE connection with " + theServerInfo.mHost);
				try
				{
					Socket theSocket = new Socket(theServerInfo.mHost, Integer.parseInt(theServerInfo.mPort));
					theSocket.setSoTimeout( kTimeout );	// Won't wait forever, it would break the server.
					
					logMessage("Setting up input and output streams");
					BufferedReader inshare = new BufferedReader(new InputStreamReader( theSocket.getInputStream()) );
					PrintWriter outshare = new PrintWriter(new OutputStreamWriter( theSocket.getOutputStream()) );
					logMessage("Sending SHARE command");
					outshare.print("SHARE\r\n");
					outshare.flush();
					logMessage("Waiting for reply");

					String templine = "", returncode = "";
					templine = inshare.readLine();
					while (templine != null)
					{
						returncode += templine + "\n";
						templine = inshare.readLine();
					}

					logMessage("Received Data");
					if (returncode.startsWith("200 OK"))
					{
						logMessage("200 OK : Return code is good. Parsing records.");

						StringTokenizer lines = new StringTokenizer(returncode, "\n");
						while (lines.hasMoreTokens() == true)
						{
							StringTokenizer tabs = new StringTokenizer(lines.nextToken(), "\t");
							String currenttoken = tabs.nextToken();
							if (currenttoken.startsWith("no-entries"))
							{
								logMessage("Remote server has no records.");
								break;
							}
							else if (currenttoken.startsWith("200 OK"))
							{
								// go to the next token
							}
							else
							{
								// take the share record, break up its tokens and add it to the list
								String addresspair = new String(currenttoken);
								String timepair = new String(tabs.nextToken());
								String statuspair = new String(tabs.nextToken());
								String descpair = new String(tabs.nextToken());

								logMessage(
										"[SHARE] " + addresspair
										+ " " + timepair
										+ " " + statuspair
										+ " " + descpair );
								
								if ( QueryRecord(addresspair.substring(9)) == -1 )
								{
									THostInfo theNewInfo = new THostInfo();

									theNewInfo.mName = addresspair.substring(9);
									theNewInfo.mLastValidation = timepair.substring(15);
									if ( statuspair.substring(8).compareTo("DOWN") == 0 )
									{
										// Reflect sharing status of DOWN
										theNewInfo.mStatus = -2;
									}
									else
									{
										// Reflect sharing status of UP
										theNewInfo.mStatus = -1;
									}
									theNewInfo.mDesc = descpair.substring(13);

									mHostInfoVector.addElement(theNewInfo);
								}
								else
								{
									logMessage( "Duplicate Share Record.  Not adding: " + addresspair );
								}
							}
						}
					}
					else
						logMessage("Return code is bad. Not getting any records from this server.");

					theSocket.close();
				} catch (IOException e) {;}
			} // for (int foo = 0; foo < mSharingInfoVector.size(); foo++)
		} // synchronized( mSharingInfoVector )
				
		mLastValidation = ReturnRFCTime(new Date());
		saveServers();
		mValidationInProgress -= 1;
		logMessage("Ending validation of records");
	}

	// ====================================================================	//
	// void saveServers( void ) [static, private]
	// ====================================================================	//
	// Checks the registered Newtons.

	public static void saveServers()
	{
		try {
			FileWriter outcmdfile = new FileWriter(cmdfile, false);
 
			synchronized ( mHostInfoVector )
			{
				for (int foo = 0; foo < mHostInfoVector.size(); foo++)
				{
					THostInfo theInfo = (THostInfo) mHostInfoVector.elementAt(foo);
					// Dont save SHARE records
					if ( !(theInfo.mStatus == -1 || theInfo.mStatus == -2 ) )
					{
						String templine = "REGUP " + theInfo.mHost;
						if (theInfo.mPort != 80)
						{
							templine = templine + ":" + theInfo.mPort;
						}
						templine = templine + " " + theInfo.mDesc;
						outcmdfile.write(templine + "\r\n");
					}
				}
			} // synchronized ( mHostInfoVector )
			
			outcmdfile.flush();
			outcmdfile.close();
		} catch (IOException e) {
			System.out.println("   [FATAL] Can't write to log file: " + cmdfile);
		}
	}
}

// ================================================	//
//                                                  //
//     "Beware of these column-writing CEOs."       //
//                                                  //
//             Jean-Louis Gassee, 24 May 2000       //
//                                                  //
// ================================================	//