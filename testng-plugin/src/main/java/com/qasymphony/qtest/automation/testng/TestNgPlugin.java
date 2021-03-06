package com.qasymphony.qtest.automation.testng;

import com.qasymphony.qtest.automation.domain.TestScript;
import com.qasymphony.qtest.automation.domain.testcase.TestCase;
import com.qasymphony.qtest.automation.plugin.access.atm.model.AtmPluginConfiguration;
import com.qasymphony.qtest.automation.plugin.access.atm.model.BuildCommandRequest;
import com.qasymphony.qtest.automation.plugin.access.atm.model.CommandResponse;
import com.qasymphony.qtest.automation.plugin.api.AbstractQAutomationPlugin;
import com.qasymphony.qtest.automation.plugin.api.QAutomationPluginIdentifier;
import com.qasymphony.qtest.automation.plugin.api.UnhandledRequestTypeException;
import com.qasymphony.qtest.automation.plugin.api.annotation.Extension;
import com.qasymphony.qtest.automation.plugin.api.annotation.Load;
import com.qasymphony.qtest.automation.plugin.api.annotation.UnLoad;
import com.qasymphony.qtest.automation.plugin.api.context.PluginContext;
import com.qasymphony.qtest.automation.plugin.api.logging.Logger;
import com.qasymphony.qtest.automation.plugin.api.request.QAutomationPluginApiRequest;
import com.qasymphony.qtest.automation.plugin.api.response.QAutomationPluginApiResponse;
import com.qasymphony.qtest.automation.testng.core.TestNGClassScanner;
import com.qasymphony.qtest.automation.testng.core.TestNgCommandBuilder;
import com.qasymphony.qtest.automation.util.SystemEnvironment;
import com.qasymphony.qtest.automation.util.validators.FileValidator;
import com.qasymphony.qtest.automation.util.validators.Validation;
import com.qasymphony.qtest.automation.util.validators.Validator;
import org.qas.api.internal.util.json.JsonArray;
import org.qas.api.internal.util.json.JsonException;
import org.qas.api.internal.util.json.JsonObject;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.*;

import static java.lang.String.format;
import static com.qasymphony.qtest.automation.plugin.api.response.DefaultQAutomationPluginApiResponse.success;
import static com.qasymphony.qtest.automation.plugin.api.response.DefaultQAutomationPluginApiResponse.error;
import static com.qasymphony.qtest.automation.plugin.api.response.DefaultQAutomationPluginApiResponse.badRequest;
/**
 * TestNgPlugin
 *
 * @author Dzung Nguyen
 * @version $Id TestNgPlugin 2015-06-08 12:25:30z dzungvnguyen $
 * @since 1.0
 */
@Extension
public class TestNgPlugin extends AbstractQAutomationPlugin {
  //~ class properties ========================================================
  private static final Logger LOG = Logger.getLogger(TestNgPlugin.class);

  public static final String EXTENSION = "automation-testing";

  public static final String REQUEST_PLUGIN_CONFIGURATION = "agent.atm.plugin-configuration";
  public static final String REQUEST_SCAN_TESTCASE = "agent.atm.scan-testcase";
  public static final String REQUEST_BUILD_COMMAND = "agent.atm.build-command";
  public static final String REQUEST_COLLECT_TESTLOGS = "agent.atm.collect-testlog";
	public static final String REQUEST_CLEANUP_ENVIRONMENT = "agent.atm.cleanup-environment";

  private static final List<String> supportedVersions = Arrays.asList("1.0");

  private final SystemEnvironment systemEnvironment;
  private final TestNgCommandBuilder testNgCommandBuilder;
  private final Map<String, MessageHandler> messageHandlerMap = new LinkedHashMap<>();

