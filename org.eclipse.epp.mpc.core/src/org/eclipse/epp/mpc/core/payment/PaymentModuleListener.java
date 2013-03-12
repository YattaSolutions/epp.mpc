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

public interface PaymentModuleListener {
	public void paymentItemChanged(PaymentItem item);

	public void paymentTransactionCompleted(PaymentTransaction transaction);

	public void paymentTransactionCanceled(PaymentTransaction transaction);
}
