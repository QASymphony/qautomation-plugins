package com.qasymphony.qtest.automation.testng.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.qasymphony.qtest.automation.util.DirectoryScanner;
import com.qasymphony.qtest.util.XmlTransformerUtils;

public class TestCaseRunService {
  //~ class members ===========================================================
  /**
   * Generate new testNG xml file in targetPath Directory
   * 
   * @param contentSets the set of automation content (Java TestCase class)
   * @param targetPath the target path.
   *
   * @throws ParserConfigurationException if an error occurs during parsing XML.
   * @throws TransformerException if an error occurs during generate XML file.
   * @return The TestNG XML file.
   */
  public String generateTestNGXml(Set<String> contentSets, String targetPath) throws ParserConfigurationException,
      TransformerException {

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    // Create <suite> element
    Document doc = docBuilder.newDocument();
    Element suiteElement = doc.createElement(XmlTransformerUtils.XML_ELEMENT_SUITE);
    suiteElement.setAttribute(XmlTransformerUtils.XML_ATTRIBUTE_NAME, DateFormatUtils.format(new Date(), "yyyyMMdd"));
    doc.appendChild(suiteElement);

    for (String content : contentSets) {
      // Create <test> element with automation content information
      Element testElement = (Element) doc.importNode(
          XmlTransformerUtils.transformAutomationContentToTestElement(content), true);
      testElement.setAttribute(XmlTransformerUtils.XML_ATTRIBUTE_NAME, content);
      suiteElement.appendChild(testElement);
    }

    String fileName = "generated_xml_" + DateFormatUtils.format(new Date(), "yyyyMMddhhmmss");
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

    DOMSource source = new DOMSource(doc);
    File xmlFile = new File(targetPath, fileName + ".xml");
    StreamResult result = new StreamResult(xmlFile);
    transformer.transform(source, result);
    return xmlFile.toString();
  }

  /**
   * Generate TestNG XML file based on existing file. This method will parse and collect
   * the configuration in existing TestNG XML, remove un-necessary tag and append the
   * needed test into XML file.
   * 
   * @param contentSets the given set of automation test content (Java Test class)
   * @param sourceXmlPath the source XML path.
   * @param targetPath the target directory where the new TestNG XML place.
   *
   * @throws ParserConfigurationException if an error occurs during parsing existing XML.
   * @throws SAXException if an error occurs during constructing SAX engine.
   * @throws IOException if an error occurs during reading existing XML.
   * @throws TransformerException if an error occurs during generate XML file.
   * @return The new TestNG XML file.
   */
  public String generateTestNGXml(Set<String> contentSets, String sourceXmlPath, String targetPath)
      throws ParserConfigurationException, SAXException, IOException, TransformerException {
    File xmlSourceFile = new File(sourceXmlPath);

    // the source file may not be existed or empty, so generate new file.
    if (!xmlSourceFile.exists() || xmlSourceFile.length() == 0) {
      return generateTestNGXml(contentSets, targetPath);
    }

    // generate the TestNG XML based on existing file.
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    docFactory.setIgnoringComments(true);
    docFactory.setValidating(false);
    docFactory.setIgnoringElementContentWhitespace(true);
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(xmlSourceFile);

    // build set of run class
    Map<String, Set<String>> classSets = new HashMap<>();
    for (String content : contentSets) {
      String className = content.split("#")[0];
      Set<String> methodSets = classSets.get(className);
      if (methodSets == null) {
        methodSets = new HashSet<>();
        classSets.put(className, methodSets);
      }
      if (content.split("#").length == 1) {
        methodSets.add("*");
      } else {
        String methodName = content.split("#")[1];
        methodSets.add(methodName);
      }
    }

    List<Element> removeElements = new ArrayList<>();

    NodeList classElements = doc.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_CLASS);

