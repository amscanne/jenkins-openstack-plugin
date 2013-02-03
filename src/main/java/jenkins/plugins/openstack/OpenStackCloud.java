package jenkins.plugins.openstack;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.openstack.keystone.KeystoneClient;
import org.openstack.keystone.api.Authenticate;
import org.openstack.keystone.model.Access;
import org.openstack.keystone.model.Authentication;
import org.openstack.keystone.model.Authentication.PasswordCredentials;
import org.openstack.keystone.utils.KeystoneUtils;
import org.openstack.nova.NovaClient;
import org.openstack.nova.api.FlavorsCore;
import org.openstack.nova.api.ImagesCore;
import org.openstack.nova.api.ServersCore;
import org.openstack.nova.model.Flavor;
import org.openstack.nova.model.Flavors;
import org.openstack.nova.model.Image;
import org.openstack.nova.model.Images;
import org.openstack.nova.model.Server;
import org.openstack.nova.model.ServerForCreate;
import org.openstack.nova.model.ServerForCreate.SecurityGroup;
import org.openstack.nova.model.Servers;

public class OpenStackCloud extends AbstractCloudImpl {

	public final String id;
	public final String authUrl;
    public final String authUsername;
    public final String authPassword;
	public final String authTenant;
	public final String regionName;
    public final List<SlaveTemplate> templates;

    @DataBoundConstructor
    public OpenStackCloud(String id,
    		 			  String authUrl,
    		 			  String authUsername,
    		 			  String authPassword,
    		 			  String authTenant,
    		 			  String regionName,
    		 			  String instanceCapStr,
    		 			  List<SlaveTemplate> templates) {
        super(id, instanceCapStr);
        this.id = Util.fixEmptyAndTrim(id);
        this.authUrl = authUrl;
        this.authUsername = authUsername;
        this.authPassword = authPassword;
        this.authTenant = authTenant;
        this.regionName = regionName;
        
        if( templates == null )
        	templates = Collections.emptyList();
        this.templates = templates;
        readResolve();
    }

    public Object readResolve() {
        for( SlaveTemplate template : templates ) {
        	template.setParent(this);
        }
        return this;
    }
    
    protected static NovaClient connect(String authUrl,
    									String authUsername,
    									String authPassword,
    									String authTenant,
    									String regionName) {
    	KeystoneClient keystone = new KeystoneClient(authUrl);
    	Authentication authentication = new Authentication();
    	PasswordCredentials passwordCredentials = new PasswordCredentials();
    	passwordCredentials.setUsername(authUsername);
    	passwordCredentials.setPassword(authPassword);
    	authentication.setPasswordCredentials(passwordCredentials);
    	authentication.setTenantName(authTenant);   	
    	Access access = keystone.execute(new Authenticate(authentication));
    	
    	NovaClient novaClient = new NovaClient(
    								KeystoneUtils.findEndpointURL(
    										access.getServiceCatalog(),
    										"compute", null, "public"),
    								access.getToken().getId());
    	return novaClient;
    }

	public NovaClient connect() {
		return connect(authUrl, authUsername, authPassword, authTenant, regionName);
	}
	
	public static String getFlavorRef(NovaClient client, String flavorId) {
		Flavors flavors = client.execute(FlavorsCore.listFlavors());
    	for( Flavor flavor : flavors ) {
    		if( flavor.getId().equals(flavorId) || flavor.getName().equals(flavorId) ) {
    			return flavor.getId();
    		}
    	}
    	return null;
	}

	public static String getImageRef(NovaClient client, String imageId) {
		Images images = client.execute(ImagesCore.listImages());
    	for( Image image : images ) {
    		if( image.getId().equals(imageId) || image.getName().equals(imageId) ) {
    			return image.getId();
    		}
    	}
    	return null;
	}
		
	public static Server boot(NovaClient client, SlaveTemplate slave) {
		ServerForCreate serverForCreate = new ServerForCreate();
		serverForCreate.setName(slave.id);
		serverForCreate.setImageRef(getImageRef(client, slave.imageId));
		serverForCreate.setFlavorRef(getFlavorRef(client, slave.flavorId));
		if( slave.keyName.length() > 0 )
			serverForCreate.setKeyName(slave.keyName);
		if( slave.securityGroups != null )
			for( String securityGroup : slave.securityGroups )
				if( securityGroup.length() > 0 ) 
					serverForCreate.getSecurityGroups().add(new SecurityGroup(securityGroup));
		
		Server server = client.execute(ServersCore.createServer(serverForCreate));
		return server;
	}
	
