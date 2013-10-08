package etri.sdn.controller.module.staticentrypusher;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.openflow.protocol.ver1_0.messages.OFAction;
import org.openflow.protocol.ver1_0.messages.OFActionOutput;
import org.openflow.protocol.ver1_0.messages.OFActionOpaqueEnqueue;
import org.openflow.protocol.ver1_0.messages.OFActionSetDlDst;
import org.openflow.protocol.ver1_0.messages.OFActionSetDlSrc;
import org.openflow.protocol.ver1_0.messages.OFActionSetNwDst;
import org.openflow.protocol.ver1_0.messages.OFActionSetNwSrc;
import org.openflow.protocol.ver1_0.messages.OFActionSetNwTos;
import org.openflow.protocol.ver1_0.messages.OFActionSetTpSrc;
import org.openflow.protocol.ver1_0.messages.OFActionSetTpDst;
import org.openflow.protocol.ver1_0.messages.OFActionSetVlanId;
import org.openflow.protocol.ver1_0.messages.OFActionSetVlanPcp;
import org.openflow.protocol.ver1_0.messages.OFActionStripVlan;
import org.openflow.protocol.ver1_0.messages.OFFlowMod;
import org.openflow.protocol.ver1_0.messages.OFMatch;
import org.openflow.protocol.ver1_0.types.OFFlowModCommand;
import org.openflow.protocol.ver1_0.types.OFPortNo;
import org.openflow.util.HexString;
import etri.sdn.controller.protocol.packet.IPv4;
import etri.sdn.controller.util.AppCookie;
import etri.sdn.controller.util.Logger;

/**
 * Represents static flow entries to be maintained by the controller on the 
 * switches. 
 */
public class StaticFlowEntryType {
    
	public static final int STATIC_FLOW_APP_ID = 10;

	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_SWITCH = "switch_id";
	public static final String COLUMN_ACTIVE = "active";
	public static final String COLUMN_IDLE_TIMEOUT = "idle_timeout";
	public static final String COLUMN_HARD_TIMEOUT = "hard_timeout";
	public static final String COLUMN_PRIORITY = "priority";
	public static final String COLUMN_COOKIE = "cookie";
	public static final String COLUMN_WILDCARD = "wildcards";
	public static final String COLUMN_IN_PORT = "in_port";
	public static final String COLUMN_DL_SRC = "dl_src";
	public static final String COLUMN_DL_DST = "dl_dst";
	public static final String COLUMN_DL_VLAN = "dl_vlan";
	public static final String COLUMN_DL_VLAN_PCP = "dl_vlan_pcp";
	public static final String COLUMN_DL_TYPE = "dl_type";
	public static final String COLUMN_NW_TOS = "nw_tos";
	public static final String COLUMN_NW_PROTO = "nw_proto";
	public static final String COLUMN_NW_SRC = "nw_src"; // includes CIDR-style netmask, e.g. "128.8.128.0/24")
	public static final String COLUMN_NW_DST = "nw_dst";
	public static final String COLUMN_TP_DST = "tp_dst";
	public static final String COLUMN_TP_SRC = "tp_src";
	public static final String COLUMN_ACTIONS = "actions";

	/**
	 * subaction class.
	 * Each subaction represents a command whose name is 'set-vlan-id', etc.
	 * 
	 * @author shkang
	 *
	 */
    private static class SubActionStruct {
		OFAction action;
		int len;
	}

    /**
     * represents a null(zero) mac.
     */
	private static byte[] zeroMac = new byte[] { 0x0, 0x0, 0x0, 0x0, 0x0, 0x0 };

	/**
	 * This function generates a random hash for the bottom half of the cookie
	 * 
	 * @param fm
	 * @param userCookie
	 * @param name
	 * @return A cookie that encodes the application ID and a hash
	 */
	public static long computeEntryCookie(OFFlowMod fm, int userCookie,
			String name) {
		// flow-specific hash is next 20 bits LOOK! who knows if this
		int prime = 211;
		int flowHash = 2311;
		for (int i = 0; i < name.length(); i++)
			flowHash = flowHash * prime + (int) name.charAt(i);

		return AppCookie.makeCookie(STATIC_FLOW_APP_ID, flowHash);
	}

