package org.openflow.protocol.ver1_3.messages;

import java.nio.ByteBuffer;
import org.openflow.util.*;

import java.util.List;
import java.util.LinkedList;
import org.openflow.protocol.ver1_3.types.*;

public class OFMatchOxm extends OFMatch  {
    public static int MINIMUM_LENGTH = 4;

    List<OFOxm>  oxm_fields;

    public OFMatchOxm() {
        super();
		setLength(U16.t(MINIMUM_LENGTH));
		setType(OFMatchType.valueOf((short)1));
		this.oxm_fields = new LinkedList<OFOxm>();
    }
    
    public OFMatchOxm(OFMatchOxm other) {
    	super(other);
		this.oxm_fields = (other.oxm_fields == null)? null: new LinkedList<OFOxm>();
		for ( OFOxm i : other.oxm_fields ) { this.oxm_fields.add( new OFOxm(i) ); }
    }

	public List<OFOxm> getOxmFields() {
		return this.oxm_fields;
	}
	
	public OFMatchOxm setOxmFields(List<OFOxm> oxm_fields) {
		this.oxm_fields = oxm_fields;
		return this;
	}
			

    public void readFrom(ByteBuffer data) {
        int mark = data.position();
		super.readFrom(data);
		if (this.oxm_fields == null) this.oxm_fields = new LinkedList<OFOxm>();
		int __cnt = ((int)getLength() - (data.position() - mark));
		while (__cnt > 0) { OFOxm t = new OFOxm(); t.readFrom(data); this.oxm_fields.add(t); __cnt -= OFOxm.MINIMUM_LENGTH; }
		int __align = alignment((short)8);
		while (__align > 0 && 8 - __align > 0) { data.get(); __align += 1; }
    }

    public void writeTo(ByteBuffer data) {
    	super.writeTo(data);
        if (this.oxm_fields != null ) for (OFOxm t: this.oxm_fields) { t.writeTo(data); }
		int __align = alignment((short)8);
		while (__align > 0 && 8 - __align > 0) { data.put((byte)0); __align += 1; }
    }

    public String toString() {
        return super.toString() +  ":OFMatchOxm-"+":oxm_fields=" + oxm_fields.toString();
    }

	// compute length (without final alignment)    
    public short computeLength() {
    	short len = (short)MINIMUM_LENGTH;
    	for ( OFOxm i : this.oxm_fields ) { len += i.computeLength(); }
    	return len;
    }
    
    // calculate the amount that will be increased by the alignment requirement.
    public short alignment(short req) {
    	if (req == 0) return 0;
    	short l = (short)(computeLength() % req);
    	if ( l == 0 ) { return 0; }
    	return (short)( req - l );
    }
    
    // compute the difference with MINIMUM_LENGTH (with alignment)
    public short lengthDiff() {
    	return (short)(computeLength() - (short)MINIMUM_LENGTH + alignment((short)8));
    }

    @Override
    public int hashCode() {
        		
		final int prime = 2297;
		int result = super.hashCode() * prime;
		result = prime * result + ((oxm_fields == null)?0:oxm_fields.hashCode());
		return result;
    }

    @Override
    public boolean equals(Object obj) {
        
		if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OFMatchOxm)) {
            return false;
        }
        OFMatchOxm other = (OFMatchOxm) obj;
		if ( oxm_fields == null && other.oxm_fields != null ) { return false; }
		else if ( !oxm_fields.equals(other.oxm_fields) ) { return false; }
        return true;
    }
}
