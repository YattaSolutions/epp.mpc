/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	  The Eclipse Foundation - initial API and implementation
 *    Yatta Solutions - category filtering (bug 314936), error handling (bug 374105),
 *                      multiselect hints (bug 337774)
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.ui.commands;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.epp.internal.mpc.core.ServiceLocator;
import org.eclipse.epp.internal.mpc.core.service.Catalog;
import org.eclipse.epp.internal.mpc.core.service.CatalogService;
import org.eclipse.epp.internal.mpc.core.service.Category;
import org.eclipse.epp.internal.mpc.core.service.Market;
import org.eclipse.epp.internal.mpc.ui.CatalogRegistry;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.epp.internal.mpc.ui.catalog.MarketplaceCatalog;
import org.eclipse.epp.internal.mpc.ui.catalog.MarketplaceCategory;
import org.eclipse.epp.internal.mpc.ui.wizards.AbstractTagFilter;
import org.eclipse.epp.internal.mpc.ui.wizards.ComboTagFilter;
import org.eclipse.epp.internal.mpc.ui.wizards.MarketplaceCatalogConfiguration;
import org.eclipse.epp.internal.mpc.ui.wizards.MarketplaceFilter;
import org.eclipse.epp.internal.mpc.ui.wizards.MarketplaceWizard;
import org.eclipse.epp.internal.mpc.ui.wizards.MarketplaceWizardDialog;
import org.eclipse.epp.internal.mpc.ui.wizards.Operation;
import org.eclipse.epp.mpc.ui.CatalogDescriptor;
import org.eclipse.equinox.internal.p2.discovery.DiscoveryCore;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogCategory;
import org.eclipse.equinox.internal.p2.discovery.model.Tag;
import org.eclipse.equinox.internal.p2.ui.discovery.util.WorkbenchUtil;
import org.eclipse.equinox.internal.p2.ui.discovery.wizards.CatalogFilter;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * @author David Green
 * @author Carsten Reckord
 */
public class MarketplaceWizardCommand extends AbstractHandler implements IHandler {

	private List<CatalogDescriptor> catalogDescriptors;

	private CatalogDescriptor selectedCatalogDescriptor;

	private String wizardState;

	private String wizardPage;

	private Map<String, Operation> operationByNodeId;

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final MarketplaceCatalog catalog = new MarketplaceCatalog();

		catalog.setEnvironment(DiscoveryCore.createEnvironment());
		catalog.setVerifyUpdateSiteAvailability(false);

		MarketplaceCatalogConfiguration configuration = new MarketplaceCatalogConfiguration();
		configuration.setVerifyUpdateSiteAvailability(false);
		configuration.setInitialPage(wizardPage);

		if (catalogDescriptors == null || catalogDescriptors.isEmpty()) {
			final IStatus remoteCatalogStatus = installRemoteCatalogs();
			configuration.getCatalogDescriptors().addAll(CatalogRegistry.getInstance().getCatalogDescriptors());
			if (configuration.getCatalogDescriptors().isEmpty()) {
				// doesn't make much sense to continue without catalogs.
				// nothing will work and no way to recover later
				IStatus cause;
				if (!remoteCatalogStatus.isOK()) {
					cause = remoteCatalogStatus;
				} else {
					cause = new Status(IStatus.ERROR, MarketplaceClientUi.BUNDLE_ID,
							Messages.MarketplaceWizardCommand_noRemoteCatalogs);
				}
				IStatus exitStatus = new Status(IStatus.ERROR, MarketplaceClientUi.BUNDLE_ID, cause.getCode(),
						Messages.MarketplaceWizardCommand_cannotOpenMarketplace, new CoreException(cause));
				StatusManager.getManager().handle(exitStatus,
						StatusManager.SHOW | StatusManager.BLOCK | StatusManager.LOG);
				return null;
			} else if (!remoteCatalogStatus.isOK()) {
				StatusManager.getManager().handle(remoteCatalogStatus, StatusManager.LOG);
			}
		} else {
			configuration.getCatalogDescriptors().addAll(catalogDescriptors);
		}
		if (selectedCatalogDescriptor != null) {
			if (selectedCatalogDescriptor.getLabel().equals("org.eclipse.epp.mpc.descriptorHint")) { //$NON-NLS-1$
				CatalogDescriptor resolvedDescriptor = CatalogRegistry.getInstance().findCatalogDescriptor(
						selectedCatalogDescriptor.getUrl().toExternalForm());
				if (resolvedDescriptor == null) {
					IStatus status = new Status(IStatus.ERROR, MarketplaceClientUi.BUNDLE_ID,
							Messages.MarketplaceWizardCommand_CouldNotFindMarketplaceForSolution, new ExecutionException(
									selectedCatalogDescriptor.getUrl().toExternalForm()));
					StatusManager.getManager().handle(status,
							StatusManager.SHOW | StatusManager.BLOCK | StatusManager.LOG);
					return null;
				} else {
					configuration.setCatalogDescriptor(resolvedDescriptor);
				}
			} else {
				configuration.setCatalogDescriptor(selectedCatalogDescriptor);
			}
		}

