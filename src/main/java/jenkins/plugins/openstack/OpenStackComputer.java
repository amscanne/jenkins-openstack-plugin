package jenkins.plugins.openstack;

import java.util.concurrent.Future;

import hudson.slaves.AbstractCloudComputer;

public class OpenStackComputer extends AbstractCloudComputer<OpenStackSlave> {

	public final OpenStackSlave slave;
	
	public OpenStackComputer(OpenStackSlave slave) {
        super(slave);
        this.slave = slave;
    }

	@Override
	public OpenStackSlave getNode() {
		return this.slave;
	}

	@Override
	public Boolean isUnix() {
		return slave.isUnix();
	}
	
	@Override
    protected Future<?> _connect(boolean forceReconnect) {
    	setNode(slave);
    	return super._connect(forceReconnect);
    }
}