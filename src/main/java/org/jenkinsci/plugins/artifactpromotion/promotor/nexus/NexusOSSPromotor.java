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
package org.jenkinsci.plugins.artifactpromotion.promotor.nexus;

import org.jenkinsci.plugins.artifactpromotion.AbstractPromotor;
import org.jenkinsci.plugins.artifactpromotion.promotor.AbstractPromotorDescription;
import org.jenkinsci.plugins.artifactpromotion.promotor.IPromotorClosure;
import org.jenkinsci.plugins.artifactpromotion.promotor.Promotor;

import hudson.Extension;
import hudson.model.Descriptor;

/**
 * Sonatype Nexus OSS specific {@link Promotor} implementation.
 * 
 * @author Timo "timii" Paananen
 * @author Halil-Cem Guersoy
 * 
 */
@Extension
public class NexusOSSPromotor extends AbstractPromotor {

	@SuppressWarnings("unchecked")
	public Descriptor<Promotor> getDescriptor() {
		return new AbstractPromotorDescription() {

			@Override
			public String getDisplayName() {
				return "Nexus OSS";
			}
		};
	}

	/**
	 * This method creates the Nexus OSS promoter which is encapsulated into a
	 * 'closure' to make this plugin run on slaves, too.
	 * 
	 * @see org.jenkinsci.plugins.artifactpromotion.promotor.Promotor#callPromotor(hudson.remoting.VirtualChannel)
	 * 
	 * @return NexusOSSPromoterClosure
	 */
	@Override
	protected IPromotorClosure getPromotor() {
		return new NexusOSSPromoterClosure(this.getListener(),
				this.getLocalRepositoryURL(), this.getExpandedTokens(), this.getStagingRepository(), this.getReleaseRepository(), this.getGeneratePom());
	}

}
