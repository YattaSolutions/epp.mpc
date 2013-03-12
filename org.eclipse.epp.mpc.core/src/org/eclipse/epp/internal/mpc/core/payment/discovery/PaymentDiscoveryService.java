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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCorePlugin;
import org.eclipse.epp.internal.mpc.core.payment.Messages;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * @author Carsten Reckord
 */
@SuppressWarnings("restriction")
public class PaymentDiscoveryService {

	private List<PaymentDiscoveryHandler> handlers;

	public PaymentDiscoveryService() {
		initialize();
	}

	public boolean isEnabled() {
		return !handlers.isEmpty();
	}

	private void initialize() {
		handlers = new ArrayList<PaymentDiscoveryHandler>();

		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IExtensionPoint extensionPoint = registry.getExtensionPoint("org.eclipse.epp.mpc.core", //$NON-NLS-1$
				"paymentDiscovery"); //$NON-NLS-1$

		//plugin.xml integrity has already been established while initializing the payment sub-system
		//so trust these discovery locations to be authorized
		for (IConfigurationElement element : extensionPoint.getConfigurationElements()) {
			IContributor contributor = element.getContributor();
			if (!"org.eclipse.epp.mpc.core".equals(contributor.getName()) //$NON-NLS-1$
					|| !"org.eclipse.epp.mpc.core".equals(element.getNamespaceIdentifier())) { //$NON-NLS-1$
				//skip external extension
				//TODO log
				continue;
			}
			if ("paymentDiscovery".equals(element.getName())) { //$NON-NLS-1$
				String url = element.getAttribute("url"); //$NON-NLS-1$
				try {
					URI uri = new URI(url);
					PaymentDiscoveryHandler handler = new PaymentDiscoveryHandler(uri.toURL());
					handlers.add(handler);
				} catch (URISyntaxException e) {
					//TODO log
				} catch (MalformedURLException e) {
					//TODO log
				}
			}
		}
	}

	public CatalogItem discoverConnectors(URL marketplaceUrl, String nodeId, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, Messages.PaymentDiscoveryService_Discovery, handlers.size());
		MultiStatus errors = new MultiStatus(MarketplaceClientCore.BUNDLE_ID, 0,
				Messages.PaymentDiscoveryService_discoveryErrors, null);
		IProfile profile = getProfile();
		for (PaymentDiscoveryHandler handler : handlers) {
			try {
				CatalogItem result = handler.discoverConnectors(marketplaceUrl, nodeId, profile, progress.newChild(1));
				if (result != null) {
					return result;
				}
			} catch (CoreException ex) {
				errors.add(ex.getStatus());
			}
		}
		if (errors.getSeverity() > IStatus.WARNING) {
			throw new CoreException(errors);
		}
		return null;
	}

	private IProfile getProfile() throws CoreException {
		BundleContext bundleContext = MarketplaceClientCorePlugin.getBundle().getBundleContext();
		ServiceReference<IProvisioningAgent> serviceReference = bundleContext.getServiceReference(IProvisioningAgent.class);
		if (serviceReference != null) {
			IProvisioningAgent agent = bundleContext.getService(serviceReference);
			try {
				IProfileRegistry profileRegistry = (IProfileRegistry) agent.getService(IProfileRegistry.SERVICE_NAME);
				if (profileRegistry != null) {
					return profileRegistry.getProfile(IProfileRegistry.SELF);
				}
			} finally {
				bundleContext.ungetService(serviceReference);
			}
		}
		IStatus error = new Status(IStatus.ERROR, MarketplaceClientCore.BUNDLE_ID,
				Messages.PaymentDiscoveryService_profileNotFound);
		throw new CoreException(error);
	}

	public static boolean requireRestart(IInstallableUnit[] ius) {
		for (IInstallableUnit unit : ius) {
			String value = unit.getProperty("org.eclipse.epp.mpc.payment.module.install.restart"); //$NON-NLS-1$
			if (!"false".equalsIgnoreCase(value)) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}
}
