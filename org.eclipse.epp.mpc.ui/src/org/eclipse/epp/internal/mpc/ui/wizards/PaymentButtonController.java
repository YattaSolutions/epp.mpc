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

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Collections;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.epp.internal.mpc.core.payment.PaymentServiceImpl;
import org.eclipse.epp.internal.mpc.core.payment.discovery.model.PaymentDiscoveryModule;
import org.eclipse.epp.internal.mpc.ui.MarketplaceClientUi;
import org.eclipse.epp.internal.mpc.ui.catalog.MarketplaceNodeCatalogItem;
import org.eclipse.epp.internal.mpc.ui.payment.discovery.PaymentDiscoveryUI;
import org.eclipse.epp.mpc.core.payment.PaymentItem;
import org.eclipse.epp.mpc.core.payment.PaymentModule;
import org.eclipse.epp.mpc.core.payment.PaymentModuleListener;
import org.eclipse.epp.mpc.core.payment.PaymentService;
import org.eclipse.epp.mpc.core.payment.PaymentServiceListener;
import org.eclipse.epp.mpc.core.payment.PaymentTransaction;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.internal.p2.discovery.model.Icon;
import org.eclipse.equinox.internal.p2.ui.discovery.wizards.DiscoveryResources;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.statushandlers.StatusManager;

final class PaymentButtonController {

	private class UpdateListener implements PaymentServiceListener, PaymentModuleListener {

		public void paymentModuleRemoved(String moduleId) {
			runUpdatePaymentModuleJob();
		}

		public void paymentModuleAdded(String moduleId) {
			runUpdatePaymentModuleJob();
		}

		public void paymentItemChanged(PaymentItem item) {
			String nodeId = item.getNodeId();
			if (connector.getId().equals(nodeId)) {
				runUpdatePaymentItemJob(item.getPaymentModule());
			}
		}

		public void paymentTransactionCompleted(PaymentTransaction transaction) {
			// ignore
		}

		public void paymentTransactionCanceled(PaymentTransaction transaction) {
			// ignore
		}
	}

	private final PaymentService paymentService;

	private final DiscoveryItem<?> item;

	private final MarketplaceNodeCatalogItem connector;

	private final IRunnableContext runnableContext;

	private final Button button;

	private final IAdaptable contextAdapter;

	private final DiscoveryResources resources;

	private UpdateListener updateListener;

	private boolean runningDiscovery;

	PaymentButtonController(PaymentService paymentService, DiscoveryItem<?> item, DiscoveryResources resources,
			Button button,
			IRunnableContext runnableContext, IAdaptable contextAdapter) {
		this.paymentService = paymentService;
		this.item = item;
		this.resources = resources;
		this.button = button;
		this.runnableContext = runnableContext;
		this.contextAdapter = contextAdapter;

		this.connector = (MarketplaceNodeCatalogItem) item.getData();
		initialize();
	}

