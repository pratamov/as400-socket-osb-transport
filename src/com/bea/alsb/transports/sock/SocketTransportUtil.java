/*
  Copyright (c) 2006 BEA Systems, Inc.
	All rights reserved

	THIS IS UNPUBLISHED PROPRIETARY
	SOURCE CODE OF BEA Systems, Inc.
	The copyright notice above does not
	evidence any actual or intended
	publication of such source code.
*/
package com.bea.alsb.transports.sock;

import com.bea.wli.sb.transports.EndPointConfiguration;
import com.bea.wli.sb.transports.TransportException;
import com.bea.wli.sb.transports.TransportManagerHelper;
import org.apache.xmlbeans.XmlObject;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides utility methods that can be used in Socket Transport.
 */
public class SocketTransportUtil {
  public static final String DEFAULT_MESSAGE_DELIMITER = "\\r\\n\\r\\n";
  private static final String RESOURCE_BUNDLE_NAME = "com.bea.alsb.transports.socket.SocketTransportMessages";
  public static final Logger logger = Logger.getLogger("sample.transports.socket", RESOURCE_BUNDLE_NAME);

  /**
   * Returns the SocketEndpointConfiguration of the passed configuration. If
   * the instance is of same type it will be casted and returned else
   * instance's stream will be parsed and returns SocketEndpointConfiguration.
   *
   * @param configuration
   * @return
   * @throws TransportException
   */
  public static SocketEndpointConfiguration getConfig(
    EndPointConfiguration configuration) throws TransportException {
    XmlObject xbean = configuration.getProviderSpecific();

    if (xbean instanceof SocketEndpointConfiguration) {
      return (SocketEndpointConfiguration) xbean;
    } else {
      try {
        return SocketEndpointConfiguration.Factory.parse(xbean.newInputStream());
      }
      catch (Exception e) {
        throw new TransportException(e.getMessage(),e);
      }
    }
  }

  /**
   * Establish and return a JMX Connector using the internal 'wlx' protocol
   * where wlx is a collocated protocol.
   *
   * @param jndiName the JNDI name of the mbean server to connect to.
   */
  public static JMXConnector getServerSideConnection(String jndiName)
    throws IOException {
    return getConnection(COLOCATED_PROTOCOL, jndiName, null, 0, null, null);
  }

  private static final String COLOCATED_PROTOCOL = "wlx";
  private static final String JNDI_ROOT = "/jndi/";

  private static JMXConnector getConnection(String protocol, String URI,
                                            String hostName, int portNumber,
                                            String userName, String password)
    throws IOException, MalformedURLException {
    JMXServiceURL serviceURL = null;
    if (protocol.equals(COLOCATED_PROTOCOL)) {
      serviceURL = new JMXServiceURL(protocol, null, 0, JNDI_ROOT + URI);
    } else {
      serviceURL =
        new JMXServiceURL(protocol, hostName, portNumber, JNDI_ROOT + URI);
    }

    Hashtable<String, String> h = new Hashtable<String, String>();
    h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES,
      "weblogic.management.remote");
    if (userName != null && password != null) {
      h.put(Context.SECURITY_PRINCIPAL, userName);
      h.put(Context.SECURITY_CREDENTIALS, password);
    }

    JMXConnector connection = JMXConnectorFactory.connect(serviceURL, h);

    return connection;
  }

  public static String toPublicServiceURI(String uriVal)
      throws URISyntaxException {
    Set<String> servers = null;
    if(TransportManagerHelper.isRuntimeEnabled()) {
        servers = TransportManagerHelper.getRuntimeServerNames();
    }

    String host = "localhost";
    if (TransportManagerHelper.isRuntimeEnabled() &&
        servers != null && !servers.isEmpty()) {
      // give one of the runtime servers as host.
      String server = servers.iterator().next();
      try {
        JMXConnector jmxConnector = SocketTransportUtil.getServerSideConnection(
            DomainRuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
        host = TransportManagerHelper.getDomainRuntimeService(jmxConnector).
            getDomainConfiguration().lookupServer(server).getListenAddress();
      } catch (Exception e) {
        SocketTransportUtil.logger.log(Level.SEVERE, "800137", e);
        host = "localhost";
      }
    }

    int port = Integer.parseInt(uriVal);
    URI uri = new URI("tcp", null, host, port, null, null, null);
    return uri.toString();
  }
  
  public static boolean validateMessageDelimiter(String messageDelimiter) {
    return true;
  }
  
  public static String decodeMessageDelimiter(String messageDelimiter) {
    StringBuilder sb = new StringBuilder();
    int prevIndex = 0;
    int currIndex = 0;
    while ((currIndex = messageDelimiter.indexOf('\\', prevIndex)) != -1) {
      sb.append(messageDelimiter.substring(prevIndex, currIndex));
      prevIndex = currIndex + 1;
      char escapedCharacter = messageDelimiter.charAt(prevIndex);
      switch (escapedCharacter) {
      case 'r':
        ++prevIndex;
        sb.append('\r');
        break;
      case 'n':
        ++prevIndex;
        sb.append('\n');
        break;
      default:
        sb.append('\\');
        break;
      }
    }
    sb.append(messageDelimiter.substring(prevIndex));
    return sb.toString();
  }

    public static String formatText(String key, Object... params)
    {
        return formatText(Locale.getDefault(), key, params);
    }

    public static String formatText(Locale locale, String key, Object... params)
    {
        ResourceBundle bundle = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME, locale);
        String pattern = bundle.getString(key);
        return MessageFormat.format(pattern, params);
    }
}
