package com.qasymphony.qtest.automation.testng.core;

import com.qasymphony.qtest.automation.testng.util.Https;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * TestNgAgentListener
 *
 * @author Dzung Nguyen
 * @version $Id TestNgAgentListener 2015-03-25 04:08:30z dzungvnguyen $
 * @since 1.0
 */
public class TestNgAgentListener implements ITestListener {
  //~ class properties ========================================================
  private Long jobInstanceId;
  private String logBasePath;

  //~ class members ===========================================================
  @Override
  public void onTestStart(ITestResult result) {}

  @Override
  public void onTestSuccess(ITestResult result) {
    String testLog = toJsonString(result, "PASS");
    Https.submitLog(testLog.getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public void onTestFailure(ITestResult result) {
    String testLog = toJsonString(result, "FAIL");
    Https.submitLog(testLog.getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    String testLog = toJsonString(result, "SKIP");
    Https.submitLog(testLog.getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    String testLog = toJsonString(result, "PASS");
    Https.submitLog(testLog.getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public void onStart(ITestContext context) {

  }

  @Override
  public void onFinish(ITestContext context) {

  }

  /**
   * @return the json object of the given test result.
   */
  private String toJsonString(ITestResult result, String status) {
    StringBuilder testLogBuilder = new StringBuilder("{");

    testLogBuilder.append(quote("jobInstanceId")).append(":").append(resolveJobInstanceId()).append(",");
    testLogBuilder.append(quote("className")).append(":").append(quote(result.getTestClass().getRealClass().getCanonicalName())).append(",");
    testLogBuilder.append(quote("methodName")).append(":").append(quote(result.getMethod().getMethodName())).append(",");
    testLogBuilder.append(quote("status")).append(":").append(quote(status)).append(",");
    testLogBuilder.append(quote("startTime")).append(":").append((result.getStartMillis() <= 0 ? System.currentTimeMillis() : result.getStartMillis())).append(",");
    testLogBuilder.append(quote("endTime")).append(":").append((result.getEndMillis() <= 0 ? System.currentTimeMillis() : result.getEndMillis()));

    // build the test log.
    String testLogFile = writeExceptionToLog(result.getThrowable());
    if (testLogFile != null) {
      testLogBuilder.append(",");
      testLogBuilder.append(quote("logPath")).append(":").append(quote(testLogFile));
    }
    testLogBuilder.append("}");

    return testLogBuilder.toString();
  }

  /**
   * @return the exception log file.
   */
  private String writeExceptionToLog(Throwable cause) {
    if (cause != null) {
      File logFile = new File(resolveLogBasePath(), UUID.randomUUID().toString() + ".txt");
      try {
        PrintWriter writer = new PrintWriter(new FileWriter(logFile));
        cause.printStackTrace(writer);
        closeQuietly(writer);
      } catch (IOException ioe) {
        // never mind, I don't want to handle this exception.
      }

      // return absolute file.
      return logFile.getAbsolutePath();
    }

    return null;
  }

  /**
   * Close the writer.
   *
   * @param writer the given writer to store data.
   */
  private void closeQuietly(PrintWriter writer) {
    try {
      if (writer != null) {
        writer.flush();
        writer.close();
      }
    } catch (Exception ex) {
      // never mind, we don't want to handle this exception.
    }
  }

  /**
   * @return the log base path of log attachment.
   */
  private String resolveLogBasePath() {
    if (logBasePath == null) {
      logBasePath = System.getenv("LOG_PATH");
      if (Https.isEmpty(logBasePath)) {
        logBasePath = System.getProperty("LOG_PATH");
      }
    }

    return logBasePath;
  }

  /**
   * @return the job instance identifier.
   */
  private Long resolveJobInstanceId() {
    if (jobInstanceId == null) {
      String jobInstanceIdStr = System.getenv("JOB_INSTANCE_ID");
      if (Https.isEmpty(jobInstanceIdStr)) {
        jobInstanceIdStr = System.getProperty("JOB_INSTANCE_ID", "-1");
      }

      jobInstanceId = Long.parseLong(jobInstanceIdStr);
    }

    return jobInstanceId;
  }

  /**
   * Quote the string.
   *
   * @param string the given string to qoute.
   * @return the quote value.
   */
  private static String quote(String string) {
    if(string != null && string.length() != 0) {
      char c = 0;
      int len = string.length();
      StringBuffer sb = new StringBuffer(len + 4);
      sb.append('\"');

      for(int i = 0; i < len; ++i) {
        char b = c;
        c = string.charAt(i);
        switch(c) {
          case '\b':
            sb.append("\\b");
            break;
          case '\t':
            sb.append("\\t");
            break;
          case '\n':
            sb.append("\\n");
            break;
          case '\f':
            sb.append("\\f");
            break;
          case '\r':
            sb.append("\\r");
            break;
          case '\"':
          case '\\':
            sb.append('\\');
            sb.append(c);
            break;
          case '/':
            if(b == 60) {
              sb.append('\\');
            }

            sb.append(c);
            break;
          default:
            if(c >= 32 && (c < 128 || c >= 160)) {
              sb.append(c);
            } else {
              String t = "000" + Integer.toHexString(c);
              sb.append("\\u" + t.substring(t.length() - 4));
            }
        }
      }

      sb.append('\"');
      return sb.toString();
    } else {
      return "\"\"";
    }
  }
}
