package edu.stanford.holyc.host;

public class HostNetworkConfig{
	/** WiFi attributes we need */
	public static String wifiMACaddr = "";
	public static String wifiIPaddr = "";
	public static String wifiGWMAC = "";
	public static String wifiGWIP = "";
	
	/** Virtual Interface (veth1) attributes we need */
	public static String vethMac = "";
	public static String vethIP = "192.168.0.2";
	public static String vethIPMask = "255.255.255.0";
	
	/** 3G Interface (rmnet0) attributes we need */
	public static String mobileMACaddr = "";
	public static String mobileIPaddr = "";
	public static String mobileGWMAC = "";
	public static String mobileGWIP = "";
}