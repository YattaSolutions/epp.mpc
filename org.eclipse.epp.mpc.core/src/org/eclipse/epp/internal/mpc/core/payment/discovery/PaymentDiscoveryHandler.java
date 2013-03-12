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

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.payment.Messages;
import org.eclipse.equinox.internal.p2.discovery.AbstractDiscoveryStrategy;
import org.eclipse.equinox.internal.p2.discovery.Catalog;
import org.eclipse.equinox.internal.p2.discovery.DiscoveryCore;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.osgi.util.NLS;

/**
 * @author Carsten Reckord
 */
@SuppressWarnings("restriction")
class PaymentDiscoveryHandler {

	private static final String DISCOVERY_SCHEME = "discovery/{0}/{1}"; //$NON-NLS-1$

	private final URL discoveryLocation;

	private final Map<String, Object> resultCache = new HashMap<String, Object>();

	PaymentDiscoveryHandler(URL discoveryLocation) {
		this.discoveryLocation = discoveryLocation;
	}

	protected Catalog getCatalog(URL marketplaceUrl, String nodeId) {
		String path = getDiscoveryPath(marketplaceUrl, nodeId);
		return getCatalog(path);
	}

	private Catalog getCatalog(String discoveryPath) {
		Catalog catalog = new Catalog();
		catalog.setEnvironment(DiscoveryCore.createEnvironment());
		catalog.setVerifyUpdateSiteAvailability(false);

		AbstractDiscoveryStrategy discoveryStrategy = new PaymentDiscoveryStrategy(discoveryLocation, discoveryPath);
		catalog.getDiscoveryStrategies().add(discoveryStrategy);

		return catalog;
	}

	private String getDiscoveryPath(URL marketplaceUrl, String nodeId) {
		String marketplaceId = marketplaceUrl == null ? "" : marketplaceUrl.getAuthority() //$NON-NLS-1$
				+ marketplaceUrl.getPath();
		try {
			marketplaceId = URLEncoder.encode(marketplaceId, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			// can't happen
			throw new IllegalStateException();
		}
		if (marketplaceId.length() == 0) {
			marketplaceId = "_"; //$NON-NLS-1$
		}
		String path = MessageFormat.format(DISCOVERY_SCHEME, marketplaceId, nodeId);
		return path;
	}

	public CatalogItem discoverConnectors(URL marketplaceUrl, String nodeId, IProfile profile, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, Messages.PaymentDiscoveryService_Discovery, 100);

		String discoveryPath = getDiscoveryPath(marketplaceUrl, nodeId);

		Object result = resultCache.get(discoveryPath);

		CatalogItem catalogItem;
		if (result == null) {
			try {
				catalogItem = performDiscovery(discoveryPath, profile, progress);
				resultCache.put(discoveryPath, catalogItem);
			} catch (CoreException ex) {
				resultCache.put(discoveryPath, ex);
				throw ex;
			} catch (RuntimeException ex) {
				IStatus status = new Status(IStatus.ERROR, MarketplaceClientCore.BUNDLE_ID, 0,
						Messages.PaymentDiscoveryHandler_unexpectedError, ex);
				CoreException coreEx = new CoreException(status);
				resultCache.put(discoveryPath, ex);
				throw coreEx;
			}
		} else if (result instanceof CatalogItem) {
			catalogItem = (CatalogItem) result;
		} else {
			throw (CoreException) result;
		}
		progress.done();
		return catalogItem;
	}

	private CatalogItem performDiscovery(String discoveryPath, IProfile profile, SubMonitor progress)
			throws CoreException {
		Catalog catalog = getCatalog(discoveryPath);

		IStatus status = catalog.performDiscovery(progress.newChild(75));

		if (!status.isOK()) {
			throw new CoreException(status);
		}

		int totalItemWork = catalog.getItems().size();
		List<CatalogItem> items = new ArrayList<CatalogItem>(totalItemWork);
		SubMonitor filterProgress = SubMonitor.convert(progress.newChild(25), totalItemWork);
		for (CatalogItem item : catalog.getItems()) {
			totalItemWork--;
			if (!isInstalled(profile, item, filterProgress.newChild(1))) {
				items.add(item);
			}
			filterProgress.setWorkRemaining(totalItemWork);
		}
		catalog.dispose();

		switch (items.size()) {
		case 0:
			return null;
		case 1:
			return items.get(0);
		default:
			IStatus multipleWarning = new Status(IStatus.WARNING, MarketplaceClientCore.BUNDLE_ID, NLS.bind(
					Messages.PaymentDiscoveryHandler_multiplePaymentModules, discoveryPath));
			MarketplaceClientCore.getLog().log(multipleWarning);
			return items.get(0);
		}
	}

	private boolean isInstalled(IProfile profile, CatalogItem item, IProgressMonitor monitor) {
		if (profile == null) {
			return false;
		}

		IQuery<IInstallableUnit> query;
		List<String> installableUnits = item.getInstallableUnits();
		switch (installableUnits.size()) {
		case 0:
			return true;
		case 1:
			query = QueryUtil.createIUQuery(installableUnits.get(0));
			break;
		default:
			List<IQuery<IInstallableUnit>> queries = new ArrayList<IQuery<IInstallableUnit>>();
			for (String iuId : installableUnits) {
				queries.add(QueryUtil.createIUQuery(iuId));
			}
			query = QueryUtil.createCompoundQuery(queries, true);
		}

		IQueryResult<IInstallableUnit> result = profile.query(query, monitor);

		return !result.isEmpty();
	}

	protected static boolean requireRestart(IInstallableUnit[] ius) {
		for (IInstallableUnit unit : ius) {
			String value = unit.getProperty("org.eclipse.epp.mpc.payment.module.install.restart"); //$NON-NLS-1$
			if (!"false".equalsIgnoreCase(value)) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}
}