  //~ class members ===========================================================
  /**
   * Creates {@link TestNgPlugin TestNG plugin} instance.
   */
  public TestNgPlugin() {
    this.systemEnvironment = new SystemEnvironment();
    this.testNgCommandBuilder = new TestNgCommandBuilder(systemEnvironment);
    messageHandlerMap.put(REQUEST_PLUGIN_CONFIGURATION, getPluginConfigurationMessageHandler());
    messageHandlerMap.put(REQUEST_SCAN_TESTCASE, scanTestCasesMessageHandler());
    messageHandlerMap.put(REQUEST_BUILD_COMMAND, buildCommandMessageHandler());
    messageHandlerMap.put(REQUEST_COLLECT_TESTLOGS, collectTestLogsMessageHandler());
		messageHandlerMap.put(REQUEST_CLEANUP_ENVIRONMENT, cleanupEnvironmentMessageHandler());
  }

  /**
   * Load TestNG plugin.
   */
  @Load
  public void onLoad(PluginContext context) {
    LOG.info(format("[TestNG Plugin] Loading plugin ..."));
    final Validation validation = new Validation();

    // create validator.
    Validator validator = FileValidator.defaultFile(
      "lib/testng-plugin-log-collector.jar",
      "lib/testng-plugin-log-collector.jar",
      true
    );
    validator.validate(validation);

    if (!validation.isSuccessful()) {
      LOG.warn("[TestNG Plugin] error occurs during copying resource.");
      validation.logErrors();
    }

    LOG.info(format("[TestNG Plugin] Loaded."));
  }

  /**
   * Unload TestNG plugin.
   */
  @UnLoad
  public void onUnLoad(PluginContext context) {
    LOG.info(format("[TestNG Plugin] Unloading plugin ..."));

    // get destination file.
    File destinationFile = FileValidator.defaultFile(
      "lib/testng-plugin-log-collector.jar",
      "lib/testng-plugin-log-collector.jar",
      false
    ).getDestinationFile();

    if (destinationFile != null && destinationFile.exists()) {
      destinationFile.delete();
    }

    LOG.info(format("[TestNG Plugin] UnLoaded."));
  }

