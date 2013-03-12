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
package org.eclipse.epp.internal.mpc.core.payment.discovery.xml;

import java.math.BigDecimal;

import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryPrice;
import org.eclipse.epp.internal.mpc.core.service.xml.UnmarshalContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * @author Carsten Reckord
 */
public class PaymentDiscoveryPriceContentHandler extends UnmarshalContentHandler {

	private static final String NS_URI = ""; //$NON-NLS-1$

	private PaymentDiscoveryPrice model;

	@Override
	public void startElement(String uri, String localName, Attributes attributes) {
		if (localName.equals("price")) { //$NON-NLS-1$
			model = new PaymentDiscoveryPrice();
			//support both attribute and nested element form
			model.setAmount(toAmount(attributes.getValue(NS_URI, "amount"))); //$NON-NLS-1$
			model.setCurrency(attributes.getValue(NS_URI, "currency")); //$NON-NLS-1$
			model.setPriceType(attributes.getValue(NS_URI, "priceType")); //$NON-NLS-1$
			model.setPurchaseType(attributes.getValue(NS_URI, "purchaseType")); //$NON-NLS-1$
		} else if (localName.equals("amount")) { //$NON-NLS-1$
			capturingContent = true;
		} else if (localName.equals("currency")) { //$NON-NLS-1$
			capturingContent = true;
		} else if (localName.equals("priceType")) { //$NON-NLS-1$
			capturingContent = true;
		} else if (localName.equals("purchaseType")) { //$NON-NLS-1$
			capturingContent = true;
		}
	}

	@Override
	public boolean endElement(String uri, String localName) throws SAXException {
		if (localName.equals("price")) { //$NON-NLS-1$
			getUnmarshaller().setModel(model);
			model = null;
			endNested(uri, localName);
		} else if (localName.equals("amount")) { //$NON-NLS-1$
			BigDecimal amount = toAmount(content.toString());
			model.setAmount(amount);
			capturingContent = false;
			content = null;
		} else if (localName.equals("currency")) { //$NON-NLS-1$
			model.setCurrency(content.toString());
			capturingContent = false;
			content = null;
		} else if (localName.equals("priceType")) { //$NON-NLS-1$
			model.setPriceType(content.toString());
			capturingContent = false;
			content = null;
		} else if (localName.equals("purchaseType")) { //$NON-NLS-1$
			model.setPurchaseType(content.toString());
			capturingContent = false;
			content = null;
		}
		return false;
	}

	private BigDecimal toAmount(String string) {
		BigDecimal amount = toBigDecimal(string);
		if (amount != null && amount.signum() < 0) {
			// fail soft
			amount = null;
		}
		return amount;
	}

	private BigDecimal toBigDecimal(String string) {
		if (string == null) {
			return null;
		}
		string = string.trim();
		if (string.length() == 0) {
			return null;
		}
		try {
			return new BigDecimal(string);
		} catch (NumberFormatException e) {
			// fail soft
			return null;
		}
	}

}
