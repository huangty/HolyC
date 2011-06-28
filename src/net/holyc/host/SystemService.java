package net.holyc.host;

import java.util.HashMap;

public class SystemService {
	@SuppressWarnings("serial")
	static final HashMap<Integer, String> id2Service = new HashMap<Integer, String>() {
		{
			put(0, "root");
			put(1000, "system");
			put(1001, "radio");
			put(1002, "bluetooth");
			put(1003, "graphics");
			put(1004, "input");
			put(1005, "audio");
			put(1006, "camera");
			put(1007, "log");
			put(1008, "compass");
			put(1009, "mount");
			put(1010, "wifi");
			put(1014, "dhcp");
			put(1011, "adb");
			put(1012, "install");
			put(1013, "media");
			put(1025, "nfc");
			put(2000, "shell");
			put(2001, "cache");
			put(2002, "diag");
			put(3001, "net_bt_admin");
			put(3002, "net_bt");
			put(1015, "sdcard_rw");
			put(1016, "vpn");
			put(1017, "keystore");
			put(1018, "usb");
			put(1021, "gps");
			put(3003, "inet");
			put(3004, "net_raw");
			put(3005, "net_admin");
			put(9998, "misc");
			put(9999, "nobody");	
		}
	};

}