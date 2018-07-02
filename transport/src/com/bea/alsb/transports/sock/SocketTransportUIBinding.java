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

import com.bea.alsb.presentation.CustomHelpProvider;

import com.bea.wli.sb.services.BindingTypeInfo;
import com.bea.wli.sb.transports.EndPointConfiguration;
import com.bea.wli.sb.transports.TransportException;
import com.bea.wli.sb.transports.TransportManagerHelper;
import com.bea.wli.sb.transports.TransportValidationContext;
import com.bea.wli.sb.transports.ui.TransportEditField;
import com.bea.wli.sb.transports.ui.TransportUIBinding;
import com.bea.wli.sb.transports.ui.TransportUIContext;
import com.bea.wli.sb.transports.ui.TransportUIError;
import com.bea.wli.sb.transports.ui.TransportUIFactory;
import static com.bea.wli.sb.transports.ui.TransportUIFactory.getStringValues;
import com.bea.wli.sb.transports.ui.TransportUIGenericInfo;
import com.bea.wli.sb.transports.ui.TransportViewField;
import org.apache.xmlbeans.XmlObject;
import javax.management.remote.JMXConnector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.util.logging.Level;

import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;

/**
 * This class represents the binding between UI and the metdata of the
 * transport. It provides UI validation and rendering of the transport provider
 * specific objects.
 */
