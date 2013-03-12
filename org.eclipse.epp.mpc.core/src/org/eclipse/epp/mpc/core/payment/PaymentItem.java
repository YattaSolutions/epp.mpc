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
package org.eclipse.epp.mpc.core.payment;

import java.math.BigDecimal;
import java.util.Currency;

public class PaymentItem {

	public static enum PriceType {
		EXACT, STARTING_FROM, DONATION
	}

	public static enum PurchaseType {
		UNLIMITED(null), PER_USE(Messages.PaymentItem_Per_use), DAILY(Messages.PaymentItem_daily), WEEKLY(Messages.PaymentItem_weekly), MONTHLY(Messages.PaymentItem_monthly), YEARLY(Messages.PaymentItem_yearly), OTHER(null);

		private final String label;

		private PurchaseType(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	private final PaymentModule paymentModule;

	private String nodeId;

	private String id;

	private BigDecimal price;

	private Currency currency;

	private boolean owned;

	private boolean subscription;

	private boolean refundable;

	private boolean cancelable;

	private PriceType priceType = PriceType.EXACT;

	private PurchaseType purchaseType = null;

	public PaymentItem(PaymentModule paymentModule) {
		super();
		this.paymentModule = paymentModule;
	}

	public PaymentModule getPaymentModule() {
		return paymentModule;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public Currency getCurrency() {
		return currency;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public PriceType getPriceType() {
		return priceType;
	}

	public void setPriceType(PriceType priceType) {
		this.priceType = priceType;
	}

	public PurchaseType getPurchaseType() {
		return purchaseType;
	}

	public void setPurchaseType(PurchaseType purchaseType) {
		this.purchaseType = purchaseType;
	}

	public boolean isOwned() {
		return owned;
	}

	public void setOwned(boolean owned) {
		this.owned = owned;
	}

	public boolean isSubscription() {
		return subscription;
	}

	public void setSubscription(boolean subscription) {
		this.subscription = subscription;
	}

	public boolean isRefundable() {
		return refundable;
	}

	public void setRefundable(boolean refundable) {
		this.refundable = refundable;
	}

	public boolean isCancelable() {
		return cancelable;
	}

	public void setCancelable(boolean cancelable) {
		this.cancelable = cancelable;
	}
}
