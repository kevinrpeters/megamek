/*
 * MegaMek - Copyright (C) 2018 - The MegaMek Team
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
package megamek.common.templates;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import megamek.common.Messages;
import megamek.common.Mounted;
import megamek.common.WeaponType;
import megamek.common.Aero;
import megamek.common.AmmoType;
import megamek.common.Bay;
import megamek.common.Entity;
import megamek.common.logging.DefaultMmLogger;
import megamek.common.verifier.BayData;
import megamek.common.verifier.EntityVerifier;
import megamek.common.verifier.TestAero;

/**
 * Creates a TRO template model for aerospace and conventional fighters.
 * 
 * @author Neoancient
 *
 */
public class AeroTROView extends TROView {

	private final Aero aero;
	
	public AeroTROView(Aero aero) {
		this.aero = aero;
	}

	@Override
	protected String getTemplateFileName(boolean html) {
		if (html) {
			return "aero.ftlh";
		}
		return "aero.ftl";
	}

	@Override
	protected void initModel(EntityVerifier verifier) {
		setModelData("formatArmorRow", new FormatTableRowMethod(new int[] { 20, 10},
				new Justification[] { Justification.LEFT, Justification.CENTER }));
		addBasicData(aero);
		addArmorAndStructure();
		int nameWidth = addEquipment(aero);
		setModelData("formatEquipmentRow", new FormatTableRowMethod(new int[] { nameWidth, 12, 8, 8, 5, 5, 5, 5, 5},
				new Justification[] { Justification.LEFT, Justification.CENTER, Justification.CENTER,
						Justification.CENTER, Justification.CENTER, Justification.CENTER, Justification.CENTER, 
						Justification.CENTER, Justification.CENTER }));
		addFluff();
		setModelData("isOmni", aero.isOmni());
		setModelData("isConventional", aero.hasETypeFlag(Entity.ETYPE_CONV_FIGHTER));
		TestAero testAero = new TestAero(aero, verifier.aeroOption, null);
		setModelData("engineName", stripNotes(aero.getEngine().getEngineName()));
		setModelData("engineMass", NumberFormat.getInstance().format(testAero.getWeightEngine()));
		setModelData("safeThrust", aero.getWalkMP());
		setModelData("maxThrust", aero.getRunMP());
		setModelData("si", aero.get0SI());
		setModelData("hsCount", aero.getHeatType() == Aero.HEAT_DOUBLE?
				aero.getOHeatSinks() + " [" + (aero.getOHeatSinks() * 2) + "]" : aero.getOHeatSinks());
		setModelData("fuelPoints", aero.getFuel());
		setModelData("fuelMass", aero.getFuelTonnage());
		setModelData("hsMass", NumberFormat.getInstance().format(testAero.getWeightHeatSinks()));
		if (aero.getCockpitType() == Aero.COCKPIT_STANDARD) {
			setModelData("cockpitType", "Cockpit");
		} else {
			setModelData("cockpitType", Aero.getCockpitTypeString(aero.getCockpitType()));
		}
		setModelData("cockpitMass", NumberFormat.getInstance().format(testAero.getWeightControls()));
		String atName = formatArmorType(aero, true);
		if (atName.length() > 0) {
			setModelData("armorType", " (" + atName + ")");
		} else {
			setModelData("armorType", "");
		}
		setModelData("armorFactor", aero.getTotalOArmor());
		setModelData("armorMass", NumberFormat.getInstance().format(testAero.getWeightArmor()));
		if (aero.isOmni()) {
			addFixedOmni(aero);
		}
	}
	
	private void addFluff() {
		addMechVeeAeroFluff(aero);
		// Add fluff frame description
	}

	private static final int[][] AERO_ARMOR_LOCS = {
			{Aero.LOC_NOSE}, {Aero.LOC_RWING, Aero.LOC_LWING}, {Aero.LOC_AFT}
	};
	
	private void addArmorAndStructure() {
		setModelData("armorValues", addArmorStructureEntries(aero,
				(en, loc) -> en.getOArmor(loc),
				AERO_ARMOR_LOCS));
		if (aero.hasPatchworkArmor()) {
			setModelData("patchworkByLoc", addPatchworkATs(aero, AERO_ARMOR_LOCS));
		}
	}
	
	protected void addWeaponBays(String[][] arcSets) {
		Map<String, List<Mounted>> baysByLoc = aero.getWeaponBayList()
				.stream().collect(Collectors.groupingBy(m -> getArcAbbr(m)));
		List<String> bayArcs = new ArrayList<>();
		Map<String, Integer> heatByLoc = new HashMap<>();
		Map<String, List<Map<String, Object>>> bayDetails = new HashMap<>();
		for (String[] arcSet : arcSets) {
			List<Mounted> bayList = baysByLoc.get(arcSet[0]);
			if (null != bayList) {
				List<Map<String, Object>> rows = new ArrayList<>();
				int heat = 0;
				for (Mounted bay : bayList) {
					Map<String, Object> row = createBayRow(bay);
					heat += ((Number) row.get("heat")).intValue();
					rows.add(row);
				}
				String arcName = Arrays.stream(arcSet).collect(Collectors.joining("/"))
						.replaceAll("\\s+(Fwd|Aft)\\/", "/");
				bayArcs.add(arcName);
				heatByLoc.put(arcName, heat);
				bayDetails.put(arcName, rows);
			}
		}
		setModelData("weaponBayArcs", bayArcs);
		setModelData("weaponBayHeat", heatByLoc);
		setModelData("weaponBays", bayDetails);
	}
	
