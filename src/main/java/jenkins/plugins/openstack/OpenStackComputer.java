package jenkins.plugins.openstack;

import java.util.concurrent.Future;

import hudson.slaves.AbstractCloudComputer;

public class OpenStackComputer extends AbstractCloudComputer<OpenStackSlave> {

	private final OpenStackSlave slave;
	
	public OpenStackComputer(OpenStackSlave slave) {
        super(slave);
        this.slave = slave;
    }
	
    protected Future<?> _connect(boolean forceReconnect) {
    	setNode(slave);
    	return super._connect(forceReconnect);
    }
}