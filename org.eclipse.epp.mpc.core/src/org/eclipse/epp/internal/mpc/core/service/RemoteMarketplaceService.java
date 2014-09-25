/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     The Eclipse Foundation - initial API and implementation
 *     Yatta Solutions - bug 432803: public API
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.core.service;

import org.eclipse.epp.mpc.core.service.IMarketplaceUnmarshaller;
import org.eclipse.epp.mpc.core.service.ServiceHelper;

public class RemoteMarketplaceService<T> extends RemoteService<T> {

	public RemoteMarketplaceService(Class<T> cls) {
		super(cls, createOrGetUnmarshaller());
	}

	private static IMarketplaceUnmarshaller createOrGetUnmarshaller() {
		IMarketplaceUnmarshaller unmarshaller = ServiceHelper.getMarketplaceUnmarshaller();
		if (unmarshaller == null) {
			//no unmarshaller registered, create a default instance
			unmarshaller = new MarketplaceUnmarshaller();
		}
		return unmarshaller;
	}

	public static final String API_URI_SUFFIX = "api/p"; //$NON-NLS-1$

}