	private void initialize() {
		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				paymentButtonPressed();
			}
		});
		button.addDisposeListener(new DisposeListener() {

			public void widgetDisposed(DisposeEvent e) {
				dispose();
			}
		});
		updateListener = new UpdateListener();
		paymentService.addPaymentServiceListener(updateListener);
		PaymentModule paymentModule = getPaymentModule();
		if (paymentModule != null) {
			updatePaymentModule(null, paymentModule);
		}

		refresh();
	}

	private void updatePaymentModule(PaymentModule oldPaymentModule, PaymentModule newPaymentModule) {
		if (oldPaymentModule != null) {
			oldPaymentModule.removePaymentModuleListener(updateListener);
		}
		if (newPaymentModule != null) {
			newPaymentModule.addPaymentModuleListener(updateListener);
		}
	}

	void dispose() {
		if (updateListener != null) {
			paymentService.removePaymentServiceListener(updateListener);
			PaymentModule paymentModule = getPaymentModule();
			if (paymentModule != null) {
				paymentModule.removePaymentModuleListener(updateListener);
			}
		}
	}

	private void runUpdatePaymentModuleJob() {
		if (runningDiscovery) {
			return;
		}
		Job updateJob = new Job(Messages.PaymentButtonController_Updating_payment_module) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					PaymentModule oldPaymentModule = getPaymentModule();
					PaymentModule newPaymentModule = paymentService.getPaymentModule(connector.getId(), monitor);
					if (newPaymentModule != oldPaymentModule) {
						updatePaymentModule(oldPaymentModule, newPaymentModule);
						if (newPaymentModule != null) {
							connector.setDiscoveredPaymentConnector(null);
						}
						runUpdatePaymentItemJob(newPaymentModule);
					}
				} catch (CoreException e) {
					return e.getStatus();
				}
				return Status.OK_STATUS;
			}
		};
		updateJob.setPriority(Job.SHORT);
		updateJob.setSystem(true);
		updateJob.schedule();
	}

	private void runUpdatePaymentItemJob(final PaymentModule paymentModule) {
		Job updateJob = new Job(Messages.PaymentButtonController_Updating_payment_status) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return updatePaymentInfo(paymentModule, monitor);
			}
		};
		updateJob.setPriority(Job.SHORT);
		updateJob.setSystem(true);
		updateJob.schedule();
	}

	IStatus updatePaymentInfo(final PaymentModule paymentModule, IProgressMonitor monitor) {
		try {
			PaymentItem oldItem = connector.getPaymentItem();
			PaymentItem newItem = paymentModule.query(connector.getId(), monitor);
			if (newItem != oldItem && (newItem == null || !newItem.equals(oldItem))) {
				connector.setPaymentItem(newItem);
				if (!item.isDisposed()) {
					try {
						Display display = item.getDisplay();
						display.asyncExec(new Runnable() {
							public void run() {
								refresh();
							}
						});
					} catch (SWTException e) {
						if (e.code == SWT.ERROR_WIDGET_DISPOSED) {
							//ignore
						}
						throw e;
					}
				}
			}
		} catch (CoreException e) {
			return e.getStatus();
		}
		return Status.OK_STATUS;
	}

	PaymentModule getPaymentModule() {
		PaymentItem paymentItem = connector.getPaymentItem();
		return paymentItem == null ? null : paymentItem.getPaymentModule();
	}

	void refresh() {
		PaymentItem paymentItem = connector.getPaymentItem();
		PaymentModule paymentModule = paymentItem == null ? null : paymentItem.getPaymentModule();

		boolean enabled = true;
		if (paymentModule == null && connector.getDiscoveredPaymentConnector() == null) {
			enabled = false;
		} else {
			if (paymentItem != null && paymentItem.isOwned() && !paymentItem.isRefundable()
					&& !paymentItem.isCancelable()) {
				enabled = false;//looks like there's nothing left to do...
				if (paymentModule != null && paymentModule.canPurchase(paymentItem)) {
					enabled = true;//but we are able to buy again... (e.g. additional license volume...)
				}
			}
		}
		button.setEnabled(enabled);

		updateButtonLabel(paymentItem, paymentModule);
	}

	private void updateButtonLabel(PaymentItem paymentItem, PaymentModule paymentModule) {
		ILabelProvider labelProvider = null;
		String buttonText = null;
		Image buttonIcon = null;

		if (paymentItem != null) {
			buttonText = DefaultPaymentItemLabelProvider.getAlternativeActionLabel(paymentItem, paymentModule);
		}
		try {
			if (paymentModule instanceof IAdaptable) {
				IAdaptable adaptable = (IAdaptable) paymentModule;
				labelProvider = (ILabelProvider) adaptable.getAdapter(ILabelProvider.class);
				if (labelProvider != null) {
					String text = buttonText;
					if (text == null) {
						text = labelProvider.getText(paymentItem);//FIXME what if this returns null?
					}
					buttonIcon = labelProvider.getImage(paymentItem);

					//update the text too, now that we are sure this provider doesn't cause a problem
					buttonText = text;
				}
			} else if (connector.getDiscoveredPaymentConnector() != null) {
				PaymentDiscoveryModule data = (PaymentDiscoveryModule) connector.getDiscoveredPaymentConnector()
						.getData();
				String imageUrl = data.getImage();
				if (imageUrl != null) {
					Icon icon = new Icon();
					// don't know the size
					icon.setImage32(imageUrl);
					buttonIcon = resources.getIconImage(connector.getSource(), connector.getIcon(), 32, false);
				}
			}
		} catch (Exception ex) {
			StatusManager.getManager().handle(
					new Status(IStatus.ERROR, MarketplaceClientUi.BUNDLE_ID,
							Messages.PaymentButtonController_Error_updating_label, ex), StatusManager.LOG);
			labelProvider = null;
		}
		if (labelProvider == null || buttonText == null) {
			labelProvider = new DefaultPaymentItemLabelProvider();
			if (buttonText == null) {
				buttonText = labelProvider.getText(paymentItem);
			}
			if (buttonIcon == null) {
				buttonIcon = labelProvider.getImage(paymentItem);
			}
		}
		String oldText = button.getText();
		Image oldIcon = button.getImage();
		button.setText(buttonText);
		button.setImage(buttonIcon);
		if ((oldText == null && buttonText != null) || (oldText != null && !oldText.equals(buttonText))
				|| (oldIcon != buttonIcon)) {
			item.layout();
		}
	}

	void paymentButtonPressed() {
		CatalogItem discoveredPaymentConnector = connector.getDiscoveredPaymentConnector();
		if (discoveredPaymentConnector != null) {
			installDiscoveredConnector();
		} else {
			performPaymentAction();
		}
	}

	private void performPaymentAction() {
		PaymentItem paymentItem = connector.getPaymentItem();
		PaymentModule paymentModule = paymentItem == null ? null : paymentItem.getPaymentModule();
		if (paymentItem != null && paymentModule != null) {
			purchaseItem(paymentItem, paymentModule);
		}
	}

	private void purchaseItem(final PaymentItem paymentItem, final PaymentModule paymentModule) {
		paymentModule.purchase(paymentItem, null, contextAdapter);
	}

	private void installDiscoveredConnector() {
		CatalogItem discoveredPaymentConnector = connector.getDiscoveredPaymentConnector();
		int result = openAskInstallDialog(connector.getName(), discoveredPaymentConnector.getProvider());
		if (result != Window.OK) {
			return;
		}
		try {
			runningDiscovery = true;
			IStatus status = PaymentDiscoveryUI.install(Collections.singletonList(discoveredPaymentConnector),
					runnableContext);
			if (status.getSeverity() > IStatus.WARNING) {
				//failed, just return - error has already been handled...
				return;
			}
			//succeeded, clear discovery data
			connector.setDiscoveredPaymentConnector(null);
			runnableContext.run(true, true, new IRunnableWithProgress() {

				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						PaymentModule oldPaymentModule = getPaymentModule();
						((PaymentServiceImpl) paymentService).refresh();
						PaymentModule newPaymentModule = paymentService.getPaymentModule(connector.getId(), monitor);
						if (newPaymentModule != oldPaymentModule) {
							updatePaymentModule(oldPaymentModule, newPaymentModule);
							updatePaymentInfo(newPaymentModule, monitor);
						}
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			});

			//run the proper action after installing the connector
			performPaymentAction();
		} catch (InvocationTargetException e) {
			IStatus status = MarketplaceClientUi.computeStatus(e, Messages.PaymentButtonController_Failed_to_install);
			StatusManager.getManager().handle(status, StatusManager.LOG | StatusManager.BLOCK | StatusManager.SHOW);
		} catch (InterruptedException e) {
			//cancelled, just ignore
		} finally {
			runningDiscovery = false;
		}
	}

	private int openAskInstallDialog(final String purchasedItem, final String paymentProvider) {
		TitleAreaDialog dialog = new TitleAreaDialog(item.getShell()) {
			{
				setShellStyle(getShellStyle() | SWT.RESIZE);
			}

			@Override
			protected Control createDialogArea(Composite parent) {
				Composite container = (Composite) super.createDialogArea(parent);

				setTitle(Messages.PaymentButtonController_PaymentModule_required_Title);
				setMessage(Messages.PaymentButtonController_PaymentModule_required_shortDescription);

				//TODO preliminary dialog - waiting for final design
				StyledText label = new StyledText(container, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
				label.setEditable(false);
				label.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));

				String text = Messages.PaymentButtonController_PaymentModule_required_bodyText;
				text = MessageFormat.format(text, purchasedItem, paymentProvider);
				label.setText(text);
				return container;
			}
		};
		dialog.setBlockOnOpen(true);
		dialog.create();
		int result = dialog.open();
		return result;
	}
}
