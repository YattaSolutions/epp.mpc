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

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.epp.internal.mpc.ui.payment.discovery.messages"; //$NON-NLS-1$

	public static String PaymentDiscoveryInstallOperation_Applying_changes;

	public static String PaymentDiscoveryInstallOperation_Error_missing_signature;

	public static String PaymentDiscoveryInstallOperation_Installing_connectors;

	public static String PaymentDiscoveryUI_Error_installing_payment_module;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
