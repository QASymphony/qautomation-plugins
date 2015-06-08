package com.qasymphony.qtest.automation.testng.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Https
 *
 * @author Dzung Nguyen
 * @version $Id Https 2015-03-25 03:47:30z dzungvnguyen $
 * @since 1.0
 */
public class Https {
  //~ class properties ========================================================
  private static final String SERVICE_PATH = "listener/plugin/logs";

  //~ class members ===========================================================
  private Https() {}

  /**
   * Send test log to server.
   *
   * @param testLog the given test log to sent.
   */
  public static void submitLog(byte[] testLog) {
    HttpURLConnection connection = null;
    try {
      connection = openConnection();

      // setting data.
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setInstanceFollowRedirects(false);

      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("charset", "utf-8");
      connection.setRequestProperty("Content-Length", Long.toString(testLog.length));
      connection.setUseCaches(false);

      // write data to stream.
      try (DataOutputStream writer = new DataOutputStream(connection.getOutputStream())) {
        writer.write(testLog);
        writer.flush();
      }

      // read stream.
      try(Reader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
        while (in.read() >= 0) {}
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  /**
   * @return the {@link HttpURLConnection HTTP URL connection}.
   */
  private static HttpURLConnection openConnection() throws IOException {
    String serviceHost = System.getenv("AGENT_SERVER_URL");
    if (isEmpty(serviceHost)) {
      serviceHost = System.getProperty("AGENT_SERVER_URL", "http://localhost:6789/");
    }

    String serviceUrl = serviceHost + (serviceHost.endsWith("/") ? "" : "/") + SERVICE_PATH;
    return (HttpURLConnection) new URL(serviceUrl).openConnection();
  }

  /**
   * @return {@code true} if value is {@code null} or empty, otherwise {@code false}
   */
  public static boolean isEmpty(String value) {
    return (value == null || "".equals(value));
  }
}
