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
package org.eclipse.epp.internal.mpc.ui.payment.discovery;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.epp.internal.mpc.core.payment.discovery.PaymentDiscoveryService;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.internal.p2.ui.IProvHelpContextIds;
import org.eclipse.equinox.internal.p2.ui.ProvUI;
import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.internal.p2.ui.dialogs.ProvisioningWizardDialog;
import org.eclipse.equinox.internal.p2.ui.discovery.operations.DiscoveryInstallOperation;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.InstallOperation;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.RemediationOperation;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.equinox.p2.ui.Policy;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.statushandlers.StatusManager;

public class PaymentDiscoveryInstallOperation extends DiscoveryInstallOperation {

	/**
	 * Copy of {@link org.eclipse.equinox.internal.p2.engine.EngineActivator.PROP_UNSIGNED_POLICY}
	 */
	private static final String PROP_UNSIGNED_POLICY = "eclipse.p2.unsignedPolicy"; //$NON-NLS-1$

	private final List<CatalogItem> installableConnectors;

	private ProvisioningUI provisioningUI;

	private IStatus status;

	public PaymentDiscoveryInstallOperation(List<CatalogItem> installableConnectors) {
		super(installableConnectors);
		this.installableConnectors = installableConnectors;
	}

	@Override
	public void run(IProgressMonitor progressMonitor) throws InvocationTargetException, InterruptedException {
		try {
			SubMonitor monitor = SubMonitor.convert(
					progressMonitor,
					org.eclipse.equinox.internal.p2.ui.discovery.wizards.Messages.InstallConnectorsJob_task_configuring,
					250);
			try {
				final IInstallableUnit[] ius = computeInstallableUnits(monitor.newChild(50));

				checkCancelled(monitor);

				int restartPolicy = getRestartPolicy(ius);
				provisioningUI = getProvisioningUI(restartPolicy);

				Set<URI> repositoryLocations = new HashSet<URI>();
				for (CatalogItem descriptor : installableConnectors) {
					URI uri = new URL(descriptor.getSiteUrl()).toURI();
					repositoryLocations.add(uri);
				}

				final InstallOperation installOperation = resolve(monitor.newChild(50), ius,
						repositoryLocations.toArray(new URI[0]));

				checkCancelled(monitor);

				RemediationOperation remediationOperation = null;
				if (installOperation.getResolutionResult().getSeverity() > IStatus.WARNING) {
					monitor.setTaskName(ProvUIMessages.ProvisioningOperationWizard_Remediation_Operation);
					remediationOperation = new RemediationOperation(
							provisioningUI.getSession(), installOperation.getProfileChangeRequest());
					remediationOperation.resolveModal(monitor.newChild(50));
				} else {
					monitor.setWorkRemaining(100);
				}

				ProvisioningJob job = openInstallWizard(provisioningUI, Arrays.asList(ius), installOperation,
						remediationOperation);

				checkCancelled(monitor);

				runInstallJob(provisioningUI, job, restartPolicy, monitor.newChild(100));

			} finally {
				monitor.done();
			}
			status = Status.OK_STATUS;
		} catch (OperationCanceledException e) {
			status = Status.CANCEL_STATUS;
			throw new InterruptedException();
		} catch (Exception e) {
			throw new InvocationTargetException(e);
		}
	}

	public IStatus getStatus() {
		return status;
	}

	private ProvisioningJob openInstallWizard(final ProvisioningUI provisioningUI, List<IInstallableUnit> ius,
			InstallOperation installOperation, RemediationOperation remediationOperation) {
		final PaymentDiscoveryInstallWizard wizard = new PaymentDiscoveryInstallWizard(provisioningUI,
				installOperation, ius, null);
		if (remediationOperation != null) {
			wizard.setRemediationOperation(remediationOperation);
		}

		final int[] action = new int[1];
		final Runnable displayRunnable = new Runnable() {

			public void run() {
				WizardDialog dialog = new ProvisioningWizardDialog(ProvUI.getDefaultParentShell(), wizard);
				dialog.create();
				PlatformUI.getWorkbench()
				.getHelpSystem()
				.setHelp(dialog.getShell(), IProvHelpContextIds.INSTALL_WIZARD);
				dialog.setBlockOnOpen(true);
				action[0] = dialog.open();
			}
		};

		PlatformUI.getWorkbench().getDisplay().syncExec(displayRunnable);
		if (action[0] == Window.CANCEL) {
			throw new OperationCanceledException();
		}

		return wizard.getProvisioningJob();
	}

