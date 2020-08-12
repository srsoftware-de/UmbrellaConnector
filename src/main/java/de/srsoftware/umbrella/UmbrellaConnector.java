package de.srsoftware.umbrella;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.keawe.localconfig.Configuration;
import de.srsoftware.tools.Tools;
import de.srsoftware.tools.urls.Urls;

/**
 * This class allows Java Applications to access Umbrella Services using a Umbrella login 
 * @author Stephan Richter
 *
 */
public class UmbrellaConnector {
	private static final Logger Log = LoggerFactory.getLogger(UmbrellaConnector.class);
	private Configuration config;
	private HashMap<String,String> tokens = new HashMap<String, String>();
	
	public UmbrellaConnector(Configuration config) {
		this.config = config;
		CookieManager cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
	}

	public static void main(String[] args) throws IOException, RedirectException {
		String configFile = Configuration.dir("UmbrellaConnector")+File.separator+"test.config";
		Configuration config = new Configuration(configFile);
		UmbrellaConnector umbrella = new UmbrellaConnector(config);
		URL url = new URL("https://umbrella.srsoftware.de/task/1/view");
		String response = umbrella.request(url);
		Log.info("Response: {}",response);
	}

	private String readToken() throws IOException {
		ServerSocket server = new ServerSocket(8765);
		Socket client = server.accept();
		InputStream in = client.getInputStream();
		String response = "";
		byte[] buffer = new byte[1024];
		if (in.read(buffer) != -1) response = new String(buffer,StandardCharsets.UTF_8);
		OutputStream out = client.getOutputStream();
		out.write("You may now close this page.".getBytes(StandardCharsets.UTF_8));
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

	private String request(URL url) throws IOException, RedirectException {
		Log.debug("requesting {}",url);
		int count = 0;
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setInstanceFollowRedirects(false); // do not follow redirects automatically
		String redirect = connection.getHeaderField("Location");
		while (redirect != null) {
			count++;
			if (count>20) throw new RedirectException(redirect);
			URL newUrl = new URL(redirect);
			if (redirect.contains("/login?")) { // if we are redirected to the login page: alter redirect to point to local servlet
				String loginPage = redirect.split("\\?")[0];
				Tools.openWebpage(loginPage+"?returnTo=http://localhost:8765/intelliMind");
				String token = token(newUrl); // spawn servlet, receive token
				Log.debug("Token: {}",token);
				newUrl = new URL(url+"?token="+token); // update original redirect with token
			} 
			Log.debug("requesting {}",newUrl);
			connection = (HttpURLConnection)newUrl.openConnection();
			connection.setInstanceFollowRedirects(false);
			redirect = connection.getHeaderField("Location");

		}
		InputStream in = connection.getInputStream();
		String response = new String(in.readAllBytes(),StandardCharsets.UTF_8);
		in.close();
		return response;
	}
	
	
	private String token(URL url) throws RedirectException {
		String hostname = Urls.hostname(url);
		String token = tokens.get(hostname);
		if (token == null) {
			try {
				request(url);
				token = readToken();
				if (token != null) tokens.put(hostname,token);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return token;
	}
}