    /**
     * Sets defaults for an OFFlowMod
     * 
     * @param fm The OFFlowMod to set defaults for
     * @param entryName The name of the entry. Used to compute the cookie.
     */
    public static void initDefaultFlowMod(OFFlowMod fm, String entryName) {
        fm.setIdleTimeout((short) 0);   // infinite
        fm.setHardTimeout((short) 0);   // infinite
        fm.setBufferId(0xffffffff /* OFPacketOut.BUFFER_ID_NONE */);
//        fm.setCommand((short) 0);
        fm.setCommand(OFFlowModCommand.valueOf((short)0));
        fm.setFlags((short) 0);
//        fm.setOutPort(OFPort.OFPP_NONE.getValue());
        fm.setOutPort(OFPortNo.OFPP_NONE.getValue());
        fm.setCookie(computeEntryCookie(fm, 0, entryName));  
        fm.setPriority(Short.MAX_VALUE);
    }
    
    /**
     * Gets the entry name of a flow mod
     * 
     * @param fmJson The OFFlowMod in a JSON representation
     * @return The name of the OFFlowMod, null if not found
     * @throws IOException If there was an error parsing the JSON
     */
    public static String getEntryNameFromJson(String fmJson) throws IOException{
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(fmJson);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            
            if (n == "name")
                return jp.getText();
        }
        
