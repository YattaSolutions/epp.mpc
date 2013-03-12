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
import org.eclipse.epp.internal.mpc.core.service.xml.UnmarshalContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * @author Carsten Reckord
 */
public class PaymentDiscoveryModuleContentHandler extends UnmarshalContentHandler {

	private static final String NS_URI = ""; //$NON-NLS-1$

	private PaymentDiscoveryModule model;

	@Override
	public void startElement(String uri, String localName, Attributes attributes) {
		if (localName.equals("module")) { //$NON-NLS-1$
			model = new PaymentDiscoveryModule();

			model.setId(attributes.getValue(NS_URI, "id")); //$NON-NLS-1$
			model.setName(attributes.getValue(NS_URI, "name")); //$NON-NLS-1$
			model.setProvider(attributes.getValue(NS_URI, "provider")); //$NON-NLS-1$
			model.setUrl(attributes.getValue(NS_URI, "url")); //$NON-NLS-1$
		} else if (localName.equals("image")) { //$NON-NLS-1$
			capturingContent = true;
		} else if (localName.equals("iu")) { //$NON-NLS-1$
			capturingContent = true;
		}
	}

	@Override
	public boolean endElement(String uri, String localName) throws SAXException {
		if (localName.equals("module")) { //$NON-NLS-1$
			getUnmarshaller().setModel(model);
			model = null;
			endNested(uri, localName);
			return true;
		} else if (localName.equals("image")) { //$NON-NLS-1$
			if (content != null) {
				String image = content.toString().trim();
				if (image.length() > 0) {
					model.setImage(image);
				}
				content = null;
			}
			capturingContent = false;
		} else if (localName.equals("iu")) { //$NON-NLS-1$
			if (content != null) {
				String iu = content.toString().trim();
				if (iu.length() > 0) {
					model.getIus().add(iu);
				}
				content = null;
			}
			capturingContent = false;
		}
		return false;
	}

}