    // scan class tag then remove if not exist in content set
    if (classElements.getLength() > 0) {
      int length = classElements.getLength();
      for (int i = 0; i < length; i++) {
        Element classElement = (Element) classElements.item(i);
        String className = classElement.getAttribute(XmlTransformerUtils.XML_ATTRIBUTE_NAME);
        // if (className.isEmpty() || !classSets.contains(className)) {
        // removeElements.add(classElement);
        // }
        Set<String> methodSets = classSets.get(className);
        if (methodSets == null) {
          removeElements.add(classElement);
        } else {
          NodeList includeElement = doc.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_INCLUDE);
          for (int j = 0; j < includeElement.getLength(); j++) {
            removeElements.add((Element) includeElement.item(j));
          }

          NodeList excludeElement = doc.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_EXCLUDE);
          for (int j = 0; j < excludeElement.getLength(); j++) {
            removeElements.add((Element) excludeElement.item(j));
          }

          classSets.remove(className);
        }
      }
    }

    NodeList packageElements = doc.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_PACKAGE);

    if (packageElements.getLength() > 0) {
      int length = packageElements.getLength();
      for (int i = 0; i < length; i++) {
        Element packageElement = (Element) packageElements.item(i);
        String packageName = packageElement.getAttribute(XmlTransformerUtils.XML_ATTRIBUTE_NAME).replace(".",
            File.separator);
        boolean packageExist = false;
        for (Iterator<String> iterator = classSets.keySet().iterator(); iterator.hasNext();) {
          String className = iterator.next();
          if (DirectoryScanner.match(packageName, className.replace(".", File.separator))) {
            if (!packageExist)
              packageExist = true;
            iterator.remove();
          }
        }
        if (!packageExist) {
          removeElements.add(packageElement);
        }
      }
    }

    // remove all groups tag
    NodeList groupElements = doc.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_GROUP);
    for (int i = 0; i < groupElements.getLength(); i++) {
      Element groupElement = (Element) groupElements.item(i);
      removeElements.add(groupElement);
    }

    for (Element element : removeElements) {
      Element parentNode = (Element) element.getParentNode();
      parentNode.removeChild(element);
      // remove empty tag
      // if (parentNode.getChildNodes().getLength() == 0) {
      // parentNode.getParentNode().removeChild(parentNode);
      // }
    }

    NodeList suiteList = doc.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_SUITE);
    Element suiteElement;
    if (suiteList.getLength() == 0) {
      suiteElement = doc.createElement(XmlTransformerUtils.XML_ELEMENT_SUITE);
    } else {
      suiteElement = (Element) suiteList.item(0);
    }

    //generate not exist test tag
    for (String className : classSets.keySet()) {
      Set<String> methodSets = classSets.get(className);
      Element testElement = (Element) doc.importNode(XmlTransformerUtils.transformAutomationContentToTestElement(className), true);
      testElement.setAttribute(XmlTransformerUtils.XML_ATTRIBUTE_NAME, className + (new Date()).getTime());
      suiteElement.appendChild(testElement);
      if (!methodSets.contains("*")) {
        Element methods = doc.createElement(XmlTransformerUtils.XML_ELEMENT_METHODS);
        for (String methodName : methodSets) {
          Element includeElement = doc.createElement(XmlTransformerUtils.XML_ELEMENT_INCLUDE);
          includeElement.setAttribute(XmlTransformerUtils.XML_ATTRIBUTE_NAME, methodName);
          methods.appendChild(includeElement);
        }
        Element classElement = (Element) testElement.getElementsByTagName(XmlTransformerUtils.XML_ELEMENT_CLASS).item(0);
        classElement.appendChild(methods);
        
      }
    }

    String fileName = FilenameUtils.getBaseName(sourceXmlPath) + DateFormatUtils.format(new Date(), "yyyyMMddhhmmss");
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

    DOMSource source = new DOMSource(doc);
    File xmlFile = new File(targetPath, fileName + ".xml");
    StreamResult result = new StreamResult(xmlFile);
    transformer.transform(source, result);
    return xmlFile.toString();
  }
}
