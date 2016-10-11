package org.apache.nifi.processors.opcdaclient.util;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.nifi.logging.ComponentLog;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.AddFailedException;
import org.openscada.opc.lib.da.DuplicateGroupException;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.ItemState;
import org.openscada.opc.lib.da.Server;
import org.openscada.opc.lib.da.UnknownGroupException;
import org.openscada.opc.lib.da.browser.Branch;
import org.openscada.opc.lib.da.browser.Leaf;
import org.openscada.opc.lib.da.browser.TreeBrowser;

public class OPCInitialTagConfig {
	private static OPCInitialTagConfig instance = null;
	public static final String NO_SUBGROUP_MSG = "Unable to find sub-group: %s %nPossible Matches:%s";
	public static final String ERROR_CODE = "errorCode";
	public static final String QUALITY = "quality";
	public static final String TIMESTAMP = "timestamp";
	public static final String VALUE = "value";

	public static OPCInitialTagConfig getInstance() {
		if (instance == null) {
			synchronized (OPCInitialTagConfig.class) {
				instance = new OPCInitialTagConfig();
			}
		}
		return instance;
	}

	public synchronized String fetchTagState(Map<String, Item> opcTags, ComponentLog logger) throws JIException {
		Map<String, Map<String, Object>> data = new TreeMap<String, Map<String, Object>>();
		TimeZone timeZone = TimeZone.getTimeZone("UTC");
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MMM dd HH:mm:ss zzz yyyy", Locale.US);
		simpleDateFormat.setTimeZone(timeZone);

		// logger.info("*****looking for latest data with
		// opcTags-group-{}-\n{}",new
		// Object[]{flowfile.getAttribute("groupName"),opcTags});

		StringBuffer output = new StringBuffer();
		for (String key : opcTags.keySet()) {
			Item item = opcTags.get(key);
			// not serializable directly
			ItemState is = item.read(false);
			final Map<String, Object> itemStateAsMap = getItemStateAsMap(is);
			data.put(key, itemStateAsMap);
			// 1 file per group not per tag
			output.append(key + "-" + opcTags.size() + "," + itemStateAsMap.get(VALUE) + ","
					+ simpleDateFormat.format(itemStateAsMap.get(TIMESTAMP)) + "," + itemStateAsMap.get(QUALITY) + ","
					+ itemStateAsMap.get(ERROR_CODE) + "\n");
		}
		return output.toString();
	}

	public static Map<String, Object> getItemStateAsMap(ItemState is) throws JIException {
		Map<String, Object> retVal = new TreeMap<String, Object>();
		retVal.put(ERROR_CODE, Integer.valueOf(is.getErrorCode()));
		retVal.put(QUALITY, Short.valueOf(is.getQuality()));
		retVal.put(TIMESTAMP, is.getTimestamp().getTime());
		retVal.put(VALUE, JIVariantMarshaller.toJavaType(is.getValue()));
		// retVal.put("value2", JIVariantMarshaller.dumpValue(is.getValue()));

		return retVal;
	}

	public synchronized Map<String, Item> fetchSpecificTagsMap(Server server, String groupName,
			Map<String, Item> opcItems, List<String> specificItemIds, ComponentLog logger) throws Exception {
		Group opcGroup = null;
		// Map<String, Item> opcItems = new HashMap<String, Item>();
		List<String> itemIds = new ArrayList<String>();
		try {

			if (groupName != null && !groupName.isEmpty()) {
				try {
					opcGroup = server.addGroup(groupName);
				} catch (DuplicateGroupException ex) {
					// return opcItems;
					logger.warn("Duplicate Group {}" + groupName);
					opcGroup = server.findGroup(groupName);
				}
			}

			if (specificItemIds != null) {
				if (opcGroup != null) {
					for (String specificItemId : specificItemIds) {
						itemIds.add(specificItemId);
						try {
							Item i = opcGroup.addItem(specificItemId);
							opcItems.put(specificItemId, i);
						} catch (AddFailedException e) {
							// may be item is invalid - skip for POC
							continue;
						}
					}
				}

			}

			return opcItems;

		} catch (Exception ex) {
			throw new Exception(ex.getMessage(), ex);
		}
	}

	public List<String> fetchAllTags(Server server, ComponentLog logger) throws Exception {

		List<String> itemIds = new ArrayList<String>();
		try {
			TreeBrowser treeBrowser = server.getTreeBrowser();
			Branch root = treeBrowser.browse();
			Branch parent = root;

			ArrayList<String> possibleBranches = new ArrayList<String>();
			StringBuilder possibleMatches = new StringBuilder();
			for (Branch b : parent.getBranches()) {
				possibleBranches.add(String.format("%n[B] %s", b.getName()));
			}
			Collections.sort(possibleBranches, String.CASE_INSENSITIVE_ORDER);
			ArrayList<String> possibleLeaves = new ArrayList<String>();
			for (Leaf l : parent.getLeaves()) {
				possibleLeaves.add(String.format("%n[T] %s", l.getName()));
			}
			Collections.sort(possibleLeaves, String.CASE_INSENSITIVE_ORDER);

			for (String s : possibleBranches) {
				possibleMatches.append(s);
			}
			for (String s : possibleLeaves) {
				possibleMatches.append(s);
			}

			populateItemsMapRecursive(parent, itemIds);

		} catch (Exception e) {
			logger.error(e.getMessage());
			throw e;
		}

		return itemIds;

	}

	public void populateItemsMapRecursive(Branch parent, List<String> itemIds) throws JIException, AddFailedException {
		for (Leaf l : parent.getLeaves()) {
			registerLeaf(l, itemIds);
		}
		for (Branch child : parent.getBranches()) {
			populateItemsMapRecursive(child, itemIds);
		}
	}

	private void registerLeaf(Leaf l, List<String> itemIds) throws JIException, AddFailedException {
		String itemId = l.getItemId();
		itemIds.add(itemId);
		// if (opcGroup != null) {
		// Item i = opcGroup.addItem(itemId);
		// }
	}

	public synchronized void unregisterGroup(Server server, String groupName, ComponentLog logger) throws JIException {
		Group opcGroup;
		try {
			logger.info("Trying to Unregister Group []", new Object[] { groupName });
			opcGroup = server.findGroup(groupName);

			if (opcGroup != null) {
				server.removeGroup(opcGroup, true);
			}
		} catch (IllegalArgumentException | UnknownHostException | UnknownGroupException | NotConnectedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