        return null;
    }
    
    /**
     * Parses an OFFlowMod (and it's inner OFMatch) to the storage entry format.
     * 
     * @param fm	The FlowMod to parse
     * @param sw	The switch the FlowMod is going to be installed on
     * @param name	The name of this static flow entry
     * @return 		A Map representation of the storage entry 
     */
    public static Map<String, Object> flowModToStorageEntry(OFFlowMod fm, String sw, String name) {
        Map<String, Object> entry = new HashMap<String, Object>();
        OFMatch match = fm.getMatch();
        entry.put(COLUMN_NAME, name);
        entry.put(COLUMN_SWITCH, sw);
        entry.put(COLUMN_ACTIVE, Boolean.toString(true));
        entry.put(COLUMN_PRIORITY, Short.toString(fm.getPriority()));
        entry.put(COLUMN_WILDCARD, Integer.toString(match.getWildcards()));
        
        if ((fm.getActions() != null) && (fm.getActions().size() > 0))
        	entry.put(COLUMN_ACTIONS, flowModActionsToString(fm.getActions()));
        
        if (match.getInputPort() != 0)
        	entry.put(COLUMN_IN_PORT, Short.toString(match.getInputPort()));
        
        if (!Arrays.equals(match.getDataLayerSource(), zeroMac))
        	entry.put(COLUMN_DL_SRC, HexString.toHexString(match.getDataLayerSource()));

        if (!Arrays.equals(match.getDataLayerDestination(), zeroMac))
        	entry.put(COLUMN_DL_DST, HexString.toHexString(match.getDataLayerDestination()));
        
        if (match.getDataLayerVirtualLan() != -1)
        	entry.put(COLUMN_DL_VLAN, Short.toString(match.getDataLayerVirtualLan()));
        
        if (match.getDataLayerVirtualLanPriorityCodePoint() != 0)
        	entry.put(COLUMN_DL_VLAN_PCP, Short.toString(match.getDataLayerVirtualLanPriorityCodePoint()));
        
        if (match.getDataLayerType() != 0)
        	entry.put(COLUMN_DL_TYPE, Short.toString(match.getDataLayerType()));
        
        if (match.getNetworkTypeOfService() != 0)
        	entry.put(COLUMN_NW_TOS, Short.toString(match.getNetworkTypeOfService()));
        
        if (match.getNetworkProtocol() != 0)
        	entry.put(COLUMN_NW_PROTO, Short.toString(match.getNetworkProtocol()));
        
        if (match.getNetworkSource() != 0)
        	entry.put(COLUMN_NW_SRC, IPv4.fromIPv4Address(match.getNetworkSource()));
        
        if (match.getNetworkDestination() != 0)
        	entry.put(COLUMN_NW_DST, IPv4.fromIPv4Address(match.getNetworkDestination()));
        
        if (match.getTransportSource() != 0)
        	entry.put(COLUMN_TP_SRC, Short.toString(match.getTransportSource()));
        
        if (match.getTransportDestination() != 0)
        	entry.put(COLUMN_TP_DST, Short.toString(match.getTransportDestination()));
        
        return entry;
    }
    
    /**
     * Returns a String representation of all the openflow actions.
     * 
     * @param fmActions	A list of OFActions to encode into one string
     * @return			A string of the actions encoded for our database
     */
    private static String flowModActionsToString(List<OFAction> fmActions) {
        StringBuilder sb = new StringBuilder();
        for (OFAction a : fmActions) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            switch(a.getType()) {
                case OUTPUT:
                    sb.append("output=" + Short.toString(((OFActionOutput)a).getPort()));
                    break;
                case OPAQUE_ENQUEUE:
                    int queue = ((OFActionOpaqueEnqueue)a).getQueueId();
                    short port = ((OFActionOpaqueEnqueue)a).getPort();
                    sb.append("enqueue=" + Short.toString(port) + ":0x" + String.format("%02x", queue));
                    break;
                case STRIP_VLAN:
                    sb.append("strip-vlan");
                    break;
                case SET_VLAN_ID:
                    sb.append("set-vlan-id=" + 
                        Short.toString(((OFActionSetVlanId)a).getVlanId()));
                    break;
                case SET_VLAN_PCP:
                    sb.append("set-vlan-priority=" +
                        Byte.toString(((OFActionSetVlanPcp)a).getVlanPcp()));
                    break;
                case SET_DL_SRC:
                    sb.append("set-src-mac=" + 
                        HexString.toHexString(((OFActionSetDlSrc)a).getDlAddr()));
                    break;
                case SET_DL_DST:
                    sb.append("set-dst-mac=" + 
                        HexString.toHexString(((OFActionSetDlDst)a).getDlAddr()));
                    break;
                case SET_NW_TOS:
                    sb.append("set-tos-bits=" +
                        Byte.toString(((OFActionSetNwTos)a).getNwTos()));
                    break;
                case SET_NW_SRC:
                    sb.append("set-src-ip=" +
                        IPv4.fromIPv4Address(((OFActionSetNwSrc)a).getNwAddr()));
                    break;
                case SET_NW_DST:
                    sb.append("set-dst-ip=" +
                        IPv4.fromIPv4Address(((OFActionSetNwDst)a).getNwAddr()));
                    break;
                case SET_TP_SRC:
                    sb.append("set-src-port=" +
                        Short.toString(((OFActionSetTpSrc)a).getTpPort()));
                    break;
                case SET_TP_DST:
                    sb.append("set-dst-port=" +
                        Short.toString(((OFActionSetTpDst)a).getTpPort()));
                    break;
                default:
                    Logger.error("Could not decode action: {}", a);
                    break;
            }
                
        }
        return sb.toString();
    }
    
   
    /**
     * Turns a JSON formatted Static Flow Pusher string into a storage entry.
     * Expects a string in JSON along the lines of:
     * <pre>
     *        {
     *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
     *            "name":         "flow-mod-1",
     *            "cookie":       "0",
     *            "priority":     "32768",
     *            "ingress-port": "1",
     *            "actions":      "output=2",
     *        }
     * </pre>
     * 
     * @param fmJson The JSON formatted static flow pusher entry
     * @return The map of the storage entry
     * @throws IOException If there was an error parsing the JSON
     */
    public static Map<String, Object> jsonToStorageEntry(String fmJson) throws IOException {
        Map<String, Object> entry = new HashMap<String, Object>();
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(fmJson);
            //jp.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            //jp.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
            //jp.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            if (n == "name")
                entry.put(COLUMN_NAME, jp.getText());
            else if (n == "switch")
                entry.put(COLUMN_SWITCH, jp.getText());
            else if (n == "actions")
                entry.put(COLUMN_ACTIONS, jp.getText());
            else if (n == "priority")
                entry.put(COLUMN_PRIORITY, jp.getText());
            else if (n == "active")
                entry.put(COLUMN_ACTIVE, jp.getText());
            else if (n == "wildcards")
                entry.put(COLUMN_WILDCARD, jp.getText());
            else if (n == "ingress-port")
                entry.put(COLUMN_IN_PORT, jp.getText());
            else if (n == "src-mac")
                entry.put(COLUMN_DL_SRC, jp.getText());
            else if (n == "dst-mac")
                entry.put(COLUMN_DL_DST, jp.getText());
            else if (n == "vlan-id")
                entry.put(COLUMN_DL_VLAN, jp.getText());
            else if (n == "vlan-priority")
                entry.put(COLUMN_DL_VLAN_PCP, jp.getText());
            else if (n == "ether-type")
                entry.put(COLUMN_DL_TYPE, jp.getText());
            else if (n == "tos-bits")
                entry.put(COLUMN_NW_TOS, jp.getText());
            else if (n == "protocol")
                entry.put(COLUMN_NW_PROTO, jp.getText());
            else if (n == "src-ip")
                entry.put(COLUMN_NW_SRC, jp.getText());
            else if (n == "dst-ip")
                entry.put(COLUMN_NW_DST, jp.getText());
            else if (n == "src-port")
                entry.put(COLUMN_TP_SRC, jp.getText());
            else if (n == "dst-port")
                entry.put(COLUMN_TP_DST, jp.getText());
            else if (n == "idle-timeout")
            	entry.put(COLUMN_IDLE_TIMEOUT, jp.getText());
            else if (n == "hard-timeout")
            	entry.put(COLUMN_HARD_TIMEOUT, jp.getText());
            	
        }
        
        return entry;
    }
    
    /**
     * Parses OFFlowMod actions from strings.
     * The parsing result of the second argument is packed into the first argument.
     * The second argument is a list of sub-actions separated by comma(','). 
     * Each sub-action begins with the action name, such as 'output', 'enqueue', 'strip-vlan', etc.
     * Each sub-action is parsed by decode_xxx function where 'xxx' is the name of the action.
     * 
     * @param flowMod	The OFFlowMod to set the actions for
     * @param actionstr	The string containing all the actions
     * @param log		A logger to log for errors.
     */
    public static void parseActionString(OFFlowMod flowMod, String actionstr) {
        List<OFAction> actions = new LinkedList<OFAction>();
        int actionsLength = 0;
        if (actionstr != null) {
            actionstr = actionstr.toLowerCase();
            for (String subaction : actionstr.split(",")) {
                String action = subaction.split("[=:]")[0];
                SubActionStruct subaction_struct = null;
                
                if (action.equals("output")) {
                    subaction_struct = decode_output(subaction);
                }
                else if (action.equals("enqueue")) {
                    subaction_struct = decode_enqueue(subaction);
                }
                else if (action.equals("strip-vlan")) {
                    subaction_struct = decode_strip_vlan(subaction);
                }
                else if (action.equals("set-vlan-id")) {
                    subaction_struct = decode_set_vlan_id(subaction);
                }
                else if (action.equals("set-vlan-priority")) {
                    subaction_struct = decode_set_vlan_priority(subaction);
                }
                else if (action.equals("set-src-mac")) {
                    subaction_struct = decode_set_src_mac(subaction);
                }
                else if (action.equals("set-dst-mac")) {
                    subaction_struct = decode_set_dst_mac(subaction);
                }
                else if (action.equals("set-tos-bits")) {
                    subaction_struct = decode_set_tos_bits(subaction);
                }
                else if (action.equals("set-src-ip")) {
                    subaction_struct = decode_set_src_ip(subaction);
                }
                else if (action.equals("set-dst-ip")) {
                    subaction_struct = decode_set_dst_ip(subaction);
                }
                else if (action.equals("set-src-port")) {
                    subaction_struct = decode_set_src_port(subaction);
                }
                else if (action.equals("set-dst-port")) {
                    subaction_struct = decode_set_dst_port(subaction);
                }
                else {
                    Logger.error("Unexpected action '{}', '{}'", action, subaction);
                }
                
                if (subaction_struct != null) {
                    actions.add(subaction_struct.action);
                    actionsLength += subaction_struct.len;
                }
            }
        }
        Logger.debug("action {}", actions);
        
        flowMod.setActions(actions);
        flowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH + actionsLength);
    } 
    
    /**
     * parse a subaction 'output'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_output(String subaction) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("output=(?:((?:0x)?\\d+)|(all)|(controller)|(local)|(ingress-port)|(normal)|(flood))").matcher(subaction);
        if (n.matches()) {
            OFActionOutput action = new OFActionOutput();
            action.setMaxLength((short) Short.MAX_VALUE);
            short port = OFPortNo.OFPP_NONE.getValue();
            if (n.group(1) != null) {
                try {
                    port = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid port in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else if (n.group(2) != null)
                port = OFPortNo.OFPP_ALL.getValue();
            else if (n.group(3) != null)
                port = OFPortNo.OFPP_CONTROLLER.getValue();
            else if (n.group(4) != null)
                port = OFPortNo.OFPP_LOCAL.getValue();
            else if (n.group(5) != null)
                port = OFPortNo.OFPP_IN_PORT.getValue();
            else if (n.group(6) != null)
                port = OFPortNo.OFPP_NORMAL.getValue();
            else if (n.group(7) != null)
                port = OFPortNo.OFPP_FLOOD.getValue();
            action.setPort(port);
            Logger.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionOutput.MINIMUM_LENGTH;
        }
        else {
            Logger.error("Invalid subaction: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    /**
     * parse a subaction 'enqueue'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_enqueue(String subaction) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("enqueue=(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(subaction);
        if (n.matches()) {
            short portnum = 0;
            if (n.group(1) != null) {
                try {
                    portnum = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid port-num in: '{}' (error ignored)", subaction);
                    return null;
                }
            }

            int queueid = 0;
            if (n.group(2) != null) {
                try {
                    queueid = get_int(n.group(2));
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid queue-id in: '{}' (error ignored)", subaction);
                    return null;
               }
            }
            
            OFActionOpaqueEnqueue action = new OFActionOpaqueEnqueue();
            action.setPort(portnum);
            action.setQueueId(queueid);
            Logger.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionOpaqueEnqueue.MINIMUM_LENGTH;
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    /**
     * parse a subaction 'strip-vlan'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_strip_vlan(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("strip-vlan").matcher(subaction);
        
        if (n.matches()) {
        	OFActionStripVlan action = new OFActionStripVlan();
            Logger.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionStripVlan.MINIMUM_LENGTH;
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    /**
     * parse a subaction 'set-vlan-id'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_vlan_id(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-vlan-id=((?:0x)?\\d+)").matcher(subaction);
        
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    short vlanid = get_short(n.group(1));
                    OFActionSetVlanId action = new OFActionSetVlanId();
                    action.setVlanId(vlanid);
                    Logger.debug("  action {}", action);

                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionSetVlanId.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid VLAN in: {} (error ignored)", subaction);
                    return null;
                }
            }          
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    /**
     * parse a aubsection 'set-vlan-priority'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_vlan_priority(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-vlan-priority=((?:0x)?\\d+)").matcher(subaction); 
        
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    byte prior = get_byte(n.group(1));
                    OFActionSetVlanPcp action = new OFActionSetVlanPcp();
                    action.setVlanPcp(prior);
                    Logger.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionSetVlanPcp.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid VLAN priority in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    /**
     * parse a subaction 'set-src-mac'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_src_mac(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction); 

        if (n.matches()) {
            byte[] macaddr = get_mac_addr(n, subaction);
            if (macaddr != null) {
            	OFActionSetDlSrc action = new OFActionSetDlSrc();
                action.setDlAddr(macaddr);
                Logger.debug("action {}", action);

                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionSetDlSrc.MINIMUM_LENGTH;
            }            
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    /**
     * parse a subaction 'set-dst-mac'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_dst_mac(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction);
        
        if (n.matches()) {
            byte[] macaddr = get_mac_addr(n, subaction);            
            if (macaddr != null) {
            	OFActionSetDlDst action = new OFActionSetDlDst();
                action.setDlAddr(macaddr);
                Logger.debug("  action {}", action);
                
                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionSetDlDst.MINIMUM_LENGTH;
            }
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    /**
     * parse a subaction 'set-tos-bits'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_tos_bits(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-tos-bits=((?:0x)?\\d+)").matcher(subaction); 

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    byte tosbits = get_byte(n.group(1));
                    OFActionSetNwTos action = new OFActionSetNwTos();
                    action.setNwTos(tosbits);
                    Logger.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionSetNwTos.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    /**
     * parse a subaction 'set-src-ip'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_src_ip(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);

        if (n.matches()) {
            int ipaddr = get_ip_addr(n, subaction);
            OFActionSetNwSrc action = new OFActionSetNwSrc();
            action.setNwAddr(ipaddr);
            Logger.debug("  action {}", action);

            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionSetNwSrc.MINIMUM_LENGTH;
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    /**
     * parse a subaction 'set-dst-ip'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_dst_ip(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);

        if (n.matches()) {
            int ipaddr = get_ip_addr(n, subaction);
            OFActionSetNwDst action = new OFActionSetNwDst();
            action.setNwAddr(ipaddr);
            Logger.debug("action {}", action);
 
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionSetNwDst.MINIMUM_LENGTH;
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    /**
     * parse a subaction 'set-src-port'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_src_port(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-port=((?:0x)?\\d+)").matcher(subaction); 

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    short portnum = get_short(n.group(1));
                    OFActionSetTpSrc action = new OFActionSetTpSrc();
                    action.setTpPort(portnum);
                    Logger.debug("action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionSetTpSrc.MINIMUM_LENGTH;;
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid src-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    /**
     * parse a subaction 'set-dst-port'
     * 
     * @param subaction
     * @return
     */
    private static SubActionStruct decode_set_dst_port(String subaction) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-port=((?:0x)?\\d+)").matcher(subaction);

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    short portnum = get_short(n.group(1));
                    OFActionSetTpDst action = new OFActionSetTpDst();
                    action.setTpPort(portnum);
                    Logger.debug("action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionSetTpDst.MINIMUM_LENGTH;;
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            Logger.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    /**
     * from a subaction, extract a mac address. 
     * The extraction rule is given by the first argument.
     * This method is called by 
     * {@link #decode_set_dst_mac(String)}, and {@link #decode_set_src_mac(String)}. 
     * 
     * @param n
     * @param subaction
     * @return
     */
    private static byte[] get_mac_addr(Matcher n, String subaction) {
        byte[] macaddr = new byte[6];
        
        for (int i=0; i<6; i++) {
            if (n.group(i+1) != null) {
                try {
                    macaddr[i] = get_byte("0x" + n.group(i+1));
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid src-mac in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else { 
                Logger.debug("Invalid src-mac in: '{}' (null, error ignored)", subaction);
                return null;
            }
        }
        
        return macaddr;
    }
    
    /**
     * This method extracts IP address information from the given subaction. 
     * This method is called by 
     * {@link #decode_set_dst_ip(String)}, and {@link #decode_set_src_ip(String)}.
     * 
     * @param n
     * @param subaction
     * @return
     */
    private static int get_ip_addr(Matcher n, String subaction) {
        int ipaddr = 0;

        for (int i=0; i<4; i++) {
            if (n.group(i+1) != null) {
                try {
                    ipaddr = ipaddr<<8;
                    ipaddr = ipaddr | get_int(n.group(i+1));
                }
                catch (NumberFormatException e) {
                    Logger.debug("Invalid src-ip in: '{}' (error ignored)", subaction);
                    return 0;
                }
            }
            else {
                Logger.debug("Invalid src-ip in: '{}' (null, error ignored)", subaction);
                return 0;
            }
        }
        
        return ipaddr;
    }
    
    /**
     * Parse int as decimal, hex (start with 0x or #) or octal (starts with 0)
     * 
     * @param str
     * @return	int representation of the given string
     */
    private static int get_int(String str) {
        return (int)Integer.decode(str);
    }
   
    /**
     * Parse short as decimal, hex (start with 0x or #) or octal (starts with 0)
     * 
     * @param str
     * @return	short representation of the given string
     */
    private static short get_short(String str) {
        return (short)(int)Integer.decode(str);
    }
   
    /**
     * Parse byte as decimal, hex (start with 0x or #) or octal (starts with 0)
     * 
     * @param str
     * @return	byte representation of the given string
     */
    private static byte get_byte(String str) {
        return Integer.decode(str).byteValue();
    }
    
    /**
     * Checks to see if the user matches IP information without
     * checking for the correct ether-type (2048).
     * 
     * @param rows The Map that is a string representation of
     * the static flow.
     * @reutrn True if they checked the ether-type, false otherwise
     */
    public static boolean checkMatchIp(Map<String, Object> rows) {
        boolean matchEther = false;
        String val = (String) rows.get(COLUMN_DL_TYPE);
        if (val != null) {
            int type = 0;
            // check both hex and decimal
            if (val.startsWith("0x")) {
                type = Integer.parseInt(val.substring(2), 16);
            } else {
                try {
                    type = Integer.parseInt(val);
                } catch (NumberFormatException e) { /* fail silently */}
            }
            if (type == 2048) matchEther = true;
        }
        
        if ((rows.containsKey(COLUMN_NW_DST) || 
                rows.containsKey(COLUMN_NW_SRC) ||
                rows.containsKey(COLUMN_NW_PROTO) ||
                rows.containsKey(COLUMN_NW_TOS)) &&
                (matchEther == false))
            return false;
        
        return true;
    }
}

