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

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

public interface PaymentModule {

	/**
	 * The module's unique id
	 */
	String getId();

	/**
	 * Registers the {@link PaymentService} with the module. Called once during module initialization.
	 */
	void setPaymentService(PaymentService paymentService);

	PaymentService getPaymentService();

	/**
	 * Check if this module is capable of providing payment services for the given marketplace entry. Typically, this
	 * checks whether the payment service knows the node.
	 * 
	 * @param nodeId
	 * @param monitor
	 * @return true if this module can handle requests for the given node
	 */
	boolean handles(String nodeId, IProgressMonitor monitor);

	/**
	 * Get basic pricing and status information for the given marketplace entry.
	 * 
	 * @param nodeId
	 * @param monitor
	 * @return payment information for the given node
	 * @throws CoreException
	 */
	PaymentItem query(String nodeId, IProgressMonitor monitor) throws CoreException;

	boolean canPurchase(PaymentItem item);

	/**
	 * Mark the given item to be purchased on checkout. Typically this will put the item into some sort of shopping cart
	 * in a backing remote payment service. This might open the shopping cart in the UI to specify further details
	 * (choice of license type, subscriptions etc). The item is expected to be previously retrieved via
	 * {@link #query(String, IProgressMonitor)} from the same payment module.
	 * 
	 * @param item
	 *            the item to buy
	 * @param monitor
	 * @param context
	 *            the IAdaptable (or <code>null</code>) provided by the caller in order to supply UI information for
	 *            prompting the user if necessary. When this parameter is not <code>null</code>, it should minimally
	 *            contain an adapter for the org.eclipse.swt.widgets.Shell.class. If called from within a wizard, it
	 *            should also contain an adapter for the org.eclipse.jface.wizard.IWizard.class.
	 * @return a status indicating the operation's conclusion (OK, CANCEL, ERROR)
	 */
	IStatus purchase(PaymentItem item, IProgressMonitor monitor, IAdaptable context);

	/**
	 * Mark the given item to not be purchased on checkout. Typically this will remove the item from a shopping cart in
	 * a backing remote payment service, where it has previously been added with
	 * {@link #purchase(PaymentItem, IProgressMonitor, IAdaptable)}. Does nothing if the item hasn't been added before.
	 * The item is expected to be previously retrieved via {@link #query(String, IProgressMonitor)} from the same
	 * payment module.
	 * 
	 * @param item
	 *            the item to remove
	 * @param monitor
	 * @param context
	 *            the IAdaptable (or <code>null</code>) provided by the caller in order to supply UI information for
	 *            prompting the user if necessary. When this parameter is not <code>null</code>, it should minimally
	 *            contain an adapter for the org.eclipse.swt.widgets.Shell.class. If called from within a wizard, it
	 *            should also contain an adapter for the org.eclipse.jface.wizard.IWizard.class.
	 * @return a status indicating the operation's conclusion (OK, CANCEL, ERROR)
	 */
	IStatus remove(PaymentItem item, IProgressMonitor monitor, IAdaptable context);

	/**
	 * Proceed with the checkout of the current {@link #getSession() session}. Typically this will submit a shopping
	 * cart previously filled with {@link #purchase(PaymentItem, IProgressMonitor, IAdaptable)} for final payment
	 * processing. This might open the shopping cart in the UI.
	 * 
	 * @param item
	 *            the item to remove
	 * @param monitor
	 * @param context
	 *            the IAdaptable (or <code>null</code>) provided by the caller in order to supply UI information for
	 *            prompting the user if necessary. When this parameter is not <code>null</code>, it should minimally
	 *            contain an adapter for the org.eclipse.swt.widgets.Shell.class. If called from within a wizard, it
	 *            should also contain an adapter for the org.eclipse.jface.wizard.IWizard.class.
	 * @return a status indicating the operation's conclusion (OK, CANCEL, ERROR)
	 */
	IStatus checkout(IProgressMonitor monitor, IAdaptable context);

	/**
	 * Cancel the current {@link session} and clear it of all previously
	 * {@link #purchase(PaymentItem, IProgressMonitor, IAdaptable) added} items.
	 * 
	 * @param item
	 *            the item to remove
	 * @param monitor
	 * @param context
	 *            the IAdaptable (or <code>null</code>) provided by the caller in order to supply UI information for
	 *            prompting the user if necessary. When this parameter is not <code>null</code>, it should minimally
	 *            contain an adapter for the org.eclipse.swt.widgets.Shell.class. If called from within a wizard, it
	 *            should also contain an adapter for the org.eclipse.jface.wizard.IWizard.class.
	 * @return a status indicating the operation's conclusion (OK, CANCEL, ERROR)
	 */
	IStatus cancel(IProgressMonitor monitor, IAdaptable context);

	/**
	 * Get some basic information about the current purchasing session. This includes the list of items currently marked
	 * to be purchased.
	 */
	PaymentTransaction getSession();

	/**
	 * List past purchases
	 */
	List<PaymentTransaction> getPurchases(IProgressMonitor monitor) throws CoreException;

	void addPaymentModuleListener(PaymentModuleListener listener);

	void removePaymentModuleListener(PaymentModuleListener listener);
}
