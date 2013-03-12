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
package org.eclipse.epp.mpc.core.payment;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * A PaymentService manages PaymentModules that offer payment operations for catalog items.
 * 
 * @author Carsten Reckord
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface PaymentService {

	/**
	 * The base url of the marketplace that payment is provided for.
	 */
	String getCatalogUrl();

	/**
	 * Get a registered payment module by id. Only installed modules will be returned, no discovery is performed.
	 * 
	 * @param moduleId
	 *            the id of the module
	 * @return a PaymentModule matching the given id
	 */
	PaymentModule getPaymentModule(String moduleId);

	/**
	 * Get a payment module for the given node on the marketplace identified by {@link #getCatalogUrl()}. A payment
	 * module for the marketplace is returned, for which {@link PaymentModule#handles(String, IProgressMonitor)
	 * handles(nodeId)} returns <em>true</em>. Might query remote servers to determine the correct module.
	 * 
	 * @param nodeId
	 *            a solution's id on the marketplace
	 * @param monitor
	 * @return a payment module that can handle the given node
	 * @throws CoreException
	 */
	PaymentModule getPaymentModule(String nodeId, IProgressMonitor monitor) throws CoreException;

	void addPaymentServiceListener(PaymentServiceListener listener);

	void removePaymentServiceListener(PaymentServiceListener listener);
}