	private Map<String, Object> createBayRow(Mounted bay) {
		Map<WeaponType, Integer> weaponCount = new HashMap<>();
		int heat = 0;
		int srv = 0;
		int mrv = 0;
		int lrv = 0;
		int erv = 0;
		int multiplier = ((WeaponType) bay.getType()).isCapital()? 10 : 1;
		Map<Integer, Integer> shotsByAmmoType = bay.getBayAmmo().stream()
				.map(eqNum -> aero.getEquipment(eqNum))
				.collect(Collectors.groupingBy(m -> ((AmmoType) m.getType()).getAmmoType(),
						Collectors.summingInt(Mounted::getBaseShotsLeft))); 
		for (Integer eqNum : bay.getBayWeapons()) {
			final Mounted wMount = aero.getEquipment(eqNum);
			if (null == wMount) {
				DefaultMmLogger.getInstance().error(getClass(), "createBayRow(Mounted)",
						"Bay " + bay.getName() + " has non-existent weapon");
				continue;
			}
			final WeaponType wtype = (WeaponType) wMount.getType();
			weaponCount.merge(wtype, 1, Integer::sum);
			heat += wtype.getHeat();
			srv += wtype.getShortAV() * multiplier;
			mrv += wtype.getMedAV() * multiplier;
			lrv += wtype.getLongAV() * multiplier;
			erv += wtype.getExtAV() * multiplier;
		}
		Map<String, Object> retVal = new HashMap<>();
		List<String> weapons = new ArrayList<>();
		for (Map.Entry<WeaponType, Integer> entry : weaponCount.entrySet()) {
			final WeaponType wtype = entry.getKey();
			if (shotsByAmmoType.containsKey(wtype.getAmmoType())) {
				weapons.add(String.format("%d %s (%d shots)",
						entry.getValue(), wtype.getName(),
						shotsByAmmoType.get(wtype.getAmmoType())));
			}
		}
		retVal.put("weapons", weapons);
		retVal.put("heat", heat);
		retVal.put("srv", Math.round(srv / 10.0) + "(" + srv + ")");
		retVal.put("mrv", Math.round(mrv / 10.0) + "(" + mrv + ")");
		retVal.put("lrv", Math.round(lrv / 10.0) + "(" + lrv + ")");
		retVal.put("erv", Math.round(erv / 10.0) + "(" + erv + ")");
		retVal.put("class", bay.getType().getName().replaceAll("\\s+Bay", ""));
		return retVal;
	}
	
	/**
	 * Firing arc abbreviation, which may be different than mounting location for side arcs on
	 * small craft and dropships
	 * 
	 * @param Mounted  The weapon mount
	 * @return         The arc abbreviation.
	 */
	protected String getArcAbbr(Mounted m) {
		return aero.getLocationAbbr(m.getLocation());
	}
	
	protected void addTransportBays() {
		List<Map<String, Object>> bays = new ArrayList<>();
		for (Bay bay : aero.getTransportBays()) {
			if (bay.isQuarters()) {
				continue;
			}
			BayData bayData = BayData.getBayType(bay);
			if (null != bayData) {
				Map<String, Object> bayRow = new HashMap<>();
				bayRow.put("name", bayData.getDisplayName());
				if (bayData.isCargoBay()) {
					bayRow.put("size", bay.getCapacity() + Messages.getString("TROView.tons"));
				} else {
					bayRow.put("size", (int) bay.getCapacity());
				}
				bayRow.put("doors", bay.getDoors());
				bays.add(bayRow);
			} else {
				DefaultMmLogger.getInstance().warning(getClass(), "addBays()",
						"Could not determine bay type for " + bay.toString());
			}
		}
		setModelData("bays", bays);
	}
	
	/**
	 * Adds ammo data used by large craft
	 */
	protected void addAmmo() {
		Map<String, List<Mounted>> ammoByType = aero.getAmmo().stream()
				.collect(Collectors.groupingBy(m -> m.getType().getName()));
		List<Map<String, Object>> ammo = new ArrayList<>();
		for (List<Mounted> aList : ammoByType.values()) {
			Map<String, Object> ammoEntry = new HashMap<>();
			ammoEntry.put("name", aList.get(0).getType().getName().replaceAll("\\s+Ammo", ""));
			ammoEntry.put("shots", aList.stream().mapToInt(Mounted::getUsableShotsLeft).sum());
			ammoEntry.put("tonnage", aList.stream().mapToDouble(m -> m.getType().getTonnage(aero)).sum());
			ammo.add(ammoEntry);
		}
		setModelData("ammo", ammo);
	}

	/**
	 * Convenience method to add the number of crew in a category to a list, and choose the singular or
	 * plural form. The localized string property should be provided for both singular and plural entries,
	 * even if they are the same (such as enlisted/non-rated and bay personnel in English).
	 * 
	 * The model needs to have a "crew" entry initialized to a {@code List<String>} before calling this
	 * method.
	 * 
	 * @param stringKey The key for the string property in the singular form. A "TROView." prefix will be added,
	 *                  and if the plural form is needed "s" will be appended.
	 * @param count     The number of crew in the category
	 * @throws NullPointerException If the "crew" property in the model has not been initialized
	 * @throws ClassCastException   If the crew property of the model is not a {@code List<String>}
	 */
	@SuppressWarnings("unchecked")
	protected void addCrewEntry(String stringKey, int count) {
		if (count > 1) {
			((List<String>) getModelData("crew"))
			.add(String.format(Messages.getString("TROView." + stringKey + "s"), count));
		} else if (count > 2) {
			((List<String>) getModelData("crew"))
			.add(String.format(Messages.getString("TROView." + stringKey), count));
		}
	}

}

