/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	The Eclipse Foundation - initial API and implementation
 *  Yatta Solutions - bug 314936, bug 398200
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.ui.catalog;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.payment.PaymentServiceImpl;
import org.eclipse.epp.internal.mpc.core.service.CachingMarketplaceService;
import org.eclipse.epp.internal.mpc.core.service.Categories;
import org.eclipse.epp.internal.mpc.core.service.Category;
import org.eclipse.epp.internal.mpc.core.service.DefaultMarketplaceService;
import org.eclipse.epp.internal.mpc.core.service.Ius;
import org.eclipse.epp.internal.mpc.core.service.Market;
import org.eclipse.epp.internal.mpc.core.service.MarketplaceService;
import org.eclipse.epp.internal.mpc.core.service.News;
import org.eclipse.epp.internal.mpc.core.service.Node;
import org.eclipse.epp.internal.mpc.core.service.SearchResult;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.epp.internal.mpc.ui.catalog.MarketplaceCategory.Contents;
import org.eclipse.epp.internal.mpc.ui.util.ConcurrentTaskManager;
import org.eclipse.epp.mpc.core.payment.PaymentService;
import org.eclipse.epp.mpc.ui.CatalogDescriptor;
import org.eclipse.equinox.internal.p2.discovery.AbstractDiscoveryStrategy;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogCategory;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.internal.p2.discovery.model.Icon;
import org.eclipse.equinox.internal.p2.discovery.model.Overview;
import org.eclipse.equinox.internal.p2.discovery.model.Tag;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.osgi.framework.Bundle;

/**
 * @author David Green
 */
public class MarketplaceDiscoveryStrategy extends AbstractDiscoveryStrategy {

	private static final String DOT_FEATURE_DOT_GROUP = ".feature.group"; //$NON-NLS-1$

	private static final Pattern BREAK_PATTERN = Pattern.compile("<!--\\s*break\\s*-->"); //$NON-NLS-1$

	protected final CatalogDescriptor catalogDescriptor;

	private final MarketplaceService marketplaceService;

	private final PaymentServiceImpl paymentService;

	private MarketplaceCatalogSource source;

	private MarketplaceInfo marketplaceInfo;

	private Map<String, IInstallableUnit> featureIUById;

	public MarketplaceDiscoveryStrategy(CatalogDescriptor catalogDescriptor) {
		if (catalogDescriptor == null) {
			throw new IllegalArgumentException();
		}
		this.catalogDescriptor = catalogDescriptor;
		marketplaceService = createMarketplaceService();
		PaymentServiceImpl paymentService = new PaymentServiceImpl(marketplaceService, catalogDescriptor.getUrl()
				.toExternalForm());
		if (paymentService.isPaymentServiceEnabled()) {
			this.paymentService = paymentService;
		} else {
			this.paymentService = null;
		}
		source = new MarketplaceCatalogSource(marketplaceService);
		marketplaceInfo = MarketplaceInfo.getInstance();
	}

