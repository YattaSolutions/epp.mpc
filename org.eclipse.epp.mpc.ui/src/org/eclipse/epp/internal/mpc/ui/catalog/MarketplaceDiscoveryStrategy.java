/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	The Eclipse Foundation - initial API and implementation
 *  Yatta Solutions - bug 314936, bug 398200, public API (bug 432803)
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.ui.catalog;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.ServiceLocator;
import org.eclipse.epp.internal.mpc.core.payment.PaymentServiceImpl;
import org.eclipse.epp.internal.mpc.core.service.Identifiable;
import org.eclipse.epp.internal.mpc.core.service.Node;
import org.eclipse.epp.internal.mpc.core.service.SearchResult;
import org.eclipse.epp.internal.mpc.core.util.URLUtil;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.epp.internal.mpc.ui.catalog.MarketplaceCategory.Contents;
import org.eclipse.epp.internal.mpc.ui.util.ConcurrentTaskManager;
import org.eclipse.epp.mpc.core.model.ICategories;
import org.eclipse.epp.mpc.core.model.ICategory;
import org.eclipse.epp.mpc.core.model.IIdentifiable;
import org.eclipse.epp.mpc.core.model.IIus;
import org.eclipse.epp.mpc.core.model.IMarket;
import org.eclipse.epp.mpc.core.model.INews;
import org.eclipse.epp.mpc.core.model.INode;
import org.eclipse.epp.mpc.core.model.ISearchResult;
import org.eclipse.epp.mpc.core.payment.PaymentService;
import org.eclipse.epp.mpc.core.service.IMarketplaceService;
import org.eclipse.epp.mpc.core.service.IMarketplaceServiceLocator;
import org.eclipse.epp.mpc.ui.CatalogDescriptor;
import org.eclipse.epp.mpc.ui.MarketplaceUrlHandler;
import org.eclipse.equinox.internal.p2.discovery.AbstractDiscoveryStrategy;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogCategory;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.internal.p2.discovery.model.Icon;
import org.eclipse.equinox.internal.p2.discovery.model.Overview;
import org.eclipse.equinox.internal.p2.discovery.model.Tag;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.osgi.util.NLS;

/**
 * @author David Green
 */
public class MarketplaceDiscoveryStrategy extends AbstractDiscoveryStrategy {

	private static final String DOT_FEATURE_DOT_GROUP = ".feature.group"; //$NON-NLS-1$

	private static final Pattern BREAK_PATTERN = Pattern.compile("<!--\\s*break\\s*-->"); //$NON-NLS-1$

	protected final CatalogDescriptor catalogDescriptor;

	private final IMarketplaceService marketplaceService;

	private final PaymentServiceImpl paymentService;

	private MarketplaceCatalogSource source;

	private MarketplaceInfo marketplaceInfo;

	private Map<String, IInstallableUnit> featureIUById;