		configuration.getFilters().clear();

		final ComboTagFilter marketFilter = new ComboTagFilter() {
			@Override
			public void catalogUpdated(boolean wasCancelled) {
				List<Tag> choices = new ArrayList<Tag>();
				for (CatalogCategory category : catalog.getCategories()) {
					if (category instanceof MarketplaceCategory) {
						MarketplaceCategory marketplaceCategory = (MarketplaceCategory) category;
						for (Market market : marketplaceCategory.getMarkets()) {
							Tag marketTag = new Tag(Market.class, market.getId(), market.getName());
							marketTag.setData(market);
							choices.add(marketTag);
						}
					}
				}
				setChoices(choices);
			}
		};
		marketFilter.setSelectAllOnNoSelection(true);
		marketFilter.setNoSelectionLabel(Messages.MarketplaceWizardCommand_allMarkets);
		marketFilter.setTagClassification(Category.class);
		marketFilter.setChoices(new ArrayList<Tag>());

		final ComboTagFilter marketCategoryTagFilter = new ComboTagFilter() {
			@Override
			public void catalogUpdated(boolean wasCancelled) {
				updateCategoryChoices(this, marketFilter);
			}
		};
		marketCategoryTagFilter.setSelectAllOnNoSelection(true);
		marketCategoryTagFilter.setNoSelectionLabel(Messages.MarketplaceWizardCommand_allCategories);
		marketCategoryTagFilter.setTagClassification(Category.class);
		marketCategoryTagFilter.setChoices(new ArrayList<Tag>());

