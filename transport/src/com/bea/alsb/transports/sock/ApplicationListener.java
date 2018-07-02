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

import com.bea.wli.sb.transports.TransportException;
import com.bea.wli.sb.transports.TransportManager;
import com.bea.wli.sb.transports.TransportManagerHelper;
import weblogic.application.ApplicationLifecycleEvent;
import weblogic.application.ApplicationLifecycleListener;
import weblogic.application.ApplicationException;

import java.util.logging.Level;

/**
 * This class provides callbacks for deployment events.
 */
public class ApplicationListener extends ApplicationLifecycleListener {

    /**
     * After an application is initialized, this method is invoked by the Deploy
     * framework. Socket transport is registered with TransportManager.
     *
     * @param evt
     */
    public void preStart(ApplicationLifecycleEvent evt) throws ApplicationException {
        try {
            TransportManager man = TransportManagerHelper.getTransportManager();
            SocketTransportProvider instance = SocketTransportProvider.getInstance();
            man.registerProvider(instance, null);

            SocketTransportUtil.logger.info("800100");
        } catch (TransportException e) {
            SocketTransportUtil.logger.log(Level.SEVERE, "800128", e);
            throw new ApplicationException(e);
        }
    }

    /**
     * This method is invoked by Deploy framework when an application is commenced
     * to shutdown.
     *
     * @param evt
     */
    public void preStop(ApplicationLifecycleEvent evt) {
        /** TransportManager does not have unregisterProvider method.
         * Whenever a transport needs to be refreshed,
         * server should get offline and the transport can be deployed.
         * This restrictioin is made by the Transport SDK.
         */
    }
}
