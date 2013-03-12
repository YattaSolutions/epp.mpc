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
package org.eclipse.epp.internal.mpc.core.payment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.text.MessageFormat;

import org.eclipse.core.internal.runtime.DevClassPathHelper;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCorePlugin;
import org.eclipse.osgi.internal.service.security.KeyStoreTrustEngine;
import org.eclipse.osgi.service.security.TrustEngine;
import org.eclipse.osgi.signedcontent.InvalidContentException;
import org.eclipse.osgi.signedcontent.SignedContent;
import org.eclipse.osgi.signedcontent.SignedContentEntry;
import org.eclipse.osgi.signedcontent.SignedContentFactory;
import org.eclipse.osgi.signedcontent.SignerInfo;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * @author Carsten Reckord
 */
@SuppressWarnings("restriction")
final class SecurityHelper {
	private static final String TRUST_STORE_PASS = "epp-mpc"; //$NON-NLS-1$

	private static final boolean ALLOW_DEV_MODE = true;

	private static final boolean DISABLE = true;

	private static interface SignedContentFactoryRunnable {
		void run(SignedContentFactory contentFactory) throws SecurityException;
	}

	private static final boolean inDeveloperMode = ALLOW_DEV_MODE && DevClassPathHelper.inDevelopmentMode();

	private static SecurityHelper instance;

	public static synchronized SecurityHelper getInstance() {
		Bundle contextBundle = MarketplaceClientCorePlugin.getBundle();
		if (instance != null) {
			if (instance.bundleContext != contextBundle.getBundleContext()) {
				instance = null;
			}
		}
		if (instance == null) {
			instance = create(contextBundle);
		}
		return instance;
	}

	private static SecurityHelper create(Bundle bundle) {
		//first establish some self-trust
		SecurityHelper selfHelper = new SecurityHelper(bundle.getBundleContext());
		selfHelper.verify(bundle, true);

		//now create the helper with our additional local trust store
		SecurityHelper trustedHelper = new SecurityHelper(bundle, "keystore.jks", TRUST_STORE_PASS.toCharArray()); //$NON-NLS-1$
		return trustedHelper;
	}

	private final TrustEngine trustEngine;

	private final BundleContext bundleContext;

	private SecurityHelper(BundleContext bundleContext) {
		this.bundleContext = bundleContext;
		this.trustEngine = null;
	}

