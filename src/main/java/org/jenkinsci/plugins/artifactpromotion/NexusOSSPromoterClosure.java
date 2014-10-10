package org.jenkinsci.plugins.artifactpromotion;

import hudson.model.BuildListener;
import hudson.util.Secret;

import java.util.Map;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jenkinsci.plugins.artifactpromotion.exception.PromotionException;

public class NexusOSSPromoterClosure extends AbstractNexusOSSPromoterClosure {
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * @param localRepositoryURL
	 * @param listener
	 * @param expandedTokens
	 * @param releaseUser
	 * @param releasePassword
	 * @param stagingUser
	 * @param stagingPassword
	 */
	public NexusOSSPromoterClosure(
			BuildListener listener,
			String localRepositoryURL,
			Map<PromotionBuildTokens, String> expandedTokens,
			String releaseUser, Secret releasePassword,
			String stagingUser, Secret stagingPassword) {
		super(listener, localRepositoryURL,expandedTokens,releaseUser, releasePassword,stagingUser, stagingPassword);
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.artifactpromotion.IPromotorClosure#promote()
	 */
	public void promote() throws PromotionException {
		
		this.listener.getLogger().println("Started with promotion");
		
		AetherInteraction aether = new AetherInteraction(this.listener);
		RepositorySystem system = aether.getNewRepositorySystem();
		RepositorySystemSession session = aether.getRepositorySystemSession(
				system, localRepositoryURL);
		
		RemoteRepository stagingRepository = 
				aether.getRepository(stagingUser, 
						  stagingPassword, 
						  "stagingrepo",
						  this.expandedTokens
									.get(PromotionBuildTokens.STAGING_REPOSITORY));
								
		ArtifactWrapper artifact = getArtifact(aether, system, session,
				stagingRepository);
		if (artifact == null) {
			throw new PromotionException(
					"Could not fetch artifacts for promotion");
		}

		// upload the artifact and its pom to the release repos
		DeployResult result = deployPromotionArtifact(aether, system, session,
				artifact);
		if (result == null) {
			throw new PromotionException(
					"Could not deploy artifacts to release repository");
		}

		deleteArtifact(stagingRepository, artifact);
	}

	private ArtifactWrapper getArtifact(AetherInteraction aether,
			RepositorySystem system, RepositorySystemSession session,
			RemoteRepository stagingRepo) {

		this.listener.getLogger().println("Get Artifact and corresponding POM");
		Artifact artifact = null;
		Artifact pom = null;
		try {
			artifact = aether.getArtifact(session, system, stagingRepo,
					this.expandedTokens.get(PromotionBuildTokens.GROUP_ID),
					this.expandedTokens.get(PromotionBuildTokens.ARTIFACT_ID),
					this.expandedTokens.get(PromotionBuildTokens.EXTENSION),
					this.expandedTokens.get(PromotionBuildTokens.VERSION));
			pom = aether.getArtifact(session, system, stagingRepo,
					this.expandedTokens.get(PromotionBuildTokens.GROUP_ID),
					this.expandedTokens.get(PromotionBuildTokens.ARTIFACT_ID),
					ArtifactPromotionBuilder.POMTYPE,
					this.expandedTokens.get(PromotionBuildTokens.VERSION));
		} catch (ArtifactResolutionException e) {
			this.listener.getLogger().println(
					"Could not resolve artifact: " + e.getMessage());
			return null;
		}

		return new ArtifactWrapper(artifact, pom);
	}

}
