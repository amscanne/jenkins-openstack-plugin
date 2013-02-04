package jenkins.plugins.openstack;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.Set;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack.nova.NovaClient;
import org.openstack.nova.model.Server;

public class SlaveTemplate implements Describable<SlaveTemplate> {
	
	public final String id;
    public final String description;

    public final String imageId;
    public final String flavorId;
    public final String availabilityZone;
    public final String keyName;
    public final String securityGroupsStr;
    
    public final boolean isUnix;
    public final String remoteFS;
    public final String remoteUser;
    public final String remotePassword;
    public final String privateKey;
    public final String labels;
    public final String numExecutors;
    public final boolean stopOnTerminate;
    
    private transient OpenStackCloud parent;
    public transient String[] securityGroups;
    private transient Set<LabelAtom> labelSet;

    @DataBoundConstructor
    public SlaveTemplate(String id,
			 			 String description,
    					 String imageId,
    					 String flavorId,
    					 String keyName,
    					 String availabilityZone,
    					 String securityGroupsStr,
    					 boolean isUnix,
    					 String remoteFS,
    					 String remoteUser,
    					 String remotePassword,
    					 String privateKey,
    					 String labelString,
    					 String numExecutors,
    					 boolean stopOnTerminate) {
    	
    	this.id = id;
        this.description = description;

        this.imageId = imageId;
        this.flavorId = flavorId;
        this.keyName = keyName;
        this.availabilityZone = availabilityZone;
        this.securityGroupsStr = Util.fixNull(securityGroupsStr);
        
        this.isUnix = isUnix;
        this.remoteFS = Util.fixNull(remoteFS);
        this.remoteUser = (remoteUser == null || remoteUser.length() == 0) ? "root" : remoteUser;
        this.remotePassword = Util.fixEmpty(remotePassword);
        this.privateKey = Util.fixEmpty(privateKey);
        this.labels = Util.fixNull(labelString);
        this.numExecutors = numExecutors;
        this.stopOnTerminate = stopOnTerminate;
        
        readResolve();
    }
    
    public Object readResolve() {
        securityGroups = this.securityGroupsStr.split(" ");
        labelSet = Label.parse(this.labels);
        return this;
    }
    
    public OpenStackCloud getParent() {
        return parent;
    }
    
    void setParent(OpenStackCloud parent) {
    	this.parent = parent;
    }

	public boolean matches(Label label) {
        return label.matches(labelSet);
	}
	
    public int getNumExecutors() {
        try {
            return Integer.parseInt(numExecutors);
        } catch (NumberFormatException e) {
        	return 1;
        }
    }

    public OpenStackSlave provision() throws IOException, FormException, InterruptedException {
		NovaClient client = parent.connect();
		Server server = OpenStackCloud.boot(client, this);
		OpenStackSlave slave = new OpenStackSlave(this, parent, server);
    	slave.waitForActive();
    	return slave;
    }
    
    public OpenStackSlave attach(Server server) throws IOException, FormException, InterruptedException {
    	OpenStackSlave slave = new OpenStackSlave(this, parent, server);
    	slave.waitForActive();
    	return slave;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveTemplate> {

    	public FormValidation doValidate(
                @QueryParameter String authUrl,
                @QueryParameter String authUsername,
                @QueryParameter String authPassword,
                @QueryParameter String authTenant,
                @QueryParameter String regionName,
                @QueryParameter String imageId,
                @QueryParameter String flavorId,
                @QueryParameter String keyName,
                @QueryParameter String availabilityZone)
                		throws IOException, ServletException {
    		
    		NovaClient client = OpenStackCloud.connect(authUrl, authUsername, authPassword, authTenant, regionName);
    		String flavorRef = OpenStackCloud.getFlavorRef(client, flavorId);
    		String imageRef = OpenStackCloud.getImageRef(client, imageId);
    		
        	if( flavorRef == null )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidFlavor());
        	if( imageRef == null )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidImage());

        	return FormValidation.ok(Messages.OpenStackCloud_Success());
        }
    	
		@Override
		public String getDisplayName() {
			return "Slave template";
		}
    }

    public Descriptor<SlaveTemplate> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }
}