public class SocketTransportUIBinding
    implements TransportUIBinding, CustomHelpProvider {
  private TransportUIContext uiContext;
  private static final String ENABLE_NAGLE_ALGORITHM =
    "Enable Nagle Algorithm";
  private static final String BACKLOG = "Backlog";
  private static final String TIME_OUT = "Timeout (in milli seconds)";
  private static final String REQUEST_RESPONSE = "Is response required";
  private static final String REQUEST_ENCODING = "Request encoding";
  private static final String RESPONSE_ENCODING = "Response encoding";
  private static final String DISPATCH_POLICY = "Dispatch policy";
  private static final String INBOUND_URI_FORMAT_AUTOFILL = "9999";
  private static final String OUTBOUND_URI_FORMAT_AUTOFILL =
        "tcp://localhost:8888";
  private static final String MESSAGE_DELIMITER = "Message Delimiter";

  private Locale locale;

  public SocketTransportUIBinding(TransportUIContext uiContext) {
    this.uiContext = uiContext;
    locale = uiContext.getLocale();
  }

  /**
   * Returns true if the message type is either TEXT or XML. Socket transport
   * supports XML and TEXT message types only for both the request and the
   * response messages.
   *
   * @param bindingType
   * @return
   */
  public boolean isServiceTypeSupported(BindingTypeInfo bindingType) {
    try {
      BindingTypeInfo.BindingTypeEnum type = bindingType.getType();

      /**
       * If the binding is mixed, request type should exist and it should be
       * either TEXT or XML type and  if there is any response type,
       * it must be either TEXT or XML.
       */
      if (type.equals(BindingTypeInfo.BindingTypeEnum.MIXED)) {
        BindingTypeInfo.MessageTypeEnum responseMessageType =
          bindingType.getResponseMessageType();
        if (responseMessageType != null) {
          if (!(
            BindingTypeInfo.MessageTypeEnum.TEXT.equals(responseMessageType) ||
              BindingTypeInfo.MessageTypeEnum.XML
                .equals(responseMessageType))) {
            return false;
          }
        }
        BindingTypeInfo.MessageTypeEnum requestMessageType =
          bindingType.getRequestMessageType();
        if (requestMessageType != null) {
          return
            BindingTypeInfo.MessageTypeEnum.TEXT.equals(requestMessageType) ||
              BindingTypeInfo.MessageTypeEnum.XML.equals(requestMessageType);
        } else {
          return false;
        }
      }
      /**
       * Binding type must be either ABSTRACT_XML or XML.
       */
      return type.equals(BindingTypeInfo.BindingTypeEnum.ABSTRACT_XML)
        || type.equals(BindingTypeInfo.BindingTypeEnum.XML);
    } catch (TransportException e) {
      SocketTransportUtil.logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
      return false;
    }
  }


  /**
   * Called at service definition time to provide information that is provider
   * specific for the main transport page. This includes information like URI
   * hints and autofill field data.
   *
   * @return
   */
  public TransportUIGenericInfo getGenericInfo() {
    TransportUIGenericInfo genInfo = new TransportUIGenericInfo();
    if (uiContext.isProxy()) {
      genInfo.setUriFormat(
        TextMessages.getMessage(TextMessages.INBOUND_URI_FORMAT, locale));
      genInfo.setUriAutofill(INBOUND_URI_FORMAT_AUTOFILL);
    } else {
      genInfo.setUriFormat(
        TextMessages.getMessage(TextMessages.OUTBOUND_URI_FORMAT, locale));
      genInfo.setUriAutofill(OUTBOUND_URI_FORMAT_AUTOFILL);
    }
    return genInfo;
  }


  /**
   * Called at service definition time to get provider-specific contents of the
   * edit pane for endpoint configuration. This method is called when the
   * transport configuration page is first rendered.
   */
  public TransportEditField[] getEditPage(EndPointConfiguration config,
                                          BindingTypeInfo binding)
    throws TransportException {
    List<TransportEditField> fields = new ArrayList<TransportEditField>();
    SocketEndpointConfiguration sockConfig = null;
    if (config != null && config.isSetProviderSpecific()) {
      sockConfig = SocketTransportUtil.getConfig(config);
    }
    String requestEncoding = null;
    String responseEncoding = null;
    if (sockConfig != null) {
      requestEncoding = sockConfig.getRequestEncoding();
      responseEncoding = sockConfig.getResponseEncoding();
    }
    boolean requestResponse =
      sockConfig == null || sockConfig.getRequestResponse();
    TransportUIFactory.CheckBoxObject checkbox =
      TransportUIFactory.createCheckbox(null, requestResponse, true);
    TransportEditField editField =
      TransportUIFactory.createEditField(REQUEST_RESPONSE,
        TextMessages.getMessage(TextMessages.REQUEST_RESPONSE, locale),
        TextMessages.getMessage(TextMessages.REQUEST_RESPONSE_INFO, locale),
        false, checkbox);
    fields.add(editField);
    long timeout = 5000;
    boolean enableNA = true;
    String messageDelimiter = SocketTransportUtil.DEFAULT_MESSAGE_DELIMITER;

    if (uiContext.isProxy()) {
      int backlog = 5;
      if (sockConfig != null) {
        SocketInboundPropertiesType inboundProperties =
          sockConfig.getInboundProperties();
        backlog = inboundProperties.getBacklog();
        timeout = inboundProperties.getTimeout();
        enableNA = inboundProperties.getEnableNagleAlgorithm();
        messageDelimiter = inboundProperties.getMessageDelimiter();
      }
      TransportUIFactory.TextBoxObject textBox =
        TransportUIFactory.createTextBox(backlog + "", 20);
      editField =
        TransportUIFactory
          .createEditField(BACKLOG,
            TextMessages.getMessage(TextMessages.BACKLOG, locale),
            TextMessages.getMessage(TextMessages.BACKLOG_INFO, locale), false,
            textBox);
      fields.add(editField);
    } else {
      if (sockConfig != null) {
        SocketOutboundPropertiesType outboundProperties =
          sockConfig.getOutboundProperties();
        timeout = outboundProperties.getTimeout();
        enableNA = outboundProperties.getEnableNagleAlgorithm();
        messageDelimiter = outboundProperties.getMessageDelimiter();
      }
    }
    
    TransportUIFactory.TextBoxObject messageDelimiterTextBox = TransportUIFactory
        .createTextBox(messageDelimiter, 32);
    editField = TransportUIFactory.createEditField(MESSAGE_DELIMITER,
        TextMessages.getMessage(TextMessages.MESSAGE_DELIMITER, locale),
        TextMessages.getMessage(TextMessages.MESSAGE_DELIMITER_INFO, locale),
        false, messageDelimiterTextBox);
    fields.add(editField);

    TransportUIFactory.TextBoxObject textBox =
      TransportUIFactory.createTextBox(timeout + "", 20);
    editField = TransportUIFactory
      .createEditField(TIME_OUT,
        TextMessages.getMessage(TextMessages.TIME_OUT, locale),
        TextMessages.getMessage(TextMessages.TIME_OUT_INFO, locale), false,
        textBox);
    fields.add(editField);
    checkbox = TransportUIFactory.createCheckbox(enableNA);
    editField =
      TransportUIFactory.createEditField(ENABLE_NAGLE_ALGORITHM,
        TextMessages.getMessage(TextMessages.ENABLE_NAGLE_ALGORITHM, locale),
        TextMessages.getMessage(TextMessages.ENABLE_NAGLE_ALGORITHM_INFO,
          locale)
        , false, checkbox);
    fields.add(editField);

    TransportUIFactory.TransportUIObject uiObject =
      TransportUIFactory.createTextBox(requestEncoding, 10);
    TransportEditField field =
      TransportUIFactory.createEditField(
        REQUEST_ENCODING,
        TextMessages.getMessage(TextMessages.REQUEST_ENCODING, locale),
        TextMessages.getMessage(TextMessages.REQUEST_ENCODING_INFO, locale),
        uiObject);
    fields.add(field);

    uiObject = TransportUIFactory.createTextBox(responseEncoding, 10);
    field = TransportUIFactory.createEditField(RESPONSE_ENCODING,
      TextMessages.getMessage(TextMessages.RESPONSE_ENCODING, locale),
      TextMessages.getMessage(TextMessages.RESPONSE_ENCODING_INFO, locale),
      uiObject);
    fields.add(field);

    String curDispatchPolicy = DEFAULT_WORK_MANAGER;
    if (sockConfig != null && sockConfig.getDispatchPolicy() != null) {
      curDispatchPolicy = sockConfig.getDispatchPolicy();
    }
    if (curDispatchPolicy == null) {
      curDispatchPolicy = DEFAULT_WORK_MANAGER;
    }
    field = getDispatchPolicyEditField(curDispatchPolicy);
    fields.add(field);

    return fields.toArray(new TransportEditField[fields.size()]);
  }


  /**
   * Called at service definition time to get contents of the edit pane for
   * endpoint configuration. This method is called each time the event for the
   * field of the given name is triggered. The set of field can be updated
   * accordingly.
   */
  public TransportEditField[] updateEditPage(TransportEditField[] fields,
                                             String name)
    throws TransportException {
    /** update the values only for REQUEST_RESPONSE field. */
    if (!REQUEST_RESPONSE.equals(name)) {
      return fields;
    }
    /** RESPONSE_ENCODING field should be enabled only when  REQUEST_RESPONSE
     * is true.*/
    Map<String, TransportEditField> fieldMap =
      TransportEditField.getFieldMap(fields);
    TransportEditField editField = fieldMap.get(REQUEST_RESPONSE);
    TransportUIFactory.CheckBoxObject selectObject =
      (TransportUIFactory.CheckBoxObject) editField.getObject();
    boolean b = selectObject.ischecked();
    fieldMap.get(RESPONSE_ENCODING).setDisabled(!b);
    return fields;
  }


  /**
   * Called at the time the service details are viewed in read-only mode to get
   * the contents of the summary pane for endpoint configuration
   */
  public TransportViewField[] getViewPage(EndPointConfiguration config)
    throws TransportException {
    List<TransportViewField> fields = new ArrayList<TransportViewField>();
    SocketEndpointConfiguration socketEndpointConfiguration =
      SocketTransportUtil.getConfig(config);
    TransportViewField field =
      new TransportViewField(REQUEST_RESPONSE,
        TextMessages.getMessage(TextMessages.REQUEST_RESPONSE, locale),
        socketEndpointConfiguration.getRequestResponse());
    fields.add(field);

    if (uiContext.isProxy()) {
      SocketInboundPropertiesType inboundProperties =
        socketEndpointConfiguration.getInboundProperties();

      field = new TransportViewField(BACKLOG,
        TextMessages.getMessage(TextMessages.BACKLOG, locale),
        inboundProperties.getBacklog());
      fields.add(field);
      
      field = new TransportViewField(MESSAGE_DELIMITER, TextMessages
          .getMessage(TextMessages.MESSAGE_DELIMITER, locale),
          inboundProperties.getMessageDelimiter());
      fields.add(field);

      field = new TransportViewField(TIME_OUT,
        TextMessages.getMessage(TextMessages.TIME_OUT, locale),
        inboundProperties.getTimeout());
      fields.add(field);

      field = new TransportViewField(ENABLE_NAGLE_ALGORITHM,
        TextMessages.getMessage(TextMessages.ENABLE_NAGLE_ALGORITHM, locale),
        inboundProperties.getEnableNagleAlgorithm());
      fields.add(field);
    } else {
      SocketOutboundPropertiesType outboundProperties =
        socketEndpointConfiguration.getOutboundProperties();
      
      field = new TransportViewField(MESSAGE_DELIMITER, TextMessages
          .getMessage(TextMessages.MESSAGE_DELIMITER, locale),
          outboundProperties.getMessageDelimiter());
      fields.add(field);
      
      field = new TransportViewField(TIME_OUT,
        TextMessages.getMessage(TextMessages.TIME_OUT, locale),
        outboundProperties.getTimeout());
      fields.add(field);

      field = new TransportViewField(ENABLE_NAGLE_ALGORITHM,
        TextMessages.getMessage(TextMessages.ENABLE_NAGLE_ALGORITHM, locale),
        outboundProperties.getEnableNagleAlgorithm());
      fields.add(field);

    }

    field = new TransportViewField(REQUEST_ENCODING,
      TextMessages.getMessage(TextMessages.REQUEST_ENCODING, locale),
      socketEndpointConfiguration.getRequestEncoding());
    fields.add(field);

    field = new TransportViewField(RESPONSE_ENCODING,
      TextMessages.getMessage(TextMessages.RESPONSE_ENCODING, locale),
      socketEndpointConfiguration.getResponseEncoding());
    fields.add(field);

    String dispatchPolicy = socketEndpointConfiguration.getDispatchPolicy();
    if (dispatchPolicy == null) {
      dispatchPolicy = DEFAULT_WORK_MANAGER;
    }
    field = new TransportViewField(DISPATCH_POLICY,
      TextMessages.getMessage(TextMessages.DISPATCH_POLICY, locale),
      dispatchPolicy
    );
    fields.add(field);

    return fields.toArray(new TransportViewField[fields.size()]);
  }

  public static final String DEFAULT_WORK_MANAGER = "default";


  /**
   * Builds the disptahc policies in the ui object.
   *
   * @param curDispatchPolicy
   * @return TransportEditField containing existing dispatch policies.
   */
  public TransportEditField getDispatchPolicyEditField(
    String curDispatchPolicy) {

    TransportUIFactory.TransportUIObject uiObject = null;
    Set<String> wmSet = null;
    JMXConnector jmxConnector = null;

    if (TransportManagerHelper.isOffline()) {
      jmxConnector =
        (JMXConnector) uiContext.get(TransportValidationContext.JMXCONNECTOR);
    } else {
      try {
        jmxConnector = SocketTransportUtil.getServerSideConnection(
          DomainRuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
      } catch (Exception e) {
        SocketTransportUtil.logger.log(Level.SEVERE, "800137", e);
      }
    }
    try {
      if (jmxConnector != null) {
        wmSet = TransportManagerHelper.getDispatchPolicies(jmxConnector);
      } else {
        wmSet = TransportManagerHelper.getDispatchPolicies();
      }
    } catch (Exception ex) {
      wmSet = null; //continue
      SocketTransportUtil.logger.log(Level.SEVERE, "800124", ex);
    }


    if (wmSet == null) {
      // if JMXConnector not available or impossible to connect provide a simple edit field
      uiObject = TransportUIFactory.createTextBox(curDispatchPolicy);
    } else {
      // create a drop down list
      // adding default work manager to the list.
      wmSet.add(DEFAULT_WORK_MANAGER);

      String[] values = wmSet.toArray(new String[wmSet.size()]);
      uiObject = TransportUIFactory.createSelectObject(
        values,
        values,
        curDispatchPolicy,
        TransportUIFactory.SelectObject.DISPLAY_LIST,
        false);
    }

    return TransportUIFactory.createEditField(DISPATCH_POLICY,
      TextMessages.getMessage(TextMessages.DISPATCH_POLICY, locale),
      TextMessages.getMessage(TextMessages.DISPATCH_POLICY_INFO, locale),
      uiObject);
  }

  /**
   * Validates the main form of the transport by checking whether the configured
   * URIs are valid or not.
   *
   * @param fields
   * @return Returns an array of TransportUIError of the invalid URIs.
   */
  public TransportUIError[] validateMainForm(TransportEditField[] fields) {
    Map<String, TransportUIFactory.TransportUIObject> map =
      TransportEditField.getObjectMap(fields);

    List<TransportUIError> errors = new ArrayList<TransportUIError>();
    if (!uiContext.isProxy()) {
      List<String[]> uris = getStringValues(map, TransportUIBinding.PARAM_URI);
      for (String[] uristr : uris) {
        try {
          URI uri = new URI(uristr[0]);
          if (!(uri.getScheme().equals("tcp") && uri.getHost() != null)) {
            errors.add(new TransportUIError(TransportUIBinding.PARAM_URI,
              "Invalid URI"));
          }
          validatePort(uri.getPort(), errors);
        } catch (URISyntaxException e) {
          errors.add(new TransportUIError(TransportUIBinding.PARAM_URI,
            e.getMessage()));
        }
      }
    } else {
      List<String[]> uris = getStringValues(map, TransportUIBinding.PARAM_URI);
      for (String[] uristr : uris) {
        String str = uristr[0];
        try {
          int port = Integer.parseInt(str);
          validatePort(port, errors);
        } catch (NumberFormatException e) {
          errors.add(new TransportUIError(TransportUIBinding.PARAM_URI,
              "Port should be a valid positive integer."));
        }
      }
    }

    return errors == null || errors.isEmpty() ? null :
      errors.toArray(new TransportUIError[errors.size()]);
  }

  private void validatePort(int port, List<TransportUIError> errors) {
    if(port <=0 ) {
      errors.add(new TransportUIError(TransportUIBinding.PARAM_URI,
              "Port should be a valid positive integer."));
    }
  }


  /**
   * validate the provider-specific transport endpoint parameters in the
   * request.
   */
  public TransportUIError[] validateProviderSpecificForm(
    TransportEditField[] fields) {
    /** Socket transport configuration cn be validated here. */
    return new TransportUIError[0];
  }


  /**
   * creates the Transport Provider Specific configuration from the UI form.
   * This method will be called only upon a successfull call to {@link
   * #validateMainForm} and {@link #validateProviderSpecificForm}
   */
  public XmlObject getProviderSpecificConfiguration(TransportEditField[] fields)
    throws TransportException {

    SocketEndpointConfiguration socketEndpointConfig =
      SocketEndpointConfiguration.Factory.newInstance();
    Map<String, TransportUIFactory.TransportUIObject> map =
      TransportEditField.getObjectMap(fields);
    socketEndpointConfig.setRequestResponse(
      TransportUIFactory.getBooleanValue(map, REQUEST_RESPONSE));

    if (uiContext.isProxy()) {
      SocketInboundPropertiesType socketInboundPropertiesType =
        socketEndpointConfig.addNewInboundProperties();
      socketInboundPropertiesType
        .setBacklog(TransportUIFactory.getIntValue(map, BACKLOG));
      socketInboundPropertiesType.setEnableNagleAlgorithm(
        TransportUIFactory.getBooleanValue(map, ENABLE_NAGLE_ALGORITHM));
      socketInboundPropertiesType
        .setTimeout(TransportUIFactory.getIntValue(map, TIME_OUT));
      socketInboundPropertiesType.setMessageDelimiter(
          TransportUIFactory.getStringValue(map, MESSAGE_DELIMITER));
    } else {
      SocketOutboundPropertiesType socketOutboundPropertiesType =
        socketEndpointConfig.addNewOutboundProperties();
      socketOutboundPropertiesType.setEnableNagleAlgorithm(
        TransportUIFactory.getBooleanValue(map, ENABLE_NAGLE_ALGORITHM));
      socketOutboundPropertiesType
        .setTimeout(TransportUIFactory.getIntValue(map, TIME_OUT));
      socketOutboundPropertiesType.setMessageDelimiter(
          TransportUIFactory.getStringValue(map, MESSAGE_DELIMITER));
    }

    String reqEnc = TransportUIFactory.getStringValue(map, REQUEST_ENCODING);
    if (reqEnc != null && reqEnc.trim().length() != 0) {
      socketEndpointConfig.setRequestEncoding(reqEnc);
    }
    String resEnc = TransportUIFactory.getStringValue(map, RESPONSE_ENCODING);
    if (resEnc != null && resEnc.trim().length() != 0) {
      socketEndpointConfig.setResponseEncoding(resEnc);
    }

    String dispatchPolicy =
      TransportUIFactory.getStringValue(map, DISPATCH_POLICY);
    socketEndpointConfig.setDispatchPolicy(dispatchPolicy);

    return socketEndpointConfig;
  }

  public Reader getHelpPage() {
    String helpFile = "help/en/contexts_socketTransport.html";
    ClassLoader clLoader = Thread.currentThread().getContextClassLoader();
    InputStream is = clLoader.getResourceAsStream(helpFile);
    InputStreamReader helpReader = null;
    if(is!=null)
      helpReader = new InputStreamReader(is);
    else
      SocketTransportUtil.logger
        .warning(SocketTransportUtil.formatText(uiContext.getLocale(), "800138"));
    return helpReader;
  }

}
