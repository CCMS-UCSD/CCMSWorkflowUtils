package edu.ucsd.util;

public interface OnDemandOperation{
	public boolean execute();
	public boolean resourceExists();
	public boolean resourceDated();
	public String  getResourceName();
}
