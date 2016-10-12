package org.apache.nifi.processors.opcdaclient.connection;

import java.util.UUID;

import org.openscada.opc.lib.common.ConnectionInformation;

public class OPCDAConnection extends ConnectionInformation {
	
	private String id;
	
	public OPCDAConnection() {
		this.id = UUID.randomUUID().toString();
	}
	
	public String getId() {
		return id;
	}

}
