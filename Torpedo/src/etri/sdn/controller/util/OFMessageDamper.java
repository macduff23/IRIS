/*
 * Copyright Big Switch Networks 2012
 */

package etri.sdn.controller.util;

import java.io.IOException;
//import java.util.EnumSet;
import java.util.Set;

import org.openflow.protocol.OFMessage;
//import org.openflow.protocol.OFType;

import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;

/**
 * Dampens OFMessages sent to an OF switch. A message is only written to 
 * a switch if the same message (as defined by .equals()) has not been written
 * in the last n milliseconds. Timer granularity is based on TimedCache
 * @author gregor
 * @author bjlee
 *
 */
public final class OFMessageDamper {
    /**
     * An entry in the TimedCache. A cache entry consists of the sent message
     * as well as the switch to which the message was sent. 
     * 
     * NOTE: We currently use the full OFMessage object. To save space, we 
     * could use a cryptographic hash (e.g., SHA-1). However, this would 
     * obviously be more time-consuming.... 
     * 
     * We also store a reference to the actual IOFSwitch object and /not/
     * the switch DPID. This way we are guaranteed to not dampen messages if
     * a switch disconnects and then reconnects.
     * 
     * @author gregor
     */
    private static class DamperEntry {
        OFMessage msg;
        IOFSwitch sw;
        public DamperEntry(OFMessage msg, IOFSwitch sw) {
            super();
            this.msg = msg;
            this.sw = sw;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((msg == null) ? 0 : msg.hashCode());
            result = prime * result + ((sw == null) ? 0 : sw.hashCode());
            return result;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            DamperEntry other = (DamperEntry) obj;
            if (msg == null) {
                if (other.msg != null) return false;
            } else if (!msg.equals(other.msg)) return false;
            if (sw == null) {
                if (other.sw != null) return false;
            } else if (!sw.equals(other.sw)) return false;
            return true;
        }
    }
    
    private TimedCache<DamperEntry> cache;
//    private EnumSet<OFType> msgTypesToCache;
    private Set<Byte> msgTypesToCache;
    /**
     * 
     * @param capacity the maximum number of messages that should be 
     * kept
     * @param typesToDampen The set of OFMessageTypes that should be 
     * dampened by this instance. Other types will be passed through
     * @param timeout The dampening timeout. A message will only be
     * written if the last write for the an equal message more than
     * timeout ms ago. 
     */
    public OFMessageDamper(int capacity, 
                           Set<Byte> typesToDampen,  
                           int timeout) {
        cache = new TimedCache<DamperEntry>(capacity, timeout);
//        msgTypesToCache = EnumSet.copyOf(typesToDampen);
        msgTypesToCache = typesToDampen;
    }        
    
    /**
     * write the messag to the switch according to our dampening settings
     * @param sw
     * @param msg
     * @return true if the message was written to the switch, false if
     * the message was dampened. 
     * @throws IOException
     */
    public boolean write(Connection conn, OFMessage msg) throws IOException {
        if (! msgTypesToCache.contains(msg.getTypeByte())) {
            conn.write(msg);
            return true;
        }
        
        DamperEntry entry = new DamperEntry(msg, conn.getSwitch());
        if (cache.update(entry)) {
            // entry exists in cache. Dampening.
            return false; 
        } else {
            conn.write(msg);
            return true;
        }
    }
}
