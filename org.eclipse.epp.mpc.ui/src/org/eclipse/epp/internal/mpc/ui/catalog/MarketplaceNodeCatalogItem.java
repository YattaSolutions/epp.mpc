/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	The Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.ui.catalog;

import java.net.URL;

import org.eclipse.epp.internal.mpc.core.service.Node;
import org.eclipse.epp.mpc.core.payment.PaymentItem;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;

/**
 * @author David Green
 */
public class MarketplaceNodeCatalogItem extends CatalogItem {

	private URL marketplaceUrl;

	private Boolean updateAvailable;

	private PaymentItem paymentItem;

	private CatalogItem discoveredPaymentConnector;

	@Override
	public Node getData() {
		return (Node) super.getData();
	}

	public URL getMarketplaceUrl() {
		return marketplaceUrl;
	}

	public void setMarketplaceUrl(URL marketplaceUrl) {
		this.marketplaceUrl = marketplaceUrl;
	}

	public Boolean getUpdateAvailable() {
		return updateAvailable;
	}

	public void setUpdateAvailable(Boolean updateAvailable) {
		this.updateAvailable = updateAvailable;
	}

	public PaymentItem getPaymentItem() {
		return paymentItem;
	}

	public void setPaymentItem(PaymentItem paymentItem) {
		this.paymentItem = paymentItem;
	}

	public CatalogItem getDiscoveredPaymentConnector() {
		return discoveredPaymentConnector;
	}

	public void setDiscoveredPaymentConnector(CatalogItem discoveredPaymentConnector) {
		this.discoveredPaymentConnector = discoveredPaymentConnector;
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
		MarketplaceNodeCatalogItem other = (MarketplaceNodeCatalogItem) obj;
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
