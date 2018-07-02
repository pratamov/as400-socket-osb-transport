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

import com.bea.wli.sb.sources.Source;
import com.bea.wli.sb.sources.StringSource;
import com.bea.wli.sb.sources.TransformException;
import com.bea.wli.sb.sources.TransformOptions;
import com.bea.wli.sb.transports.InboundTransportMessageContext;
import com.bea.wli.sb.transports.TransportException;
import com.bea.wli.sb.transports.TransportEndPoint;
import com.bea.wli.sb.transports.RequestMetaData;
import com.bea.wli.sb.transports.ResponseMetaData;
import com.bea.wli.sb.transports.TransportOptions;
import org.apache.xmlbeans.XmlObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.ResourceBundle;
import java.util.logging.Level;

/**
 * This class represents the message context at transportlevel for an incoming
 * message.
 */
public class SocketInboundMessageContext
  implements InboundTransportMessageContext {
  private SocketTransportEndPoint endPoint;
  private Socket clientSocket;
  private String msgId;
  private String msg;
  private SocketRequestMetaData requestMetadata;
  private SocketResponseMetaData responseMetaData;
  private Source responsePayload;
  private static int count = 0;

  /**
   * Constructor of SocketInboundMessageContext. Initializes the field
   * variables, reads the message from the input stream and it is set.
   *
   * @param endPoint
   * @param clientSocket
   * @param msgId
   * @param msg
   */
  public SocketInboundMessageContext(SocketTransportEndPoint endPoint,
                                     Socket clientSocket, String msgId,
                                     String msg) throws TransportException {
    this.endPoint = endPoint;
    this.clientSocket = clientSocket;
    this.msgId = msgId;
    this.msg = msg;

    String requestEncoding = endPoint.getRequestEncoding();
    if(requestEncoding == null) {
      requestEncoding = "utf-8";
    }
    requestMetadata = new SocketRequestMetaData(requestEncoding);
    ((SocketRequestHeaders)requestMetadata.getHeaders()).setMessageCount(++count);
    requestMetadata.setClientHost(clientSocket.getInetAddress().getHostAddress());
    requestMetadata.setClientPort(clientSocket.getPort());
  }

  /**
   * @return the service endpoint object which has received this incoming
   *         message
   */
  public TransportEndPoint getEndPoint() throws TransportException {
    return endPoint;
  }

  /**
   * @return the meta-data for the request part of the message, e.g. headers,
   *         etc. Returns null if there is no request meta-data
   */
  public RequestMetaData getRequestMetaData() throws TransportException {
    return requestMetadata;
  }

  /**
   * @return returns a source (e.g. input stream or a DOM object) for reading
   *         data in the body of the request of an inbound message or null if
   *         there is no body of the request. Note that the entire body of the
   *         payload is retrieved.
   */
  public Source getRequestPayload() throws TransportException {
    if (msg == null) {
      return null;
    }

    return new StringSource(msg);
  }

  /**
   * @return empty (new) meta-data for the response part of the message, e.g.
   *         headers, etc. Used for initializing the inbound response
   */
  public ResponseMetaData createResponseMetaData() throws TransportException {
    SocketResponseMetaData responseMetaData =
      new SocketResponseMetaData(endPoint.getResponseEncoding());
    return responseMetaData;
  }


  /**
   * @return meta-data for the response part of the message, e.g. headers, etc
   *         initialized according to transport provider-specific XMLBean. Used
   *         for initializing the inbound response
   */
  public ResponseMetaData createResponseMetaData(XmlObject rmdXML)
    throws TransportException {
    SocketResponseMetaDataXML xmlObject =
      SocketResponseMetaData.getSocketResponseMetaData(rmdXML);
    if (xmlObject != null) {
      return new SocketResponseMetaData(xmlObject);
    }
    return null;
  }

  /**
   * sets the response metadata of the message.
   *
   * @param rmd
   * @throws TransportException when the passed metadata is not an instance of
   *                            SocketResponseMetaData.
   */
  public void setResponseMetaData(ResponseMetaData rmd)
    throws TransportException {
    if (!(rmd instanceof SocketResponseMetaData)) {
      throw new TransportException(SocketTransportUtil.formatText("800108", SocketResponseMetaData.class.getName()));
    }
    responseMetaData = (SocketResponseMetaData) rmd;
  }

  public void setResponsePayload(Source src) throws TransportException {
    responsePayload = src;
  }

  /**
   * Sends the response back to the client.
   */
  public void close(TransportOptions transportOptions) {

    OutputStream outputStream = null;
    try {
      /** If message pattern is one way, return immediately.*/
      if (endPoint.getMessagePattern()
        .equals(TransportEndPoint.MessagePatternEnum.ONE_WAY)) {
        return;
      }
      /** Write the response back to the client. */
      String reqEnc =
        endPoint.getSocketEndpointConfiguration().getRequestEncoding();
      if(reqEnc == null) {
          reqEnc = "utf-8";
      }
      outputStream = clientSocket.getOutputStream();
      if (responsePayload != null) {
        TransformOptions options = new TransformOptions();

        options.setCharacterEncoding(reqEnc);
        responsePayload.writeTo(outputStream, options);
      } else {
        SocketTransportUtil.logger.info("800102");
      }
      /** write message delimiter at the end. */
      outputStream.write(SocketTransportUtil.decodeMessageDelimiter(
          endPoint.getSocketEndpointConfiguration().getInboundProperties()
              .getMessageDelimiter()).getBytes(reqEnc));
      outputStream.flush();
    } catch (IOException e) {
      SocketTransportUtil.logger.log(Level.SEVERE, "800130", e);
    } catch (TransformException e) {
      SocketTransportUtil.logger.log(Level.SEVERE, "800130", e);
    } catch (TransportException e) {
      SocketTransportUtil.logger.log(Level.SEVERE, "800130", e);
    } finally {
      try {
        // closing the socket stream.
        clientSocket.close();
      } catch (IOException ignore) {
      }
    }
  }

  public URI getURI() {
    return endPoint.getURI()[0];
  }

  public String getMessageId() {
    return msgId;
  }

}
