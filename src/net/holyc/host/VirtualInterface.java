package net.holyc.host;

/**
* The class to control virtual interface 
*
* @author Te-Yuan Huang (huangty@stanford.edu)
*/

public class VirtualInterface extends HostInterface {
	private boolean enabled = false;
	private boolean hasIP = false;
	public VirtualInterface(String name) {		
		setName(name);
	}
			
	@Override
	public String searchName() {
		return getName();
	}

	@Override
	public void setInterfaceEnable(boolean enable) {
		if(enable){
	   		Utility.runRootCommand("/data/local/bin/busybox ifconfig " + getName() + " up ", false);
		}else{
	   		Utility.runRootCommand("/data/local/bin/busybox ifconfig " + getName() + " down ", false);
		}
		enabled = true;
	}

	@Override
	public boolean getInterfaceEnable(){
		return enabled;
	}	
	
	@Override
	public String getIP(){
		if(hasIP){
			return super.getIP();
		}
		return "";		
	}
	
	public void setIP(String ip, String mask){
   		Utility.runRootCommand("/data/local/bin/busybox ifconfig " + getName() + " " + ip + " netmask " + mask + " mtu 1400", false);
		hasIP = true;
		setIP(ip);		
	}
	
	@Override
	public void setMac(String mac){
   		Utility.runRootCommand("/data/local/bin/busybox ifconfig " + getName() + " down ", false);
   		Utility.runRootCommand("/data/local/bin/busybox ifconfig " + getName() + " hw ether " + mac, false);
   		Utility.runRootCommand("/data/local/bin/busybox ifconfig " + getName() + " up ", false);
		super.setMac(mac);		
	}	
		
}