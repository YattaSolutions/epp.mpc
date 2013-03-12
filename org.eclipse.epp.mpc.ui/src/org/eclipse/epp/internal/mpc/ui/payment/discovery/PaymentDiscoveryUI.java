/*******************************************************************************
 * Copyright (c) 2013 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Yatta Solutions - initial API and implementation
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.ui.payment.discovery;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.ui.statushandlers.StatusManager;

public class PaymentDiscoveryUI {

	private PaymentDiscoveryUI() {
	}

	public static IStatus install(List<CatalogItem> descriptors, IRunnableContext context) {
		try {
			PaymentDiscoveryInstallOperation operation = new PaymentDiscoveryInstallOperation(descriptors);
			context.run(true, true, operation);
			return operation.getStatus();
		} catch (InvocationTargetException e) {
			IStatus status = MarketplaceClientUi.computeWellknownProblemStatus(e);
			if (status == null) {
				if (e.getCause() instanceof CoreException) {
					CoreException core = (CoreException) e.getCause();
					status = core.getStatus();
				} else {
					status = MarketplaceClientUi.computeStatus(e, Messages.PaymentDiscoveryUI_Error_installing_payment_module);
				}
			}
			StatusManager.getManager().handle(status, StatusManager.SHOW | StatusManager.BLOCK | StatusManager.LOG);
			return status;
		} catch (InterruptedException e) {
			// canceled
			return Status.CANCEL_STATUS;
		}
	}
}
