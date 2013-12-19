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
package org.eclipse.epp.internal.mpc.ui.wizards;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUiPlugin;
import org.eclipse.epp.mpc.core.payment.PaymentItem;
import org.eclipse.epp.mpc.core.payment.PaymentModule;
import org.eclipse.epp.mpc.core.payment.PaymentTransaction;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;

public class DefaultPaymentItemLabelProvider extends LabelProvider {

	@Override
	public String getText(Object element) {
		if (element instanceof PaymentItem) {
			PaymentItem paymentItem = (PaymentItem) element;

			switch (paymentItem.getPriceType()) {
			case STARTING_FROM:
				return NLS.bind(Messages.DefaultPaymentItemLabelProvider_Start_price, getPrice(paymentItem));
			case DONATION:
				return Messages.DefaultPaymentItemLabelProvider_Donation;
			case EXACT:
			default:
				return getPrice(paymentItem);
			}
		}
		return Messages.DefaultPaymentItemLabelProvider_Purchase;
	}

	@Override
	public Image getImage(Object element) {
		return MarketplaceClientUiPlugin.getInstance()
				.getImageRegistry()
				.get(MarketplaceClientUiPlugin.ITEM_ICON_CART);
	}

	protected String getPrice(PaymentItem paymentItem) {
		String price = MessageFormat.format(Messages.DefaultPaymentItemLabelProvider_Price_format, paymentItem.getCurrency(), paymentItem.getPrice());
		if (paymentItem.getPurchaseType() != null) {
			String purchaseTypeLabel = paymentItem.getPurchaseType().getLabel();
			if (purchaseTypeLabel != null) {
				//price = MessageFormat.format(Messages.DefaultPaymentItemLabelProvider_Price_and_type, price, purchaseTypeLabel);
			}
		}
		return price;
	}

	public static String getAlternativeActionLabel(PaymentItem paymentItem, PaymentModule paymentModule) {
		boolean owned = paymentItem.isOwned();
		boolean cancelable = owned && paymentItem.isCancelable();
		boolean refundable = owned && paymentItem.isRefundable();
		boolean purchasePending = false;
		if (!owned && paymentModule != null) {
			PaymentTransaction session = paymentModule.getSession();
			if (session != null) {
				List<PaymentItem> pendingItems = session.getItems();
				String nodeId = paymentItem.getNodeId();
				for (PaymentItem pendingItem : pendingItems) {
					if (nodeId.equals(pendingItem.getNodeId())) {
						purchasePending = true;
					}
				}
			}
		}
		if (purchasePending) {
			return Messages.DefaultPaymentItemLabelProvider_Purchase_pending;
		} else if (refundable) {
			return Messages.DefaultPaymentItemLabelProvider_Refund;
		} else if (cancelable) {
			return Messages.DefaultPaymentItemLabelProvider_Unsubscribe;
		} else if (owned) {
			return Messages.DefaultPaymentItemLabelProvider_Owned;
		}
		return null;
	}

}
