package com.qasymphony.qtest.automation.testng;

import com.qasymphony.qtest.automation.plugin.api.request.QAutomationPluginApiRequest;
import com.qasymphony.qtest.automation.plugin.api.response.QAutomationPluginApiResponse;

/**
 * MessageHandler
 *
 * @author Dzung Nguyen
 * @version $Id MessageHandler 2015-06-08 12:33:30z dzungvnguyen $
 * @since 1.0
 */
public interface MessageHandler {
  /**
   * Handle the plugin request.
   *
   * @param request the given plugin request.
   * @return the response.
   */
  QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request);
}
