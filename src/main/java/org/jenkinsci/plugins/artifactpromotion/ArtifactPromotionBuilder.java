/**
 * The MIT License
 * Copyright (c) 2014 Halil-Cem Guersoy and all contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.artifactpromotion;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.jenkinsci.plugins.artifactpromotion.exception.PromotionException;
import org.jenkinsci.plugins.artifactpromotion.promotor.Promotor;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * In this class we encapsulate the process of moving an artifact from one
 * repository into another one.
 * 
 * @author Halil-Cem Guersoy (hcguersoy@gmail.com)
 * 
 */
public class ArtifactPromotionBuilder extends Builder {

	/**
	 * The POM extension.
	 */
	public static final String POMTYPE = "pom";

	private final String groupId;
	private final String artifactId;
	private final String version;
	private final String extension;

	/**
	 * The location of the local repository system relative to the workspace. In
	 * this repository the downloaded artifact will be saved.
	 */
	private final String localRepoLocation = "target" + File.separator + "local-repo";

	/**
	 * Promoter for staging.
	 */
	private final transient AbstractPromotor artifactPromoter;

	/**
	 * Name of the promoter class.
	 */
	private final String promoterClass;

	// Fields for UI

	/**
	 * Flag to write more info in the job console.
	 */
	private final boolean debug;

	private final boolean generatePom;
	
