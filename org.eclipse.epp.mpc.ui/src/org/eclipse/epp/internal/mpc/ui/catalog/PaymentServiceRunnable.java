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
package org.eclipse.epp.internal.mpc.ui.catalog;

import java.math.BigDecimal;
import java.util.Currency;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.payment.discovery.PaymentDiscoveryService;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryModule;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryPrice;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.epp.mpc.core.payment.PaymentItem;
import org.eclipse.epp.mpc.core.payment.PaymentModule;
import org.eclipse.epp.mpc.core.payment.PaymentService;
import org.eclipse.epp.mpc.core.payment.PaymentItem.PriceType;
import org.eclipse.epp.mpc.core.payment.PaymentItem.PurchaseType;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.osgi.util.NLS;

class PaymentServiceRunnable implements Runnable {

	private final MarketplaceNodeCatalogItem catalogItem;

	private final IProgressMonitor monitor;

	private final PaymentService paymentService;

	private final PaymentDiscoveryService discoveryService;

	private final MultiStatus errors;

	PaymentServiceRunnable(PaymentService paymentService, PaymentDiscoveryService discoveryService,
			MarketplaceNodeCatalogItem catalogItem, IProgressMonitor monitor, MultiStatus errors) {
		this.paymentService = paymentService;
		this.discoveryService = discoveryService;
		this.catalogItem = catalogItem;
		this.monitor = monitor;
		this.errors = errors;
	}

	public void run() {
		IStatus status = null;
		PaymentModule paymentModule;
		try {
			paymentModule = paymentService.getPaymentModule(catalogItem.getId(), monitor);
			if (paymentModule == null) {
				status = discoverConnector();
			} else {
				status = queryPaymentItem(paymentModule);
			}
		} catch (CoreException e) {
			status = new Status(IStatus.ERROR, MarketplaceClientCore.BUNDLE_ID, NLS.bind(
					Messages.MarketplaceDiscoveryStrategy_Error_resolving_payment_module, catalogItem.getName()), e);
		} catch (Exception e) {
			status = MarketplaceClientUi.computeStatus(e, ""/*TODO*/);
		}
		if (!status.isOK()) {
			errors.add(status);
		}
	}

	private IStatus queryPaymentItem(PaymentModule paymentModule) {
		try {
			PaymentItem paymentItem = paymentModule.query(catalogItem.getId(), monitor);
			if (paymentItem != null) {
				catalogItem.setPaymentItem(paymentItem);
			}
		} catch (CoreException e) {
			return new Status(IStatus.ERROR, MarketplaceClientCore.BUNDLE_ID, NLS.bind(
					Messages.MarketplaceDiscoveryStrategy_Error_getting_payment_data, catalogItem.getName()), e);
		}
		return Status.OK_STATUS;
	}

	private IStatus discoverConnector() {
		try {
			CatalogItem discoveredConnector = discoveryService.discoverConnectors(catalogItem.getMarketplaceUrl(),
					catalogItem.getId(), monitor);
			if (discoveredConnector != null) {
				catalogItem.setDiscoveredPaymentConnector(discoveredConnector);
				try {
					PaymentItem preliminaryPrice = getPreliminaryPrice(discoveredConnector);
					catalogItem.setPaymentItem(preliminaryPrice);
				} catch (Exception e) {
					return new Status(IStatus.WARNING, MarketplaceClientCore.BUNDLE_ID, "", e);//TODO message //$NON-NLS-1$
				}
			}
		} catch (CoreException e) {
			return e.getStatus();
		}
		return Status.OK_STATUS;
	}

	private PaymentItem getPreliminaryPrice(CatalogItem discoveredConnector) {
		PaymentDiscoveryModule discoveryModule = (PaymentDiscoveryModule) discoveredConnector.getData();
		PaymentDiscoveryPrice price = discoveryModule.getPrice();
		if (price != null) {
			BigDecimal amount = price.getAmount();
			if (amount == null) {
				//fail soft
				return null;
			}
			Currency currency;
			String currencyCode = price.getCurrency();
			if (currencyCode == null) {
				throw new IllegalArgumentException("Missing currency code");
			}
			try {
				currency = Currency.getInstance(currencyCode);
			} catch (Exception ex) {
				throw new IllegalArgumentException("Invalid currency code: " + currencyCode);
			}
			PriceType priceType = getPriceType(price);
			PurchaseType purchaseType = getPurchaseType(price);
			if (BigDecimal.ZERO.compareTo(amount) > 0) {
				throw new IllegalArgumentException("Invalid price: " + amount);
			} else if (BigDecimal.ZERO.compareTo(amount) == 0 && priceType != PriceType.DONATION) {
				throw new IllegalArgumentException("Invalid price: " + amount);
			}

			PaymentItem paymentItem = new PaymentItem(null);
			paymentItem.setCurrency(currency);
			paymentItem.setPrice(amount);
			paymentItem.setPriceType(priceType);
			paymentItem.setPurchaseType(purchaseType);
			return paymentItem;
		}
		return null;
	}

	private PriceType getPriceType(PaymentDiscoveryPrice price) {
		PriceType priceType;
		String priceTypeString = price.getPriceType();
		if (priceTypeString == null || "from".equalsIgnoreCase(priceTypeString)) {
			priceType = PriceType.STARTING_FROM;
		} else if ("exact".equalsIgnoreCase(priceTypeString)) {
			priceType = PriceType.EXACT;
		} else if ("donation".equalsIgnoreCase(priceTypeString)) {
			priceType = PriceType.DONATION;
		} else {
			throw new IllegalArgumentException("Invalid price type: " + priceTypeString);
		}
		return priceType;
	}

	private PurchaseType getPurchaseType(PaymentDiscoveryPrice price) {
		PurchaseType purchaseType = null;
		String purchaseTypeString = price.getPurchaseType();
		if (purchaseTypeString == null) {
			purchaseType = PurchaseType.UNLIMITED;
		} else {
			PurchaseType[] values = PurchaseType.values();
			for (PurchaseType type : values) {
				if (type.name().equalsIgnoreCase(purchaseTypeString)) {
					purchaseType = type;
					break;
				}
			}
		}
		if (purchaseType == null) {
			throw new IllegalArgumentException("Invalid purchase type: " + purchaseTypeString);
		}
		return purchaseType;
	}
}