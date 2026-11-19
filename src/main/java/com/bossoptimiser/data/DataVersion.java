package com.bossoptimiser.data;

import lombok.Value;

/** Provenance of the bundled combat datasets. */
@Value
public class DataVersion
{
	int schema;
	String generated;
	String source;
	String sourceRef;
	String licence;

	public String shortRef()
	{
		return sourceRef != null && sourceRef.length() > 10 ? sourceRef.substring(0, 10) : String.valueOf(sourceRef);
	}
}