  @Override
  public QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request)
    throws UnhandledRequestTypeException {
    try {
      if (messageHandlerMap.containsKey(request.requestName())) {
        return messageHandlerMap.get(request.requestName()).handle(request);
      }

      return badRequest(format("Invalid request name %s", request.requestName()));
    } catch (Throwable e) {
      return error(e.getMessage());
    }
  }

  @Override
  public QAutomationPluginIdentifier pluginIdentifier() {
    return new QAutomationPluginIdentifier(EXTENSION, supportedVersions);
  }

  /**
   * @return the build command message handler.
   */
  MessageHandler buildCommandMessageHandler() {
    return new MessageHandler() {
      @Override
      public QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request) {
        LOG.info(format("[TestNG Plugin] handle build command request with message: %s", request.requestBody()));
        try {
          BuildCommandRequest commandRequest =
            new BuildCommandRequest().fromJson(new JsonObject(request.requestBody()));

          return success(testNgCommandBuilder.buildCommand(commandRequest).toJson().toString());
        } catch (Exception e) {
          LOG.warn(format("[TestNG Plugin] Error occurs during building command. Message: %s", e.getMessage()));
          return success(new CommandResponse().addError(e.getMessage()).toJson().toString());
        }
      }
    };
  }
	
  /**
   * @return the cleanup environment message handler.
   */
  MessageHandler cleanupEnvironmentMessageHandler() {
    return new MessageHandler() {
			@SuppressWarnings({"unchecked"})
      @Override
      public QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request) {
				LOG.info(format("[TestNG Plugin] handle cleanup environment request with message: %s", request.requestBody()));
				try {
					if (request.requestBody() != null) {
						JsonObject cleanupJson = new JsonObject(request.requestBody());
						
						BuildCommandRequest buildCommand = new BuildCommandRequest();
						JsonObject commandRequestJson = cleanupJson.optJsonObject("command_request");
						if (commandRequestJson != null) {
							buildCommand = buildCommand.fromJson(commandRequestJson);
						}
						
						// create map of environment.
						JsonObject environmentJson = cleanupJson.optJsonObject("environments");
						Map<String, String> environmentVariables = new HashMap<>();
						if (environmentJson != null && environmentJson.length() > 0) {
							Iterator<String> keyIt = (Iterator<String>) environmentJson.keys();
							while (keyIt.hasNext()) {
								String key = keyIt.next();
								environmentVariables.put(key, environmentJson.optString(key));
							}
						}
						
						// cleanup environment.
						testNgCommandBuilder.cleanupEnvironment(buildCommand, environmentVariables);
					}
				} catch (Exception e) {
          LOG.warn(format("[TestNG Plugin] Error occurs during cleanup environment. Message: %s", e.getMessage()));
				}
				
        return success("{}");
      }
    };
  }

  /**
   * @return the plugin configuration message handler.
   */
  MessageHandler getPluginConfigurationMessageHandler() {
    return new MessageHandler() {
      @Override
      public QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request) {
        LOG.info("[TestNG Plugin] handle get plugin configuration request.");
        AtmPluginConfiguration configuration = new AtmPluginConfiguration(
          "TestNG Agent", true, true
        );

        return success(configuration.toJson().toString());
      }
    };
  }

  /**
   * @return the collection of test-logs; this handler only return the empty list of
   * test-log because supporting realtime test-log collecting.
   */
  MessageHandler collectTestLogsMessageHandler() {
    return new MessageHandler() {
      @Override
      public QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request) {
        LOG.info("[TestNG Plugin] handle collect test-logs request.");
        return success("[]");
      }
    };
  }

  /**
   * @return scan test-cases message handler.
   */
  MessageHandler scanTestCasesMessageHandler() {
    return new MessageHandler() {
      @Override
      public QAutomationPluginApiResponse handle(QAutomationPluginApiRequest request) {
        LOG.info(format("[TestNG Plugin] handle scan test-case with message: %s", request.requestBody()));
        List<TestCase> testCases = null;

				TestNGClassScanner classScanner = null;
        try {
          TestScript testScript = new TestScript().fromJson(new JsonObject(request.requestBody()));

          String workingDirectory = normalizeWithEndSeparator(testScript.getTestDirectory());
          File templateFile = new File(workingDirectory, UUID.randomUUID().toString());
          classScanner = new TestNGClassScanner(templateFile);

          testCases = classScanner.scan(
            workingDirectory,
            testScript.getIncludePattern(),
            testScript.getExcludePattern(),
            normalizeWithEndSeparator(testScript.getLibraryDirectory()),
            testScript.isScanLibrary()
          );
        } catch (JsonException jex) {
          // ignore this exception.
        } catch (Exception e) {
          LOG.warn(format("[TestNG Plugin] Error occurs during scan TestNG test class. message: %s", e.getMessage()));
        } finally {
					try {
        		classScanner.cleanScanner();
					} catch (Exception e) {
						LOG.warn(format("[TestNG Plugin] Error occurred during cleaning scan resource. Message: %s", e.getMessage()), e);
					}
					
					LOG.info(format("[TestNG Plugin] scan test-case done."));
        }

        JsonArray jaTestCase = new JsonArray();

        if (testCases != null && !testCases.isEmpty()) {
          for (TestCase tc : testCases) {
            jaTestCase.put(tc.toJson());
          }
        }

        return success(jaTestCase.toString());
      }
    };
  }
	
	private static String normalizeWithEndSeparator(String filename) {
    String normalize = FilenameUtils.normalizeNoEndSeparator(filename);
    File file = new File(filename);
    if (file.isDirectory()) {
      return (normalize != null && normalize.trim().length() > 0) ? normalize.trim() + File.separator : "";
    } else {
      return normalize;
    }
  }
}
