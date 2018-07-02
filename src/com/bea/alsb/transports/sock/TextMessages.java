package com.bea.alsb.transports.sock;

import weblogic.i18n.Localizer;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.HashMap;

/**
 * It contains messages used by {@link SocketTransportUIBinding}. It has an API
 * to get localized messages.
 *
 * These are mapped in /l10n/SocketTransportTextMessages.xml
 *
 * Messages can be added here and map it in the above mapping xml.
 * More information can be found at http://edocs.bea.com/wls/docs91/i18n/MssgCats.html
 *  
 */
public class TextMessages {
  public static final String BACKLOG = "BACKLOG";
  public static final String BACKLOG_INFO = "BACKLOG_INFO";
  public static final String TIME_OUT = "TIME_OUT";
  public static final String TIME_OUT_INFO = "TIME_OUT_INFO";
  public static final String REQUEST_RESPONSE = "REQUEST_RESPONSE";
  public static final String REQUEST_RESPONSE_INFO = "REQUEST_RESPONSE_INFO";
  public static final String REQUEST_ENCODING = "REQUEST_ENCODING";
  public static final String REQUEST_ENCODING_INFO = "REQUEST_ENCODING_INFO";
  public static final String RESPONSE_ENCODING = "RESPONSE_ENCODING";
  public static final String RESPONSE_ENCODING_INFO = "RESPONSE_ENCODING_INFO";
  public static final String DISPATCH_POLICY = "DISPATCH_POLICY";
  public static final String DISPATCH_POLICY_INFO = "DISPATCH_POLICY_INFO";
  public static final String ENABLE_NAGLE_ALGORITHM = "ENABLE_NAGLE_ALGORITHM";
  public static final String ENABLE_NAGLE_ALGORITHM_INFO =
    "ENABLE_NAGLE_ALGORITHM_INFO";
  public static final String MESSAGE_DELIMITER = "MESSAGE_DELIMITER";
  public static final String MESSAGE_DELIMITER_INFO = "MESSAGE_DELIMITER_INFO";

  public static final String INBOUND_URI_FORMAT = "INBOUND_URI_FORMAT";
  public static final String OUTBOUND_URI_FORMAT = "OUTBOUND_URI_FORMAT";

  private static final String propsClazz =
    "com.bea.alsb.transports.socket.SocketTransportTextMessages";

  public static String getMessage(String id, Locale locale) {
    ResourceBundle bundle = ResourceBundle.getBundle(propsClazz, locale);
    return bundle.getString(id);
  }

  public static String getMessage(String id) {
    return getMessage(id, Locale.getDefault());
  }
}