		final IPropertyChangeListener marketListener = new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				final String property = event.getProperty();
				if (AbstractTagFilter.PROP_SELECTED.equals(property)) {
					updateCategoryChoices(marketCategoryTagFilter, marketFilter);
				}
			}
		};
		marketFilter.addPropertyChangeListener(marketListener);

		configuration.getFilters().add(marketFilter);
		configuration.getFilters().add(marketCategoryTagFilter);
		configuration.setInitialState(wizardState);
		if (operationByNodeId != null && !operationByNodeId.isEmpty()) {
			configuration.setInitialOperationByNodeId(operationByNodeId);
		}

		for (CatalogFilter filter : configuration.getFilters()) {
			((MarketplaceFilter) filter).setCatalog(catalog);
		}

		MarketplaceWizard wizard = new MarketplaceWizard(catalog, configuration);

		wizard.setWindowTitle(Messages.MarketplaceWizardCommand_eclipseMarketplace);

		WizardDialog dialog = new MarketplaceWizardDialog(WorkbenchUtil.getShell(), wizard);
		dialog.open();

		return null;
	}

	private void updateCategoryChoices(final ComboTagFilter marketCategoryTagFilter, final ComboTagFilter marketFilter) {
		Set<Tag> newChoices = new HashSet<Tag>();
		List<Tag> choices = new ArrayList<Tag>();

		Set<Market> selectedMarkets = new HashSet<Market>();
		for (Tag marketTag : marketFilter.getSelected()) {
			selectedMarkets.add((Market) marketTag.getData());
		}

		final MarketplaceCatalog catalog = (MarketplaceCatalog) marketCategoryTagFilter.getCatalog();
		for (CatalogCategory category : catalog.getCategories()) {
			if (category instanceof MarketplaceCategory) {
				MarketplaceCategory marketplaceCategory = (MarketplaceCategory) category;
				for (Market market : marketplaceCategory.getMarkets()) {
					if (selectedMarkets.isEmpty() || selectedMarkets.contains(market)) {
						for (Category marketCategory : market.getCategory()) {
							Tag categoryTag = new Tag(Category.class, marketCategory.getId(), marketCategory.getName());
							categoryTag.setData(marketCategory);
							if (newChoices.add(categoryTag)) {
								choices.add(categoryTag);
							}
						}
					}
				}
			}
		}
		Collections.sort(choices, new Comparator<Tag>() {
			public int compare(Tag o1, Tag o2) {
				return o1.getLabel().compareTo(o2.getLabel());
			}
		});
		marketCategoryTagFilter.setChoices(choices);
	}

	public void setCatalogDescriptors(List<CatalogDescriptor> catalogDescriptors) {
		this.catalogDescriptors = catalogDescriptors;
	}

	public void setSelectedCatalogDescriptor(CatalogDescriptor selectedCatalogDescriptor) {
		this.selectedCatalogDescriptor = selectedCatalogDescriptor;
	}

	public void setWizardState(String wizardState) {
		this.wizardState = wizardState;
	}

	public void setWizardPage(String wizardPage) {
		this.wizardPage = wizardPage;
	}

	public void setOperationByNodeId(Map<String, Operation> operationByNodeId) {
		this.operationByNodeId = operationByNodeId;
	}

	public IStatus installRemoteCatalogs() {
		try {
			final AtomicReference<List<Catalog>> result = new AtomicReference<List<Catalog>>();

			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						CatalogService catalogService = ServiceLocator.getInstance().getCatalogService();
						final List<Catalog> catalogs = catalogService.listCatalogs(monitor);
						result.set(catalogs);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			});

			List<Catalog> catalogs = result.get();
			for (Catalog catalog : catalogs) {
				CatalogDescriptor descriptor = new CatalogDescriptor();
				descriptor.setLabel(catalog.getName());
				descriptor.setUrl(new URL(catalog.getUrl()));
				descriptor.setIcon(ImageDescriptor.createFromURL(new URL(catalog.getImageUrl())));
				descriptor.setDescription(catalog.getDescription());
				descriptor.setInstallFromAllRepositories(!catalog.isSelfContained());
				if (catalog.getDependencyRepository() != null) {
					descriptor.setDependenciesRepository(new URL(catalog.getDependencyRepository()));
				}
				registerOrOverrideCatalog(descriptor);
				CatalogRegistry.getInstance().addCatalogBranding(descriptor, catalog.getBranding());
				if (catalog.getNews() != null) {
					CatalogRegistry.getInstance().addCatalogNews(descriptor, catalog.getNews());
				}
			}
		} catch (InterruptedException ie) {
			return Status.CANCEL_STATUS;
		} catch (Exception e) {
			IStatus status = MarketplaceClientUi.computeStatus(e,
					Messages.MarketplaceWizardCommand_CannotInstallRemoteLocations);
			return status;
		}
		return Status.OK_STATUS;
	}

	private void registerOrOverrideCatalog(CatalogDescriptor descriptor) {
		CatalogRegistry catalogRegistry = CatalogRegistry.getInstance();
		List<CatalogDescriptor> descriptors = catalogRegistry.getCatalogDescriptors();
		for (CatalogDescriptor catalogDescriptor : descriptors) {
			if (catalogDescriptor.getUrl().toExternalForm().equals(descriptor.getUrl().toExternalForm())) {
				catalogRegistry.unregister(catalogDescriptor);
			}
		}
		catalogRegistry.register(descriptor);
	}
}
