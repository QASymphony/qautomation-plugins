package com.qasymphony.qtest.util;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.qasymphony.qtest.automation.plugin.api.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class XmlTransformerUtils {
  //~ class properties ========================================================
  private static final Logger logger = Logger.getLogger(XmlTransformerUtils.class);
  public static final String XML_ELEMENT_SUITE = "suite";
  public static final String XML_ATTRIBUTE_NAME = "name";
  public static final String XML_ELEMENT_TEST = "test";
  public static final String XML_ELEMENT_CLASSES = "classes";
  public static final String XML_ELEMENT_CLASS  = "class";
  public static final String XML_ELEMENT_METHODS = "methods";
  public static final String XML_ELEMENT_INCLUDE = "include";
  public static final String XML_ELEMENT_EXCLUDE = "exclude";
  public static final String XML_ELEMENT_PACKAGE = "package";
  public static final String XML_ELEMENT_GROUP = "groups";

  //~ class members ===========================================================
  public static Node transformXmlStringToTestElement(String xml) {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    try {
      xml = "<test>" + xml + "</test>";
      builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(xml)));
      NodeList nodes = doc.getChildNodes();

      if (nodes.getLength() == 1) {
        return nodes.item(0);
      }

      return null;
    } catch (Exception ex) {
      logger.warn("Error while transforming xml string to test element.", ex);
      return null;
    }
  }
  
  public static Node transformAutomationContentToTestElement(String content) {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    try {
      builder = factory.newDocumentBuilder();
      Document doc = builder.newDocument();
      Element testEle = doc.createElement(XML_ELEMENT_TEST);
      Element classesNode = doc.createElement(XML_ELEMENT_CLASSES);
      testEle.appendChild(classesNode);
      
      //create classes element
      String[] autoContent = content.split("#");
      String className = autoContent[0];
      Element classEle = doc.createElement(XML_ELEMENT_CLASS);
      classEle.setAttribute(XML_ATTRIBUTE_NAME, className);
      classesNode.appendChild(classEle);
      
      //create method element
      if (autoContent.length == 2) {
        String methodName = autoContent[1];
        Element methods = doc.createElement(XML_ELEMENT_METHODS);
        Element include = doc.createElement(XML_ELEMENT_INCLUDE);
        include.setAttribute(XML_ATTRIBUTE_NAME, methodName);
        methods.appendChild(include);
        classEle.appendChild(methods);
      }
      
      return testEle;
    } catch (Exception ex) {
      logger.warn("Error while transforming automation content to test element ", ex);
      return null;
    }
  }
  
}
