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
package org.eclipse.epp.internal.mpc.core.payment.discovery.model;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carsten Reckord
 */
public class PaymentDiscoveryModule {
	private String id;

	private String provider;

	private String name;

	private String url;

	private String image;

	private PaymentDiscoveryPrice price;

	private final List<String> ius = new ArrayList<String>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public List<String> getIus() {
		return ius;
	}

	public PaymentDiscoveryPrice getPrice() {
		return price;
	}

	public void setPrice(PaymentDiscoveryPrice price) {
		this.price = price;
	}
}