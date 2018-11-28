package edu.ucsd.data;

import java.io.File;
import java.net.URI;

import org.apache.commons.io.FileUtils;

import edu.ucsd.util.FileIOUtils;
import edu.ucsd.util.SpectrumFileUtils;

@SuppressWarnings("serial")
public class ProteoSAFeFile
extends File
{
	/*========================================================================
	 * Properties
	 *========================================================================*/
	private Integer spectra;
	private Long    checksum;
	private String  hash;
	
	/*========================================================================
	 * Constructors
	 *========================================================================*/
	public ProteoSAFeFile(File file) {
		super(file, "");
	}
	
	public ProteoSAFeFile(File parent, String child) {
		super(parent, child);
	}
	
	public ProteoSAFeFile(String pathname) {
		super(pathname);
	}
	
	public ProteoSAFeFile(String parent, String child) {
		super(parent, child);
	}
	
	public ProteoSAFeFile(URI uri) {
		super(uri);
	}
	
	/*========================================================================
	 * Property accessor methods
	 *========================================================================*/
	public Integer getSpectra() {
		// can only count spectra in a readable file
		if (isFile() == false || canRead() == false) {
			spectra = null;
			return spectra;
		}
		// if spectrum count is not known, count them up
		if (spectra == null) try {
			spectra = SpectrumFileUtils.countMS2Spectra(this);
		} catch (Throwable error) {}
		return spectra;
	}
	
	public Long getChecksum() {
		// can only compute checksum for a readable file
		if (isFile() == false || canRead() == false) {
			checksum = null;
			return checksum;
		}
		// if checksum is not known, compute it
		if (checksum == null) try {
			checksum = FileUtils.checksumCRC32(this);
		} catch (Throwable error) {}
		return checksum;
	}
	
	public String getHash() {
		// can only compute hash for a readable file
		if (isFile() == false || canRead() == false) {
			hash = null;
			return hash;
		}
		// if hash is not known, compute it
		if (hash == null) try {
			hash = FileIOUtils.getMD5Hash(this);
		} catch (Throwable error) {}
		return hash;
	}
}
