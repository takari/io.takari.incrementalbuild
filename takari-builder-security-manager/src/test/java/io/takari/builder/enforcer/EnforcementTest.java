package io.takari.builder.enforcer;

import static org.junit.Assert.assertEquals;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.Policy;

public class EnforcementTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @After
  public void tearDownSecurityManager() {
    ComposableSecurityManagerPolicy.removeSystemSecurityManager();
  }

  @Test
  public void testXmlXinclude() throws Exception {
    // standard java library classes must not be able to access restricted resources

    File file = new File("src/test/data/xinclude/main.xml").getCanonicalFile();
    File forbidden = new File("src/test/data/xinclude/xincluded.xml").getCanonicalFile();

    ComposableSecurityManagerPolicy.setSystemSecurityManager();

    Policy policy = new EmptyPolicy() {
      @Override
      public void checkRead(String path) {
        if (forbidden.getAbsolutePath().equals(path)) {
          throw new SecurityException(path);
        }
      }
    };

    ComposableSecurityManagerPolicy.registerContextPolicy("test", policy);

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setXIncludeAware(true);
    DocumentBuilder db = dbf.newDocumentBuilder();

    // the xml parser is not expected to be able to access xincluded.xml file
    thrown.expect(SecurityException.class);
    thrown.expectMessage("xinclude/xincluded.xml");

    Document doc = db.parse(file);
    Element root = doc.getDocumentElement();
    Element xincluded = (Element) root.getElementsByTagName("xincluded").item(0);
    assertEquals("xincluded-value", xincluded.getTextContent());
  }

}
