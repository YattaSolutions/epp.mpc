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
package org.eclipse.epp.internal.mpc.core.payment.discovery;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.epp.internal.mpc.core.payment.Messages;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryModule;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryPrice;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryResult;
import org.eclipse.epp.internal.mpc.core.payment.discovery.xml.PaymentDiscoveryResultContentHandler;
import org.eclipse.epp.internal.mpc.core.service.MarketplaceUnmarshaller;
import org.eclipse.epp.internal.mpc.core.service.RemoteService;
import org.eclipse.epp.internal.mpc.core.service.xml.UnmarshalContentHandler;
import org.eclipse.epp.internal.mpc.core.service.xml.Unmarshaller;
import org.eclipse.equinox.internal.p2.discovery.AbstractDiscoveryStrategy;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogCategory;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;

/**
 * @author Carsten Reckord
 */
@SuppressWarnings("restriction")
public class PaymentDiscoveryStrategy extends AbstractDiscoveryStrategy {

	private static final String CATEGORY_NAME = Messages.PaymentDiscoveryStrategy_Category;

	private static final String CATEGORY_ID = "org.eclipse.epp.mpc.payment.module.category"; //$NON-NLS-1$

	private URL baseUrl;

	private String requestPath;

	public PaymentDiscoveryStrategy(URL baseUrl, String requestPath) {
		this.baseUrl = baseUrl;
		this.requestPath = requestPath;
	}

	public URL getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(URL baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getRequestPath() {
		return requestPath;
	}

	public void setRequestPath(String requestPath) {
		this.requestPath = requestPath;
	}

	@Override
	public void performDiscovery(IProgressMonitor monitor) throws CoreException {
		PaymentDiscoveryRemoteService remoteService = new PaymentDiscoveryRemoteService();
		remoteService.setBaseUrl(baseUrl);

		PaymentDiscoveryResult result = remoteService.processRequest(getRequestPath(), monitor);//FIXME
		PaymentDiscoveryPrice price = result.getPrice();

		CatalogCategory category = new CatalogCategory();
		category.setId(CATEGORY_ID);
		category.setName(CATEGORY_NAME);
		getCategories().add(category);

		List<PaymentDiscoveryModule> modules = result.getModules();
		List<CatalogItem> items = getItems();
		for (PaymentDiscoveryModule module : modules) {
			String connectorSiteUrl;
			try {
				connectorSiteUrl = resolveDiscoveryUrl(module.getUrl());
			} catch (MalformedURLException e) {
				//TODO log
				continue;
			}

			module.setPrice(price);
			CatalogItem catalogItem = new CatalogItem();
			catalogItem.setId(module.getId());
			catalogItem.setSiteUrl(connectorSiteUrl);
			catalogItem.setName(module.getName());
			catalogItem.setProvider(module.getProvider());
			catalogItem.setData(module);
			catalogItem.setCategoryId(CATEGORY_ID);
			catalogItem.setInstallableUnits(module.getIus());
			items.add(catalogItem);
		}
	}

	private String resolveDiscoveryUrl(String relativeUrl) throws MalformedURLException {
		String url;
		if (relativeUrl.startsWith("http://")) {
			url = relativeUrl;
		} else {
			String path = getRequestPath();
			if (!path.endsWith("/")) {
				path += "/";
			}
			URL baseUrl = new URL(getBaseUrl(), path);
			url = new URL(baseUrl, relativeUrl).toExternalForm();
		}
		return url;
	}

	private static class PaymentDiscoveryRemoteService extends RemoteService<PaymentDiscoveryResult> {

		private PaymentDiscoveryRemoteService() {

			super(PaymentDiscoveryResult.class, new MarketplaceUnmarshaller() {
				@Override
				protected Unmarshaller createUnmarshaller() {
					Map<String, UnmarshalContentHandler> handlers = new HashMap<String, UnmarshalContentHandler>();
					handlers.put("modules", new PaymentDiscoveryResultContentHandler()); //$NON-NLS-1$
					return new Unmarshaller(handlers);
				}
			});
		}


		@Override
		public PaymentDiscoveryResult processRequest(String relativeUrl, IProgressMonitor monitor) throws CoreException {
			return super.processRequest(relativeUrl, monitor);
		}
	}
}
