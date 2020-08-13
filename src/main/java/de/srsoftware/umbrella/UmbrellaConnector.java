package de.srsoftware.umbrella;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.srsoftware.tools.Tools;
import de.srsoftware.tools.urls.Urls;

/**
 * This class allows Java Applications to access Umbrella Services using a Umbrella login 
 * @author Stephan Richter
 *
 */
public class UmbrellaConnector {
	private static final Logger Log = LoggerFactory.getLogger(UmbrellaConnector.class);
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private HashMap<String,String> tokens = new HashMap<String, String>();
	private int port;
	
	/**
	 * Set up new connector: enable cookies
	 * @param port 
	 */
	public UmbrellaConnector(int port) {
		this.port = port;
		CookieManager cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
	}
	
	/**
	 * short method for URL encoding
	 * @param value
	 * @return
	 */
	private String encode(String value) {
		return URLEncoder.encode(value,UTF8);
	}
	
	/**
	 * sends a request for a token to the umbrella site, receives the answer via spawened web server
	 * @param url
	 * @return
	 * @throws RedirectException
	 */
	private String getToken(URL url) throws RedirectException {
		String hostname = Urls.hostname(url);
		String token = tokens.get(hostname);
		if (token == null) {
			try {
				request(url,null);
				token = readToken();
				if (token != null) tokens.put(hostname,token);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return token;
	}
	
	/**
	 * turn a given HttpUrlConnection into a POST request, submitting data from the data map.
	 * @param connection
	 * @param data
	 * @throws IOException
	 */
	private void makePostRequest(HttpURLConnection connection, Map<String, String> data) throws IOException {
		StringJoiner arguments = new StringJoiner("&");
		for (Entry<String, String> entry : data.entrySet()) arguments.add(map(entry));
		byte[] argBytes = arguments.toString().getBytes(UTF8);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setFixedLengthStreamingMode(argBytes.length);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
		connection.connect();
		try (OutputStream server = connection.getOutputStream()) {
			server.write(argBytes);			
		}
	}

	/**
	 * Demo method
	 * @param args
	 * @throws IOException
	 * @throws RedirectException
	 */
	public static void main(String[] args) throws IOException, RedirectException {
		UmbrellaConnector umbrella = new UmbrellaConnector(8765);
		URL url = new URL("https://umbrella.srsoftware.de/mindmap/umbrella");
		String response = umbrella.request(url,new HashMap<String, String>(Map.of("id", "5")));
		Log.info("Response: {}",response);
	}
	
	/**
	 * build a string in the form <em>key=value</em>
	 * @param entry
	 * @return
	 */
	private CharSequence map(Entry<String, String> entry) {
		return encode(entry.getKey())+"="+encode(entry.getValue());
	}


	/**
	 * spawn a webserver to receive the token from umbrella
	 * @return
	 * @throws IOException
	 */
	private String readToken() throws IOException {
		ServerSocket server = new ServerSocket(port);
		Socket client = server.accept();
		InputStream in = client.getInputStream();
		String response = "";
		byte[] buffer = new byte[1024];
		if (in.read(buffer) != -1) response = new String(buffer,UTF8);
		OutputStream out = client.getOutputStream();
		out.write("You may now close this page.".getBytes(UTF8));
		out.close();
		in.close();
		client.close();
		server.close();
		String[] lines = response.replace("\r", "").split("\n");
		Pattern pattern = Pattern.compile("token=(\\S+)");
		for (String line :  lines) {
			Matcher m = pattern.matcher(line);
			if (m.find()) return m.group().substring(6);
		}
		return null;
	}

	/**
	 * send a request to an umbrella server. if the data map is not null, its contents will be sent via POST.
	 * @param url
	 * @param data
	 * @return
	 * @throws IOException
	 * @throws RedirectException
	 */
	public String request(URL url, Map<String,String> data) throws IOException, RedirectException {
		Log.debug("requesting {}",url);
		int count = 0;
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setInstanceFollowRedirects(false); // do not follow redirects automatically
		if (data != null) makePostRequest(connection,data);
		String redirect = connection.getHeaderField("Location");
		while (redirect != null) {
			count++;
			if (count>7) throw new RedirectException(redirect);
			URL newUrl = new URL(redirect);
			if (redirect.contains("/login?")) { // if we are redirected to the login page: alter redirect to point to local servlet
				String loginPage = redirect.split("\\?")[0];
				Tools.openWebpage(loginPage+"?returnTo=http://localhost:"+port+"/intelliMind");
				String token = getToken(newUrl); // spawn servlet, receive token
				Log.debug("Token: {}",token);
				newUrl = url;
				if (data == null) data = new HashMap<String, String>();
				data.put("token", token);
			} 
			Log.debug("requesting {}",newUrl);
			connection = (HttpURLConnection)newUrl.openConnection();
			connection.setInstanceFollowRedirects(false);
			if (data != null) makePostRequest(connection,data);
			redirect = connection.getHeaderField("Location");

		}
		InputStream in = connection.getInputStream();
		String response = new String(in.readAllBytes(),UTF8);
		in.close();
		return response;
	}
}
