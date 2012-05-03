package edu.ucsd.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

public class AminoAcidUtils
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	public static final Set<String> KNOWN_AMINO_ACIDS = new HashSet<String>(
		Arrays.asList(new String[]{
			"A", "C", "D", "E", "F", "G", "H", "I", "K", "L",
			"M", "N", "P", "Q", "R", "S", "T", "V", "W", "Y"
		})
	);
	
	public static enum PTMType {
		FIXED("fix", "Fixed"),
		OPTIONAL("opt", "Optional"),
		FIXED_N_TERMINAL("fix_nterm", "Fixed, N-Terminal"),
		OPTIONAL_N_TERMINAL("opt_nterm", "Optional, N-Terminal");
		// C_TERMINAL("cterminal", "C-terminal");
		
		public final String code;
		public final String desc;
		
		private PTMType(String code, String desc) {
			this.code = code;
			this.desc = desc;
		}
	}
	
	public static enum PTM {
		OXIDATION				("+15.994915,M,opt", "Oxidation"),
		LYSINE_METHYLATION		("+14.015650,K,opt", "Lysine Methylation"),
		PYROGLUTAMATE_FORMATION	("-17.026549,Q,opt_nterm", "Pyroglutamate Formation"),
		PHOSPHORYLATION			("+79.966331,STY,opt", "Phosphorylation"),
		NTERM_CARBAMYLATION		("+43.005814,*,opt_nterm", "N-terminal Carbamylation"),
		NTERM_ACETYLATION		("+42.010565,*,opt_nterm", "N-terminal Acetylation");
		
		public final String code;
		public final String desc;
		
		private PTM(String code, String desc) {
			this.code = code;
			this.desc = desc;
		}
		
		public Double getMassOffset() {
			return AminoAcidUtils.getMassOffset(code);
		}
		
		public Set<String> getResidues() {
			return AminoAcidUtils.getResidues(code);
		}
		
		public String getResidueString() {
			StringBuffer residueString = new StringBuffer();
			for (String residue : getResidues())
				residueString.append(residue);
			return residueString.toString();
		}
		
		public PTMType getType() {
			return AminoAcidUtils.getType(code);
		}
		
		public String getTypeString() {
			return getType().desc.toUpperCase();
		}
	}
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static final Collection<String> validatePTMs(
		Map<String, Collection<String>> parameters
	) {
		if (parameters == null)
			return null;
		PTMType[] ptmTypes = PTMType.values();
		Collection<String> errors = new Vector<String>(ptmTypes.length);
		for (PTMType type : ptmTypes) {
			Collection<String> theseErrors =
				validatePTMs(getMassOffsets(parameters, type), type);
			if (theseErrors != null)
				errors.addAll(theseErrors);
		}
		if (errors.isEmpty())
			return null;
		else return errors;
	}
	
	public static final Collection<String> validatePTMs(
		Map<String, Collection<Double>> ptms, PTMType type
	) {
		if (ptms == null || ptms.isEmpty() || type == null)
			return null;
		Collection<String> residues = new HashSet<String>(ptms.keySet());
		Collection<String> errors = new HashSet<String>(residues.size());
		for (String residue : residues) {
			Collection<Double> massOffsets = ptms.get(residue);
			if (massOffsets == null || massOffsets.isEmpty())
				ptms.remove(residue);
			else switch (type) {
				case FIXED:
					if (residue.equals("*"))
						errors.add(
							"A fixed PTM was specified on residue \"*\".");
					else if (massOffsets.size() > 1)
						errors.add(String.format(
							"More than one fixed PTM was specified " +
							"on residue \"%s\".", residue));
					break;
				case OPTIONAL:
					break;
				case FIXED_N_TERMINAL:
					if (massOffsets.size() > 1)
						errors.add(String.format(
							"More than one fixed N-Terminal PTM was " +
							"specified on residue \"%s\".", residue));
					break;
				case OPTIONAL_N_TERMINAL:
				default:
					break;
			}
		}
		if (errors.isEmpty())
			return null;
		else return errors;
	}
	
	public static final Map<String, Collection<Double>> getMassOffsets(
		Map<String, Collection<String>> parameters, PTMType type
	) {
		if (parameters == null || type == null)
			return null;
		// build amino acid mass offsets map
		Map<String, Collection<Double>> massOffsets =
			new HashMap<String, Collection<Double>>(20);
		// if this map is for fixed PTMs, process the
		// "cysteine_protease.cysteine" parameter
		if (type.equals(PTMType.FIXED)) {
			String cysteine = WorkflowParameterUtils.getParameter(
				parameters, "cysteine_protease.cysteine");
			if (cysteine != null) {
				if (cysteine.equals("c57"))
					addMassOffset(massOffsets, "C", 57.021463723);
				else if (cysteine.equals("c58"))
					addMassOffset(massOffsets, "C", 58.005479308);
				else if (cysteine.equals("c99"))
					addMassOffset(massOffsets, "C", 99.068413915);
			}
		}
		// next process the pre-defined PTMs
		for (PTM ptm : PTM.values()) {
			String parameter = WorkflowParameterUtils.getParameter(
				parameters, "ptm." + ptm.toString());
			// only process the appropriately typed pre-defined PTMs
			if (parameter != null && parameter.equals("on") &&
				type.equals(ptm.getType()))
				addMassOffset(massOffsets, ptm.code);
		}
		// finally process the "ptm.custom_PTM" parameters
		Collection<String> customPTMs = parameters.get("ptm.custom_PTM");
		if (customPTMs != null && customPTMs.isEmpty() == false) {
			for (String customPTM : customPTMs) {
				// only process the appropriately typed custom PTMs
				PTMType customType = getType(customPTM);
				if (type.equals(customType))
					addMassOffset(massOffsets, customPTM);
			}
		}
		// return the completed mass offsets map
		if (massOffsets == null || massOffsets.size() < 1)
			return null;
		else return massOffsets;
	}
	
	public static final boolean isAminoAcid(String residue) {
		if (residue == null)
			return false;
		else return KNOWN_AMINO_ACIDS.contains(residue);
	}
	
	public static final Double getMassOffset(String customPTM) {
		if (customPTM == null)
			return null;
		// user-specified PTMs are assumed to be encoded as
		// a string in the format <mass>,<residue>,<type>
		String[] fields = customPTM.split(",");
		if (fields.length != 3)
			return null;
		try {
			return Double.parseDouble(fields[0]);
		} catch (NumberFormatException error) {
			return null;
		}
	}
	
	public static final String getResidueCode(String customPTM) {
		if (customPTM == null)
			return null;
		// user-specified PTMs are assumed to be encoded as
		// a string in the format <mass>,<residue>,<type>
		String[] fields = customPTM.split(",");
		if (fields.length != 3)
			return null;
		else return fields[1];
	}
	
	public static final Set<String> getResidues(String customPTM) {
		String code = getResidueCode(customPTM);
		if (code == null)
			return null;
		// extract all residues encoded in the residue field, and add them
		Set<String> residues = new TreeSet<String>();
		if (code.equals("*"))
			residues.add(code);
		else for (int i=0; i<code.length(); i++) {
			String residue = Character.toString(code.charAt(i));
			if (isAminoAcid(residue) == false)
				return null;
			else residues.add(residue);
		}
		// return the completed residue set
		if (residues == null || residues.size() < 1)
			return null;
		else return residues;
	}
	
	public static final PTMType getType(String customPTM) {
		if (customPTM == null)
			return null;
		// user-specified PTMs are assumed to be encoded as
		// a string in the format <mass>,<residue>,<type>
		String[] fields = customPTM.split(",");
		if (fields.length != 3)
			return null;
		// if the specified "type" field matches that of a declared
		// PTM type, return that type; otherwise return null
		for (PTMType type : PTMType.values())
			if (fields[2].equals(type.code))
				return type;
		return null;
	}
	
	public static final boolean addMassOffset(
		Map<String, Collection<Double>> massOffsets, String customPTM
	) {
		if (massOffsets == null || customPTM == null)
			return false;
		// parse this custom PTM into its set of residues and its mass offset
		Set<String> residues = getResidues(customPTM);
		Double massOffset = getMassOffset(customPTM);
		if (residues == null || residues.size() < 1 || massOffset == null)
			return false;
		// try to add the mass offset for each residue in this custom PTM
		for (String residue : residues)
			if (addMassOffset(massOffsets, residue, massOffset) == false)
				return false;
		return true;
	}
	
	public static final boolean addMassOffset(
		Map<String, Collection<Double>> massOffsets,
		String residue, Double massOffset
	) {
		if (massOffsets == null || residue == null || massOffset == null)
			return false;
		else if (isAminoAcid(residue) == false && residue.equals("*") == false)
			return false;
		Collection<Double> offsets = massOffsets.get(residue);
		if (offsets == null)
			offsets = new Vector<Double>();
		offsets.add(massOffset);
		massOffsets.put(residue, offsets);
		return true;
	}
}
