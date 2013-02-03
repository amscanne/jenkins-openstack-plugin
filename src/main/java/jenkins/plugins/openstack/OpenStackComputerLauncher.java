package jenkins.plugins.openstack;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

public class OpenStackComputerLauncher extends ComputerLauncher {
	
    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
		OpenStackComputer oscomputer = (OpenStackComputer)computer;
		try {
			oscomputer.getNode().launch(computer, listener);
		} catch (InterruptedException e) {
			listener.error(e.toString());
			e.printStackTrace();
		}
	}
}