	/**
	 * The default constructor. The parameters are injected by jenkins builder
	 * and are the same as the (private) fields.
	 * 
	 * @param groupId
	 *            The groupId of the artifact
	 * @param artifactId
	 *            The artifactId of the artifact.
	 * @param version
	 *            The version of the artifact.
	 * @param extension
	 *            The file extension of the artifact.
	 * @param stagingRepository
	 *            The URL of the staging repository.
	 * @param stagingUser
	 *            User to be used on staging repo.
	 * @param stagingPW
	 *            Password to be used on staging repo.
	 * @param releaseUser
	 *            User to be used on release repo.
	 * @param releasePW
	 *            Password to be used on release repo.
	 * @param releaseRepository
	 *            The URL of the staging repository
	 * @param promoterClass
	 *            The vendor specific class which is used for the promotion,
	 *            e.g. for NexusOSS
	 * @param debug
	 *            Flag for debug output. Currently not used.
	 */
	@DataBoundConstructor
	public ArtifactPromotionBuilder(String groupId, String artifactId, String version, String extension,
			String stagingRepository, String stagingUser, Secret stagingPW, String releaseUser, Secret releasePW,
			String releaseRepository, String promoterClass, boolean debug, boolean generatePom) {
		
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.extension = extension == null ? "jar" : extension;
		this.generatePom = generatePom;

		this.debug = debug;
		this.promoterClass = promoterClass;
			
		try {
			this.artifactPromoter = (AbstractPromotor) Jenkins.getInstance().getExtensionList(this.promoterClass)
					.iterator().next();
			this.artifactPromoter.setReleaseRepository(new Repository(releaseUser, releasePW, releaseRepository, "releaseRepository"));
			this.artifactPromoter.setStagingRepository( new Repository(stagingUser, stagingPW, stagingRepository, "stagingRepository"));
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

		artifactPromoter.setListener(listener);
		if(this.debug) {
			listener.getLogger().println("---------------> "+ this.toString());
		}
		PrintStream logger = listener.getLogger();
		Map<PromotionBuildTokens, String> expandedTokens = expandTokens(build, listener);
		if (expandedTokens == null) {
			return false;
		}
		artifactPromoter.setExpandedTokens(expandedTokens);	
		artifactPromoter.setGeneratePom(generatePom);

		String localRepoPath = build.getWorkspace() + File.separator + this.localRepoLocation;
		artifactPromoter.setLocalRepositoryURL(localRepoPath);

		if (debug) {
			logger.println("Local repository path: [" + localRepoPath + "]");
		}

		try {
			artifactPromoter.callPromotor(launcher.getChannel());
		} catch (PromotionException e) {
			logger.println(e.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * Expands needed build tokens
	 * 
	 * @param build
	 * @param listener
	 * @return Map<PromotionBuildTokens, String> of expanded tokens
	 * @throws TokenExpansionException
	 *             if token expansion fails
	 */
	private Map<PromotionBuildTokens, String> expandTokens(AbstractBuild<?, ?> build, BuildListener listener) {
		PrintStream logger = listener.getLogger();
		Map<PromotionBuildTokens, String> tokens = new HashMap<PromotionBuildTokens, String>();
		try {
			tokens.put(PromotionBuildTokens.GROUP_ID, TokenMacro.expandAll(build, listener, groupId));
			tokens.put(PromotionBuildTokens.ARTIFACT_ID, TokenMacro.expandAll(build, listener, artifactId));
			tokens.put(PromotionBuildTokens.VERSION, TokenMacro.expandAll(build, listener, version));
			tokens.put(PromotionBuildTokens.EXTENSION, TokenMacro.expandAll(build, listener, extension));
		} catch (MacroEvaluationException mee) {
			logger.println("Could not evaluate a macro" + mee);
			return null;

		} catch (IOException ioe) {
			logger.println("Got an IOException during evaluation of a makro token" + ioe);
			return null;
		} catch (InterruptedException ie) {
			logger.println("Got an InterruptedException during avaluating a makro token" + ie);
			return null;
		}
		return tokens;
	}

	@Override
	public ArtifactPromotionDescriptorImpl getDescriptor() {
		return (ArtifactPromotionDescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link ArtifactPromotionBuilder}.
	 */
	@Extension
	public static final class ArtifactPromotionDescriptorImpl extends BuildStepDescriptor<Builder> {

		/**
		 * In order to load the persisted global configuration, you have to call
		 * load() in the constructor.
		 */
		public ArtifactPromotionDescriptorImpl() {
			load();
		}

		// Form validation

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckArtifactId(@QueryParameter String value) {
			if (value.length() == 0)
				return FormValidation.error("Please set an ArtifactId!");
			return FormValidation.ok();
		}

		public FormValidation doCheckGroupId(@QueryParameter String value) {
			if (value.length() == 0)
				return FormValidation.error("Please set a GroupId!");
			return FormValidation.ok();
		}

		public FormValidation doCheckVersion(@QueryParameter String value) {
			if (value.length() == 0)
				return FormValidation.error("Please set a Version for your artifact!");
			return FormValidation.ok();
		}

		public FormValidation doCheckStagingRepository(@QueryParameter String value) {
			return checkURI(value);
		}

		public FormValidation doCheckReleaseRepository(@QueryParameter String value) {
			return checkURI(value);
		}

		/**
		 * This method checks originally the URL if it is valid. On the way to
		 * support tokens this behavior is build out. It will be reactivated
		 * after a general refactoring for better token macro support.
		 * 
		 * TODO implment a URL validation which works with token macro plugin
		 * 
		 * @param value
		 * @return
		 */
		private FormValidation checkURI(String value) {
			if (value.length() == 0) {
				return FormValidation.error("Please set an URL for the staging repository!");
			}
			return FormValidation.ok();
		}

		// TODO connectivity tests

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Single Artifact Promotion";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			// useFrench = formData.getBoolean("useFrench");
			// ^Can also use req.bindJSON(this, formData);
			// (easier when there are many fields; need set* methods for this,
			// like setUseFrench)
			// save();
			return super.configure(req, formData);
		}

		/**
		 * Generates LisBoxModel for available Repository systems
		 * 
		 * @return available Promoters as ListBoxModel
		 */
		public ListBoxModel doFillPromoterClassItems() {
			ListBoxModel promoterModel = new ListBoxModel();
			for (Promotor promotor : Jenkins.getInstance().getExtensionList(Promotor.class)) {
				promoterModel.add(promotor.getDescriptor().getDisplayName(), promotor.getClass().getCanonicalName());
			}

			return promoterModel;
		}
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public String getExtension() {
		return extension;
	}

	public String getStagingRepository() {
		return this.artifactPromoter.getStagingRepository().getURL();
	}

	public String getStagingUser() {
		return this.artifactPromoter.getStagingRepository().getUsername();
	}

	public Secret getStagingPW() {
		return this.artifactPromoter.getStagingRepository().getPassword();
	}

	public String getReleaseUser() {
		return this.artifactPromoter.getReleaseRepository().getUsername();
	}

	public Secret getReleasePW() {
		return this.artifactPromoter.getReleaseRepository().getPassword();
	}

	public String getReleaseRepository() {
		return this.artifactPromoter.getReleaseRepository().getURL();
	}

	public boolean isDebug() {
		return debug;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this); 
	}
}
