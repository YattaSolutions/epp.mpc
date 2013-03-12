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

import java.util.Collection;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.ui.dialogs.InstallWizardPage;
import org.eclipse.equinox.internal.p2.ui.dialogs.PreselectedIUInstallWizard;
import org.eclipse.equinox.internal.p2.ui.dialogs.ProvisioningOperationWizard;
import org.eclipse.equinox.internal.p2.ui.dialogs.ResolutionResultsWizardPage;
import org.eclipse.equinox.internal.p2.ui.model.ElementUtils;
import org.eclipse.equinox.internal.p2.ui.model.IUElementListRoot;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.InstallOperation;
import org.eclipse.equinox.p2.operations.ProfileChangeOperation;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.ui.AcceptLicensesWizardPage;
import org.eclipse.equinox.p2.ui.LoadMetadataRepositoryJob;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.wizard.IWizardPage;

public class PaymentDiscoveryInstallWizard extends PreselectedIUInstallWizard {

	private PaymentDiscoveryWizardInstallPage resolutionPage;

	public PaymentDiscoveryInstallWizard(ProvisioningUI ui, InstallOperation operation,
			Collection<IInstallableUnit> initialSelections, LoadMetadataRepositoryJob job) {
		super(ui, operation, initialSelections, job);
	}

	@Override
	protected ResolutionResultsWizardPage createResolutionPage() {
		resolutionPage = new PaymentDiscoveryWizardInstallPage(ui, this, root, operation);
		return resolutionPage;
	}

	public ProvisioningJob getProvisioningJob() {
		return resolutionPage == null ? null : resolutionPage.getProvisioningJob();
	}

	//Fix taken from MavenDiscoveryInstallWizard
	@Override
	protected void initializeResolutionModelElements(Object[] selectedElements) {
		super.initializeResolutionModelElements(selectedElements);
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=348660
		// PreselectedIUInstallWizard does not ask to approve licenses if original selection has not changed
		// give license page analyse preselected and do it's thing if necessary
		// TODO remove when Bug348660 is fixed in p2
		workaroundBug348660();
	}

	private void workaroundBug348660() {
		for (IWizardPage page : getPages()) {
			if (page instanceof AcceptLicensesWizardPage) {
				AcceptLicensesWizardPage licensePage = (AcceptLicensesWizardPage) page;
				licensePage.update(ElementUtils.elementsToIUs(planSelections).toArray(new IInstallableUnit[0]),
						operation);
			}
		}
	}

	public static final class PaymentDiscoveryWizardInstallPage extends InstallWizardPage {

		private ProfileChangeOperation resolvedOperation;

		private ProvisioningJob provisioningJob;

		public PaymentDiscoveryWizardInstallPage(ProvisioningUI ui, ProvisioningOperationWizard wizard,
				IUElementListRoot root, ProfileChangeOperation operation) {
			super(ui, wizard, root, operation);
			this.resolvedOperation = operation;
		}

		@Override
		public boolean performFinish() {
			if (resolvedOperation.getResolutionResult().getSeverity() != IStatus.ERROR) {
				this.provisioningJob = resolvedOperation.getProvisioningJob(null);
				return true;
			}
			return false;
		}

		@Override
		public void updateStatus(IUElementListRoot root, ProfileChangeOperation op) {
			super.updateStatus(root, op);
			this.resolvedOperation = op;
		}

		public ProvisioningJob getProvisioningJob() {
			return provisioningJob;
		}
	}

}