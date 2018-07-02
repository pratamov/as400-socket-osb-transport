package com.bea.alsb.transports.sock;

import com.bea.wli.sb.sources.Source;
import com.bea.wli.sb.sources.StreamSource;
import com.bea.wli.sb.sources.TransformException;
import com.bea.wli.sb.sources.TransformOptions;
import com.bea.wli.sb.transports.TransportSender;
import com.bea.wli.sb.transports.EndPointConfiguration;
import com.bea.wli.sb.transports.TransportOptions;
import com.bea.wli.sb.transports.OutboundTransportMessageContext;
import com.bea.wli.sb.transports.ServiceTransportSender;
import com.bea.wli.sb.transports.TransportException;
import com.bea.wli.sb.transports.NoServiceTransportSender;
import com.bea.wli.sb.transports.ResponseMetaData;
import com.bea.wli.sb.transports.TransportSendListener;
import com.bea.wli.sb.transports.TransportManagerHelper;
import com.bea.wli.sb.transports.TransportManager;

import java.util.Random;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.util.logging.Level;

/**
 * This class provides transport level message context abstraction for an
 * outgoing message.
 */
public class SocketOutboundMessageContext
  implements OutboundTransportMessageContext {
  private TransportSender sender;
  private TransportOptions options;
  private String msgId;
  private EndPointConfiguration config;
  private InputStream responseIS;
  private SocketResponseMetaData responseMetadata;

  /**
   * Initializes the field variables. sender should be either {@link
   * com.bea.wli.sb.transports.ServiceTransportSender} or {@link
   * com.bea.wli.sb.transports.NoServiceTransportSender}.
   *
   * @param sender
   * @param options
   * @throws com.bea.wli.sb.transports.TransportException if the given sender type is neither
   *         ServiceTransportSender nor NoServiceTransportSender.
   */
  public SocketOutboundMessageContext(TransportSender sender,
                                      TransportOptions options)
    throws TransportException {
    this.msgId = new Random().nextInt() + "." + System.nanoTime();
    this.sender = sender;
    this.options = options;
    if (sender instanceof ServiceTransportSender) {
      this.config =
        ((ServiceTransportSender) sender).getEndPoint().getConfiguration();
    } else if (sender instanceof NoServiceTransportSender) {
      this.config =
        ((NoServiceTransportSender) sender).getEndPointConfiguration();
    } else {
      throw new TransportException(SocketTransportUtil.formatText("800129"));
    }
  }


  /**
   * @return the meta-data for the response part of the message, e.g. headers,
   *         etc. Returns null if there is no response meta-data
   */
  public ResponseMetaData getResponseMetaData() throws TransportException {
    return responseMetadata;
  }

  /**
   * Returns the Source of the response stream.
   *
   * @return
   * @throws TransportException
   */
  public Source getResponsePayload() throws TransportException {
    return responseIS == null ? null : new StreamSource(responseIS);
  }

  /**
     * @return the base uri for to which the message was sent for an outbound
     * message or from which the message was sent on an inbound message
     */
  public URI getURI() {
    return options.getURI();
  }

  /**
   * @return returns transport provider-specific message identifier. Ideally it
   *         should uniquely identify the message among other messages going
   *         through the ALSB runtime, However, ALSB does not depend on the
   *         message Id being unique. The message Id will be added to the
   *         message context and thus visible in the pipeline.
   */
  public String getMessageId() {
    return msgId;
  }

  /**
   * Sends the message to the external service, schedules a Runnable which sets
   * the response metadata and reads the response from the external service.
   *
   * @param listener
   */
  public void send(final TransportSendListener listener)
    throws TransportException {
    String address = options.getURI().toString();
    try {
      throw new TransportException("test this"); 
      String host = null;
      int port = 0;
      try {
        URI uri = new URI(address);
        host = uri.getHost();
        port = uri.getPort();
      } catch (URISyntaxException e) {
        new TransportException(e.getMessage(), e);
      }
      SocketTransportUtil.logger.log(Level.INFO, "800103", new Object[] {host, port});
      final Socket clientSocket = new Socket(host, port);

      SocketEndpointConfiguration socketEndpointConfiguration =
        SocketTransportUtil.getConfig(config);
      SocketOutboundPropertiesType outboundProperties =
        socketEndpointConfiguration.getOutboundProperties();
      clientSocket.setTcpNoDelay(!outboundProperties.getEnableNagleAlgorithm());
      clientSocket.setSoTimeout(outboundProperties.getTimeout());


        String reqEnc = socketEndpointConfiguration.getRequestEncoding();
        if (reqEnc == null) {
          reqEnc = "utf-8";
        }

      // Send the message to the external service.
      OutputStream outputStream = clientSocket.getOutputStream();
      TransformOptions transformOptions = new TransformOptions();
      transformOptions.setCharacterEncoding(reqEnc);
      sender.getPayload().writeTo(outputStream, transformOptions);
      outputStream.write(SocketTransportUtil.decodeMessageDelimiter(
          outboundProperties.getMessageDelimiter()).getBytes(reqEnc));
      outputStream.flush();
      SocketTransportUtil.logger.info("800104");

      PipelineAcknowledgementTask task =
        new PipelineAcknowledgementTask(listener, clientSocket,
          socketEndpointConfiguration);
      TransportManagerHelper
        .schedule(task, socketEndpointConfiguration.getDispatchPolicy());
    } catch (UnknownHostException e) {
      SocketTransportUtil.logger.severe(e.getLocalizedMessage());
      throw new TransportException(e.getMessage(), e);
    } catch (IOException e) {
      SocketTransportUtil.logger.severe(e.getLocalizedMessage());
      throw new TransportException(e.getMessage(), e);
    } catch (TransformException e) {
      SocketTransportUtil.logger.severe(e.getLocalizedMessage());
      throw new TransportException(e.getMessage(), e);
    } catch (TransportException e) {
      SocketTransportUtil.logger.severe(e.getLocalizedMessage());
      throw e;
    }
  }

  /**
   * This task does the acknowledgement work of the outbound to the pipeline.
   */
  class PipelineAcknowledgementTask implements Runnable {
    private TransportSendListener listener;
    private Socket clientSocket;
    private SocketEndpointConfiguration epc;

    public PipelineAcknowledgementTask(TransportSendListener listener,
                                       Socket clientSocket,
                                       SocketEndpointConfiguration epc) {
      this.listener = listener;
      this.clientSocket = clientSocket;
      this.epc = epc;
    }

    /**
     * It reads the response sent from the external service, sets the headers
     * and invokes the pipeline.
     */
    public void run() {
      try {
        // if the end-point is one-way, don't read the response.
        if (!epc.getRequestResponse()) {
          SocketTransportUtil.logger.info("800105");
          listener.onReceiveResponse(SocketOutboundMessageContext.this);
          return;
        }

        String resEnc = getResponseEncoding();
        responseMetadata = new SocketResponseMetaData(resEnc);
        InetAddress inetAddress = clientSocket.getInetAddress();
        responseMetadata.setEndPointHost(inetAddress.getHostName());
        responseMetadata.setEndPointIP(inetAddress.getHostAddress());

        // Reading the response from the external service.
        InputStream inputStream = clientSocket.getInputStream();
        InputStreamReader inputStreamReader =
          new InputStreamReader(inputStream, resEnc);
        int i = -1;
        StringBuilder sb = new StringBuilder();
        char[] buff = new char[512];
        while (true) {
          i = inputStreamReader.read(buff);
          if (i == -1) {
            break;
          }
          sb.append(buff, 0, i);
          /** if it ends with message delimiter sequence, come out.
           * We can read the content after the specified delimiter because 
           * we are expecting only one message per connection,
           * i.e we are closing the connection after processing a single message.
           */
          SocketEndpointConfiguration socketEndpointConfiguration =
            SocketTransportUtil.getConfig(config);
          SocketOutboundPropertiesType outboundProperties =
            socketEndpointConfiguration.getOutboundProperties();
          if ((i = sb
              .indexOf(SocketTransportUtil
                  .decodeMessageDelimiter(outboundProperties
                      .getMessageDelimiter()))) != -1) {
            break;
          }
        }
        if (i != -1) {
          // strip \r\n\r\n from the message.
          String msg = sb.substring(0, i);
          responseIS = new ByteArrayInputStream(msg.getBytes(resEnc));
          listener.onReceiveResponse(SocketOutboundMessageContext.this);
        } else {
          // Message format is wrong, it should end with \r\n\r\n
          listener.onError(SocketOutboundMessageContext.this,
            TransportManager.TRANSPORT_ERROR_GENERIC,
            SocketTransportUtil.formatText("mesagebody800107"));
        }
      } catch (IOException e) {
        SocketTransportUtil.logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        listener.onError(SocketOutboundMessageContext.this,
          TransportManager.TRANSPORT_ERROR_GENERIC, e.getLocalizedMessage());
      } catch (TransportException trex) {
        SocketTransportUtil.logger.log(Level.SEVERE, trex.getLocalizedMessage(), trex);
        listener.onError(SocketOutboundMessageContext.this,
          TransportManager.TRANSPORT_ERROR_GENERIC, trex.getLocalizedMessage());
      } finally {
        try {
          clientSocket.close();
        } catch (IOException e) {
          SocketTransportUtil.logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }
      }
    }

    private String getResponseEncoding() {
      String resEnc = epc.getResponseEncoding();
      if (resEnc == null) {
        resEnc = "utf-8";
      }
      return resEnc;
    }

  }
}
