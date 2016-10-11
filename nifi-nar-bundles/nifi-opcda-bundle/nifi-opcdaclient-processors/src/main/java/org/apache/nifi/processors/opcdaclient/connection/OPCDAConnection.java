package org.apache.nifi.processors.opcdaclient.connection;

import java.util.UUID;

public class OPCDAConnection {
	
	private String id;
	
	private String server;
	
	private String domain;
	
	private String username;
	
	private String password;
	
	private String classId;
	
	private String readTimeout;
	
	private String pollRepeat;
	
	private String async;
	
	public OPCDAConnection() {
		this.id = UUID.randomUUID().toString();
	}
	
	public String getId() {
		return id;
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getClassId() {
		return classId;
	}

	public void setClassId(String classId) {
		this.classId = classId;
	}

	public String getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(String readTimeout) {
		this.readTimeout = readTimeout;
	}

	public String getPollRepeat() {
		return pollRepeat;
	}

	public void setPollRepeat(String pollRepeat) {
		this.pollRepeat = pollRepeat;
	}

	public String getAsync() {
		return async;
	}

	public void setAsync(String async) {
		this.async = async;
	}

}
