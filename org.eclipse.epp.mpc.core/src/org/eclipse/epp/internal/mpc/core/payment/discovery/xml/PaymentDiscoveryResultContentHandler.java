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

import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryModule;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryPrice;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryResult;
import org.eclipse.epp.internal.mpc.core.service.xml.UnmarshalContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * @author Carsten Reckord
 */
public class PaymentDiscoveryResultContentHandler extends UnmarshalContentHandler {

	private PaymentDiscoveryResult model;

	@Override
	public void startElement(String uri, String localName, Attributes attributes) {
		if (localName.equals("modules")) { //$NON-NLS-1$
			model = new PaymentDiscoveryResult();
		} else if (localName.equals("module")) { //$NON-NLS-1$
			PaymentDiscoveryModuleContentHandler childHandler = new PaymentDiscoveryModuleContentHandler();
			startNested(childHandler, model);
			childHandler.startElement(uri, localName, attributes);
		} else if (localName.equals("price")) { //$NON-NLS-1$
			PaymentDiscoveryPriceContentHandler childHandler = new PaymentDiscoveryPriceContentHandler();
			startNested(childHandler, model);
			childHandler.startElement(uri, localName, attributes);
		}
	}

	@Override
	public boolean endElement(String uri, String localName) throws SAXException {
		if (localName.equals("modules")) { //$NON-NLS-1$
			getUnmarshaller().setModel(model);
			model = null;
			return true;
		} else if (localName.equals("module")) { //$NON-NLS-1$
			PaymentDiscoveryModule module = (PaymentDiscoveryModule) getUnmarshaller().getModel();
			if (module != null) {
				model.getModules().add(module);
			}
		} else if (localName.equals("price")) { //$NON-NLS-1$
			PaymentDiscoveryPrice price = (PaymentDiscoveryPrice) getUnmarshaller().getModel();
			if (price != null) {
				model.setPrice(price);
			}
		}
		return false;
	}

}
