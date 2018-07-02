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

import com.bea.wli.sb.transports.TransportEndPoint;
import com.bea.wli.sb.transports.TransportException;
import com.bea.wli.sb.transports.TransportManagerHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.logging.Level;


/**
 * This is a receiver thread of the socket transport end point which is
 * responsible for opening a server socket at the configured port and handles
 * start/stop/suspend/resume operations. It creates SocketInboundMessageContext
 * and sends it to the transport SDK.
 */
public class SocketTransportReceiver implements Runnable {
  private SocketTransportEndPoint endPoint;
  private ServerSocket serverSocket;
  private String dispatchPolicy;
  private int timeout;
  private boolean enableNagleAlgorithm;
  private volatile boolean isStopping;
  private int backLog;
  private String messageDelimiter;

  /**
   * Initializes the ServerSocket with the endpoint configuration.
   *
   * @throws TransportException
   */
  private void init() throws TransportException {
    SocketEndpointConfiguration epc =
      endPoint.getSocketEndpointConfiguration();
    /** Store endpoint configuration variables locally to avoid reading these
     * from xbean again and again.*/
    dispatchPolicy = epc.getDispatchPolicy();
    SocketInboundPropertiesType inboundProperties =
      epc.getInboundProperties();
    timeout = inboundProperties.getTimeout();
    enableNagleAlgorithm = inboundProperties.getEnableNagleAlgorithm();
    backLog = inboundProperties.getBacklog();
    messageDelimiter = inboundProperties.getMessageDelimiter();
  }

  public SocketTransportReceiver(SocketTransportEndPoint endPoint)
    throws TransportException {
    this.endPoint = endPoint;
    init();
  }

  public void run() {
    Socket clientSocket = null;
    URI uri = endPoint.getURI()[0];
    String address = uri.toString();
    SocketTransportUtil.logger.log(Level.INFO, "800125", address);

    int port = Integer.parseInt(address);
    try {
      serverSocket = new ServerSocket();
      serverSocket.bind(new InetSocketAddress(port), backLog);
    } catch (IOException e) {
      SocketTransportUtil.logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
      return;
    }

    while (true) {
      try {
        SocketTransportUtil.logger.log(Level.INFO, "800118", serverSocket.toString());
        clientSocket = serverSocket.accept();
      } catch (IOException e) {
        if (!isStopping) {
          SocketTransportUtil.logger.log(Level.SEVERE, "800119", e);
        } else {
          SocketTransportUtil.logger.log(Level.INFO, "800135", serverSocket.toString());
        }
        return;
      }
      SocketTransportUtil.logger.log(Level.INFO, "800127", clientSocket.toString());

      /** create a worker and schedule it */
      WorkerThread workerThread =
        new WorkerThread(clientSocket, endPoint, timeout, enableNagleAlgorithm, messageDelimiter);

      try {
        TransportManagerHelper.schedule(workerThread, dispatchPolicy);
      } catch (TransportException e) {
        SocketTransportUtil.logger.log(Level.SEVERE, "800133", e);
      }
    }
  }

  public void stopAcceptor() {
    try {
      isStopping = true;
      serverSocket.close();
    } catch (IOException e) {
      SocketTransportUtil.logger.severe(e.getLocalizedMessage());
    }
  }

  /**
   * This class represents a single thread of execution of send the data to the
   * transport.
   */
  static class WorkerThread implements Runnable {
    private Socket clientSocket;
    private SocketTransportEndPoint endPoint;
    private int timeout;
    private boolean enableNagleAlgorithm;
    private String messageDelimiter;

    public WorkerThread(Socket clientSocket, SocketTransportEndPoint endPoint,
                        int timeout, boolean enableNagleAlgorithm, String messageDelimiter) {
      this.clientSocket = clientSocket;
      this.endPoint = endPoint;
      this.timeout = timeout;
      this.enableNagleAlgorithm = enableNagleAlgorithm;
      this.messageDelimiter = messageDelimiter;
    }

    public void run() {
      try {
        /** set the socket properties. */
        clientSocket.setSoTimeout(timeout);
        clientSocket.setTcpNoDelay(!enableNagleAlgorithm);

        String msgId = new Random().nextInt() + "." + System.nanoTime();
        InputStream inputStream = clientSocket.getInputStream();

        /** read the incoming message */
        String encoding = endPoint.getRequestEncoding();
        if(encoding == null) {
          encoding = "utf-8";
        }
        InputStreamReader inputStreamReader =
          new InputStreamReader(inputStream, encoding);

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
          if ((i = sb.indexOf(SocketTransportUtil.decodeMessageDelimiter(messageDelimiter))) != -1) {
            break;
          }
        }
        String msg;
        if (i != -1) {
          msg = sb.substring(0, i);
        } else {
          throw new MessageFormatException(SocketTransportUtil.formatText("800107"));
        }

        /** if its one way close the connection. */
        if (endPoint.getMessagePattern()
          .equals(TransportEndPoint.MessagePatternEnum.ONE_WAY)) {
          try {
            // closing the input stream only because we didn't open any output
            // stream.
            clientSocket.getInputStream().close();
          } catch (IOException e) {
            SocketTransportUtil.logger.severe(e.getLocalizedMessage());
          }
        }
        final SocketInboundMessageContext inboundMessageContext =
          new SocketInboundMessageContext(endPoint, clientSocket, msgId, msg);

        /** send inbound cotext to SDK which sends it to the pipeline */
        TransportManagerHelper.getTransportManager()
          .receiveMessage(inboundMessageContext, null);

      } catch (TransportException e) {
        SocketTransportUtil.logger.log(Level.SEVERE, getErrorMsg(), e);
      } catch (IOException e) {
        SocketTransportUtil.logger.log(Level.SEVERE, getErrorMsg(), e);
      } catch (MessageFormatException e) {
        SocketTransportUtil.logger.log(Level.SEVERE, getErrorMsg(), e);
      }
    }

    private String getErrorMsg() {
      return SocketTransportUtil.formatText("800134");
    }
  }

}
