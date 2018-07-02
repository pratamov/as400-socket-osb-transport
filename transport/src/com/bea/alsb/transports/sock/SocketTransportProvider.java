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

import com.bea.wli.config.Ref;
import com.bea.wli.config.env.NonQualifiedEnvValue;
import com.bea.wli.config.resource.Diagnostic;
import com.bea.wli.config.resource.Diagnostics;
import com.bea.wli.sb.management.query.ProxyServiceQuery;
import com.bea.wli.sb.services.ServiceInfo;
import com.bea.wli.sb.services.BindingTypeInfo;
import com.bea.wli.sb.transports.*;
import com.bea.wli.sb.transports.ui.TransportUIBinding;
import com.bea.wli.sb.transports.ui.TransportUIContext;
import com.bea.wli.sb.util.Refs;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlError;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Locale;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * This class implements {@link com.bea.wli.sb.transports.TransportProvider} and
 * provides functionality for socket level transport.
 */
public final class SocketTransportProvider
    implements TransportProvider, TransportCustomBindingProvider {

  private static SocketTransportProvider instance =
    new SocketTransportProvider();

  private SocketTransportCustomBindingProvider bindingProvider;
  /**
   * ID of the socket transport.
   */
  public static final String ID = "socket";
  private static final String URI = "URI";
  private static final String REQUEST_RESPONSE = "request-response";
  private Hashtable<Ref, SocketTransportEndPoint> endPoints =
    new Hashtable<Ref, SocketTransportEndPoint>();
  private static final String ENABLED = "#socket_ep_enabled";
  private static final String DELETE_ENDPOINT = "#_delete_socket_ep";
  private static final String UPDATE_OLD_ENDPOINT = "#_update_old_socket_ep";

  private SocketTransportProvider() {
    bindingProvider = new SocketTransportCustomBindingProvider(this);
  }

  /**
   * @return singleton object of this class.
   */
  public static SocketTransportProvider getInstance() {
    return instance;
  }

  /**
   * @return Returns {@link #ID}.
   */
  public String getId() {
    return ID;
  }

  /**
   * Validates the EndPointConfiguration and updates Diagnostics object if there
   * are any validation errors/messages.
   *
   * @param context
   */
  public void validateEndPointConfiguration(
    TransportValidationContext context) {
    if (context == null)
      throw new IllegalArgumentException("context is null");

    ServiceInfo serviceInfo = context.getServiceInfo();
    Diagnostics diags = context.getDiagnostics();
    Locale locale = context.getLocale();

    if (serviceInfo == null)
      throw new IllegalArgumentException("service info is null");
    if (diags == null)
      throw new IllegalArgumentException("diagnostics is null");

    EndPointConfiguration endPoint = serviceInfo.getEndPointConfiguration();
    String invalidURI = SocketTransportUtil.formatText(locale, "800109");
    // checking whether the configured URI is valid.
    if (!endPoint.getInbound()) {
      URIType[] uriArray = endPoint.getURIArray();
      for (URIType uriType : uriArray) {
        URI uri = null;
        try {
          uri = new URI(uriType.getValue());
        } catch (URISyntaxException e) {
          diags.add(Diagnostic.mkCannotCommit(0, null, invalidURI, null));
          continue;
        }
        if (!(uri.getScheme().equals("tcp") && uri.getHost() != null &&
          uri.getPort() != -1)) {
          diags.add(Diagnostic.mkCannotCommit(0, null, invalidURI, null));
        }
      }
    } else {
      String uri = endPoint.getURIArray()[0].getValue();
        try {
          Integer.parseInt(uri);
        } catch (NumberFormatException e) {
          diags.add(Diagnostic.mkCannotCommit(0, null, e.getMessage(), null));
        }

      try {
        List<Ref> refs = TransportManagerHelper.searchInstanceIds(
          Refs.PROXY_TYPE,
          ProxyServiceQuery.KEY_PROXYURI,
          uri);
        Ref serviceRef = serviceInfo.getRef();
        refs.remove(serviceRef);
        for (Ref ref : refs) {
          // URIs should not have conflicts with socket transport only.
          // (If your customized transport can have a conflict with other transports
          // then it should be validated accordingly.)
          if (ID.equals(TransportManagerHelper.getServiceInfo(ref).
                  getEndPointConfiguration().getProviderId())) {
            String message = SocketTransportUtil.formatText(locale, "800136", ref.getFullName());
            diags.add(Diagnostic.mkCannotCommit(0, null, message, null));
            break;
          }
        }
      } catch (TransportException e) {
        SocketTransportUtil.logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
      }
    }
  }


  /**
   * Creates and deploys an outbound or inbound endpoint on this server
   * <p/>
   * The semantics of this operation is as follows: prepare whatever is
   * necessary for this endpoint to be operational, but it cannot process
   * messages on this until a activationComplete() call has been received.
   */
  public TransportEndPoint createEndPoint(
    EndPointOperations.Create createContext) throws TransportException {
      if(!TransportManagerHelper.isRuntimeEnabled())
      {
          return null;
      }
    Ref ref = createContext.getRef();
    createContext.getScratchPad().put(ref.getFullName()+ENABLED, createContext.isEnabled());
    SocketTransportEndPoint socketTransportEndPoint =
      new SocketTransportEndPoint(ref,
        createContext.getEndPointConfiguration(), this);
    endPoints.put(ref, socketTransportEndPoint);
    SocketTransportUtil.logger.log(Level.INFO, "800111", ref.getFullName());
    return socketTransportEndPoint;
  }


  /**
   * Updates the existing endpoint with new configuration. The return value from
   * this method has to be a difference instance of TransportEndPoint object
   * than that which previously existed. The semantics are: prepare for update,
   * but do not update until activationComplete call has been received.
   *
   * @throws TransportException
   */
  public TransportEndPoint updateEndPoint(EndPointOperations.Update update)
    throws TransportException {
    if(!TransportManagerHelper.isRuntimeEnabled())
    {
          return null;
    }
    Ref ref = update.getRef();
    SocketTransportEndPoint oldEp = endPoints.get(ref);
    /** oldEP can be null, when the socket transport is restarted and existing
     * configuration is updated.
     */
    if (oldEp != null) {
      update.getScratchPad().put(ref.getFullName()+UPDATE_OLD_ENDPOINT, oldEp);
    }
    endPoints.remove(ref);
    update.getScratchPad().put(ref.getFullName()+ENABLED, update.isEnabled());
    SocketTransportEndPoint endPoint = new SocketTransportEndPoint(ref,
      update.getEndPointConfiguration(), this);
    endPoints.put(ref, endPoint);
    return endPoint;
  }

  /**
   * Suspends (disables) the endpoint with the given service reference
   */
  public void suspendEndPoint(EndPointOperations.Suspend suspend)
    throws TransportException {
  }

  /**
   * Resumes (Re-enables) a previously suspended endpoint with the given service
   * reference
   */
  public void resumeEndPoint(EndPointOperations.Resume resume)
    throws TransportException {
  }


  /**
   * Delete an endpoint associated with the given service reference
   */
  public void deleteEndPoint(EndPointOperations.Delete delete)
    throws TransportException {
    if(!TransportManagerHelper.isRuntimeEnabled())
    {
        return;
    }
    Ref ref = delete.getRef();
    SocketTransportEndPoint transportEndPoint = endPoints.remove(ref);
    delete.getScratchPad().put(ref.getFullName()+DELETE_ENDPOINT, transportEndPoint);
  }

  /**
   * called once per every create/update/delete/suspend/resume call to signal
   * that the activate action has completed with respect to the corresponding
   * endpoint object Does not imply success or failure of the overall session
   * activation! The provider is not allowed to throw exceptions as there is no
   * way to recover at this point.
   *
   * @param context
   */
  public void activationComplete(EndPointOperations.CommonOperation context) {
    Ref ref = context.getRef();
    EndPointOperations.EndPointOperationTypeEnum type = context.getType();
    SocketTransportEndPoint endPoint = endPoints.get(ref);

	  if(!TransportManagerHelper.isRuntimeEnabled())
      {
          return;
      }

    try {
      if (EndPointOperations.EndPointOperationTypeEnum.CREATE.equals(type)) {
          if ((Boolean) context.getScratchPad().get(ref.getFullName()+ENABLED)) {
              endPoint.start();
          }
      } else
      if (EndPointOperations.EndPointOperationTypeEnum.UPDATE.equals(type)) {
        SocketTransportEndPoint oldEP = (SocketTransportEndPoint) context
          .getScratchPad().get(ref.getFullName()+UPDATE_OLD_ENDPOINT);
        if (oldEP != null) {
          oldEP.stop();
        }
        if ((Boolean)context.getScratchPad().get(ref.getFullName()+ENABLED)) {
          endPoint.start();
        }
      } else
      if (EndPointOperations.EndPointOperationTypeEnum.DELETE.equals(type)) {
        SocketTransportEndPoint oldEP =
          (SocketTransportEndPoint) context.getScratchPad().get(ref.getFullName()+DELETE_ENDPOINT);
        if (oldEP != null) {
          oldEP.stop();
          SocketTransportUtil.logger.log(Level.INFO, "800123", ref.getFullName());
        } else {
          SocketTransportUtil.logger.log(Level.SEVERE, "800122", ref.getFullName());
        }
      } else
      if (EndPointOperations.EndPointOperationTypeEnum.RESUME.equals(type)) {
        if (endPoint != null) {
          endPoint.resume();
          SocketTransportUtil.logger.log(Level.INFO, "800112", ref.getFullName());
        } else {
          SocketTransportUtil.logger.log(Level.SEVERE, "800120", ref.getFullName());
        }
      } else
      if (EndPointOperations.EndPointOperationTypeEnum.SUSPEND.equals(type)) {
        if (endPoint != null) {
          endPoint.suspend();
          SocketTransportUtil.logger.log(Level.INFO, "800114", ref.getFullName());
        } else {
          SocketTransportUtil.logger.log(Level.SEVERE, "800116", ref.getFullName());
        }
      }
      SocketTransportUtil.logger.log(Level.INFO, "800115", ref.getFullName());
    } catch (Exception e) {
      SocketTransportUtil.logger.log(Level.SEVERE, "800132", ref.getFullName());
      SocketTransportUtil.logger.log(Level.SEVERE, e.getMessage(), e);
    }
  }

  /**
   * Return the list of all inbound and outbound endpoints for this provider
   */
  public Collection<? extends TransportEndPoint> getEndPoints() {
    return Collections.unmodifiableCollection(endPoints.values());
  }


  /**
   * return an endpoint with a given service reference
   */
  public TransportEndPoint getEndPoint(Ref ref) {
    return endPoints.get(ref);
  }

  /**
   * Sends an outbound message to an external service. The caller provides a
   * callback with is called when the response is received from an external
   * service. The semantics of the send operation are specific to the transport
   * implementation.
   *
   * @param sender   an instance of either ServiceTransportSender or
   *                 NoServiceTransportSender interface will be provided
   * @param listener a callback object the transport provider needs to invoke
   *                 asynchronously when the send operation is completed (for
   *                 one-way requests) or when the response has been received
   *                 (for request-response requests)
   * @param options  various options having to do with desired quality of
   *                 service, the mode, etc on the outbound request
   */
  public void sendMessageAsync(TransportSender sender,
                               TransportSendListener listener,
                               TransportOptions options)
    throws TransportException {
    /** whether the the other endpoint is inbound */
    boolean isInbound = false;

    if (sender instanceof ServiceTransportSender) {
      isInbound = ((ServiceTransportSender) sender).getEndPoint().isInbound();
    }

    if (!isInbound) {//other end point is an out-bound or none(NoServiceTransportSender).
      SocketOutboundMessageContext socketOutboundMessageContext =
        new SocketOutboundMessageContext(sender, options);
      socketOutboundMessageContext.send(listener);
    } else { // other endpoint is an inbound.
      SocketCoLocatedMessageContext socketCoLocatedMessageContext =
        new SocketCoLocatedMessageContext((ServiceTransportSender) sender,
          options);
      socketCoLocatedMessageContext.send(listener);
    }

  }


  /**
   * @return the XML schema type for the endpoint configuration for this
   *         provider
   */
  public SchemaType getEndPointConfigurationSchemaType() {
    return SocketEndpointConfiguration.type;
  }


  /**
   * @return the XML schema type of the request message for this provider
   */
  public SchemaType getRequestMetaDataSchemaType() {
    return SocketRequestMetaDataXML.type;
  }

  /**
   * @return the XML schema type of the request headers for this provider. If
   *         provider does not support request headers, return null.
   */
  public SchemaType getRequestHeadersSchemaType() {
    return SocketRequestHeadersXML.type;
  }

  /**
   * @return the XML schema type of the response message for this provider
   */
  public SchemaType getResponseMetaDataSchemaType() {
    return SocketResponseMetaDataXML.type;
  }


  /**
   * @return the XML schema type of the response headers for this provider. If
   *         provider does not support response headers, return null.
   */
  public SchemaType getResponseHeadersSchemaType() {
    return SocketResponseHeadersXML.type;
  }

  /**
   * @return the XML document for the static properties for this provider
   * @throws TransportException
   */
  public TransportProviderConfiguration getProviderConfiguration()
    throws TransportException {
    try {
      URL configUrl =
        this.getClass().getClassLoader().getResource("SocketConfig.xml");
      XmlOptions options = new XmlOptions().setLoadLineNumbers();
      TransportProviderConfiguration providerConfiguration =
        ProviderConfigurationDocument.Factory.parse(configUrl, options)
          .getProviderConfiguration();

      XmlOptions validateOptions = new XmlOptions();
      ArrayList<XmlError> errorList = new ArrayList<XmlError>();
      validateOptions.setErrorListener(errorList);

      boolean valid = providerConfiguration.validate(validateOptions);
      if (!valid) {
        StringBuilder sb =
          new StringBuilder(SocketTransportUtil.formatText("800139"));
        sb.append("\n");
        for (XmlError error : errorList) {          
          sb.append(SocketTransportUtil.formatText("800140",
            error.getLine()+"", error.getColumn()+"",
            error.getMessage())).append("\n");
        }
        throw new TransportException(sb.toString());
      }

      return providerConfiguration;
    } catch (Exception e) {
      SocketTransportUtil.logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
      if(e instanceof TransportException) {
        throw (TransportException) e;
      } else {
        throw new TransportException(e);
      }
    }
  }


  /**
   * Called at service definition time to get the provider-specific binding
   * object that validates provider-specific properties are present in the UI
   * context. The user interface will pass in a brand new instance of
   * TransportUIContext object for every time the user navigates the wizard. A
   * typical pattern for the provider is create a new instance of the
   * TransportUIBinding object and save the reference to the context and refer
   * to it as needed.
   */
  public TransportUIBinding getUIBinding(TransportUIContext context) {
    return new SocketTransportUIBinding(context);
  }

  /**
   * Called by the TransportManager when the server is shutting down
   */
  public void shutdown() {
    for (SocketTransportEndPoint endPoint : endPoints.values()) {
      endPoint.stop();
    }
  }


  /**
   * @return an empty List.
   */
  public Collection<NonQualifiedEnvValue> getEnvValues(Ref ref,
                                                       EndPointConfiguration epConfig) {
    return Collections.emptyList();
  }

  public void setEnvValues(Ref ref, EndPointConfiguration epConfig,
                           Collection<NonQualifiedEnvValue> envValues) {
  }

  /**
   * @return an empty List.
   */
  public Collection<Ref> getExternalReferences(EndPointConfiguration epConfig) {
    return Collections.emptyList();
  }

  public void setExternalReferences(Map<Ref, Ref> mapRefs,
                                    EndPointConfiguration epConfig) {
  }


  /**
   * Given a proxy service reference returns a map of string properties that
   * contains name/value pairs which are all the necessary provider-specific
   * attributes for a business service object to be instantiated (on a different
   * ALSB domain) that can invoke this proxy service. All the fields that are
   * exposed will be externalized by the transport provider (for e.g. replace
   * occurrences of localhost with actual server name etc.). If the transport
   * provider needs a specific business service URI which is different from a
   * proxy URI,  the properties object they return should contain a property
   * with a key "URI" and a string value to be used when creating the business
   * service. Only transport providers that support both proxy AND business
   * services (i.e. inbound AND outbound directions) need to support this.
   * Otherwise they can throw an UnsupportedOperationException. This is used in
   * UDDI import/export feature of ASLB.
   *
   * @param ref
   * @return a map of string properties that
   * contains name/value pairs which are all the necessary provider-specific
   * attributes for a business service object to be instantiated.
   */
  public Map<String, String> getBusinessServicePropertiesForProxy(Ref ref)
      throws TransportException {
    Map<String, String> props = new HashMap<String, String>();
    SocketTransportEndPoint endPoint = endPoints.get(ref);
    String uriVal = endPoint.getURI()[0].toString();
    try {
      props.put(URI, SocketTransportUtil.toPublicServiceURI(uriVal));
    } catch (URISyntaxException e) {
      throw new TransportException(e, e.getMessage());
    }
    SocketEndpointConfiguration sockConfig =
      endPoint.getSocketEndpointConfiguration();
    props.put(REQUEST_RESPONSE, sockConfig.getRequestResponse() + "");
    return props;
  }

  /**
   * Given a map of properties object from one ALSB domain returns a transport
   * endpoint configuration that can be used to instantiate a business service
   * on another ALSB domain. Only transport providers that support both proxy
   * AND business services (i.e. inbound AND outbound directions) need to
   * support this. Otherwise they can throw an UnsupportedOperationException.
   * This is used in UDDI import/export feature of ASLB.
   *
   * @param ref   if not null, it is assumed that there already exists a service
   *              endpoint with a given ref, and the result of the method will
   *              be a merge of existing configuration and passed in
   *              properties.
   * @param props
   * @return returns a transport endpoint configuration
   */

  public XmlObject getProviderSpecificConfiguration(Ref ref,
                                                    Map<String, String> props)
    throws TransportException {
    SocketEndpointConfiguration sockEPConfig = null;
    if (ref != null) {
      SocketTransportEndPoint endPoint =
        (SocketTransportEndPoint) getEndPoint(ref);
      if (endPoint == null) {
        throw new TransportException(SocketTransportUtil.formatText("800117", ref.getFullName()));
      }
      sockEPConfig = endPoint.getSocketEndpointConfiguration();
    } else {
      sockEPConfig = SocketEndpointConfiguration.Factory.newInstance();
    }
    sockEPConfig
      .setRequestResponse(Boolean.valueOf(props.get(REQUEST_RESPONSE)));
    String uri = props.get(URI);
    String newUri = null;
    try {
      newUri = new URI(uri).getPort() +"";
    } catch (URISyntaxException e) {
      throw TransportException.newInstance(e);
    }
    props.put(URI, newUri);
    return sockEPConfig;
  }

  public String getIdentifierURI(EndPointConfiguration endpoint,
                                 BindingTypeInfo.BindingTypeEnum bindingType,
                                 Ref serviceRef) {
    return bindingProvider.getIdentifierURI(endpoint, bindingType, serviceRef);
  }

  public List<String> getEndPointAddress(EndPointConfiguration endpoint,
                                         BindingTypeInfo.BindingTypeEnum bindingType,
                                         Ref serviceRef) {
    return bindingProvider.getEndPointAddress(endpoint, bindingType, serviceRef);
  }

}
