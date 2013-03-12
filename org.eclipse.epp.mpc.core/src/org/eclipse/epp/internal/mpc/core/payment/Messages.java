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
package org.eclipse.epp.internal.mpc.core.payment;

import org.eclipse.osgi.util.NLS;

/**
 * @author Carsten Reckord
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.epp.internal.mpc.core.payment.messages"; //$NON-NLS-1$


	public static String PaymentDiscoveryService_Discovery;


	public static String PaymentDiscoveryService_discoveryErrors;


	public static String PaymentDiscoveryService_profileNotFound;


	public static String PaymentDiscoveryStrategy_Category;


	public static String PaymentDiscoveryHandler_multiplePaymentModules;


	public static String PaymentDiscoveryHandler_unexpectedError;


	public static String PaymentServiceImpl_Error_creating_payment_module;


	public static String PaymentServiceImpl_Error_finding_payment_module;


	public static String PaymentServiceImpl_Error_IU_Filter;


	public static String PaymentServiceImpl_Payment_module_security_issue;


	public static String PaymentServiceImpl_securityIssues;


	public static String PaymentServiceImpl_verifyNoOwningBundle;

	public static String SecurityHelper_bundle_not_found;


	public static String SecurityHelper_bundle_not_signed;


	public static String SecurityHelper_bundle_signer_not_trusted;


	public static String SecurityHelper_bundleEntry_not_found;


	public static String SecurityHelper_error_locking_file;


	public static String SecurityHelper_no_trusted_signature;


	public static String SecurityHelper_service_not_found;


	public static String SecurityHelper_service_reference_not_found;


	public static String SecurityHelper_trust_store_tampered_content;


	public static String SecurityHelper_trust_store_tampered_size;


	public static String SecurityHelper_trusted_keystore_file_missing;


	public static String SecurityHelper_trusted_keystore_missing;


	public static String SecurityHelper_Unable_to_read_trust_store;


	public static String SecurityHelper_unable_to_verify;


	public static String SecurityHelper_unable_to_verify_bundle;


	public static String SecurityHelper_unexpected_content;


	public static String SecurityHelper_unsigned_content;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
