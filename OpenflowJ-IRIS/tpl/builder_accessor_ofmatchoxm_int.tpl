		public Builder ${signature} {
			return setValue(OFOxmMatchFields.${match_field}, (byte) ${mask}, ByteBuffer.allocate(4).putInt(value).array());
		}
		
		public boolean is${method_name}Supported() {
			return true;
		}
		