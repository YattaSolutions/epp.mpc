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
package org.eclipse.epp.internal.mpc.core.payment.discovery.model;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carsten Reckord
 */
public class PaymentDiscoveryResult {
	private PaymentDiscoveryPrice price;

	private final List<PaymentDiscoveryModule> modules = new ArrayList<PaymentDiscoveryModule>();

	public List<PaymentDiscoveryModule> getModules() {
		return modules;
	}

	public PaymentDiscoveryPrice getPrice() {
		return price;
	}

	public void setPrice(PaymentDiscoveryPrice price) {
		this.price = price;
	}
}