	private SecurityHelper(Bundle bundle, String trustedStorePath, char[] pass) {
		this.bundleContext = bundle.getBundleContext();
		TrustEngine trustEngine;
		try {
			trustEngine = getTrustEngine(bundle, trustedStorePath, pass);
		} catch (IOException e) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_Unable_to_read_trust_store,
					bundleDescription(bundle),
					trustedStorePath));
		}
		this.trustEngine = trustEngine;
	}

	public void verify(String bundleId, final boolean fullContentCheck) throws SecurityException {
		if (DISABLE) {
			return;
		}
		final Bundle[] bundles = Platform.getBundles(bundleId, null);
		if (bundles == null || bundles.length == 0) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_bundle_not_found, bundleId));
		}
		run(new SignedContentFactoryRunnable() {

			public void run(SignedContentFactory contentFactory) {
				for (Bundle bundle : bundles) {
					doVerify(contentFactory, bundle, fullContentCheck);
				}
			}
		});
	}

	public void verify(final Bundle bundle, final boolean fullContentCheck) throws SecurityException {
		run(new SignedContentFactoryRunnable() {

			public void run(SignedContentFactory contentFactory) {
				doVerify(contentFactory, bundle, fullContentCheck);
			}
		});
	}

	private void doVerify(SignedContentFactory contentFactory, final Bundle bundle, final boolean fullContentCheck) {
		if (isWorkspaceBundle(bundle)) {
			return;
		}
		SignedContent content = getSignedContent(bundle, contentFactory);
		if (!content.isSigned()) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_bundle_not_signed, bundleDescription(bundle)));
		}
		SignerInfo[] signerInfos = content.getSignerInfos();
		SignerInfo[] trustedSigners = verifySigners(content, signerInfos);
		if (trustedSigners.length == 0) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_bundle_signer_not_trusted,
					bundleDescription(bundle)));
		}

		if (fullContentCheck) {
			SignedContentEntry[] signedEntries = content.getSignedEntries();
			for (SignedContentEntry entry : signedEntries) {
				verifyEntry(bundle, entry, trustedSigners == signerInfos ? null : trustedSigners);
			}
		}
	}

	public void verify(final Bundle bundle, final String path) throws SecurityException {
		if (isWorkspaceBundle(bundle)) {
			return;
		}
		run(new SignedContentFactoryRunnable() {

			public void run(SignedContentFactory contentFactory) {
				SignedContent signedContent = getSignedContent(bundle, contentFactory);
				SignedContentEntry signedEntry = signedContent.getSignedEntry(path);
				if (signedEntry == null) {
					throw new SecurityException(NLS.bind(Messages.SecurityHelper_bundleEntry_not_found,
							bundleDescription(bundle), path));
				}
				if (!signedEntry.isSigned()) {
					throw new SecurityException(NLS.bind(Messages.SecurityHelper_unsigned_content,
							bundleDescription(bundle), path));
				}
				SignerInfo[] trustedSigners = verifySigners(signedContent, signedEntry.getSignerInfos());
				if (trustedSigners.length == 0) {
					throw new SecurityException(NLS.bind(Messages.SecurityHelper_no_trusted_signature,
							bundleDescription(bundle), path));
				}
				try {
					signedEntry.verify();
				} catch (InvalidContentException e) {
					throw securityException(e);
				} catch (IOException e) {
					throw new SecurityException(MessageFormat.format(Messages.SecurityHelper_unable_to_verify,
							bundleDescription(bundle), path, e.getMessage(), e));
				}
			}
		});
	}

	private void run(SignedContentFactoryRunnable runnable) throws SecurityException {
		if (DISABLE) {
			return;
		}
		final ServiceReference<SignedContentFactory> factoryRef = bundleContext.getServiceReference(SignedContentFactory.class);
		if (factoryRef == null) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_service_reference_not_found,
					SignedContentFactory.class.getName()));
		}
		try {
			final SignedContentFactory contentFactory = bundleContext.getService(factoryRef);
			if (contentFactory == null) {
				throw new SecurityException(NLS.bind(Messages.SecurityHelper_service_not_found,
						SignedContentFactory.class.getName()));
			}
			runnable.run(contentFactory);
		} finally {
			bundleContext.ungetService(factoryRef);
		}
	}

	private void verifyEntry(Bundle bundle, SignedContentEntry entry, SignerInfo[] trustedSigners) {
		if (!entry.isSigned()) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_unsigned_content, bundleDescription(bundle),
					entry.getName()));
		}
		try {
			entry.verify();
		} catch (InvalidContentException e) {
			throw securityException(e);
		} catch (IOException e) {
			throw new SecurityException(MessageFormat.format(Messages.SecurityHelper_unable_to_verify,
					bundleDescription(bundle),
					entry.getName(), e.getMessage()), e);
		}

		if (trustedSigners != null) {
			boolean trusted = false;
			SignerInfo[] signerInfos = entry.getSignerInfos();
			outer: for (SignerInfo signerInfo : signerInfos) {
				for (SignerInfo trustedSigner : trustedSigners) {
					if (trustedSigner.equals(signerInfo)) {
						trusted = true;
						break outer;
					}
				}
			}
			if (!trusted) {
				throw new SecurityException(NLS.bind(Messages.SecurityHelper_no_trusted_signature,
						bundleDescription(bundle), entry.getName()));
			}
		}
	}

	private SignerInfo[] verifySigners(SignedContent content, SignerInfo[] signerInfos) {
		SignerInfo[] trustedSigners = trustEngine == null ? signerInfos : new SignerInfo[signerInfos.length];
		int trusted = 0;
		for (SignerInfo info : signerInfos) {
			if (content != null) {
				try {
					content.checkValidity(info);
				} catch (CertificateExpiredException e) {
					throw new SecurityException(e);
				} catch (CertificateNotYetValidException e) {
					throw new SecurityException(e);
				}
			}
			if (trustEngine != null) {
				try {
					Certificate anchor = trustEngine.findTrustAnchor(info.getCertificateChain());
					if (anchor != null) {
						trustedSigners[trusted++] = info;
					}
				} catch (IOException e) {
					//error while reading trust chain - don't trust and just go on...
				}
			} else {
				trusted++;
			}
		}
		if (trustedSigners.length < trusted) {
			SignerInfo[] trimmed = new SignerInfo[trusted];
			System.arraycopy(trustedSigners, 0, trimmed, 0, trusted);
			trustedSigners = trimmed;
		}
		return trustedSigners;
	}

	private static String bundleDescription(Bundle bundle) {
		return bundle.getBundleId() + "(" + bundle.getSymbolicName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isWorkspaceBundle(Bundle bundle) {
		if (inDeveloperMode) {
			String[] devClassPath = DevClassPathHelper.getDevClassPath(bundle.getSymbolicName());
			if (devClassPath != null && devClassPath.length > 0) {
				return true; // always enabled bundles from workspace; they never are signed
			}
		}
		return false;
	}

	private static SecurityException securityException(Exception e) {
		return (SecurityException) new SecurityException(e.getMessage()).initCause(e);
	}

	private static SignedContent getSignedContent(final Bundle bundle, SignedContentFactory contentFactory) {
		SignedContent content;
		try {
			content = contentFactory.getSignedContent(bundle);
		} catch (InvalidKeyException e) {
			throw securityException(e);
		} catch (SignatureException e) {
			throw securityException(e);
		} catch (CertificateException e) {
			throw securityException(e);
		} catch (NoSuchAlgorithmException e) {
			throw securityException(e);
		} catch (NoSuchProviderException e) {
			throw securityException(e);
		} catch (IOException e) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_unable_to_verify_bundle,
					bundleDescription(bundle), e.getMessage()), e);
		}
		return content;
	}

	private static TrustEngine getTrustEngine(Bundle bundle, String path, char[] pass) throws IOException {
		URL entryURL = bundle.getEntry(path);
		if (entryURL == null) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_trusted_keystore_missing,
					bundleDescription(bundle)));
		}
		File keystoreFile = fileFromBundle(bundle, path);
		URL resolvedURL = FileLocator.resolve(entryURL);
		URL fileURL = keystoreFile.toURI().toURL();
		RandomAccessFile raf = new RandomAccessFile(keystoreFile, "r"); //$NON-NLS-1$
		FileLock lock = null;
		try {
			FileChannel channel = raf.getChannel();
			lock = channel.tryLock(0, Integer.MAX_VALUE, true);
			if (lock == null) {
				throw new IOException(NLS.bind(Messages.SecurityHelper_error_locking_file,
						keystoreFile.getAbsolutePath()));
			}

			TrustEngine te = getKeyStoreTrustEngine(bundle, pass, keystoreFile);
			try {
				te.getAliases();//load the trust store from file
			} catch (GeneralSecurityException e) {
				throw new SecurityException(e);
			}

			if (!fileURL.equals(resolvedURL)) {
				//check file content integrity
				//bundle entry validity is assumed to already be established
				int size = (int) channel.size();
				ByteBuffer buf = ByteBuffer.allocate(size + 1);
				while (true) {
					int read = channel.read(buf);
					if (read == -1) {
						break;
					}
					if (buf.position() > size || (read == 0 && buf.position() == size)) {
						throw new IOException(Messages.SecurityHelper_unexpected_content);
					}
					//keep reading
				}
				byte[] fileBytes = buf.array();

				byte[] entryBytes = new byte[size + 1];
				InputStream entryStream = entryURL.openStream();
				int pos = 0;
				try {
					while (true) {
						int read = entryStream.read(entryBytes, pos, entryBytes.length - pos);
						if (read == -1) {
							break;
						}
						pos += read;
						if (pos > size || (read == 0 && pos == size)) {
							throw new IOException(Messages.SecurityHelper_unexpected_content);
						}
					}
				} finally {
					entryStream.close();
				}

				if (pos != size) {
					throw new SecurityException(NLS.bind(Messages.SecurityHelper_trust_store_tampered_size, path));
				}
				for (int i = 0; i < size; i++) {
					if (fileBytes[i] != entryBytes[i]) {
						throw new SecurityException(
								NLS.bind(Messages.SecurityHelper_trust_store_tampered_content, path));
					}
				}
			}

			return te;
		} finally {
			try {
				if (lock != null) {
					lock.release();
				}
			} finally {
				raf.close();
			}
		}
	}

	private static KeyStoreTrustEngine getKeyStoreTrustEngine(Bundle bundle, char[] pass, File keystoreFile) {
		Constructor<?>[] declaredConstructors = KeyStoreTrustEngine.class.getDeclaredConstructors();
		Constructor<?> keyStoreTrustEngineConstructor = declaredConstructors[0];

		try {
			KeyStoreTrustEngine keyStoreTrustEngine;
			if (keyStoreTrustEngineConstructor.getParameterTypes().length == 5) {
				//luna
				keyStoreTrustEngine = (KeyStoreTrustEngine) keyStoreTrustEngineConstructor.newInstance(
						keystoreFile.getAbsolutePath(), "JKS", pass, "PaymentTrustStore", null); //$NON-NLS-1$//$NON-NLS-2$
			} else if (keyStoreTrustEngineConstructor.getParameterTypes().length == 4) {
				//kepler
				keyStoreTrustEngine = (KeyStoreTrustEngine) keyStoreTrustEngineConstructor.newInstance(
						keystoreFile.getAbsolutePath(), "JKS", pass, "PaymentTrustStore"); //$NON-NLS-1$//$NON-NLS-2$
			} else {
				throw new SecurityException(NLS.bind(Messages.SecurityHelper_unable_to_verify_bundle,
						bundleDescription(bundle)));
			}
			return keyStoreTrustEngine;
		} catch (Exception e) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_unable_to_verify_bundle,
					bundleDescription(bundle), e.getMessage()), e);
		}
	}

	static File fileFromBundle(Bundle bundle, String path) throws IOException {
		URL bundleURL = bundle.getEntry(path);
		URL fileURL = FileLocator.toFileURL(bundleURL);
		File keystoreFile;
		try {
			// unfortunately FileLocator returns malformed URLs, see bugs.eclipse.org #145096 - thus don't use URL.toURI
			keystoreFile = new File(new URI(fileURL.getProtocol(), fileURL.getPath(), null));
		} catch (URISyntaxException e) {
			//can really happen, if path to bundle contains problematic chars
			throw new IllegalStateException(e);
		}
		if (!keystoreFile.exists()) {
			throw new SecurityException(NLS.bind(Messages.SecurityHelper_trusted_keystore_file_missing,
					bundleDescription(bundle), path));
		}
		return keystoreFile;
	}
}
