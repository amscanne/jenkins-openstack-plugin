package jenkins.plugins.openstack;

import java.io.IOException;

import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;

import org.kohsuke.stapler.DataBoundConstructor;

public class OpenStackRetentionStrategy extends RetentionStrategy<OpenStackComputer> {

	@DataBoundConstructor
    public OpenStackRetentionStrategy() {
    }

    @Override
	public synchronized long check(OpenStackComputer c) {
        if(  c.isIdle() ) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if( idleMilliseconds > TimeUnit2.MINUTES.toMillis(30) ) {
                try {
					c.getNode().terminate();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
        }
        return 1;
    }
}