	public MarketplaceService createMarketplaceService() {
		DefaultMarketplaceService service = new DefaultMarketplaceService(this.catalogDescriptor.getUrl());
		Map<String, String> requestMetaParameters = new HashMap<String, String>();
		requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_CLIENT, MarketplaceClientCore.BUNDLE_ID);
		requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_OS, Platform.getOS());
		requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_WS, Platform.getWS());
		requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_NL, Platform.getNL());
		requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_JAVA_VERSION, System.getProperty("java.version")); //$NON-NLS-1$
		IProduct product = Platform.getProduct();
		if (product != null) {
			requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_PRODUCT, product.getId());
			Bundle productBundle = product.getDefiningBundle();
			if (productBundle != null) {
				requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_PRODUCT_VERSION,
						productBundle.getVersion().toString());
			}
		}
		Bundle runtimeBundle = Platform.getBundle("org.eclipse.core.runtime"); //$NON-NLS-1$
		if (runtimeBundle != null) {
			requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_RUNTIME_VERSION, runtimeBundle.getVersion()
					.toString());
		}
		// also send the platform version to distinguish between 3.x and 4.x platforms using the same runtime
		Bundle platformBundle = Platform.getBundle("org.eclipse.platform"); //$NON-NLS-1$
		if (platformBundle != null) {
			requestMetaParameters.put(DefaultMarketplaceService.META_PARAM_PLATFORM_VERSION,
					platformBundle.getVersion().toString());
		}
		service.setRequestMetaParameters(requestMetaParameters);
		return new CachingMarketplaceService(service);
	}

	@Override
	public void dispose() {
		if (source != null) {
			source.dispose();
			source = null;
		}
		if (marketplaceInfo != null) {
			marketplaceInfo.save();
			marketplaceInfo = null;
		}
		if (paymentService != null) {
			paymentService.dispose();
		}
		super.dispose();
	}

	@Override
	public void performDiscovery(IProgressMonitor monitor) throws CoreException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		final int totalWork = 10000000;
		final int workSegment = totalWork / 3;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_loadingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, workSegment));

			catalogCategory.setContents(Contents.FEATURED);

			SearchResult featured = marketplaceService.featured(new SubProgressMonitor(monitor, workSegment));
			handleSearchResult(catalogCategory, featured, new SubProgressMonitor(monitor, workSegment));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	protected void handleSearchResult(MarketplaceCategory catalogCategory, SearchResult result,
			final IProgressMonitor monitor) {
		if (!result.getNodes().isEmpty()) {
			int totalWork = 10000000;
			monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_loadingResources, totalWork);
			ConcurrentTaskManager executor = new ConcurrentTaskManager(result.getNodes().size(),
					Messages.MarketplaceDiscoveryStrategy_loadingResources);
			try {
				ProvisioningSession session = ProvisioningUI.getDefaultUI().getSession();
				RepositoryTracker repositoryTracker = ProvisioningUI.getDefaultUI().getRepositoryTracker();

				Set<URI> knownRepositories = new HashSet<URI>(
						Arrays.asList(repositoryTracker.getKnownRepositories(session)));

				final MultiStatus errors = new MultiStatus(MarketplaceClientCore.BUNDLE_ID, 0,
						Messages.MarketplaceDiscoveryStrategy_Error_during_query, null);
				for (final Node node : result.getNodes()) {
					final MarketplaceNodeCatalogItem catalogItem = new MarketplaceNodeCatalogItem();
					catalogItem.setMarketplaceUrl(catalogDescriptor.getUrl());
					catalogItem.setId(node.getId());
					catalogItem.setName(node.getName());
					catalogItem.setCategoryId(catalogCategory.getId());
					Categories categories = node.getCategories();
					if (categories != null) {
						for (Category category : categories.getCategory()) {
							catalogItem.addTag(new Tag(Category.class, category.getId(), category.getName()));
						}
					}
					catalogItem.setData(node);
					catalogItem.setSource(source);
					catalogItem.setLicense(node.getLicense());
					Ius ius = node.getIus();
					if (ius != null) {
						List<String> discoveryIus = new ArrayList<String>(ius.getIu());
						for (int x = 0; x < discoveryIus.size(); ++x) {
							String iu = discoveryIus.get(x);
							if (!iu.endsWith(DOT_FEATURE_DOT_GROUP)) {
								discoveryIus.set(x, iu + DOT_FEATURE_DOT_GROUP);
							}
						}
						catalogItem.setInstallableUnits(discoveryIus);
					}
					if (node.getShortdescription() == null && node.getBody() != null) {
						// bug 306653 <!--break--> marks the end of the short description.
						String descriptionText = node.getBody();
						Matcher matcher = BREAK_PATTERN.matcher(node.getBody());
						if (matcher.find()) {
							int start = matcher.start();
							if (start > 0) {
								String shortDescriptionText = descriptionText.substring(0, start).trim();
								if (shortDescriptionText.length() > 0) {
									descriptionText = shortDescriptionText;
								}
							}
						}
						catalogItem.setDescription(descriptionText);
					} else {
						catalogItem.setDescription(node.getShortdescription());
					}
					catalogItem.setProvider(node.getCompanyname());
					String updateurl = node.getUpdateurl();
					if (updateurl != null) {
						try {
							// trim is important!
							updateurl = updateurl.trim();
							new URL(updateurl);
							catalogItem.setSiteUrl(updateurl);
						} catch (MalformedURLException e) {
							// don't use malformed URLs
						}
					}
					if (node.getBody() != null || node.getScreenshot() != null) {
						final Overview overview = new Overview();
						overview.setItem(catalogItem);
						overview.setSummary(node.getBody());
						overview.setUrl(node.getUrl());
						catalogItem.setOverview(overview);

						if (node.getScreenshot() != null) {
							if (!source.getResourceProvider().containsResource(node.getScreenshot())) {
								executor.submit(new AbstractResourceRunnable(monitor, source.getResourceProvider(),
										node.getScreenshot()) {
									@Override
									protected void resourceRetrieved() {
										overview.setScreenshot(node.getScreenshot());
									}
								});
							} else {
								overview.setScreenshot(node.getScreenshot());
							}
						}
					}
					if (node.getImage() != null) {
						// FIXME: icon sizing
						if (!source.getResourceProvider().containsResource(node.getImage())) {
							executor.submit(new AbstractResourceRunnable(monitor, source.getResourceProvider(),
									node.getImage()) {
								@Override
								protected void resourceRetrieved() {
									createIcon(catalogItem, node);
								}

							});
						} else {
							createIcon(catalogItem, node);
						}
					}
					if (paymentService != null) {
						executor.submit(new PaymentServiceRunnable(paymentService,
								paymentService.getDiscoveryService(), catalogItem, monitor, errors));
					}
					items.add(catalogItem);
					marketplaceInfo.map(catalogItem.getMarketplaceUrl(), node);
					catalogItem.setInstalled(marketplaceInfo.computeInstalled(computeInstalledFeatures(monitor),
							knownRepositories, node));

				}
				try {
					executor.waitUntilFinished(new SubProgressMonitor(monitor, totalWork - 10));
				} catch (CoreException e) {
					// just log, since this is expected to occur frequently
					MarketplaceClientUi.error(e);
				}
				if (errors.getChildren().length > 0) {
					MarketplaceClientUi.getLog().log(errors);
				}
			} finally {
				executor.shutdownNow();
				monitor.done();
			}
			if (result.getMatchCount() != null) {
				catalogCategory.setMatchCount(result.getMatchCount());
				if (result.getMatchCount() > result.getNodes().size()) {
					// add an item here to indicate that the search matched more items than were returned by the server
					addCatalogItem(catalogCategory);
				}
			}
		}
	}

	public void maybeAddCatalogItem(MarketplaceCategory catalogCategory) {
		if (!items.isEmpty()) {
			CatalogItem catalogItem = items.get(items.size() - 1);
			if (catalogItem.getData() != catalogDescriptor) {
				addCatalogItem(catalogCategory);
			}
		}
	}

	public void addCatalogItem(MarketplaceCategory catalogCategory) {
		CatalogItem catalogItem = new CatalogItem();
		catalogItem.setSource(source);
		catalogItem.setData(catalogDescriptor);
		catalogItem.setId(catalogDescriptor.getUrl().toString());
		catalogItem.setCategoryId(catalogCategory.getId());
		items.add(catalogItem);
	}

	private void createIcon(CatalogItem catalogItem, final Node node) {
		Icon icon = new Icon();
		// don't know the size
		icon.setImage32(node.getImage());
		icon.setImage48(node.getImage());
		icon.setImage64(node.getImage());
		catalogItem.setIcon(icon);
	}

	public void performQuery(Market market, Category category, String queryText, IProgressMonitor monitor)
			throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.QUERY);
			SearchResult result = marketplaceService.search(market, category, queryText, new SubProgressMonitor(
					monitor, totalWork / 2));
			handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			if (result.getNodes().isEmpty()) {
				catalogCategory.setMatchCount(0);
				addCatalogItem(catalogCategory);
			}
		} finally {
			monitor.done();
		}
	}

	public void recent(IProgressMonitor monitor) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.RECENT);
			SearchResult result = marketplaceService.recent(new SubProgressMonitor(monitor, totalWork / 2));
			handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	public void featured(IProgressMonitor monitor, final Market market, final Category category) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.FEATURED);
			SearchResult result = marketplaceService.featured(new SubProgressMonitor(monitor, totalWork / 2), market,
					category);
			handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	public void popular(IProgressMonitor monitor) throws CoreException {
		// FIXME: do we want popular or favorites?
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.POPULAR);
			SearchResult result = marketplaceService.popular(new SubProgressMonitor(monitor, totalWork / 2));
			handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	public void installed(IProgressMonitor monitor) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_findingInstalled, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.INSTALLED);
			SearchResult result = new SearchResult();
			result.setNodes(new ArrayList<Node>());
			Set<String> installedFeatures = computeInstalledFeatures(monitor);
			if (!monitor.isCanceled()) {
				Set<Node> catalogNodes = marketplaceInfo.computeInstalledNodes(catalogDescriptor.getUrl(),
						installedFeatures);
				if (!catalogNodes.isEmpty()) {
					int unitWork = totalWork / (2 * catalogNodes.size());
					for (Node node : catalogNodes) {
						node = marketplaceService.getNode(node, monitor);
						result.getNodes().add(node);
						monitor.worked(unitWork);
					}
				} else {
					monitor.worked(totalWork / 2);
				}
				handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			}
		} finally {
			monitor.done();
		}
	}

	public void performQuery(IProgressMonitor monitor, Set<String> nodeIds) throws CoreException {
		Set<Node> nodes = new HashSet<Node>();
		for (String nodeId : nodeIds) {
			Node node = new Node();
			node.setId(nodeId);
			nodes.add(node);
		}
		performNodeQuery(monitor, nodes);
	}

	public void performNodeQuery(IProgressMonitor monitor, Set<Node> nodes) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.QUERY);
			SearchResult result = new SearchResult();
			result.setNodes(new ArrayList<Node>());
			if (!monitor.isCanceled()) {
				if (!nodes.isEmpty()) {
					int unitWork = totalWork / (2 * nodes.size());
					for (Node node : nodes) {
						node = marketplaceService.getNode(node, monitor);
						result.getNodes().add(node);
						monitor.worked(unitWork);
					}
				} else {
					monitor.worked(totalWork / 2);
				}
				result.setMatchCount(result.getNodes().size());
				handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
				maybeAddCatalogItem(catalogCategory);
			}
		} finally {
			monitor.done();
		}
	}

	protected Set<String> computeInstalledFeatures(IProgressMonitor monitor) {
		return computeInstalledIUs(monitor).keySet();
	}

	protected synchronized Map<String, IInstallableUnit> computeInstalledIUs(IProgressMonitor monitor) {
		if (featureIUById == null) {
			featureIUById = MarketplaceClientUi.computeInstalledIUsById(monitor);
		}
		return featureIUById;
	}

	protected MarketplaceCategory findMarketplaceCategory(IProgressMonitor monitor) throws CoreException {
		MarketplaceCategory catalogCategory = null;

		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_catalogCategory, 10000);
		try {
			for (CatalogCategory candidate : getCategories()) {
				if (candidate.getSource() == source) {
					catalogCategory = (MarketplaceCategory) candidate;
				}
			}

			if (catalogCategory == null) {
				List<Market> markets = marketplaceService.listMarkets(new SubProgressMonitor(monitor, 10000));

				// marketplace has markets and categories, however a node and/or category can appear in multiple
				// markets.  This doesn't match well with discovery's concept of a category.  Discovery requires all
				// items to be in a category, so we use a single root category and tagging.
				catalogCategory = new MarketplaceCategory();
				catalogCategory.setId("<root>"); //$NON-NLS-1$
				catalogCategory.setName("<root>"); //$NON-NLS-1$
				catalogCategory.setSource(source);

				catalogCategory.setMarkets(markets);

				categories.add(catalogCategory);
			}
		} finally {
			monitor.done();
		}
		return catalogCategory;
	}

	public News performNewsDiscovery(IProgressMonitor monitor) throws CoreException {
		return marketplaceService.news(monitor);
	}

	public void installErrorReport(IProgressMonitor monitor, IStatus result, Set<CatalogItem> items,
			IInstallableUnit[] operationIUs, String resolutionDetails) throws CoreException {
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_sendingErrorNotification, 100);
		try {
			Set<Node> nodes = new HashSet<Node>();
			for (CatalogItem item : items) {
				Object data = item.getData();
				if (data instanceof Node) {
					nodes.add((Node) data);
				}
			}
			Set<String> iuIdsAndVersions = new HashSet<String>();
			for (IInstallableUnit iu : operationIUs) {
				String id = iu.getId();
				String version = iu.getVersion() == null ? null : iu.getVersion().toString();
				iuIdsAndVersions.add(id + "," + version); //$NON-NLS-1$
			}
			marketplaceService.reportInstallError(monitor, result, nodes, iuIdsAndVersions, resolutionDetails);
		} finally {
			monitor.done();
		}
	}

	public PaymentService getPaymentService() {
		return paymentService;
	}
}
