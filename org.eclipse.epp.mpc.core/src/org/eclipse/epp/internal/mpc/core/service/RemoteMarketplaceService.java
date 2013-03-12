/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     The Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.core.service;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.epp.internal.mpc.core.service.xml.Unmarshaller;
import org.eclipse.osgi.util.NLS;

public class RemoteMarketplaceService<T> extends RemoteService<T> {

	public static final String API_URI_SUFFIX = "api/p"; //$NON-NLS-1$

	@Override
	@SuppressWarnings({ "unchecked" })
	protected T processRequest(String baseUri, String relativePath, IProgressMonitor monitor) throws CoreException {
		final Unmarshaller unmarshaller = new Unmarshaller();

		processRequest(baseUri, relativePath, unmarshaller, monitor);

		Object model = unmarshaller.getModel();
		if (model == null) {
			// if we reach here this should never happen
			throw new IllegalStateException();
		} else {
			try {
				return (T) model;
			} catch (Exception e) {
				String message = NLS.bind(Messages.DefaultMarketplaceService_unexpectedResponseContent,
						model.getClass().getSimpleName());
				throw new CoreException(createErrorStatus(message, null));
			}
		}
	}

}