	private void runInstallJob(ProvisioningUI provisioningUI, ProvisioningJob job, int restartPolicy,
			IProgressMonitor monitor)
					throws CoreException {
		monitor = SubMonitor.convert(monitor, Messages.PaymentDiscoveryInstallOperation_Installing_connectors, 100);
		final String originalPolicy = System.getProperty(PROP_UNSIGNED_POLICY);
		try {
			System.setProperty(PROP_UNSIGNED_POLICY, "fail");//$NON-NLS-1$
			IStatus result = job.runModal(monitor);
			if (result.getSeverity() > IStatus.WARNING) {
				if (result.getMessage().contains("CheckTrust")) { //$NON-NLS-1$
					MultiStatus clientStatus = new MultiStatus(
							MarketplaceClientUi.BUNDLE_ID,
							0,
							Messages.PaymentDiscoveryInstallOperation_Error_missing_signature,
							null);
					if (result.getChildren().length == 2) {
						clientStatus.add(result.getChildren()[1]);
					} else {
						clientStatus.addAll(result);
					}
					throw new CoreException(clientStatus);
				}
				throw new CoreException(result);
			} else if (result.getSeverity() > IStatus.INFO) {
				StatusManager.getManager().handle(result, StatusManager.LOG);
			}

			checkCancelled(monitor);

			Job triggerJob = new Job(Messages.PaymentDiscoveryInstallOperation_Applying_changes) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					return Status.OK_STATUS;
				}
			};
			triggerJob.setUser(true);
			provisioningUI.manageJob(triggerJob, restartPolicy);
			triggerJob.schedule();
			triggerJob.join();
		} catch (InterruptedException e) {
			throw new OperationCanceledException();
		} finally {
			if (originalPolicy == null) {
				System.getProperties().remove(PROP_UNSIGNED_POLICY);
			} else {
				System.setProperty(PROP_UNSIGNED_POLICY, originalPolicy);
			}
		}
	}

	private int getRestartPolicy(IInstallableUnit[] ius) {
		boolean requireRestart = PaymentDiscoveryService.requireRestart(ius);
		//we need to use RESTART_OR_APPLY here, because RESTART_NONE doesn't apply the change in the running profile...
		return requireRestart ? ProvisioningJob.RESTART_ONLY : ProvisioningJob.RESTART_OR_APPLY;
	}

	private ProvisioningUI getProvisioningUI(int restartPolicy) {
		ProvisioningUI defaultUi = ProvisioningUI.getDefaultUI();
		Policy policy = defaultUi.getPolicy();
		int defaultRestartPolicy = policy.getRestartPolicy();
		int provisioningRestartPolicy = defaultRestartPolicy;
		switch (restartPolicy) {
		case ProvisioningJob.RESTART_NONE:
			provisioningRestartPolicy = Policy.RESTART_POLICY_FORCE_APPLY;
			break;
		case ProvisioningJob.RESTART_OR_APPLY:
			//use FORCE_APPLY here, since RESTART_OR_APPLY is actually supposed to be "APPLY_ONLY"
			//provisioningRestartPolicy = Policy.RESTART_POLICY_PROMPT_RESTART_OR_APPLY;
			provisioningRestartPolicy = Policy.RESTART_POLICY_FORCE_APPLY;
			break;
		case ProvisioningJob.RESTART_ONLY:
		default:
			provisioningRestartPolicy = Policy.RESTART_POLICY_PROMPT;
			break;
		}

		ProvisioningUI ui = defaultUi;
		if (defaultRestartPolicy != provisioningRestartPolicy) {
			policy = copy(policy);
			policy.setRestartPolicy(provisioningRestartPolicy);
			ui = new ProvisioningUI(defaultUi.getSession(), defaultUi.getProfileId(), policy);
		}
		return ui;
	}

	private void checkCancelled(IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	private Policy copy(Policy policy) {
		Policy copy = new Policy();

		copy.setVisibleAvailableIUQuery(policy.getVisibleAvailableIUQuery());
		copy.setVisibleInstalledIUQuery(policy.getVisibleInstalledIUQuery());
		copy.setRestartPolicy(policy.getRestartPolicy());
		copy.setRepositoriesVisible(policy.getRepositoriesVisible());
		copy.setShowLatestVersionsOnly(policy.getShowLatestVersionsOnly());
		copy.setShowDrilldownRequirements(policy.getShowDrilldownRequirements());
		copy.setFilterOnEnv(policy.getFilterOnEnv());
		copy.setGroupByCategory(policy.getGroupByCategory());
		copy.setRepositoryPreferencePageId(policy.getRepositoryPreferencePageId());
		copy.setRepositoryPreferencePageName(policy.getRepositoryPreferencePageName());
		copy.setUpdateWizardStyle(policy.getUpdateWizardStyle());
		copy.setUpdateDetailsPreferredSize(policy.getUpdateDetailsPreferredSize());
		copy.setContactAllSites(policy.getContactAllSites());
		copy.setHideAlreadyInstalled(policy.getHideAlreadyInstalled());

		return copy;
	}

	private InstallOperation resolve(SubMonitor monitor, final IInstallableUnit[] ius, URI[] repositories)
			throws CoreException {
		InstallOperation installOperation = provisioningUI.getInstallOperation(Arrays.asList(ius), repositories);
		installOperation.resolveModal(monitor.newChild(40));
		if (installOperation.getResolutionResult().getSeverity() > IStatus.WARNING) {
			//try again with all known repositories
			RepositoryTracker repositoryTracker = provisioningUI.getRepositoryTracker();
			URI[] knownRepositories = repositoryTracker.getKnownRepositories(provisioningUI.getSession());
			Set<URI> allRepositories = new HashSet<URI>();
			allRepositories.addAll(Arrays.asList(repositories));
			if (allRepositories.addAll(Arrays.asList(knownRepositories))) {
				monitor.setWorkRemaining(50);
				installOperation = provisioningUI.getInstallOperation(Arrays.asList(ius),
						allRepositories.toArray(new URI[allRepositories.size()]));
				installOperation.resolveModal(monitor.newChild(50));
			}
		}
		monitor.setWorkRemaining(0);
		return installOperation;
	}
}
