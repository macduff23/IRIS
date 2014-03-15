/**
 *    Copyright 2011, Big Switch Networks, Inc. 
 *    Originally created by Byungjoon Lee, ETRI
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package etri.sdn.controller.module.netfailover;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

import com.google.common.hash.BloomFilter;

import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.module.linkdiscovery.ILinkDiscoveryListener;
import etri.sdn.controller.module.linkdiscovery.ILinkDiscoveryService;
import etri.sdn.controller.module.linkdiscovery.NodePortTuple;
import etri.sdn.controller.module.routing.IRoutingService;
import etri.sdn.controller.module.routing.Route;
import etri.sdn.controller.module.topologymanager.ITopologyListener;
import etri.sdn.controller.module.topologymanager.ITopologyService;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;

public class OFMNetFailover extends OFModule implements ILinkDiscoveryListener,
		ITopologyListener {

	private ILinkDiscoveryService linkDiscoveryService;
	private ITopologyService topologyService;
	private IRoutingService routingService;
	
	@Override
	public void topologyChanged() {
		// TODO Auto-generated method stub
	}

	@Override
	public void linkDiscoveryUpdate(LDUpdate update) {
		UpdateOperation op = update.getOperation();
		switch (op) {
		case LINK_UPDATED:
			// new link is added. We need to find all the affected routes 
			// and remove them from the flow tables of the switches.
			// TODO:
			break;
		case LINK_REMOVED:
			// a link is removed. We need to find all routes on the link
			// and remove them from the flow tables of the switches. 
			removeRoutesOnLink(update.getSrc(), update.getSrcPort(), 
							   update.getDst(), update.getDstPort());
			break;
		default:
			// does nothing
		}
	}

	private void removeRoutesOnLink(long src, OFPort srcPort, long dst,	OFPort dstPort) {
		NodePortTuple srcNpt = new NodePortTuple(src, srcPort);
		NodePortTuple dstNpt = new NodePortTuple(dst, dstPort);

		// get all switch identifiers
		Set<Long> sws = this.controller.getSwitchIdentifiers();
		
		// this is a set of access switch identifiers
		Set<Long> asws = new HashSet<Long>();
		
		for ( long s : sws ) {
			Set<OFPort> ports = this.topologyService.getPorts(s);
			for ( OFPort p: ports ) {
				// if the switch has an attachment point port, 
				// then it's used as an access switch.
				if ( this.topologyService.isAttachmentPointPort(s, p) ) {
					// if this is an access switch, we include it in the calculation.
					asws.add(s);
					break;
				}
			}
		}
		
		// now, for every pair of access switches,
		for ( long s : asws ) {
			for ( long d : asws ) {
				// we don't do the removal for the reverse direction.
				// can this be some source of evil?
				if ( s <= d ) continue;
				
				 Route r = this.routingService.getRoute(s, d);
				 BloomFilter<NodePortTuple> bf = r.getBloomFilter();
				 if ( bf.mightContain(srcNpt) && bf.mightContain(dstNpt) ) {
					 removeRouteFromNetwork(r);
				 }
			}
		}		
	}

	/**
	 * Remove a route from network by eliminating flow-mod records
	 * @param r			Route object
	 */
	private void removeRouteFromNetwork(Route r) {
		for ( NodePortTuple npt: r.getPath()) {
			removeOutgoingFlowRecord( npt.getNodeId(), npt.getPortId() );
		}
	}
	
	/**
	 * Remove Flow-mod record from the switch
	 * 
	 * @param srcId		long identifier of the switch
	 * @param outPort	outgoing port where the flow-del would be applied
	 */
	private void removeOutgoingFlowRecord(long srcId, OFPort outPort) {
		IOFSwitch sw = this.getController().getSwitch(srcId);
		if ( sw == null ) {
			return;
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		
		OFFlowDelete.Builder del = fac.buildFlowDelete();
		try {
			del
			.setOutPort(outPort)
			.setMatch(fac.matchWildcardAll())
			.setTableId(TableId.ZERO);
		} catch ( UnsupportedOperationException u ) {
			// does nothing.
		}
		
		sw.getConnection().write( del.build() );
	}

	@Override
	protected void initialize() {
		this.linkDiscoveryService = 
				(ILinkDiscoveryService) OFModule.getModule(ILinkDiscoveryService.class);
		this.topologyService = 
				(ITopologyService) OFModule.getModule(ITopologyService.class);
		this.routingService = 
				(IRoutingService) OFModule.getModule(IRoutingService.class);
		
		this.linkDiscoveryService.addListener(this);
		this.topologyService.addListener(this);
	}

	@Override
	protected boolean handleHandshakedEvent(Connection conn,
			MessageContext context) {
		return true;
	}

	@Override
	protected boolean handleMessage(Connection conn, MessageContext context,
			OFMessage msg, List<OFMessage> outgoing) {
		return true;
	}
	
	@Override
	protected boolean handleDisconnect(Connection conn) {
		return true;
	}

	@Override
	public OFModel[] getModels() {
		return null;
	}

}