	public MarketplaceDiscoveryStrategy(CatalogDescriptor catalogDescriptor) {
		if (catalogDescriptor == null) {
			throw new IllegalArgumentException();
		}
		this.catalogDescriptor = catalogDescriptor;
		marketplaceService = createMarketplaceService();//use deprecated method in case someone has overridden it
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

	/**
	 * @deprecated get a marketplace service from the registered {@link IMarketplaceServiceLocator} OSGi service instead
	 */
	@Deprecated
	public IMarketplaceService createMarketplaceService() {
		return acquireMarketplaceService();
	}

	protected IMarketplaceService acquireMarketplaceService() {
		String baseUrl = this.catalogDescriptor.getUrl().toExternalForm();
		return ServiceLocator.getCompatibilityLocator().getMarketplaceService(baseUrl);
	}

	/**
	 * @deprecated moved to {@link ServiceLocator#computeDefaultRequestMetaParameters()}
	 */
	@Deprecated
	public static Map<String, String> computeDefaultRequestMetaParameters() {
		return ServiceLocator.computeDefaultRequestMetaParameters();
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

			ISearchResult featured = marketplaceService.featured(new SubProgressMonitor(monitor, workSegment));
			handleSearchResult(catalogCategory, featured, new SubProgressMonitor(monitor, workSegment));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	protected void handleSearchResult(MarketplaceCategory catalogCategory, ISearchResult result,
			final IProgressMonitor monitor) {
		if (!result.getNodes().isEmpty()) {
			int totalWork = 10000000;
			monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_loadingResources, totalWork);
			ConcurrentTaskManager executor = new ConcurrentTaskManager(result.getNodes().size(),
					Messages.MarketplaceDiscoveryStrategy_loadingResources);
			try {
				final MultiStatus errors = new MultiStatus(MarketplaceClientCore.BUNDLE_ID, 0,
						Messages.MarketplaceDiscoveryStrategy_Error_during_query, null);
				for (final INode node : result.getNodes()) {
					try {
						final MarketplaceNodeCatalogItem catalogItem = new MarketplaceNodeCatalogItem();
						catalogItem.setMarketplaceUrl(catalogDescriptor.getUrl());
						catalogItem.setId(node.getId());
						catalogItem.setName(getCatalogItemName(node));
						catalogItem.setCategoryId(catalogCategory.getId());
						ICategories categories = node.getCategories();
						if (categories != null) {
							for (ICategory category : categories.getCategory()) {
								catalogItem.addTag(new Tag(ICategory.class, category.getId(), category.getName()));
							}
						}
						catalogItem.setData(node);
						catalogItem.setSource(source);
						catalogItem.setLicense(node.getLicense());
						IIus ius = node.getIus();
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
								URLUtil.toURL(updateurl);
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
									executor.submit(new AbstractResourceRunnable(monitor, catalogItem,
											source.getResourceProvider(), node.getScreenshot()) {
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
							if (!source.getResourceProvider().containsResource(node.getImage())) {
								executor.submit(new AbstractResourceRunnable(monitor, catalogItem,
										source.getResourceProvider(), node.getImage()) {
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
								node));
					} catch (RuntimeException ex) {
						MarketplaceClientUi.error(
								NLS.bind(Messages.MarketplaceDiscoveryStrategy_ParseError,
										node == null ? "null" : node.getId()), ex); //$NON-NLS-1$
					}
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

	private String getCatalogItemName(INode node) {
		String name = node.getName();
		String version = node.getVersion();
		return version == null || version.length() == 0 ? name : NLS.bind(
				Messages.MarketplaceDiscoveryStrategy_Name_and_Version, name, version);
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

	private void createIcon(CatalogItem catalogItem, final INode node) {
		Icon icon = new Icon();
		// don't know the size
		icon.setImage32(node.getImage());
		icon.setImage48(node.getImage());
		icon.setImage64(node.getImage());
		catalogItem.setIcon(icon);
	}

	public void performQuery(IMarket market, ICategory category, String queryText, IProgressMonitor monitor)
			throws CoreException {
		final int totalWork = 1000000;
		SubMonitor progress = SubMonitor.convert(monitor, Messages.MarketplaceDiscoveryStrategy_searchingMarketplace,
				totalWork);
		try {
			ISearchResult result;
			MarketplaceCategory catalogCategory = findMarketplaceCategory(progress.newChild(1));
			catalogCategory.setContents(Contents.QUERY);

			try {
				//check if the query matches a node url and just retrieve that node
				result = performNodeQuery(queryText, progress.newChild(totalWork / 2));
			} catch (CoreException ex) {
				// node not found, continue with regular query
				result = null;
			}

			if (result == null) {
				//regular query

				//resolve market and category if necessary
				IMarket resolvedMarket;
				ICategory resolvedCategory;
				try {
					resolvedMarket = resolve(market, catalogCategory.getMarkets());
					resolvedCategory = resolveCategory(category, catalogCategory.getMarkets());
				} catch (IllegalArgumentException ex) {
					throw new CoreException(MarketplaceClientUi.computeStatus(ex, Messages.MarketplaceDiscoveryStrategy_invalidFilter));
				} catch (NoSuchElementException ex) {
					throw new CoreException(MarketplaceClientUi.computeStatus(ex, Messages.MarketplaceDiscoveryStrategy_unknownFilter));
				}

				progress.setWorkRemaining(totalWork);
				result = marketplaceService.search(resolvedMarket, resolvedCategory, queryText,
						progress.newChild(totalWork / 2));
			}

			handleSearchResult(catalogCategory, result, progress.newChild(totalWork / 2));
			if (result.getNodes().isEmpty()) {
				catalogCategory.setMatchCount(0);
				addCatalogItem(catalogCategory);
			}
		} finally {
			progress.done();
		}
	}

	private ICategory resolveCategory(ICategory category, List<? extends IMarket> markets)
			throws IllegalArgumentException, NoSuchElementException {
		if (category != null && category.getId() == null) {
			//need to resolve
			if (category.getUrl() == null && category.getName() == null) {
				throw new IllegalArgumentException(NLS.bind(Messages.MarketplaceDiscoveryStrategy_unidentifiableItem,
						category));
			}
			for (IMarket market : markets) {
				List<? extends ICategory> categories = market.getCategory();
				ICategory resolved = resolve(category, categories);
				if (resolved != null) {
					return resolved;
				}
			}
			if (category.getUrl() != null) {
				throw new NoSuchElementException(NLS.bind(Messages.MarketplaceDiscoveryStrategy_noUrlMatch,
						category.getUrl()));
			} else {
				throw new NoSuchElementException(NLS.bind(Messages.MarketplaceDiscoveryStrategy_noNameMatch,
						category.getName()));
			}
		}
		return category;
	}

	private <T extends IIdentifiable> T resolve(T id, List<? extends T> candidates) throws IllegalArgumentException,
	NoSuchElementException {
		if (id != null && id.getId() == null) {
			//need to resolve
			if (id.getUrl() == null && id.getName() == null) {
				throw new IllegalArgumentException(NLS.bind(
						Messages.MarketplaceDiscoveryStrategy_unidentifiableItem, id));
			}
			for (T candidate : candidates) {
				if (Identifiable.matches(candidate, id)) {
					return candidate;
				}
			}
			if (id.getUrl() != null) {
				throw new NoSuchElementException(NLS.bind(Messages.MarketplaceDiscoveryStrategy_noUrlMatch, id.getUrl()));
			} else {
				throw new NoSuchElementException(NLS.bind(Messages.MarketplaceDiscoveryStrategy_noNameMatch, id.getName()));
			}
		}
		return id;
	}

	private ISearchResult performNodeQuery(String nodeUrl, IProgressMonitor progress) throws CoreException {
		final INode[] queryNode = new INode[1];
		MarketplaceUrlHandler urlHandler = new MarketplaceUrlHandler() {
			@Override
			protected boolean handleNode(CatalogDescriptor descriptor, String url, INode node) {
				queryNode[0] = node;
				return true;
			}
		};
		if (urlHandler.handleUri(nodeUrl) && queryNode[0] != null) {
			INode node = marketplaceService.getNode(queryNode[0], progress);
			SearchResult result = new SearchResult();
			result.setMatchCount(1);
			result.setNodes(Collections.singletonList((Node) node));
			return result;
		}
		return null;
	}

	public void recent(IProgressMonitor monitor) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.RECENT);
			ISearchResult result = marketplaceService.recent(new SubProgressMonitor(monitor, totalWork / 2));
			handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	public void featured(IProgressMonitor monitor, final IMarket market, final ICategory category) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.FEATURED);
			ISearchResult result = marketplaceService.featured(market, category,
					new SubProgressMonitor(monitor, totalWork / 2));
			handleSearchResult(catalogCategory, result, new SubProgressMonitor(monitor, totalWork / 2));
			maybeAddCatalogItem(catalogCategory);
		} finally {
			monitor.done();
		}
	}

	public void popular(IProgressMonitor monitor) throws CoreException {
		final int totalWork = 1000000;
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_searchingMarketplace, totalWork);
		try {
			MarketplaceCategory catalogCategory = findMarketplaceCategory(new SubProgressMonitor(monitor, 1));
			catalogCategory.setContents(Contents.POPULAR);
			ISearchResult result = marketplaceService.popular(new SubProgressMonitor(monitor, totalWork / 2));
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
				Set<INode> catalogNodes = marketplaceInfo.computeInstalledNodes(catalogDescriptor.getUrl(),
						installedFeatures);
				if (!catalogNodes.isEmpty()) {
					int unitWork = totalWork / (2 * catalogNodes.size());
					for (INode node : catalogNodes) {
						node = marketplaceService.getNode(node, monitor);
						result.getNodes().add((Node) node);
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
		Set<INode> nodes = new HashSet<INode>();
		for (String nodeId : nodeIds) {
			Node node = new Node();
			node.setId(nodeId);
			nodes.add(node);
		}
		performNodeQuery(monitor, nodes);
	}

	public void performNodeQuery(IProgressMonitor monitor, Set<? extends INode> nodes) throws CoreException {
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
					for (INode node : nodes) {
						node = marketplaceService.getNode(node, monitor);
						result.getNodes().add((Node) node);
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
				List<? extends IMarket> markets = marketplaceService.listMarkets(new SubProgressMonitor(monitor, 10000));

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

	public INews performNewsDiscovery(IProgressMonitor monitor) throws CoreException {
		return marketplaceService.news(monitor);
	}

	public void installErrorReport(IProgressMonitor monitor, IStatus result, Set<CatalogItem> items,
			IInstallableUnit[] operationIUs, String resolutionDetails) throws CoreException {
		monitor.beginTask(Messages.MarketplaceDiscoveryStrategy_sendingErrorNotification, 100);
		try {
			Set<Node> nodes = new HashSet<Node>();
			for (CatalogItem item : items) {
				Object data = item.getData();
				if (data instanceof INode) {
					nodes.add((Node) data);
				}
			}
			Set<String> iuIdsAndVersions = new HashSet<String>();
			for (IInstallableUnit iu : operationIUs) {
				String id = iu.getId();
				String version = iu.getVersion() == null ? null : iu.getVersion().toString();
				iuIdsAndVersions.add(id + "," + version); //$NON-NLS-1$
			}
			marketplaceService.reportInstallError(result, nodes, iuIdsAndVersions, resolutionDetails, monitor);
		} finally {
			monitor.done();
		}
	}

	public PaymentService getPaymentService() {
		return paymentService;
	}
}