    public List<SlaveTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public SlaveTemplate getTemplate(Label label) {
        for( SlaveTemplate t : templates )
        	if( t.matches(label) )
        		return t;
        return null;
    }

    public SlaveTemplate getTemplate(String id) {
        for( SlaveTemplate t : templates )
        	if( t.id.equals(id) )
        		return t;
        return null;    	
    }
    
    public static String getStackTrace(Throwable t) {
    	Writer writer = new StringWriter();
    	PrintWriter printer = new PrintWriter(writer);
    	t.printStackTrace(printer);
    	return writer.toString();
    }
    
    public void doAttach(StaplerRequest req,
    					 StaplerResponse rsp,
    					 @QueryParameter String serverId)
    							 throws ServletException, IOException, FormException {
        checkPermission(PROVISION);
        NovaClient client = connect();
        Server server = client.execute(ServersCore.showServer(serverId));
        SlaveTemplate t = getTemplate(server.getName());
        if( t == null )
        	rsp.sendError(404, "Template not found");
 
        try {
        	OpenStackSlave node = t.attach(server);
        	Hudson.getInstance().addNode(node);
        	rsp.sendRedirect2(req.getContextPath() + "/computer/" + node.getNodeName());
        } catch( Exception e ) {
        	rsp.sendError(500, getStackTrace(e));
        }
    }

    public void doProvision(StaplerRequest req,
    						StaplerResponse rsp,
    						@QueryParameter String id)
    								throws ServletException, IOException, FormException {
        checkPermission(PROVISION);
        SlaveTemplate t = getTemplate(id);
        if( t == null )
        	rsp.sendError(404, "Template not found");

        try {
        	OpenStackSlave node = t.provision();
        	Hudson.getInstance().addNode(node);
        	rsp.sendRedirect2(req.getContextPath()+ "/computer/" + node.getNodeName());
        } catch( Exception e ) {
        	rsp.sendError(500, getStackTrace(e));
        }
    }

    @Override
	public Collection<PlannedNode> provision(Label label, int workload) {
        final SlaveTemplate t = getTemplate(label);
        List<PlannedNode> r = new ArrayList<PlannedNode>();
        NovaClient client = connect();
        
        while( workload > 0 ) {
        	Servers servers = client.execute(ServersCore.listServers());
        	if( servers.getList().size() >= getInstanceCap() ) {
            	break;
            }
        	
            r.add(new PlannedNode(t.id,
                    Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            OpenStackSlave s = t.provision();
                            Hudson.getInstance().addNode(s);
                            s.toComputer().connect(false).get();
                            return s;
                        }
                    })
                    , t.getNumExecutors()));
            workload--;
        }
        
        return r;
    }

    @Override
	public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

	public static OpenStackCloud get(String id) {
		for (Cloud cloud : Hudson.getInstance().clouds) {
			if( cloud instanceof OpenStackCloud ) {
				OpenStackCloud openstackCloud = (OpenStackCloud) cloud;
				if( id == null )
					return openstackCloud;
				String cloudName = openstackCloud.id;
				if( id.equals(cloudName) )
					return openstackCloud;
			}
		}
		return null;
	}

	@Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        public FormValidation doTestConnection(
                @QueryParameter String authUrl,
                @QueryParameter String authUsername,
                @QueryParameter String authPassword,
                @QueryParameter String authTenant,
                @QueryParameter String regionName) throws IOException, ServletException {
        	if( authUrl.isEmpty() )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidAuthUrl());
        	if( authUsername.isEmpty() )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidAuthUsername());
        	if( authPassword.isEmpty() )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidAuthPassword());
        	if( authTenant.isEmpty() )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidAuthTenantName());
        	if( regionName.isEmpty() )
        		return FormValidation.error(Messages.OpenStackCloud_InvalidRegionName());
        	try {
        		connect(authUrl, authUsername, authPassword, authTenant, regionName);
        	} catch( Exception e ) {
        		return FormValidation.error(e, Messages.OpenStackCloud_ConnectionError());
        	}
        	return FormValidation.ok(Messages.OpenStackCloud_Success());
        }

        @Override
		public String getDisplayName() {
            return "OpenStack";
        }
    }
}
