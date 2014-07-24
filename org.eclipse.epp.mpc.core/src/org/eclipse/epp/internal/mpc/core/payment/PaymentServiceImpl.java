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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.*;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.payment.discovery.PaymentDiscoveryService;
import org.eclipse.epp.internal.mpc.core.service.DefaultMarketplaceService;
import org.eclipse.epp.internal.mpc.core.service.Ius;
import org.eclipse.epp.internal.mpc.core.service.MarketplaceService;
import org.eclipse.epp.internal.mpc.core.service.Node;
import org.eclipse.epp.mpc.core.payment.PaymentModule;
import org.eclipse.epp.mpc.core.payment.PaymentService;
import org.eclipse.epp.mpc.core.payment.PaymentServiceListener;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * @author Carsten Reckord
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public class PaymentServiceImpl implements PaymentService {

	private static boolean secure;

	static {
		try {
			SecurityHelper.getInstance();
			secure = true;
		} catch (Exception e) {
			secure = false;
			logSecurityIssue(e);
		}
	}
	private final MarketplaceService marketplaceService;

	private final PaymentDiscoveryService discoveryService;

	private final String catalogUrl;

	private final List<ModuleProxy> paymentModules;

	private final Map<String, PaymentModule> paymentModuleCache = new HashMap<String, PaymentModule>();

	private RegistryListener registryListener;

	private ListenerList paymentServiceListeners;

	public PaymentServiceImpl(MarketplaceService marketplaceService, String catalogUrl) {
		this.marketplaceService = marketplaceService;
		this.catalogUrl = catalogUrl;
		this.discoveryService = new PaymentDiscoveryService();
		this.paymentModules = new ArrayList<ModuleProxy>();
		initialize();
	}

	public final boolean isPaymentServiceEnabled() {
		try {
			SecurityHelper.getInstance();
			secure = true;
		} catch (Exception e) {
			if (secure) {
				//avoid spamming the logs
				secure = false;
				logSecurityIssue(e);
			}
		}

		if (!paymentModules.isEmpty()) {
			return true;
		}
		return discoveryService.isEnabled();
	}

	private static void logSecurityIssue(Exception e) {
		IStatus securityProblem = new Status(IStatus.ERROR, MarketplaceClientCore.BUNDLE_ID,
				Messages.PaymentServiceImpl_securityIssues, e);
		MarketplaceClientCore.getLog().log(securityProblem);
	}

	public String getCatalogUrl() {
		return catalogUrl;
	}

	public PaymentModule getPaymentModule(String moduleId) {
		for (ModuleProxy proxy : paymentModules) {
			if (moduleId.equals(proxy.getId())) {
				return proxy.resolve();
			}
		}
		return null;
	}

	public synchronized PaymentModule getPaymentModule(String nodeId, IProgressMonitor monitor) throws CoreException {
		PaymentModule paymentModule = paymentModuleCache.get(nodeId);
		SubMonitor progress = SubMonitor.convert(monitor, paymentModules.size());
		if (paymentModule == null && !paymentModuleCache.containsKey(nodeId)) {
			MultiStatus resolveErrors = new MultiStatus(MarketplaceClientCore.BUNDLE_ID, 0, NLS.bind(
					Messages.PaymentServiceImpl_Error_finding_payment_module, nodeId), null);
			for (ModuleProxy proxy : paymentModules) {
				try {
					if (proxy.handles(nodeId, progress.newChild(1))) {
						paymentModule = proxy.resolve();
						if (paymentModule != null) {
							break;
						}
					}
				} catch (Exception e) {
					IStatus error = new Status(IStatus.ERROR, MarketplaceClientCore.BUNDLE_ID, proxy.getId(), e);
					resolveErrors.add(error);
				}
			}
			if (resolveErrors.getChildren().length > 0) {
				MarketplaceClientCore.getLog().log(resolveErrors);
			}
			paymentModuleCache.put(nodeId, paymentModule);
		}
		return paymentModule;
	}

	private void initialize() {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		this.registryListener = new RegistryListener();
		registry.addListener(registryListener, "org.eclipse.epp.mpc.core.paymentModules"); //$NON-NLS-1$
		refresh();
	}

	public final void refresh() {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IExtensionPoint extensionPoint = registry.getExtensionPoint("org.eclipse.epp.mpc.core", //$NON-NLS-1$
				"paymentModules"); //$NON-NLS-1$
		List<ModuleProxy> paymentModules = new ArrayList<ModuleProxy>();
		for (IConfigurationElement element : extensionPoint.getConfigurationElements()) {
			if ("paymentModule".equals(element.getName())) { //$NON-NLS-1$
				ModuleProxy proxy = create(element);
				if (proxy != null) {
					paymentModules.add(proxy);
				}
			}
		}

		List<ModuleProxy> oldPaymentModules = this.paymentModules;
		List<ModuleProxy> removed = new ArrayList<ModuleProxy>(oldPaymentModules);
		removed.removeAll(paymentModules);

		List<ModuleProxy> added = paymentModules;
		added.removeAll(oldPaymentModules);

		remove(removed);
		add(added);
	}

	private ModuleProxy create(IConfigurationElement element) {
		ModuleProxy proxy = new ModuleProxy(element);
		String moduleCatalogUrl = proxy.getCatalogUrl();
		if (moduleCatalogUrl == null) {
			moduleCatalogUrl = DefaultMarketplaceService.ECLIPSE_MARKETPLACE_LOCATION;
		}
		if (moduleCatalogUrl.equals(catalogUrl)) {
			return proxy;
		}
		return null;
	}

	private void remove(List<ModuleProxy> modules) {
		synchronized (this) {
			if (this.paymentModules.removeAll(modules)) {
				this.paymentModuleCache.clear();
			}
		}
		firePaymentModulesRemoved(modules);
	}

	private void firePaymentModulesRemoved(List<ModuleProxy> modules) {
		if (paymentServiceListeners != null) {
			for (ModuleProxy moduleProxy : modules) {
				for (Object listener : paymentServiceListeners.getListeners()) {
					((PaymentServiceListener) listener).paymentModuleRemoved(moduleProxy.getId());
				}
			}
		}
	}

	private void add(List<ModuleProxy> modules) {
		synchronized (this) {
			for (ModuleProxy proxy : modules) {
				if (!this.paymentModules.contains(proxy)) {
					this.paymentModules.add(proxy);
				}
			}
			this.paymentModuleCache.clear();
		}
		firePaymentModulesAdded(modules);
	}

	private void firePaymentModulesAdded(List<ModuleProxy> modules) {
		if (paymentServiceListeners != null) {
			for (ModuleProxy moduleProxy : modules) {
				for (Object listener : paymentServiceListeners.getListeners()) {
					((PaymentServiceListener) listener).paymentModuleAdded(moduleProxy.getId());
				}
			}
		}
	}

	protected Node getNode(String nodeId, IProgressMonitor monitor) throws CoreException {
		Node node = new Node();
		node.setId(nodeId);
		node = marketplaceService.getNode(node, monitor);
		return node;
	}

	public MarketplaceService getMarketplaceService() {
		return marketplaceService;
	}

	public void dispose() {
		if (registryListener != null) {
			Platform.getExtensionRegistry().removeListener(registryListener);
		}
	}

	public void addPaymentServiceListener(PaymentServiceListener listener) {
		if (paymentServiceListeners == null) {
			paymentServiceListeners = new ListenerList();
		}
		paymentServiceListeners.add(listener);
	}

	public void removePaymentServiceListener(PaymentServiceListener listener) {
		if (paymentServiceListeners != null) {
			paymentServiceListeners.remove(listener);
		}
	}

	public PaymentDiscoveryService getDiscoveryService() {
		return discoveryService;
	}

	private class ModuleProxy {
		private PaymentModule instance;

		private final String id;

		private Pattern iuFilter;

		private final IConfigurationElement configurationElement;

		private boolean resolved;

		ModuleProxy(IConfigurationElement configurationElement) {
			this.configurationElement = configurationElement;
			this.id = getId(configurationElement);
		}

		private void verifyOwner() {
			String contributor = configurationElement.getContributor().getName();
			SecurityHelper.getInstance().verify(contributor, true);
		}

		private String getId(IConfigurationElement configurationElement) {
			String id = configurationElement.getAttribute("id"); //$NON-NLS-1$
			if (!id.contains(".")) { //$NON-NLS-1$
				String namespaceIdentifier = configurationElement.getNamespaceIdentifier();
				id = namespaceIdentifier + "." + id; //$NON-NLS-1$
			}
			return id;
		}

		public boolean handles(String nodeId, IProgressMonitor monitor) throws CoreException {
			SubMonitor progress = SubMonitor.convert(monitor, 100);
			if (!matchesIuFilter(nodeId, progress.newChild(20))) {
				return false;
			}
			PaymentModule instance = resolve();
			return instance != null && instance.handles(nodeId, progress.newChild(80));
		}

		public synchronized PaymentModule resolve() {
			if (!resolved) {
				resolved = true;
				PaymentModule instance = null;
				try {
					//check if contributing bundle is trustworthy
					verifyOwner();

					instance = (PaymentModule) configurationElement.createExecutableExtension("class"); //$NON-NLS-1$
					Class<? extends PaymentModule> moduleClass = instance.getClass();
					Bundle bundle = FrameworkUtil.getBundle(moduleClass);
					if (bundle == null) {
						throw new SecurityException(NLS.bind(Messages.PaymentServiceImpl_verifyNoOwningBundle,
								moduleClass.getName()));
					} else if (!configurationElement.getContributor().getName().equals(bundle.getSymbolicName())) {
						//class comes from other bundle than the extension. So verify that, too.
						SecurityHelper.getInstance().verify(bundle, true);
					}
				} catch (SecurityException e) {
					MarketplaceClientCore.error(
							NLS.bind(Messages.PaymentServiceImpl_Payment_module_security_issue, id), e);
				} catch (Exception e) {
					MarketplaceClientCore.error(
							NLS.bind(Messages.PaymentServiceImpl_Error_creating_payment_module, id), e);
				}
				if (instance == null) {
					remove(Collections.singletonList(this));
				} else {
					instance.setPaymentService(PaymentServiceImpl.this);
				}
				this.instance = instance;
			}
			return this.instance;
		}

		public String getCatalogUrl() {
			return configurationElement.getAttribute("catalog"); //$NON-NLS-1$
		}

		private boolean matchesIuFilter(String nodeId, IProgressMonitor monitor) throws CoreException {
			Node node = getNode(nodeId, monitor);
			Ius ius = node == null ? null : node.getIus();
			if (ius != null && !ius.getIu().isEmpty()) {
				if (iuFilter == null) {
					String filterText = configurationElement.getAttribute("filter"); //$NON-NLS-1$
					if (filterText != null) {
						try {
							iuFilter = Pattern.compile(filterText);
						} catch (Exception e) {
							MarketplaceClientCore.error(NLS.bind(
									Messages.PaymentServiceImpl_Error_IU_Filter, id), e);
							paymentModules.remove(this);
							return false;
						}
					}
				}
				if (iuFilter != null) {
					Matcher matcher = iuFilter.matcher(""); //$NON-NLS-1$
					for (String iu : ius.getIu()) {
						if (!matcher.reset(iu).matches()) {
							return false;
						}
					}
				}
			}
			return true;
		}

		public String getId() {
			return id;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			ModuleProxy other = (ModuleProxy) obj;
			if (id == null) {
				if (other.id != null) {
					return false;
				}
			} else if (!id.equals(other.id)) {
				return false;
			}
			return true;
		}
	}

	private class RegistryListener implements IRegistryEventListener {

		public void added(IExtension[] extensions) {
			refresh();
		}

		public void removed(IExtension[] extensions) {
			List<ModuleProxy> modules = new ArrayList<PaymentServiceImpl.ModuleProxy>();
			for (IExtension extension : extensions) {
				IConfigurationElement[] elements = extension.getConfigurationElements();
				for (IConfigurationElement element : elements) {
					if (element.getName().equals("paymentModule")) { //$NON-NLS-1$
						ModuleProxy proxy = create(element);
						if (proxy != null) {
							modules.add(proxy);
						}
					}
				}
			}
			if (!modules.isEmpty()) {
				remove(modules);
				refresh();
			}
		}

		public void added(IExtensionPoint[] extensionPoints) {
		}

		public void removed(IExtensionPoint[] extensionPoints) {
		}
	}
}
